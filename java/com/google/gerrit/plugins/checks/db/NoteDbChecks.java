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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Class to read checks from NoteDb. */
@Singleton
class NoteDbChecks implements Checks {
  private final ChangeData.Factory changeDataFactory;
  private final CheckNotes.Factory checkNotesFactory;
  private final Checkers checkers;
  private final CheckBackfiller checkBackfiller;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final Provider<ChangeQueryBuilder> queryBuilderProvider;

  @Inject
  NoteDbChecks(
      ChangeData.Factory changeDataFactory,
      CheckNotes.Factory checkNotesFactory,
      Checkers checkers,
      CheckBackfiller checkBackfiller,
      Provider<AnonymousUser> anonymousUserProvider,
      Provider<ChangeQueryBuilder> queryBuilderProvider) {
    this.changeDataFactory = changeDataFactory;
    this.checkNotesFactory = checkNotesFactory;
    this.checkers = checkers;
    this.checkBackfiller = checkBackfiller;
    this.anonymousUserProvider = anonymousUserProvider;
    this.queryBuilderProvider = queryBuilderProvider;
  }

  @Override
  public ImmutableList<Check> getChecks(
      Project.NameKey projectName, PatchSet.Id psId, GetCheckOptions options)
      throws IOException, OrmException {
    return getChecksFromNoteDb(projectName, psId, options);
  }

  @Override
  public Optional<Check> getCheck(CheckKey checkKey, GetCheckOptions options)
      throws OrmException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    Optional<Check> result =
        getChecksFromNoteDb(checkKey.repository(), checkKey.patchSet(), GetCheckOptions.defaults())
            .stream()
            .filter(c -> c.key().checkerUuid().equals(checkKey.checkerUuid()))
            .findAny();

    if (!result.isPresent() && options.backfillChecks()) {
      ChangeData changeData =
          changeDataFactory.create(checkKey.repository(), checkKey.patchSet().getParentKey());
      return checkBackfiller.getBackfilledCheckForRelevantChecker(
          checkKey.checkerUuid(), changeData, checkKey.patchSet());
    }

    return result;
  }

  private ImmutableList<Check> getChecksFromNoteDb(
      Project.NameKey repositoryName, PatchSet.Id psId, GetCheckOptions options)
      throws OrmException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    ChangeData changeData = changeDataFactory.create(repositoryName, psId.getParentKey());
    PatchSet patchSet = changeData.patchSet(psId);
    CheckNotes checkNotes = checkNotesFactory.create(changeData.change());
    checkNotes.load();

    ImmutableList<Check> existingChecks =
        checkNotes.getChecks().getOrDefault(patchSet.getRevision(), NoteDbCheckMap.empty()).checks
            .entrySet().stream()
            .map(e -> e.getValue().toCheck(repositoryName, psId, CheckerUuid.parse(e.getKey())))
            .collect(toImmutableList());

    if (!options.backfillChecks()) {
      return existingChecks;
    }

    ImmutableList<Checker> checkersForBackfiller =
        getCheckersForBackfiller(repositoryName, existingChecks);
    ImmutableList<Check> backfilledChecks =
        checkBackfiller.getBackfilledChecksForRelevantCheckers(
            checkersForBackfiller, changeData, psId);

    return Stream.concat(existingChecks.stream(), backfilledChecks.stream())
        .collect(toImmutableList());
  }

  @Override
  public CombinedCheckState getCombinedCheckState(NameKey projectName, Id patchSetId)
      throws IOException, OrmException {
    ChangeData changeData = changeDataFactory.create(projectName, patchSetId.changeId);
    ImmutableMap<String, Checker> allCheckersOfProject =
        checkers.checkersOf(projectName).stream()
            .collect(ImmutableMap.toImmutableMap(c -> c.getUuid().get(), c -> c));

    // Always backfilling checks to have a meaningful "CombinedCheckState" even when there are some
    // or all checks missing.
    ImmutableMap<String, Check> checks =
        getChecks(projectName, patchSetId, GetCheckOptions.withBackfilling()).stream()
            .collect(ImmutableMap.toImmutableMap(c -> c.key().checkerUuid().get(), c -> c));

    ChangeQueryBuilder queryBuilder =
        queryBuilderProvider.get().asUser(anonymousUserProvider.get());
    ImmutableListMultimap.Builder<CheckState, Boolean> statesAndRequired =
        ImmutableListMultimap.builder();

    for (Map.Entry<String, Check> entry : checks.entrySet()) {
      String checkerUuid = entry.getKey();
      Check check = entry.getValue();

      Checker checker = allCheckersOfProject.get(checkerUuid);
      if (checker == null) {
        // A not-relevant check.
        statesAndRequired.put(check.state(), false);
        continue;
      }

      boolean isRequired =
          checker.getStatus() == CheckerStatus.ENABLED
              && checker.isRequired()
              && checker.isCheckerRelevant(changeData, queryBuilder);
      statesAndRequired.put(check.state(), isRequired);
    }

    return CombinedCheckState.combine(statesAndRequired.build());
  }

  private ImmutableList<Checker> getCheckersForBackfiller(
      Project.NameKey projectName, List<Check> existingChecks) throws IOException {
    ImmutableSet<CheckerUuid> checkersWithExistingChecks =
        existingChecks.stream().map(c -> c.key().checkerUuid()).collect(toImmutableSet());
    return checkers.checkersOf(projectName).stream()
        .filter(c -> !checkersWithExistingChecks.contains(c.getUuid()))
        .collect(toImmutableList());
  }
}
