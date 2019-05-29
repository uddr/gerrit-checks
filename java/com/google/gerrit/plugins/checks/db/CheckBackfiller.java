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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
class CheckBackfiller {
  private final Checkers checkers;
  private final Provider<CheckerQuery> checkerQueryProvider;

  @Inject
  CheckBackfiller(Checkers checkers, Provider<CheckerQuery> checkerQueryProvider) {
    this.checkers = checkers;
    this.checkerQueryProvider = checkerQueryProvider;
  }

  ImmutableList<Check> getBackfilledChecksForRelevantCheckers(
      Collection<Checker> candidates, ChangeData cd, PatchSet.Id psId) throws StorageException {
    if (candidates.isEmpty()) {
      return ImmutableList.of();
    }
    if (!psId.equals(cd.change().currentPatchSetId())) {
      // The query system can only match against the current patch set; it doesn't make sense to
      // backfill checkers for old patch sets.
      return ImmutableList.of();
    }
    // All candidates need to be checked for relevance. Any relevant checkers are reported as
    // NOT_STARTED, with creation time matching the patch set.
    CheckerQuery checkerQuery = checkerQueryProvider.get();
    ImmutableList.Builder<Check> result = ImmutableList.builderWithExpectedSize(candidates.size());
    PatchSet ps = cd.patchSet(psId);
    for (Checker checker : candidates) {
      if (checkerQuery.isCheckerRelevant(checker, cd)) {
        // Add synthetic check at the creation time of the patch set.
        result.add(Check.newBackfilledCheck(cd.project(), ps, checker));
      }
    }
    return result.build();
  }

  Optional<Check> getBackfilledCheckForRelevantChecker(
      CheckerUuid candidate, ChangeData cd, PatchSet.Id psId) throws StorageException, IOException {
    if (!psId.equals(cd.change().currentPatchSetId())) {
      // The query system can only match against the current patch set; it doesn't make sense to
      // backfill checkers for old patch sets.
      return Optional.empty();
    }

    Optional<Checker> checker;
    try {
      checker = checkers.getChecker(candidate);
    } catch (ConfigInvalidException e) {
      // Match behavior of Checkers#checkersOf, ignoring invalid config.
      return Optional.empty();
    }
    if (!checker.isPresent()
        || checker.get().getStatus() != CheckerStatus.ENABLED
        || !checkerQueryProvider.get().isCheckerRelevant(checker.get(), cd)) {
      return Optional.empty();
    }
    return Optional.of(Check.newBackfilledCheck(cd.project(), cd.patchSet(psId), checker.get()));
  }
}
