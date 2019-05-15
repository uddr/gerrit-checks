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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.plugins.checks.testing.PendingChecksInfoSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.PendingCheckInfo;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class QueryPendingChecksIT extends AbstractCheckersTest {
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
  public void specifyingQueryIsRequired() throws Exception {
    assertInvalidQuery(null, "query is required");
  }

  @Test
  public void queryCannotBeEmpty() throws Exception {
    assertInvalidQuery("", "query is empty");
  }

  @Test
  public void queryCannotBeEmptyAfterTrim() throws Exception {
    assertInvalidQuery(" ", "query is empty");
  }

  @Test
  public void specifyingCheckerIsRequired() throws Exception {
    assertInvalidQuery("state:NOT_STARTED", "query must contain exactly 1 'checker' operator");
  }

  @Test
  public void cannotQueryPendingChecksForInvalidCheckerUuid() throws Exception {
    assertInvalidQuery(
        "checker:" + CheckerTestData.INVALID_UUID,
        "invalid checker UUID: " + CheckerTestData.INVALID_UUID);
  }

  @Test
  public void cannotSpecifyingMultipleCheckers() throws Exception {
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();

    String expectedMessage = "query must contain exactly 1 'checker' operator";
    assertInvalidQuery(
        String.format("checker:\"%s\" checker:\"%s\"", checkerUuid1, checkerUuid2),
        expectedMessage);
    assertInvalidQuery(
        String.format("checker:\"%s\" OR checker:\"%s\"", checkerUuid1, checkerUuid2),
        expectedMessage);
    assertInvalidQuery(
        String.format(
            "checker:\"%s\" (state:NOT_STARTED checker:\"%s\")", checkerUuid1, checkerUuid2),
        expectedMessage);
  }

  @Test
  public void canSpecifyCheckerAsRootPredicate() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    assertThat(queryPendingChecks(String.format("checker:\"%s\"", checkerUuid))).hasSize(1);
  }

  @Test
  public void canSpecifyCheckerInAndCondition() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    assertThat(
            queryPendingChecks(String.format("checker:\"%s\" AND state:NOT_STARTED", checkerUuid)))
        .hasSize(1);
  }

  @Test
  public void cannotSpecifyCheckerInAndConditionIfNotImmediateChild() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    String expectedMessage =
        "query must be 'checker:<checker-uuid>' or 'checker:<checker-uuid> AND <other-operators>'";
    assertInvalidQuery(
        String.format("state:NOT_STARTED AND (checker:\"%s\" OR state:NOT_STARTED)", checkerUuid),
        expectedMessage);
    assertInvalidQuery(
        String.format("state:NOT_STARTED AND NOT checker:\"%s\"", checkerUuid), expectedMessage);
  }

  @Test
  public void andConditionAtRootCanContainAnyCombinationOfOtherPredicates() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    assertThat(
            queryPendingChecks(
                String.format(
                    "checker:\"%s\" AND (state:NOT_STARTED OR state:RUNNING)", checkerUuid)))
        .hasSize(1);
    assertThat(
            queryPendingChecks(
                String.format("checker:\"%s\" AND NOT state:NOT_STARTED)", checkerUuid)))
        .isEmpty();
    assertThat(
            queryPendingChecks(
                String.format(
                    "checker:\"%s\" AND (NOT state:FAILED AND NOT (state:RUNNING OR"
                        + " state:SUCCESSFUL))",
                    checkerUuid)))
        .hasSize(1);
  }

  @Test
  public void cannotSpecifyCheckerInOrCondition() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    String expectedMessage =
        "query must be 'checker:<checker-uuid>' or 'checker:<checker-uuid> AND <other-operators>'";
    assertInvalidQuery(
        String.format("checker:\"%s\" OR state:NOT_STARTED", checkerUuid), expectedMessage);
    assertInvalidQuery(
        String.format("state:NOT_STARTED OR checker:\"%s\"", checkerUuid), expectedMessage);
  }

  @Test
  public void cannotSpecifyCheckerInNotCondition() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    assertInvalidQuery(
        String.format("NOT checker:\"%s\"", checkerUuid),
        "query must be 'checker:<checker-uuid>' or 'checker:<checker-uuid> AND <other-operators>'");
  }

  @Test
  public void queryPendingChecksForNonExistingChecker() throws Exception {
    assertThat(pendingChecksApi.query("checker:\"non:existing\"").get()).isEmpty();
  }

  @Test
  public void queryPendingChecksNotStartedStateAssumed() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create a check with state "NOT_STARTED" that we expect to be returned.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    // Create a check with state "FAILED" that we expect to be ignored.
    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .state(CheckState.FAILED)
        .upsert();

    // Create a check with state "NOT_STARTED" for other checker that we expect to be ignored.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid2))
        .state(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList = queryPendingChecks(checkerUuid);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasRepository(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void queryPendingChecksForSpecifiedState() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create a check with state "FAILED" that we expect to be returned.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.FAILED)
        .upsert();

    // Create a check with state "NOT_STARTED" that we expect to be ignored.
    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    // Create a check with state "FAILED" for other checker that we expect to be ignored.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid2))
        .state(CheckState.FAILED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList = queryPendingChecks(checkerUuid, CheckState.FAILED);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasRepository(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.FAILED));
  }

  @Test
  public void queryPendingChecksForSpecifiedStateByIsOperator() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create the check once so that in the for-loop we can always update an existing check, rather
    // than needing to check if the check already exists and then depending on this either create or
    // update it.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    for (CheckState checkState : CheckState.values()) {
      checkOperations
          .check(CheckKey.create(project, patchSetId, checkerUuid))
          .forUpdate()
          .state(checkState)
          .upsert();

      assertThat(queryPendingChecks(String.format("checker:\"%s\" is:%s", checkerUuid, checkState)))
          .hasSize(1);
    }
  }

  @Test
  public void queryPendingChecksByIsInprogressOperator() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create the check once so that in the for-loop we can always update an existing check, rather
    // than needing to check if the check already exists and then depending on this either create or
    // update it.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    for (CheckState checkState : CheckState.values()) {
      checkOperations
          .check(CheckKey.create(project, patchSetId, checkerUuid))
          .forUpdate()
          .state(checkState)
          .upsert();

      List<PendingChecksInfo> pendingChecks =
          queryPendingChecks(String.format("checker:\"%s\" is:inprogress", checkerUuid));
      if (checkState.isInProgress()) {
        assertThat(pendingChecks).hasSize(1);
      } else {
        assertThat(pendingChecks).isEmpty();
      }

      pendingChecks =
          queryPendingChecks(String.format("checker:\"%s\" is:in_progress", checkerUuid));
      if (checkState.isInProgress()) {
        assertThat(pendingChecks).hasSize(1);
      } else {
        assertThat(pendingChecks).isEmpty();
      }
    }
  }

  @Test
  public void invalidStateInIsOperatorIsRejected() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    assertInvalidQuery(
        String.format("checker:%s is:foo", checkerUuid), "unsupported operator: is:foo");
  }

  @Test
  public void queryPendingChecksForMultipleSpecifiedStates() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create a check with state "NOT_STARTED" that we expect to be returned.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    // Create a check with state "SCHEDULED" that we expect to be returned.
    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .state(CheckState.SCHEDULED)
        .upsert();

    // Create a check with state "SUCCESSFUL" that we expect to be ignored.
    PatchSet.Id patchSetId3 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId3, checkerUuid))
        .state(CheckState.SUCCESSFUL)
        .upsert();

    // Create a check with state "NOT_STARTED" for other checker that we expect to be ignored.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid2))
        .state(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED, CheckState.SCHEDULED);
    assertThat(pendingChecksList).hasSize(2);

    // The sorting of the pendingChecksList matches the sorting in which the matching changes are
    // returned from the change index, which is by last updated timestamp. Use this knowledge here
    // to do the assertions although the REST endpoint doesn't document a guaranteed sort order.
    PendingChecksInfo pendingChecksChange2 = pendingChecksList.get(0);
    assertThat(pendingChecksChange2).hasRepository(project);
    assertThat(pendingChecksChange2).hasPatchSet(patchSetId2);
    assertThat(pendingChecksChange2)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.SCHEDULED));

    PendingChecksInfo pendingChecksChange1 = pendingChecksList.get(1);
    assertThat(pendingChecksChange1).hasRepository(project);
    assertThat(pendingChecksChange1).hasPatchSet(patchSetId);
    assertThat(pendingChecksChange1)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void queryPendingChecksForSpecifiedStateUnderscoreCanBeOmitted() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // Create the check once so that in the for-loop we can always update an existing check, rather
    // than needing to check if the check already exists and then depending on this either create or
    // update it.
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    for (CheckState checkState : CheckState.values()) {
      checkOperations
          .check(CheckKey.create(project, patchSetId, checkerUuid))
          .forUpdate()
          .state(checkState)
          .upsert();

      List<PendingChecksInfo> pendingChecks =
          queryPendingChecks(
              String.format(
                  "checker:\"%s\" is:%s", checkerUuid, checkState.name().replace("_", "")));
      assertThat(pendingChecks).hasSize(1);
    }
  }

  @Test
  public void queryPendingChecksForSpecifiedStateDifferentCases() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    assertThat(queryPendingChecks(buildQueryString(checkerUuid) + " state:NOT_STARTED")).hasSize(1);
    assertThat(queryPendingChecks(buildQueryString(checkerUuid) + " state:not_started")).hasSize(1);
    assertThat(queryPendingChecks(buildQueryString(checkerUuid) + " state:NoT_StArTeD")).hasSize(1);
    assertThat(queryPendingChecks(buildQueryString(checkerUuid) + " state:NOTSTARTED")).hasSize(1);
    assertThat(queryPendingChecks(buildQueryString(checkerUuid) + " state:notstarted")).hasSize(1);
    assertThat(queryPendingChecks(buildQueryString(checkerUuid) + " state:NoTStArTeD")).hasSize(1);
  }

  @Test
  public void backfillForApplyingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    List<PendingChecksInfo> pendingChecksList = queryPendingChecks(checkerUuid);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasRepository(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void noBackfillForCheckerThatDoesNotApplyToTheProject() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();
    assertThat(queryPendingChecks(checkerUuid)).isEmpty();
  }

  @Test
  public void noBackfillForCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("message:not-matching").create();
    assertThat(queryPendingChecks(checkerUuid)).isEmpty();
  }

  @Test
  public void queryPendingChecksForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isNotEmpty();

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();
    pendingChecksList = queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isEmpty();
  }

  @Test
  public void queryPendingChecksFiltersOutChecksForClosedChangesIfQueryDoesntSpecifyStatus()
      throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).clearQuery().create();

    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(2);

    gApi.changes().id(patchSetId2.changeId().toString()).abandon();

    pendingChecksList = queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(1);
  }

  @Test
  public void queryPendingChecksReturnsChecksForClosedChangesIfQuerySpecifiesStatus()
      throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("is:open OR is:closed").create();

    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    PatchSet.Id patchSetId2 = createChange().getPatchSetId();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId2, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(2);

    gApi.changes().id(patchSetId2.changeId().toString()).abandon();

    pendingChecksList = queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(2);
  }

  @Test
  public void queryPendingChecksForInvalidCheckerFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();

    RestApiException thrown =
        assertThrows(RestApiException.class, () -> queryPendingChecks(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("Cannot query pending checks");
    assertThat(thrown).hasCauseThat().isInstanceOf(ConfigInvalidException.class);
  }

  @Test
  public void queryPendingChecksForCheckerWithInvalidQueryFails() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .query(CheckerTestData.INVALID_QUERY)
            .create();

    RestApiException thrown =
        assertThrows(RestApiException.class, () -> queryPendingChecks(checkerUuid));
    assertThat(thrown).hasMessageThat().contains("Cannot query pending checks");
    assertThat(thrown).hasCauseThat().isInstanceOf(ConfigInvalidException.class);
  }

  @Test
  public void queryPendingChecksWithoutAdministrateCheckersCapabilityWorks() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    requestScopeOperations.setApiUser(user.id());
    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasRepository(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void queryPendingChecksAnonymouslyWorks() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    requestScopeOperations.setApiUserAnonymous();
    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).hasSize(1);
    PendingChecksInfo pendingChecks = Iterables.getOnlyElement(pendingChecksList);
    assertThat(pendingChecks).hasRepository(project);
    assertThat(pendingChecks).hasPatchSet(patchSetId);
    assertThat(pendingChecks)
        .hasPendingChecksMapThat()
        .containsExactly(checkerUuid.get(), new PendingCheckInfo(CheckState.NOT_STARTED));
  }

  @Test
  public void pendingChecksDontIncludeChecksForNonVisibleChanges() throws Exception {
    // restrict project visibility so that it is only visible to administrators
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(adminGroupUuid()))
        .add(block(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    // Check is returned for admin user.
    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isNotEmpty();

    // Check is not returned for non-admin user.
    requestScopeOperations.setApiUser(user.id());
    pendingChecksList = queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isEmpty();
  }

  @Test
  public void pendingChecksDontIncludeChecksForPrivateChangesOfOtherUsers() throws Exception {
    // make change private so that it is only visible to the admin user
    gApi.changes().id(patchSetId.changeId().get()).setPrivate(true);

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    // Check is returned for admin user.
    List<PendingChecksInfo> pendingChecksList =
        queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isNotEmpty();

    // Check is not returned for non-admin user.
    requestScopeOperations.setApiUser(user.id());
    pendingChecksList = queryPendingChecks(checkerUuid, CheckState.NOT_STARTED);
    assertThat(pendingChecksList).isEmpty();
  }

  @Test
  public void pendingChecksViaRest() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations
        .newCheck(CheckKey.create(project, patchSetId, checkerUuid))
        .state(CheckState.NOT_STARTED)
        .upsert();

    RestResponse r =
        adminRestSession.get(
            String.format("/plugins/checks/checks.pending/?q=checker:%s", checkerUuid.get()));
    r.assertOK();
    List<PendingChecksInfo> pendingChecksList =
        newGson().fromJson(r.getReader(), new TypeToken<List<PendingChecksInfo>>() {}.getType());
    r.consume();
    assertThat(pendingChecksList).isNotEmpty();
  }

  private void assertInvalidQuery(String query, String expectedMessage) {
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> pendingChecksApi.query(query).get());
    assertThat(thrown).hasMessageThat().isEqualTo(expectedMessage);
  }

  private List<PendingChecksInfo> queryPendingChecks(String queryString) throws RestApiException {
    return pendingChecksApi.query(queryString).get();
  }

  private List<PendingChecksInfo> queryPendingChecks(
      CheckerUuid checkerUuid, CheckState... checkStates) throws RestApiException {
    return pendingChecksApi.query(buildQueryString(checkerUuid, checkStates)).get();
  }

  private String buildQueryString(CheckerUuid checkerUuid, CheckState... checkStates) {
    StringBuilder queryString = new StringBuilder();
    queryString.append(String.format("checker:%s", checkerUuid));

    StringJoiner stateJoiner = new StringJoiner(" OR state:", " (state:", ")");
    Stream.of(checkStates).map(CheckState::name).forEach(stateJoiner::add);
    queryString.append(stateJoiner.toString());

    return queryString.toString();
  }
}
