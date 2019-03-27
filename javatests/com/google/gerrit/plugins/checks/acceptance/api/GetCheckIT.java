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

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GetCheckIT extends AbstractCheckersTest {
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
  public void getCheck() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    assertThat(checksApiFactory.revision(patchSetId).id(checkerUuid).get())
        .isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckReturnsRepository() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).repository).isEqualTo(project.get());
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).repository)
        .isEqualTo(project.get());
  }

  @Test
  public void getCheckReturnsChangeNumber() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).changeNumber)
        .isEqualTo(patchSetId.getParentKey().get());
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).changeNumber)
        .isEqualTo(patchSetId.getParentKey().get());
  }

  @Test
  public void getCheckReturnsPatchSetId() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).patchSetId).isEqualTo(patchSetId.get());
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).patchSetId)
        .isEqualTo(patchSetId.get());
  }

  @Test
  public void getCheckReturnsCheckerUuid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).checkerUuid).isEqualTo(checkerUuid.get());
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).checkerUuid)
        .isEqualTo(checkerUuid.get());
  }

  @Test
  public void getCheckReturnsState() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).state).isEqualTo(CheckState.RUNNING);
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).state)
        .isEqualTo(CheckState.RUNNING);
  }

  @Test
  public void getCheckReturnsStateNotStartedIfStateNotSet() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).state).isEqualTo(CheckState.NOT_STARTED);
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).state)
        .isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void getCheckReturnsCreationTimestamp() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    Timestamp expectedCreationTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).created).isEqualTo(expectedCreationTimestamp);
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).created)
        .isEqualTo(expectedCreationTimestamp);
  }

  @Test
  public void getCheckReturnsUpdatedTimestamp() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    Timestamp expectedUpdatedTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).created).isEqualTo(expectedUpdatedTimestamp);
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).created)
        .isEqualTo(expectedUpdatedTimestamp);
  }

  @Test
  public void getCheckReturnsCheckerNameOnlyForCheckerOption() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name("My Checker").create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).checkerName).isNull();
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).checkerName)
        .isEqualTo("My Checker");
  }

  @Test
  public void getCheckReturnsCheckerStatusOnlyForCheckerOption() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).checkerStatus).isNull();
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).checkerStatus)
        .isEqualTo(CheckerStatus.ENABLED);
  }

  @Test
  public void getCheckReturnsBlockingConditionsOnlyForCheckerOption() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).blocking).isNull();
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).blocking)
        .containsExactly(BlockingCondition.STATE_NOT_PASSING);
  }

  @Test
  public void getCheckForCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(otherProject).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("message:not-matching").create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForCheckerWithUnsupportedOperatorInQuery() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .query(CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR)
            .create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForCheckerWithInvalidQuery() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .query(CheckerTestData.INVALID_QUERY)
            .create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForNonExistingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().deleteRef().update();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
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
    expected.repository = checkKey.repository().get();
    expected.changeNumber = checkKey.patchSet().getParentKey().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().get();
    expected.state = CheckState.NOT_STARTED;
    expected.created = psCreated;
    expected.updated = psCreated;
    assertThat(getCheckInfo(patchSetId, checkerUuid)).isEqualTo(expected);
  }

  @Test
  public void getNonExistingCheckDoesNotBackfillForDisabledChecker() throws Exception {
    Change.Id changeId = patchSetId.getParentKey();
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo expected = new CheckInfo();
    expected.repository = checkKey.repository().get();
    expected.changeNumber = checkKey.patchSet().getParentKey().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().get();
    expected.state = CheckState.NOT_STARTED;
    expected.created = psCreated;
    expected.updated = psCreated;
    assertThat(getCheckInfo(patchSetId, checkerUuid)).isEqualTo(expected);

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertCheckNotFound(patchSetId, checkerUuid);
  }

  @Test
  public void getCheckForInvalidCheckerUuid() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid checker UUID: " + CheckerTestData.INVALID_UUID);
    checksApiFactory.revision(patchSetId).id(CheckerTestData.INVALID_UUID);
  }

  @Test
  public void getCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void checkForDeletedChangeDoesNotExist() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    gApi.changes().id(patchSetId.getParentKey().get()).delete();

    try {
      getCheckInfo(patchSetId, checkerUuid);
      assert_().fail("expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      assertThat(e)
          .hasMessageThat()
          .ignoringCase()
          .contains(String.format("change %d", patchSetId.getParentKey().get()));
    }
  }

  private CheckInfo getCheckInfo(
      PatchSet.Id patchSetId, CheckerUuid checkerUuid, ListChecksOption... options)
      throws RestApiException, OrmException {
    return checksApiFactory.revision(patchSetId).id(checkerUuid).get(options);
  }

  private Timestamp getPatchSetCreated(Change.Id changeId) throws RestApiException {
    return getOnlyElement(
            gApi.changes().id(changeId.get()).get(CURRENT_REVISION).revisions.values())
        .created;
  }

  private void assertCheckNotFound(PatchSet.Id patchSetId, CheckerUuid checkerUuid)
      throws Exception {
    try {
      getCheckInfo(patchSetId, checkerUuid);
      assert_().fail("expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              String.format(
                  "Patch set %s in repository %s doesn't have check for checker %s.",
                  patchSetId, project, checkerUuid));
    }
  }
}
