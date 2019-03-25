// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.checks.testing.PendingChecksInfoSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.PendingCheckInfo;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ListPendingChecksIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));

    patchSetId = createChange().getPatchSetId();
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void specifyingCheckerUuidIsRequired() throws Exception {
    // The extension API doesn't allow to not specify a checker UUID. Call the endpoint over REST to
    // test this.
    RestResponse response = adminRestSession.get("/plugins/checks/checks.pending/");
    response.assertBadRequest();
    assertThat(response.getEntityContent()).isEqualTo("checker UUID is required");
  }

  @Test
  public void cannotListPendingChecksForInvalidCheckerUuid() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid checker UUID: " + CheckerTestData.INVALID_UUID);
    pendingChecksApi.list(CheckerTestData.INVALID_UUID);
  }

  @Test
  public void cannotListPendingChecksForNonExistingChecker() throws Exception {
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("checker non:existing not found");
    pendingChecksApi.list("non:existing");
  }

  @Test
  public void listPendingChecksNotStartedStateAssumed() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create a check with state "NOT_STARTED" that we expect to be returned.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    // Create a check with state "FAILED" that we expect to be ignored.
    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .setState(CheckState.FAILED)
        .upsert();

    // Create a check with state "NOT_STARTED" for other checker that we expect to be ignored.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid2))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList = pendingChecksApi.list(checkerUuid);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasProject(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.toString(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void listPendingChecksForSpecifiedState() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create a check with state "FAILED" that we expect to be returned.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.FAILED)
        .upsert();

    // Create a check with state "NOT_STARTED" that we expect to be ignored.
    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    // Create a check with state "FAILED" for other checker that we expect to be ignored.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid2))
        .setState(CheckState.FAILED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.FAILED);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasProject(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.toString(), new PendingCheckInfo(CheckState.FAILED));
  }

  @Test
  public void listPendingChecksForMultipleSpecifiedStates() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create a check with state "NOT_STARTED" that we expect to be returned.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    // Create a check with state "SCHEDULED" that we expect to be returned.
    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .setState(CheckState.SCHEDULED)
        .upsert();

    // Create a check with state "SUCCESSFUL" that we expect to be ignored.
    PatchSet.Id patchSetId3 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId3, checkerUuid))
        .setState(CheckState.SUCCESSFUL)
        .upsert();

    // Create a check with state "NOT_STARTED" for other checker that we expect to be ignored.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid2))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED, CheckState.SCHEDULED);
    assertThat(pendingChecksList).hasSize(2);

    // The sorting of the pendingChecksList matches the sorting in which the matching changes are
    // returned from the change index, which is by last updated timestamp. Use this knowledge here
    // to do the assertions although the REST endpoint doesn't document a guaranteed sort order.
    PendingChecksInfo pendingChecksChange2 = pendingChecksList.get(0);
    assertThat(pendingChecksChange2).hasProject(project);
    assertThat(pendingChecksChange2).hasPatchSet(patchSetId2);
    assertThat(pendingChecksChange2)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.toString(), new PendingCheckInfo(CheckState.SCHEDULED));

    PendingChecksInfo pendingChecksChange1 = pendingChecksList.get(1);
    assertThat(pendingChecksChange1).hasProject(project);
    assertThat(pendingChecksChange1).hasPatchSet(patchSetId);
    assertThat(pendingChecksChange1)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.toString(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void backfillForApplyingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    List<PendingChecksInfo> pendingChecksList = pendingChecksApi.list(checkerUuid);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasProject(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.toString(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void noBackfillForCheckerThatDoesNotApplyToTheProject() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();
    assertThat(pendingChecksApi.list(checkerUuid)).isEmpty();
  }

  @Test
  public void noBackfillForCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("message:not-matching").create();
    assertThat(pendingChecksApi.list(checkerUuid)).isEmpty();
  }

  @Test
  public void listPendingChecksForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isNotEmpty();

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();
    pendingChecksList = pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isEmpty();
  }

  @Test
  public void listPendingChecksFiltersOutChecksForClosedChangesIfQueryDoesntSpecifyStatus()
      throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).clearQuery().create();

    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(2);

    gApi.changes().id(patchSetId2.getParentKey().toString()).abandon();

    pendingChecksList = pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(1);
  }

  @Test
  public void listPendingChecksReturnsChecksForClosedChangesIfQuerySpecifiesStatus()
      throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("is:open OR is:closed").create();

    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(2);

    gApi.changes().id(patchSetId2.getParentKey().toString()).abandon();

    pendingChecksList = pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(2);
  }

  @Test
  public void listPendingChecksForInvalidCheckerFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    exception.expect(RestApiException.class);
    exception.expectMessage("Cannot list pending checks");
    exception.expectCause(instanceOf(ConfigInvalidException.class));
    pendingChecksApi.list(checkerUuid);
  }

  @Test
  public void listPendingChecksWithoutAdministrateCheckersCapabilityWorks() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    requestScopeOperations.setApiUser(user.getId());
    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasProject(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.toString(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void pendingChecksDontIncludeChecksForNonVisibleChanges() throws Exception {
    // restrict project visibility so that it is only visible to administrators
    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.allow(u.getConfig(), Permission.READ, adminGroupUuid(), "refs/*");
      Util.block(u.getConfig(), Permission.READ, REGISTERED_USERS, "refs/*");
      u.save();
    }

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    // Check is returned for admin user.
    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isNotEmpty();

    // Check is not returned for non-admin user.
    requestScopeOperations.setApiUser(user.getId());
    pendingChecksList = pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isEmpty();
  }

  @Test
  public void pendingChecksDontIncludeChecksForPrivateChangesOfOtherUsers() throws Exception {
    // make change private so that it is only visible to the admin user
    gApi.changes().id(patchSetId.getParentKey().get()).setPrivate(true);

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .setState(CheckState.NOT_STARTED)
        .upsert();

    // Check is returned for admin user.
    List<PendingChecksInfo> pendingChecksList =
        pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isNotEmpty();

    // Check is not returned for non-admin user.
    requestScopeOperations.setApiUser(user.getId());
    pendingChecksList = pendingChecksApi.list(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isEmpty();
  }
}
