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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeData.Factory;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
class CheckBackfiller {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeData.Factory changeDataFactory;
  private final Checkers checkers;
  private final Provider<AnonymousUser> anonymousUserProvider;
  private final Provider<ChangeQueryBuilder> queryBuilderProvider;

  @Inject
  CheckBackfiller(
      Factory changeDataFactory,
      Checkers checkers,
      Provider<AnonymousUser> anonymousUserProvider,
      Provider<ChangeQueryBuilder> queryBuilderProvider) {
    this.changeDataFactory = changeDataFactory;
    this.checkers = checkers;
    this.anonymousUserProvider = anonymousUserProvider;
    this.queryBuilderProvider = queryBuilderProvider;
  }

  ImmutableList<Check> getBackfilledChecksForRelevantCheckers(
      Collection<Checker> candidates, ChangeNotes notes, PatchSet.Id psId) throws OrmException {
    if (candidates.isEmpty()) {
      return ImmutableList.of();
    }
    ChangeData cd = changeDataFactory.create(notes);
    if (!psId.equals(cd.change().currentPatchSetId())) {
      // The query system can only match against the current patch set; it doesn't make sense to
      // backfill checkers for old patch sets.
      return ImmutableList.of();
    }
    // All candidates need to be checked for relevance. Any relevant checkers are reported as
    // NOT_STARTED, with creation time matching the patch set.
    ImmutableList.Builder<Check> result = ImmutableList.builderWithExpectedSize(candidates.size());
    PatchSet ps = cd.patchSet(psId);
    ChangeQueryBuilder queryBuilder = newQueryBuilder();
    for (Checker checker : candidates) {
      if (matches(checker, cd, queryBuilder)) {
        // Add synthetic check at the creation time of the patch set.
        result.add(newBackfilledCheck(cd, ps, checker));
      }
    }
    return result.build();
  }

  Optional<Check> getBackfilledCheckForRelevantChecker(
      CheckerUuid candidate, ChangeNotes notes, PatchSet.Id psId) throws OrmException, IOException {
    ChangeData cd = changeDataFactory.create(notes);
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
        || !matches(checker.get(), cd, newQueryBuilder())) {
      return Optional.empty();
    }
    return Optional.of(newBackfilledCheck(cd, cd.patchSet(psId), checker.get()));
  }

  private Check newBackfilledCheck(ChangeData cd, PatchSet ps, Checker checker) {
    return Check.builder(CheckKey.create(cd.project(), ps.getId(), checker.getUuid()))
        .setState(CheckState.NOT_STARTED)
        .setCreated(ps.getCreatedOn())
        .setUpdated(ps.getCreatedOn())
        .build();
  }

  private ChangeQueryBuilder newQueryBuilder() {
    return queryBuilderProvider.get().asUser(anonymousUserProvider.get());
  }

  private static boolean matches(Checker checker, ChangeData cd, ChangeQueryBuilder queryBuilder)
      throws OrmException {
    if (!checker.getQuery().isPresent()) {
      return cd.change().isNew();
    }
    String query = checker.getQuery().get();
    Predicate<ChangeData> predicate;
    try {
      predicate = queryBuilder.parse(query);
    } catch (QueryParseException e) {
      logger.atWarning().withCause(e).log(
          "skipping invalid query for checker %s: %s", checker.getUuid(), query);
      return false;
    }
    if (!predicate.isMatchable()) {
      // Assuming nobody modified the query behind Gerrit's back, this is programmer error:
      // CheckerQuery should not be able to produce non-matchable queries.
      logger.atWarning().log(
          "skipping non-matchable query for checker %s: %s", checker.getUuid(), query);
      return false;
    }
    if (!hasStatusPredicate(predicate)) {
      predicate = Predicate.and(ChangeStatusPredicate.open(), predicate);
    }
    return predicate.asMatchable().match(cd);
  }

  private static boolean hasStatusPredicate(Predicate<ChangeData> predicate) {
    if (predicate instanceof IndexPredicate) {
      return ((IndexPredicate<ChangeData>) predicate)
          .getField()
          .getName()
          .equals(ChangeField.STATUS.getName());
    }
    return predicate.getChildren().stream().anyMatch(CheckBackfiller::hasStatusPredicate);
  }
}
