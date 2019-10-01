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
import static com.google.gerrit.truth.CacheStatsSubject.assertThat;
import static com.google.gerrit.truth.CacheStatsSubject.cloneStats;

import com.google.common.cache.CacheStats;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.CombinedCheckStateCache;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.ChangeCheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ChangeCheckInfoIT extends AbstractCheckersTest {
  private CombinedCheckStateCache cache;
  private Change.Id changeId;
  private PatchSet.Id psId;

  @Before
  public void setUp() throws Exception {
    cache = plugin.getSysInjector().getInstance(CombinedCheckStateCache.class);
    psId = createChange().getPatchSetId();
    changeId = psId.changeId();
  }

  @Test
  public void infoRequiresOptionViaGet() throws Exception {
    assertThat(
            getChangeCheckInfo(gApi.changes().id(changeId.get()).get(ImmutableListMultimap.of())))
        .isEmpty();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
  }

  @Test
  public void infoRequiresOptionViaQuery() throws Exception {
    List<ChangeInfo> changeInfos = gApi.changes().query("change:" + changeId).get();
    assertThat(changeInfos).hasSize(1);
    assertThat(getChangeCheckInfo(changeInfos.get(0))).isEmpty();
    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
  }

  @Test
  public void noCheckers() throws Exception {
    assertThat(checkerOperations.checkersOf(project)).isEmpty();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
  }

  @Test
  public void backfillRelevantCheckerViaGet() throws Exception {
    checkerOperations.newChecker().repository(project).query("topic:foo").create();
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    gApi.changes().id(changeId.get()).topic("foo");
    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.IN_PROGRESS));
  }

  @Test
  public void combinedCheckStateViaGet() throws Exception {
    CheckerUuid optionalCheckerUuid = checkerOperations.newChecker().repository(project).create();
    CheckerUuid requiredCheckerUuid =
        checkerOperations.newChecker().repository(project).required().create();
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

  @Test
  public void combinedCheckStateViaQuery() throws Exception {
    CacheStats start = cloneStats(cache.getStats());
    long startReloadsFalse = cache.getReloadCount(false);
    long startReloadsTrue = cache.getReloadCount(true);

    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    // Cache hasn't yet populated during update.
    // TODO(xchangcheng): still initialize the cache early without doing in the submit rule.
    assertThat(cache.getStats()).since(start).hasHitCount(0);
    assertThat(cache.getStats()).since(start).hasMissCount(1);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(0);

    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    assertThat(cache.getStats()).since(start).hasHitCount(1);
    assertThat(cache.getStats()).since(start).hasMissCount(1);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(0);
  }

  @Test
  public void loadingCombinedCheckStateViaGetUpdatesCache() throws Exception {
    cache.putForTest(project, psId, CombinedCheckState.FAILED);

    CacheStats start = cloneStats(cache.getStats());
    long startReloadsFalse = cache.getReloadCount(false);
    long startReloadsTrue = cache.getReloadCount(true);

    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.FAILED));
    assertThat(cache.getStats()).since(start).hasHitCount(1);
    assertThat(cache.getStats()).since(start).hasMissCount(0);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(0);

    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    // Incurs reloads in attribute factory paths.
    assertThat(cache.getStats()).since(start).hasHitCount(2);
    assertThat(cache.getStats()).since(start).hasMissCount(0);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);

    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    assertThat(cache.getStats()).since(start).hasHitCount(3);
    assertThat(cache.getStats()).since(start).hasMissCount(0);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);
  }

  @Test
  public void updatingCheckStateUpdatesCache() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    cache.putForTest(project, psId, CombinedCheckState.IN_PROGRESS);

    CacheStats start = cloneStats(cache.getStats());
    long startReloadsFalse = cache.getReloadCount(false);
    long startReloadsTrue = cache.getReloadCount(true);

    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.IN_PROGRESS));
    assertThat(cache.getStats()).since(start).hasHitCount(1);
    assertThat(cache.getStats()).since(start).hasMissCount(0);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(0);

    // Set non-required checker to FAILED, updating combined check state to WARNING.
    CheckInput checkInput = new CheckInput();
    checkInput.state = CheckState.FAILED;
    checksApiFactory.revision(psId).id(checkerUuid).update(checkInput);

    // Incurs reload after updating check state.
    assertThat(cache.getStats()).since(start).hasHitCount(4);
    assertThat(cache.getStats()).since(start).hasMissCount(0);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);

    assertThat(queryChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.WARNING));
    assertThat(cache.getStats()).since(start).hasHitCount(5);
    assertThat(cache.getStats()).since(start).hasMissCount(0);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);
  }

  @Test
  public void repeatedlyLoadingCombinedCheckStateViaGetResultsInOnlyNoOpReloads() throws Exception {
    CacheStats start = cloneStats(cache.getStats());
    long startReloadsFalse = cache.getReloadCount(false);
    long startReloadsTrue = cache.getReloadCount(true);

    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    // Incurs reloads in attribute factory paths.
    assertThat(cache.getStats()).since(start).hasHitCount(0);
    assertThat(cache.getStats()).since(start).hasMissCount(1);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(0);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);

    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    assertThat(cache.getStats()).since(start).hasHitCount(1);
    assertThat(cache.getStats()).since(start).hasMissCount(1);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(1);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);

    assertThat(getChangeCheckInfo(changeId))
        .hasValue(new ChangeCheckInfo("checks", CombinedCheckState.NOT_RELEVANT));
    assertThat(cache.getStats()).since(start).hasHitCount(2);
    assertThat(cache.getStats()).since(start).hasMissCount(1);
    assertThat(cache.getReloadCount(false) - startReloadsFalse).isEqualTo(2);
    assertThat(cache.getReloadCount(true) - startReloadsTrue).isEqualTo(1);
  }

  private Optional<ChangeCheckInfo> getChangeCheckInfo(Change.Id id) throws Exception {
    return getChangeCheckInfo(
        gApi.changes().id(id.get()).get(ImmutableListMultimap.of("checks--combined", "true")));
  }

  private Optional<ChangeCheckInfo> queryChangeCheckInfo(Change.Id id) throws Exception {
    List<ChangeInfo> changeInfos =
        gApi.changes().query("change:" + id).withPluginOption("checks--combined", "true").get();
    assertThat(changeInfos).hasSize(1);
    return getChangeCheckInfo(changeInfos.get(0));
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
