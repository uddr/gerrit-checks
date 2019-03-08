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

import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

public class ListChecksIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;
  private CheckKey checkKey1;
  private CheckKey checkKey2;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checker1Uuid = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checker2Uuid = checkerOperations.newChecker().repository(project).create();

    checkKey1 = CheckKey.create(project, patchSetId, checker1Uuid);
    checkOperations.newCheck(checkKey1).setState(CheckState.RUNNING).upsert();

    checkKey2 = CheckKey.create(project, patchSetId, checker2Uuid);
    checkOperations.newCheck(checkKey2).setState(CheckState.RUNNING).upsert();
  }

  @Test
  public void listAll() throws Exception {
    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listExcludesCheckFromDisabledChecker() throws Exception {
    checkerOperations.checker(checkKey2.checkerUuid()).forUpdate().disable().update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info).containsExactly(checkOperations.check(checkKey1).asInfo());
  }

  @Test
  public void listExcludesCheckFromInvalidChecker() throws Exception {
    checkerOperations.checker(checkKey2.checkerUuid()).forUpdate().forceInvalidConfig().update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info).containsExactly(checkOperations.check(checkKey1).asInfo());
  }
}
