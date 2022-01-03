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

package com.google.gerrit.plugins.checks.rules;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ChecksSubmitRuleTest {
  @Test
  public void loadingCurrentPatchSetFails() throws Exception {
    ChecksSubmitRule checksSubmitRule = new ChecksSubmitRule(mock(Checks.class));

    ChangeData cd = mock(ChangeData.class);
    when(cd.project()).thenReturn(Project.nameKey("My-Project"));
    when(cd.getId()).thenReturn(Change.id(1));
    when(cd.currentPatchSet()).thenThrow(new IllegalStateException("Fail for test"));

    Optional<SubmitRecord> submitRecords = checksSubmitRule.evaluate(cd);
    assertErrorRecord(submitRecords, "failed to load the current patch set of change 1");
  }

  @Test
  public void getCombinedCheckStateFails() throws Exception {
    Checks checks = mock(Checks.class);
    when(checks.areAllRequiredCheckersPassing(any(), any()))
        .thenThrow(new IOException("Fail for test"));
    when(checks.areAllCheckersOptionalForSubmit(any(), any())).thenReturn(false);

    ChecksSubmitRule checksSubmitRule = new ChecksSubmitRule(checks);

    Change.Id changeId = Change.id(1);
    ChangeData cd = mock(ChangeData.class);
    when(cd.project()).thenReturn(Project.nameKey("My-Project"));
    when(cd.getId()).thenReturn(Change.id(1));
    when(cd.currentPatchSet())
        .thenReturn(
            PatchSet.builder()
                .id(PatchSet.id(changeId, 1))
                .commitId(ObjectId.zeroId())
                .uploader(Account.id(1000))
                .createdOn(TimeUtil.now())
                .build());

    Optional<SubmitRecord> submitRecords = checksSubmitRule.evaluate(cd);
    assertErrorRecord(submitRecords, "failed to evaluate check states for change 1");
  }

  @Test
  public void returnsEmptyOptionalIfAllCheckersAreOptional() throws Exception {
    Checks checks = mock(Checks.class);
    when(checks.areAllCheckersOptionalForSubmit(any(), any())).thenReturn(true);

    ChecksSubmitRule checksSubmitRule = new ChecksSubmitRule(checks);

    ChangeData cd = mock(ChangeData.class);
    Change.Id changeId = Change.id(1);
    when(cd.project()).thenReturn(Project.nameKey("My-Project"));
    when(cd.getId()).thenReturn(Change.id(1));
    when(cd.currentPatchSet())
        .thenReturn(
            PatchSet.builder()
                .id(PatchSet.id(changeId, 1))
                .commitId(ObjectId.zeroId())
                .uploader(Account.id(1000))
                .createdOn(TimeUtil.now())
                .build());

    Optional<SubmitRecord> submitRecord = checksSubmitRule.evaluate(cd);
    assertThat(submitRecord).isEmpty();
  }

  private static void assertErrorRecord(
      Optional<SubmitRecord> submitRecord, String expectedErrorMessage) {
    assertThat(submitRecord).isPresent();

    assertThat(submitRecord.get().status).isEqualTo(SubmitRecord.Status.RULE_ERROR);
    assertThat(submitRecord.get().errorMessage).isEqualTo(expectedErrorMessage);
  }
}
