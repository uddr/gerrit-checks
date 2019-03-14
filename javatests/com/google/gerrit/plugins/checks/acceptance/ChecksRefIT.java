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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class ChecksRefIT extends AbstractCheckersTest {
  @Inject private ProjectOperations projectOperations;
  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void noteDbRefOfCheckIsRemovedWhenChangeIsDeleted() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.NOT_STARTED).upsert();
    String noteDbChecksRef = CheckerRef.checksRef(patchSetId.getParentKey());
    assertThat(projectOperations.project(project).hasHead(noteDbChecksRef)).isTrue();

    gApi.changes().id(patchSetId.getParentKey().get()).delete();

    assertThat(projectOperations.project(project).hasHead(noteDbChecksRef)).isFalse();
  }
}
