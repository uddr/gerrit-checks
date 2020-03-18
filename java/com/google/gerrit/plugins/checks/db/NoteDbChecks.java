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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.plugins.checks.api.CombinedCheckState.CheckStateCount;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/** Class to read checks from NoteDb. */
@Singleton
class NoteDbChecks implements Checks {
  private final ChangeData.Factory changeDataFactory;
  private final CheckNotes.Factory checkNotesFactory;
  private final Checkers checkers;
  private final CheckBackfiller checkBackfiller;
  private final Provider<CheckerQuery> checkerQueryProvider;
  private final GitRepositoryManager repoManager;

  @Inject
  NoteDbChecks(
      ChangeData.Factory changeDataFactory,
      CheckNotes.Factory checkNotesFactory,
      Checkers checkers,
      CheckBackfiller checkBackfiller,
      Provider<CheckerQuery> checkerQueryProvider,
      GitRepositoryManager repoManager) {
    this.changeDataFactory = changeDataFactory;
    this.checkNotesFactory = checkNotesFactory;
    this.checkers = checkers;
    this.checkBackfiller = checkBackfiller;
    this.checkerQueryProvider = checkerQueryProvider;
    this.repoManager = repoManager;
  }

  @Override
  public ImmutableList<Check> getChecks(
      Project.NameKey projectName, PatchSet.Id psId, GetCheckOptions options)
      throws IOException, StorageException {
    return getChecksFromNoteDb(projectName, psId, options);
  }

  @Override
  public Optional<Check> getCheck(CheckKey checkKey, GetCheckOptions options)
      throws StorageException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    Optional<Check> result =
        getChecksFromNoteDb(checkKey.repository(), checkKey.patchSet(), GetCheckOptions.defaults())
            .stream()
            .filter(c -> c.key().checkerUuid().equals(checkKey.checkerUuid()))
            .findAny();

    if (!result.isPresent() && options.backfillChecks()) {
      ChangeData changeData =
          changeDataFactory.create(checkKey.repository(), checkKey.patchSet().changeId());
      return checkBackfiller.getBackfilledCheckForRelevantChecker(
          checkKey.checkerUuid(), changeData, checkKey.patchSet());
    }

    return result;
  }

  private ImmutableList<Check> getChecksFromNoteDb(
      Project.NameKey repositoryName, PatchSet.Id psId, GetCheckOptions options)
      throws StorageException, IOException {
    // TODO(gerrit-team): Instead of reading the complete notes map, read just one note.
    ChangeData changeData = changeDataFactory.create(repositoryName, psId.changeId());
    PatchSet patchSet = changeData.patchSet(psId);
    if (patchSet == null) {
      throw new StorageException("patch set not found: " + psId);
    }

    CheckNotes checkNotes = checkNotesFactory.create(changeData.change());
    checkNotes.load();

    ImmutableList<Check> existingChecks =
        checkNotes.getChecks().getOrDefault(patchSet.commitId(), NoteDbCheckMap.empty()).checks
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
  public CombinedCheckState getCombinedCheckState(
      Project.NameKey projectName, PatchSet.Id patchSetId) throws IOException, StorageException {
    ImmutableListMultimap<CheckState, Boolean> statesAndRequired =
        getStatesAndRequiredMap(projectName, patchSetId);
    return CombinedCheckState.combine(statesAndRequired);
  }

  @Override
  public boolean areAllRequiredCheckersPassing(Project.NameKey projectName, PatchSet.Id patchSetId)
      throws IOException, StorageException {
    ImmutableListMultimap<CheckState, Boolean> statesAndRequired =
        getStatesAndRequiredMap(projectName, patchSetId);
    CheckStateCount checkStateCount = CheckStateCount.create(statesAndRequired);
    return checkStateCount.failedRequiredCount() == 0
        && checkStateCount.inProgressRequiredCount() == 0;
  }

  @Override
  public String getETag(Project.NameKey projectName, Change.Id changeId) throws IOException {
    try (Repository repo = repoManager.openRepository(projectName);
        RevWalk rw = new RevWalk(repo)) {
      Ref checkRef = repo.getRefDatabase().exactRef(CheckerRef.checksRef(changeId));
      return checkRef != null ? checkRef.getObjectId().getName() : ObjectId.zeroId().getName();
    }
  }

  private ImmutableListMultimap<CheckState, Boolean> getStatesAndRequiredMap(
      Project.NameKey projectName, PatchSet.Id patchSetId) throws IOException, StorageException {
    ImmutableMap<String, Checker> allCheckersOfProject =
        checkers.checkersOf(projectName).stream()
            .collect(ImmutableMap.toImmutableMap(c -> c.getUuid().get(), c -> c));

    // Always backfilling checks to have a meaningful "CombinedCheckState" even when there are some
    // or all checks missing.
    ImmutableMap<String, Check> checks =
        getChecks(projectName, patchSetId, GetCheckOptions.withBackfilling()).stream()
            .collect(ImmutableMap.toImmutableMap(c -> c.key().checkerUuid().get(), c -> c));

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

      boolean isRequired = isRequiredForSubmit(checker, patchSetId.changeId());
      statesAndRequired.put(check.state(), isRequired);
    }

    return statesAndRequired.build();
  }

  @Override
  public boolean isRequiredForSubmit(Checker checker, Change.Id changeId) {
    ChangeData changeData = changeDataFactory.create(checker.getRepository(), changeId);

    return checker.getStatus() == CheckerStatus.ENABLED
        && checker.isRequired()
        && checkerQueryProvider.get().isCheckerRelevant(checker, changeData);
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
