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
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.Collection;
import org.easymock.EasyMock;
import org.junit.Test;

public class ChecksSubmitRuleTest extends GerritBaseTests {
  @Test
  public void loadingCurrentPatchSetFails() throws Exception {
    ChecksSubmitRule checksSubmitRule =
        new ChecksSubmitRule(EasyMock.createStrictMock(Checks.class));

    ChangeData cd = EasyMock.createStrictMock(ChangeData.class);
    expect(cd.project()).andReturn(Project.nameKey("My-Project"));
    expect(cd.getId()).andReturn(new Change.Id(1));
    expect(cd.currentPatchSet()).andThrow(new StorageException("Fail for test"));
    replay(cd);

    Collection<SubmitRecord> submitRecords =
        checksSubmitRule.evaluate(cd, SubmitRuleOptions.defaults());
    assertErrorRecord(submitRecords, "failed to load the current patch set of change 1");
  }

  @Test
  public void getCombinedCheckStateFails() throws Exception {
    Checks checks = EasyMock.createStrictMock(Checks.class);
    expect(checks.getCombinedCheckState(anyObject(), anyObject()))
        .andThrow(new StorageException("Fail for test"));
    replay(checks);

    ChecksSubmitRule checksSubmitRule = new ChecksSubmitRule(checks);

    Change.Id changeId = new Change.Id(1);
    ChangeData cd = EasyMock.createStrictMock(ChangeData.class);
    expect(cd.project()).andReturn(Project.nameKey("My-Project"));
    expect(cd.getId()).andReturn(new Change.Id(1));
    expect(cd.currentPatchSet()).andReturn(new PatchSet(PatchSet.id(changeId, 1)));
    replay(cd);

    Collection<SubmitRecord> submitRecords =
        checksSubmitRule.evaluate(cd, SubmitRuleOptions.defaults());
    assertErrorRecord(submitRecords, "failed to evaluate check states for change 1");
  }

  private static void assertErrorRecord(
      Collection<SubmitRecord> submitRecords, String expectedErrorMessage) {
    assertThat(submitRecords).hasSize(1);

    SubmitRecord submitRecord = Iterables.getOnlyElement(submitRecords);
    assertThat(submitRecord.status).isEqualTo(SubmitRecord.Status.RULE_ERROR);
    assertThat(submitRecord.errorMessage).isEqualTo(expectedErrorMessage);
  }
}
