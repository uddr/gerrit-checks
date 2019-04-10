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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.plugins.checks.api.BlockingCondition.STATE_NOT_PASSING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.ChangeCheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ChangeCheckInfoIT extends AbstractCheckersTest {
  private Change.Id changeId;
  private PatchSet.Id psId;

  @Before
  public void setUp() throws Exception {
    psId = createChange().getPatchSetId();
    changeId = psId.getParentKey();
  }

  @Test
  public void infoRequiresOption() throws Exception {
    assertThat(
            getChangeCheckInfo(gApi.changes().id(changeId.get()).get(ImmutableListMultimap.of())))
        .isEmpty();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
  }

  @Test
  public void noCheckers() throws Exception {
    assertThat(checkerOperations.checkersOf(project)).isEmpty();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
  }

  @Test
  public void backfillRelevantChecker() throws Exception {
    checkerOperations.newChecker().repository(project).query("topic:foo").create();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    gApi.changes().id(changeId.get()).topic("foo");
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.IN_PROGRESS));
  }

  @Test
  public void combinedCheckState() throws Exception {
    CheckerUuid optionalCheckerUuid = checkerOperations.newChecker().repository(project).create();
    CheckerUuid requiredCheckerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .blockingConditions(STATE_NOT_PASSING)
            .create();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.IN_PROGRESS));
    checkOperations
        .newCheck(CheckKey.create(project, psId, optionalCheckerUuid))
        .state(CheckState.SUCCESSFUL)
        .upsert();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.IN_PROGRESS));
    checkOperations
        .newCheck(CheckKey.create(project, psId, requiredCheckerUuid))
        .state(CheckState.FAILED)
        .upsert();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.FAILED));
  }

  private Optional<ChangeCheckInfo> getChangeCheckInfo(Change.Id id) throws Exception {
    return getChangeCheckInfo(
        gApi.changes().id(id.get()).get(ImmutableListMultimap.of("checks--combined", "true")));
  }

  private static Optional<ChangeCheckInfo> getChangeCheckInfo(ChangeInfo changeInfo) {
    if (changeInfo.plugins == null) {
      return Optional.empty();
    }
    ImmutableList<PluginDefinedInfo> infos =
        changeInfo.plugins.stream().filter(i -> i.name.equals("checks")).collect(toImmutableList());
    if (infos.isEmpty()) {
      return Optional.empty();
    }
    assertThat(infos).hasSize(1);
    assertThat(infos.get(0)).isInstanceOf(ChangeCheckInfo.class);
    return Optional.of((ChangeCheckInfo) infos.get(0));
  }
}
