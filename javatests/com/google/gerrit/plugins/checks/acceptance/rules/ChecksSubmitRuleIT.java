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

package com.google.gerrit.plugins.checks.acceptance.rules;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.TestCheckerCreation.Builder;
import com.google.gerrit.plugins.checks.api.CheckState;
import org.junit.Before;
import org.junit.Test;

public class ChecksSubmitRuleIT extends AbstractCheckersTest {
  private static final SubmitRequirementInfo SUBMIT_REQUIREMENT_INFO =
      new SubmitRequirementInfo("NOT_READY", "All required checks must pass", "checks_pass");

  private String testChangeId;
  private PatchSet.Id testPatchSetId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit.Result result = createChange();
    testPatchSetId = result.getPatchSetId();
    testChangeId = result.getChangeId();

    // Approves "Code-Review" label so that the change only needs to meet the submit requirements
    // about checks.
    approve(testChangeId);
  }

  @Test
  public void changeWithoutAnyApplyingChecksMayBeSubmitted() throws Exception {
    // Set up a checker which applies to a different repository than the considered change and don't
    // post any check result to the change. (More realistic than having no checkers at all.)
    checkerOperations.newChecker().repository(allProjects).required().create();

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void notStartedRequiredCheckBlocksSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.NOT_STARTED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
    assertThat(changeInfo.requirements).containsExactly(SUBMIT_REQUIREMENT_INFO);
  }

  @Test
  public void scheduledRequiredCheckBlocksSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.SCHEDULED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
    assertThat(changeInfo.requirements).containsExactly(SUBMIT_REQUIREMENT_INFO);
  }

  @Test
  public void runningRequiredCheckBlocksSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.RUNNING);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
    assertThat(changeInfo.requirements).containsExactly(SUBMIT_REQUIREMENT_INFO);
  }

  @Test
  public void failedRequiredCheckBlocksSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
    assertThat(changeInfo.requirements).containsExactly(SUBMIT_REQUIREMENT_INFO);
  }

  @Test
  public void successfulRequiredCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.SUCCESSFUL);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void notRelevantRequiredCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().create();
    postCheckResult(checkerUuid, CheckState.NOT_RELEVANT);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void notStartedCheckOfNonApplicableCheckerDoesNotBlockSubmit() throws Exception {
    // Set up a checker which applies to a different repository than the considered change but still
    // post a check result to the change. -> Check is considered as optional.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(allProjects).required().create();
    postCheckResult(checkerUuid, CheckState.NOT_STARTED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void runningCheckOfNonApplicableCheckerDoesNotBlockSubmit() throws Exception {
    // Set up a checker which applies to a different repository than the considered change but still
    // post a check result to the change. -> Check is considered as optional.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(allProjects).required().create();
    postCheckResult(checkerUuid, CheckState.RUNNING);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void failedCheckOfNonApplicableCheckerDoesNotBlockSubmit() throws Exception {
    // Set up a checker which applies to a different repository than the considered change but still
    // post a check result to the change. -> Check is considered as optional.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(allProjects).required().create();
    postCheckResult(checkerUuid, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void notStartedDisabledRequiredCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().disable().create();
    postCheckResult(checkerUuid, CheckState.NOT_STARTED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void runningDisabledRequiredCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().disable().create();
    postCheckResult(checkerUuid, CheckState.RUNNING);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void failedDisabledRequiredCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newRequiredChecker().disable().create();
    postCheckResult(checkerUuid, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void notStartedOptionalCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newOptionalChecker().create();
    postCheckResult(checkerUuid, CheckState.NOT_STARTED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void runningOptionalCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newOptionalChecker().create();
    postCheckResult(checkerUuid, CheckState.RUNNING);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void failedOptionalCheckDoesNotBlockSubmit() throws Exception {
    CheckerUuid checkerUuid = newOptionalChecker().create();
    postCheckResult(checkerUuid, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  @Test
  public void oneFailedRequiredCheckAmongSuccessfulOnesBlocksSubmit() throws Exception {
    CheckerUuid requiredCheckerUuid1 = newRequiredChecker().create();
    CheckerUuid requiredCheckerUuid2 = newRequiredChecker().create();
    CheckerUuid optionalCheckerUuid = newOptionalChecker().create();
    postCheckResult(requiredCheckerUuid1, CheckState.SUCCESSFUL);
    postCheckResult(requiredCheckerUuid2, CheckState.FAILED);
    postCheckResult(optionalCheckerUuid, CheckState.SUCCESSFUL);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isFalse();
    assertThat(changeInfo.requirements).containsExactly(SUBMIT_REQUIREMENT_INFO);
  }

  @Test
  public void oneFailedOptionalCheckAmongSuccessfulOnesDoesNotBlockSubmit() throws Exception {
    CheckerUuid requiredCheckerUuid1 = newRequiredChecker().create();
    CheckerUuid requiredCheckerUuid2 = newRequiredChecker().create();
    CheckerUuid optionalCheckerUuid = newOptionalChecker().create();
    postCheckResult(requiredCheckerUuid1, CheckState.SUCCESSFUL);
    postCheckResult(requiredCheckerUuid2, CheckState.SUCCESSFUL);
    postCheckResult(optionalCheckerUuid, CheckState.FAILED);

    ChangeInfo changeInfo = gApi.changes().id(testChangeId).get();

    assertThat(changeInfo.submittable).isTrue();
    assertThat(changeInfo.requirements).isEmpty();
  }

  private Builder newRequiredChecker() {
    return checkerOperations.newChecker().repository(project).required();
  }

  private Builder newOptionalChecker() {
    return checkerOperations.newChecker().repository(project).optional();
  }

  private void postCheckResult(CheckerUuid checkerUuid, CheckState checkState) {
    CheckKey checkKey = CheckKey.create(project, testPatchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(checkState).upsert();
  }
}
