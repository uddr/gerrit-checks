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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.rest.util.RestCall.Method;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckSubmitImpactInfo;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

@UseClockStep(startAtEpoch = true)
public class GetCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private String changeId;
  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result r = createChange();
    changeId = r.getChangeId();
    patchSetId = r.getPatchSetId();
  }

  @Test
  public void getCheck() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.RUNNING).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid))
        .isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckWithOptions() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .name("My Checker")
            .description("Description")
            .create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.RUNNING).upsert();

    CheckInfo expectedCheckInfo = checkOperations.check(checkKey).asInfo();
    expectedCheckInfo.repository = project.get();
    expectedCheckInfo.checkerName = "My Checker";
    expectedCheckInfo.checkerStatus = CheckerStatus.ENABLED;
    expectedCheckInfo.blocking = ImmutableSortedSet.of();
    expectedCheckInfo.submitImpact = new CheckSubmitImpactInfo();
    expectedCheckInfo.submitImpact.required = null;
    expectedCheckInfo.checkerDescription = "Description";
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER))
        .isEqualTo(expectedCheckInfo);
  }

  @Test
  public void getCheckWithOptionsViaRest() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name("My Checker").create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.RUNNING).upsert();

    CheckInfo expectedCheckInfo = checkOperations.check(checkKey).asInfo();
    expectedCheckInfo.repository = project.get();
    expectedCheckInfo.checkerName = "My Checker";
    expectedCheckInfo.checkerStatus = CheckerStatus.ENABLED;
    expectedCheckInfo.blocking = ImmutableSortedSet.of();
    expectedCheckInfo.submitImpact = new CheckSubmitImpactInfo();
    expectedCheckInfo.submitImpact.required = null;

    RestResponse r =
        adminRestSession.get(
            String.format(
                "/changes/%s/revisions/%s/checks~checks/%s?o=CHECKER",
                patchSetId.changeId().get(), patchSetId.get(), checkerUuid.get()));
    r.assertOK();
    CheckInfo checkInfo =
        newGson().fromJson(r.getReader(), new TypeToken<CheckInfo>() {}.getType());
    r.consume();
    assertThat(checkInfo).isEqualTo(expectedCheckInfo);

    r =
        adminRestSession.get(
            String.format(
                "/changes/%s/revisions/%s/checks~checks/%s?O=1",
                patchSetId.changeId().get(), patchSetId.get(), checkerUuid.get()));
    r.assertOK();
    checkInfo = newGson().fromJson(r.getReader(), new TypeToken<CheckInfo>() {}.getType());
    r.consume();
    assertThat(checkInfo).isEqualTo(expectedCheckInfo);
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
        .isEqualTo(patchSetId.changeId().get());
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).changeNumber)
        .isEqualTo(patchSetId.changeId().get());
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
    checkOperations.newCheck(checkKey).state(CheckState.RUNNING).upsert();

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
  public void getCheckReturnsSubmitImpactOnlyForCheckerOption() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid).blocking).isNull();
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).blocking)
        .isNotEmpty();
    assertThat(getCheckInfo(patchSetId, checkerUuid).submitImpact).isNull();
    assertThat(getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).submitImpact)
        .isNotNull();
  }

  @Test
  public void notApplyingButRequiredCheckerIsNotRequiredForSubmission() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .query("status:merged")
            .required()
            .create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();
    assertThat(
            getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).submitImpact.required)
        .isNull();
  }

  @Test
  public void disabledButRequiredCheckerIsNotRequiredForSubmission() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).disable().required().create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();
    assertThat(
            getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER).submitImpact.required)
        .isNull();
  }

  @Test
  public void getCheckWithCheckerOptionReturnsCheckEvenIfCheckerIsInvalid() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name("My Checker").create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();

    CheckInfo check = getCheckInfo(patchSetId, checkerUuid, ListChecksOption.CHECKER);
    assertThat(check).isNotNull();

    // Checker fields are not set.
    assertThat(check.checkerName).isNull();
    assertThat(check.blocking).isNull();
    assertThat(check.submitImpact).isNull();
    assertThat(check.checkerStatus).isNull();

    // Check that at least some non-checker fields are set to ensure that we didn't get a completely
    // empty CheckInfo.
    assertThat(check.repository).isEqualTo(project.get());
    assertThat(check.checkerUuid).isEqualTo(checkerUuid.get());
    assertThat(check.changeNumber).isEqualTo(patchSetId.changeId().get());
    assertThat(check.patchSetId).isEqualTo(patchSetId.get());
    assertThat(check.state).isEqualTo(CheckState.RUNNING);
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
    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void noBackfillForInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();

    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();
    assertCheckNotFound(patchSetId, checkerUuid);
  }

  @Test
  public void getChecksForMultiplePatchSets() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey1).state(CheckState.FAILED).upsert();

    PatchSet.Id currentPatchSetId = createPatchSet();
    CheckKey checkKey2 = CheckKey.create(project, currentPatchSetId, checkerUuid);
    checkOperations.newCheck(checkKey2).state(CheckState.RUNNING).upsert();

    // get check for the old patch set
    assertThat(checksApiFactory.revision(patchSetId).id(checkerUuid).get())
        .isEqualTo(checkOperations.check(checkKey1).asInfo());

    // get check for the current patch set (2 ways)
    assertThat(checksApiFactory.revision(currentPatchSetId).id(checkerUuid).get())
        .isEqualTo(checkOperations.check(checkKey2).asInfo());
    assertThat(checksApiFactory.currentRevision(currentPatchSetId.changeId()).id(checkerUuid).get())
        .isEqualTo(checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void noBackfillForNonCurrentPatchSet() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    PatchSet.Id currentPatchSetId = createPatchSet();
    assertCheckNotFound(patchSetId, checkerUuid);
    assertThat(getCheckInfo(currentPatchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckForNonExistingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forInvalidation().deleteRef().invalidate();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getNonExistingCheckBackfillsForRelevantChecker() throws Exception {
    String topic = name("topic");
    Change.Id changeId = patchSetId.changeId();
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("topic:" + topic).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    assertCheckNotFound(patchSetId, checkerUuid);

    gApi.changes().id(changeId.get()).topic(topic);

    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo expected = new CheckInfo();
    expected.repository = checkKey.repository().get();
    expected.changeNumber = checkKey.patchSet().changeId().get();
    expected.patchSetId = checkKey.patchSet().get();
    expected.checkerUuid = checkKey.checkerUuid().get();
    expected.state = CheckState.NOT_STARTED;
    expected.created = psCreated;
    expected.updated = psCreated;
    assertThat(getCheckInfo(patchSetId, checkerUuid)).isEqualTo(expected);
  }

  @Test
  public void getNonExistingCheckDoesNotBackfillForDisabledChecker() throws Exception {
    Change.Id changeId = patchSetId.changeId();
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo expected = new CheckInfo();
    expected.repository = checkKey.repository().get();
    expected.changeNumber = checkKey.patchSet().changeId().get();
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
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> checksApiFactory.revision(patchSetId).id(CheckerTestData.INVALID_UUID));
    assertThat(thrown)
        .hasMessageThat()
        .contains("invalid checker UUID: " + CheckerTestData.INVALID_UUID);
  }

  @Test
  public void getCheckForInvalidCheckerUuidViaRest() throws Exception {
    RestApiCallHelper.execute(
        adminRestSession,
        RestCall.builder(Method.GET, "/changes/%s/revisions/%s/checks~checks/%s")
            .expectedResponseCode(SC_BAD_REQUEST)
            .expectedMessage("invalid checker UUID: " + CheckerTestData.INVALID_UUID)
            .build(),
        Integer.toString(patchSetId.changeId().get()),
        Integer.toString(patchSetId.get()),
        CheckerTestData.INVALID_UUID);
  }

  @Test
  public void getCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void getCheckAnonymously() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    assertThat(getCheckInfo(patchSetId, checkerUuid)).isNotNull();
  }

  @Test
  public void checkForDeletedChangeDoesNotExist() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    gApi.changes().id(patchSetId.changeId().get()).delete();

    ResourceNotFoundException thrown =
        assertThrows(ResourceNotFoundException.class, () -> getCheckInfo(patchSetId, checkerUuid));
    assertThat(thrown)
        .hasMessageThat()
        .ignoringCase()
        .contains(String.format("change %d", patchSetId.changeId().get()));
  }

  @Test
  public void getCheckOnChangeEditRejected() throws Exception {
    int changeId = patchSetId.changeId().get();
    gApi.changes().id(changeId).edit().modifyCommitMessage("new message");
    Optional<EditInfo> editInfo = gApi.changes().id(changeId).edit().get();
    assertThat(editInfo).isPresent();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    RestResponse response =
        adminRestSession.get(
            "/changes/" + changeId + "/revisions/edit/checks~checks/" + checkerUuid.get());

    response.assertConflict();
    assertThat(response.getEntityContent()).isEqualTo("checks are not supported on a change edit");
  }

  private CheckInfo getCheckInfo(
      PatchSet.Id patchSetId, CheckerUuid checkerUuid, ListChecksOption... options)
      throws RestApiException {
    return checksApiFactory.revision(patchSetId).id(checkerUuid).get(options);
  }

  private Timestamp getPatchSetCreated(Change.Id changeId) throws RestApiException {
    return getOnlyElement(
            gApi.changes().id(changeId.get()).get(CURRENT_REVISION).revisions.values())
        .created;
  }

  private void assertCheckNotFound(PatchSet.Id patchSetId, CheckerUuid checkerUuid)
      throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(ResourceNotFoundException.class, () -> getCheckInfo(patchSetId, checkerUuid));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Patch set %s in repository %s doesn't have check for checker %s.",
                patchSetId, project, checkerUuid));
  }

  private PatchSet.Id createPatchSet() throws Exception {
    PushOneCommit.Result r = amendChange(changeId);
    PatchSet.Id currentPatchSetId = r.getPatchSetId();
    assertThat(patchSetId.changeId()).isEqualTo(currentPatchSetId.changeId());
    assertThat(patchSetId.get()).isLessThan(currentPatchSetId.get());
    return currentPatchSetId;
  }
}
