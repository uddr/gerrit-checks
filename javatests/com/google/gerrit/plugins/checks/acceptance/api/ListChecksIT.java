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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckSubmitImpactInfo;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ListChecksIT extends AbstractCheckersTest {
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
  public void listAll() throws Exception {
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).state(CheckState.RUNNING).upsert();

    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).state(CheckState.FAILED).upsert();

    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listAllWithOptions() throws Exception {
    String checkerName1 = "Checker One";
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().name(checkerName1).repository(project).create();

    String checkerName2 = "Checker Two";
    CheckerUuid checkerUuid2 =
        checkerOperations.newChecker().name(checkerName2).repository(project).create();

    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).state(CheckState.RUNNING).upsert();

    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).state(CheckState.FAILED).upsert();

    CheckInfo expectedCheckInfo1 = checkOperations.check(checkKey1).asInfo();
    expectedCheckInfo1.repository = project.get();
    expectedCheckInfo1.checkerName = checkerName1;
    expectedCheckInfo1.blocking = ImmutableSet.of();
    expectedCheckInfo1.submitImpact = new CheckSubmitImpactInfo();
    expectedCheckInfo1.submitImpact.required = null;
    expectedCheckInfo1.checkerStatus = CheckerStatus.ENABLED;

    CheckInfo expectedCheckInfo2 = checkOperations.check(checkKey2).asInfo();
    expectedCheckInfo2.repository = project.get();
    expectedCheckInfo2.checkerName = checkerName2;
    expectedCheckInfo2.blocking = ImmutableSet.of();
    expectedCheckInfo2.submitImpact = new CheckSubmitImpactInfo();
    expectedCheckInfo2.submitImpact.required = null;
    expectedCheckInfo2.checkerStatus = CheckerStatus.ENABLED;

    assertThat(checksApiFactory.revision(patchSetId).list(ListChecksOption.CHECKER))
        .containsExactly(expectedCheckInfo1, expectedCheckInfo2);
  }

  @Test
  public void listAllWithOptionsViaRest() throws Exception {
    String checkerName1 = "Checker One";
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().name(checkerName1).repository(project).create();

    String checkerName2 = "Checker Two";
    CheckerUuid checkerUuid2 =
        checkerOperations.newChecker().name(checkerName2).repository(project).create();

    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).state(CheckState.RUNNING).upsert();

    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).state(CheckState.FAILED).upsert();

    CheckInfo expectedCheckInfo1 = checkOperations.check(checkKey1).asInfo();
    expectedCheckInfo1.repository = project.get();
    expectedCheckInfo1.checkerName = checkerName1;
    expectedCheckInfo1.blocking = ImmutableSet.of();
    expectedCheckInfo1.submitImpact = new CheckSubmitImpactInfo();
    expectedCheckInfo1.submitImpact.required = null;
    expectedCheckInfo1.checkerStatus = CheckerStatus.ENABLED;

    CheckInfo expectedCheckInfo2 = checkOperations.check(checkKey2).asInfo();
    expectedCheckInfo2.repository = project.get();
    expectedCheckInfo2.checkerName = checkerName2;
    expectedCheckInfo2.blocking = ImmutableSet.of();
    expectedCheckInfo2.submitImpact = new CheckSubmitImpactInfo();
    expectedCheckInfo2.submitImpact.required = null;
    expectedCheckInfo2.checkerStatus = CheckerStatus.ENABLED;

    RestResponse r =
        adminRestSession.get(
            String.format(
                "/changes/%s/revisions/%s/checks~checks/?o=CHECKER",
                patchSetId.changeId().get(), patchSetId.get()));
    r.assertOK();
    List<CheckInfo> checkInfos =
        newGson().fromJson(r.getReader(), new TypeToken<List<CheckInfo>>() {}.getType());
    r.consume();
    assertThat(checkInfos).containsExactly(expectedCheckInfo1, expectedCheckInfo2);

    r =
        adminRestSession.get(
            String.format(
                "/changes/%s/revisions/%s/checks~checks/?O=1",
                patchSetId.changeId().get(), patchSetId.get()));
    r.assertOK();
    checkInfos = newGson().fromJson(r.getReader(), new TypeToken<List<CheckInfo>>() {}.getType());
    r.consume();
    assertThat(checkInfos).containsExactly(expectedCheckInfo1, expectedCheckInfo2);
  }

  @Test
  public void listAllWithOptionsSkipsPopulatingCheckerFieldsForInvalidCheckers() throws Exception {
    String checkerName1 = "Checker One";
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().name(checkerName1).repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid1)).upsert();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid2)).upsert();
    checkerOperations.checker(checkerUuid2).forInvalidation().nonParseableConfig().invalidate();

    List<CheckInfo> checks = checksApiFactory.revision(patchSetId).list(ListChecksOption.CHECKER);

    assertThat(checks).hasSize(2);

    Optional<CheckInfo> maybeCheck1 =
        checks.stream().filter(c -> c.checkerUuid.equals(checkerUuid1.get())).findAny();
    assertThat(maybeCheck1).isPresent();
    CheckInfo check1 = maybeCheck1.get();
    assertThat(check1.checkerName).isEqualTo(checkerName1);
    assertThat(check1.blocking).isEmpty();
    assertThat(check1.submitImpact).isNotNull();
    assertThat(check1.checkerStatus).isEqualTo(CheckerStatus.ENABLED);

    Optional<CheckInfo> maybeCheck2 =
        checks.stream().filter(c -> c.checkerUuid.equals(checkerUuid2.get())).findAny();
    assertThat(maybeCheck2).isPresent();
    CheckInfo check2 = maybeCheck2.get();
    assertThat(check2.checkerName).isNull();
    assertThat(check2.blocking).isNull();
    assertThat(check2.submitImpact).isNull();
    assertThat(check2.checkerStatus).isNull();
  }

  @Test
  public void listIncludesCheckFromCheckerThatDoesNotApplyToTheProject() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    checkerOperations.checker(checkerUuid).forUpdate().repository(otherProject).update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().query("message:not-matching").update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromNonExistingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forInvalidation().deleteRef().invalidate();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listBackfillsForRelevantChecker() throws Exception {
    String topic = name("topic");
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("topic:" + topic).create();

    assertThat(checksApiFactory.revision(patchSetId).list()).isEmpty();

    // Update change to match checker's query.
    gApi.changes().id(patchSetId.changeId().get()).topic(topic);

    Timestamp psCreated = getPatchSetCreated(patchSetId.changeId());
    CheckInfo checkInfo = new CheckInfo();
    checkInfo.repository = project.get();
    checkInfo.changeNumber = patchSetId.changeId().get();
    checkInfo.patchSetId = patchSetId.get();
    checkInfo.checkerUuid = checkerUuid.get();
    checkInfo.state = CheckState.NOT_STARTED;
    checkInfo.created = psCreated;
    checkInfo.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).list()).containsExactly(checkInfo);
  }

  @Test
  public void listDoesntBackfillForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    Timestamp psCreated = getPatchSetCreated(patchSetId.changeId());
    CheckInfo checkInfo = new CheckInfo();
    checkInfo.repository = checkKey.repository().get();
    checkInfo.changeNumber = checkKey.patchSet().changeId().get();
    checkInfo.patchSetId = checkKey.patchSet().get();
    checkInfo.checkerUuid = checkKey.checkerUuid().get();
    checkInfo.state = CheckState.NOT_STARTED;
    checkInfo.created = psCreated;
    checkInfo.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).list()).containsExactly(checkInfo);

    // Disable checker.
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertThat(checksApiFactory.revision(patchSetId).list()).isEmpty();
  }

  @Test
  public void listForMultiplePatchSets() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    // create check on the first patch set
    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey1).state(CheckState.SUCCESSFUL).upsert();

    // create a second patch set
    PatchSet.Id currentPatchSet = createPatchSet();

    // create check on the second patch set, we expect that this check is not returned for the first
    // patch set
    CheckKey checkKey2 = CheckKey.create(project, currentPatchSet, checkerUuid);
    checkOperations.newCheck(checkKey2).state(CheckState.RUNNING).upsert();

    // list checks for the old patch set
    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(checkOperations.check(checkKey1).asInfo());

    // list checks for the new patch set (2 ways)
    assertThat(checksApiFactory.currentRevision(currentPatchSet.changeId()).list())
        .containsExactly(checkOperations.check(checkKey2).asInfo());
    assertThat(checksApiFactory.revision(currentPatchSet).list())
        .containsExactly(checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void noBackfillForNonCurrentPatchSet() throws Exception {
    checkerOperations.newChecker().repository(project).create();

    PatchSet.Id currentPatchSet = createPatchSet();

    assertThat(checksApiFactory.revision(patchSetId).list()).isEmpty();
    assertThat(checksApiFactory.revision(currentPatchSet).list()).hasSize(1);
  }

  @Test
  public void listAllWithoutAdministrateCheckers() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    requestScopeOperations.setApiUser(user.id());

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listAllAnonymously() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();

    requestScopeOperations.setApiUserAnonymous();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listAllOnChangeEditRejected() throws Exception {
    gApi.changes().id(changeId).edit().modifyCommitMessage("new message");
    Optional<EditInfo> editInfo = gApi.changes().id(changeId).edit().get();
    assertThat(editInfo).isPresent();

    RestResponse response =
        adminRestSession.get("/changes/" + changeId + "/revisions/edit/checks?o=CHECKER");
    response.assertConflict();
    assertThat(response.getEntityContent()).isEqualTo("checks are not supported on a change edit");
  }

  private Timestamp getPatchSetCreated(Change.Id changeId) throws RestApiException {
    return getOnlyElement(
            gApi.changes().id(changeId.get()).get(CURRENT_REVISION).revisions.values())
        .created;
  }

  private PatchSet.Id createPatchSet() throws Exception {
    PushOneCommit.Result r = amendChange(changeId);
    PatchSet.Id currentPatchSetId = r.getPatchSetId();
    assertThat(patchSetId.changeId()).isEqualTo(currentPatchSetId.changeId());
    assertThat(patchSetId.get()).isLessThan(currentPatchSetId.get());
    return currentPatchSetId;
  }
}
