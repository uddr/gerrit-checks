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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import java.sql.Timestamp;
import org.junit.Before;
import org.junit.Test;

public class GetCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void getCheck() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    CheckInfo expected = new CheckInfo();
    expected.project = checkKey.project().get();
    expected.changeNumber = checkKey.patchSet().getParentKey().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().toString();
    expected.state = CheckState.RUNNING;
    expected.created = checkOperations.check(checkKey).get().created();
    expected.updated = expected.created;
    assertThat(checkInfo).isEqualTo(expected);
  }

  @Test
  public void getCheckWithOptions() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .name("My Checker")
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .repository(project)
            .create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    CheckInfo checkInfo =
        checksApiFactory.revision(patchSetId).id(checkerUuid).get(ListChecksOption.CHECKER);
    CheckInfo expected = new CheckInfo();
    expected.project = checkKey.project().get();
    expected.changeNumber = checkKey.patchSet().getParentKey().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().toString();
    expected.state = CheckState.RUNNING;
    expected.created = checkOperations.check(checkKey).get().created();
    expected.updated = expected.created;
    expected.checkerName = "My Checker";
    expected.blocking = ImmutableSet.of(BlockingCondition.STATE_NOT_PASSING);
    expected.checkerStatus = CheckerStatus.ENABLED;
    assertThat(checkInfo).isEqualTo(expected);
  }

  @Test
  public void getCheckForCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(otherProject).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckForCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("message:not-matching").create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckForInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckForNonExistingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().deleteRef().update();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getNonExistingCheckBackfillsForRelevantChecker() throws Exception {
    String topic = name("topic");
    Change.Id changeId = patchSetId.getParentKey();
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("topic:" + topic).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    assertCheckNotFound(patchSetId, checkerUuid);

    gApi.changes().id(changeId.get()).topic(topic);

    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo expected = new CheckInfo();
    expected.project = checkKey.project().get();
    expected.changeNumber = checkKey.patchSet().getParentKey().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().toString();
    expected.state = CheckState.NOT_STARTED;
    expected.created = psCreated;
    expected.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).id(checkerUuid).get()).isEqualTo(expected);
  }

  @Test
  public void getNonExistingCheckDoesNotBackfillForDisabledChecker() throws Exception {
    Change.Id changeId = patchSetId.getParentKey();
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo expected = new CheckInfo();
    expected.project = checkKey.project().get();
    expected.changeNumber = checkKey.patchSet().getParentKey().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().toString();
    expected.state = CheckState.NOT_STARTED;
    expected.created = psCreated;
    expected.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).id(checkerUuid).get()).isEqualTo(expected);

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertCheckNotFound(patchSetId, checkerUuid);
  }

  @Test
  public void getCheckForInvalidCheckerUuid() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid checker UUID: malformed::checker*UUID");
    checksApiFactory.revision(patchSetId).id("malformed::checker*UUID");
  }

  @Test
  public void getCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void checkForDeletedChangeDoesNotExist() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    gApi.changes().id(patchSetId.getParentKey().get()).delete();

    try {
      checksApiFactory.revision(patchSetId).id(checkerUuid).get();
      assert_().fail("expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      assertThat(e)
          .hasMessageThat()
          .ignoringCase()
          .contains(String.format("change %d", patchSetId.getParentKey().get()));
    }
  }

  private Timestamp getPatchSetCreated(Change.Id changeId) throws RestApiException {
    return getOnlyElement(
            gApi.changes().id(changeId.get()).get(CURRENT_REVISION).revisions.values())
        .created;
  }

  private void assertCheckNotFound(PatchSet.Id patchSetId, CheckerUuid checkerUuid)
      throws Exception {
    try {
      checksApiFactory.revision(patchSetId).id(checkerUuid);
      assert_().fail("expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Patch set %s in project %s doesn't have check for checker %s.",
                  patchSetId, project, checkerUuid));
    }
  }
}
