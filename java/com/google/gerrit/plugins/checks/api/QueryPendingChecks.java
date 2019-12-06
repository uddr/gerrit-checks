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

package com.google.gerrit.plugins.checks.api;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.index.query.AndPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.index.CheckPredicate;
import com.google.gerrit.plugins.checks.index.CheckQueryBuilder;
import com.google.gerrit.plugins.checks.index.CheckStatePredicate;
import com.google.gerrit.plugins.checks.index.CheckerPredicate;
import com.google.gerrit.plugins.checks.index.CheckerSchemePredicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.Option;

public class QueryPendingChecks implements RestReadView<TopLevelResource> {
  private final CheckQueryBuilder checkQueryBuilder;
  private final Checkers checkers;
  private final Checks checks;
  private final Provider<CheckerQuery> checkerQueryProvider;
  private String queryString;

  @Option(
      name = "--query",
      aliases = {"-q"},
      metaVar = "QUERY",
      usage = "check query")
  public QueryPendingChecks setQuery(String queryString) {
    this.queryString = queryString;
    return this;
  }

  @Inject
  public QueryPendingChecks(
      CheckQueryBuilder checkQueryBuilder,
      Checkers checkers,
      Checks checks,
      Provider<CheckerQuery> checkerQueryProvider) {
    this.checkQueryBuilder = checkQueryBuilder;
    this.checkers = checkers;
    this.checks = checks;
    this.checkerQueryProvider = checkerQueryProvider;
  }

  public List<PendingChecksInfo> apply() throws Exception {
    return apply(TopLevelResource.INSTANCE).value();
  }

  @Override
  public Response<List<PendingChecksInfo>> apply(TopLevelResource resource)
      throws RestApiException, IOException, ConfigInvalidException, StorageException {
    if (queryString == null) {
      throw new BadRequestException("query is required");
    }

    Predicate<Check> predicate = validateQuery(parseQuery(queryString));
    if (!hasStatePredicate(predicate)) {
      predicate = Predicate.and(new CheckStatePredicate(CheckState.NOT_STARTED), predicate);
    }
    // this variable is for the lambda expressions when using orElseThrow
    final Predicate<Check> finalPredicate = predicate;

    if (countPredicates(predicate, CheckerPredicate.class) == 1) {
      // Checker query
      Optional<Checker> checker =
          checkers.getChecker(
              getCheckerUuidFromQuery(predicate)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              String.format("no checker predicate found: %s", finalPredicate))));
      if (!checker.isPresent() || checker.get().isDisabled()) {
        return Response.ok(ImmutableList.of());
      }
      ImmutableList<ChangeData> changes =
          checkerQueryProvider.get().queryMatchingChanges(checker.get());
      return Response.ok(getPendingChecksOfChecker(checker.get(), predicate, changes));
    }
    // Scheme query
    String scheme =
        getSchemeFromQuery(predicate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format("no checker scheme predicate found: %s", finalPredicate)));
    ImmutableList<Checker> checkersOfScheme = checkers.listCheckers(scheme);
    ImmutableList<ImmutableList<ChangeData>> changes =
        checkerQueryProvider.get().queryMatchingChanges(checkersOfScheme);
    List<PendingChecksInfo> pendingChecks = new ArrayList<>();
    for (int i = 0; i < changes.size(); i++) {
      pendingChecks.addAll(
          getPendingChecksOfChecker(checkersOfScheme.get(i), predicate, changes.get(i)));
    }
    return Response.ok(pendingChecks);
  }

  private List<PendingChecksInfo> getPendingChecksOfChecker(
      Checker checker, Predicate<Check> query, List<ChangeData> changes) throws IOException {

    // The query system can only match against the current patch set; ignore non-current patch sets
    // for now.
    List<PendingChecksInfo> pendingChecks = new ArrayList<>();
    CheckerUuid checkerUuid = checker.getUuid();
    for (ChangeData cd : changes) {
      PatchSet patchSet = cd.currentPatchSet();
      CheckKey checkKey = CheckKey.create(cd.project(), patchSet.id(), checkerUuid);

      // Backfill if check is not present.
      // Backfilling is only done for relevant checkers (checkers where the repository and the query
      // matches the change). Since the change was found by executing the query of the checker we
      // know that the checker is relevant for this patch set and hence backfilling should be done.
      Check check =
          checks
              .getCheck(checkKey, GetCheckOptions.defaults())
              .orElseGet(() -> Check.newBackfilledCheck(cd.project(), patchSet, checker));

      if (query.asMatchable().match(check)) {
        pendingChecks.add(createPendingChecksInfo(cd.project(), patchSet, checkerUuid, check));
      }
    }
    return pendingChecks;
  }

  private Predicate<Check> parseQuery(String query) throws BadRequestException {
    try {
      return checkQueryBuilder.parse(query.trim());
    } catch (QueryParseException e) {
      throw new BadRequestException(e.getMessage());
    }
  }

  private static Predicate<Check> validateQuery(Predicate<Check> predicate)
      throws BadRequestException {
    int numCheckPredicates = countPredicates(predicate, CheckerPredicate.class);
    int numSchemePredicates = countPredicates(predicate, CheckerSchemePredicate.class);
    String exceptionMessage =
        String.format(
            "query must be '%s:<checker-uuid>' or '%s:<checker-uuid> AND <other-operators>' or '%s:<checker-scheme>' or '%s:<checker-scheme> AND <other-operators>'",
            CheckQueryBuilder.FIELD_CHECKER,
            CheckQueryBuilder.FIELD_CHECKER,
            CheckQueryBuilder.FIELD_SCHEME,
            CheckQueryBuilder.FIELD_SCHEME);
    if (numCheckPredicates + numSchemePredicates != 1) {
      throw new BadRequestException(
          String.format(
              "query must contain exactly 1 '%s' operator or '%s' operator",
              CheckQueryBuilder.FIELD_CHECKER, CheckQueryBuilder.FIELD_SCHEME));
    }

    // the root predicate must either be an AndPredicate ....
    if (predicate instanceof AndPredicate) {
      // if the root predicate is an AndPredicate, any of its direct children must be a
      // CheckerPredicate or CheckerSchemePredicate, the other child predicates can be anything
      // (including any combination of
      // AndPredicate, OrPredicate and NotPredicate).
      if (predicate.getChildren().stream().noneMatch(CheckerPredicate.class::isInstance)
          && predicate.getChildren().stream().noneMatch(CheckerSchemePredicate.class::isInstance)) {
        throw new BadRequestException(exceptionMessage);
      }
      // ... or a CheckerPredicate / CheckerSchemePredicate
    } else if (!(predicate instanceof CheckerPredicate
        || predicate instanceof CheckerSchemePredicate)) {
      throw new BadRequestException(exceptionMessage);
    }
    return predicate;
  }

  private static boolean hasStatePredicate(Predicate<Check> predicate) {
    if (predicate instanceof CheckStatePredicate) {
      return true;
    }
    if (predicate.getChildCount() == 0) {
      return false;
    }
    return predicate.getChildren().stream().anyMatch(QueryPendingChecks::hasStatePredicate);
  }

  /**
   * Counts the number of {@link CheckerPredicate}s in the given predicate.
   *
   * <p>This method doesn't validate that the checker predicates appear in any particular location.
   *
   * @param predicate the predicate in which the checker predicates should be counted
   * @param predicateClass the class of the predicate (schema or checker)
   * @return the number of checker predicates in the given predicate
   */
  private static int countPredicates(
      Predicate<Check> predicate, Class<? extends CheckPredicate> predicateClass) {
    if (predicateClass.isInstance(predicate)) {
      return 1;
    }
    if (predicate.getChildCount() == 0) {
      return 0;
    }
    return predicate.getChildren().stream()
        .mapToInt(p -> QueryPendingChecks.countPredicates(p, predicateClass))
        .sum();
  }

  private static Optional<CheckerUuid> getCheckerUuidFromQuery(Predicate<Check> predicate) {
    // the query validation (see #validateQuery(Predicate<Check>)) ensures that there is exactly 1
    // CheckerPredicate or CheckerSchemePredicate and that it is on the first or second level of the
    // predicate tree.

    if (predicate instanceof CheckerPredicate) {
      return Optional.of(((CheckerPredicate) predicate).getCheckerUuid());
    }

    Optional<CheckerPredicate> checkerPredicate =
        predicate.getChildren().stream()
            .filter(CheckerPredicate.class::isInstance)
            .map(p -> (CheckerPredicate) p)
            .findAny();
    return checkerPredicate.map(CheckerPredicate::getCheckerUuid);
  }

  private static Optional<String> getSchemeFromQuery(Predicate<Check> predicate) {
    // the query validation (see #validateQuery(Predicate<Check>)) ensures that there is exactly 1
    // CheckerPredicate or CheckerSchemePredicate and that it is on the first or second level of the
    // predicate tree.

    if (predicate instanceof CheckerSchemePredicate) {
      return Optional.of(((CheckerSchemePredicate) predicate).getCheckerScheme());
    }

    Optional<CheckerSchemePredicate> checkerSchemePredicate =
        predicate.getChildren().stream()
            .filter(CheckerSchemePredicate.class::isInstance)
            .map(p -> (CheckerSchemePredicate) p)
            .findAny();
    return checkerSchemePredicate.map(CheckerSchemePredicate::getCheckerScheme);
  }

  private static PendingChecksInfo createPendingChecksInfo(
      Project.NameKey repositoryName, PatchSet patchSet, CheckerUuid checkerUuid, Check check) {
    PendingChecksInfo pendingChecksInfo = new PendingChecksInfo();

    pendingChecksInfo.patchSet = new CheckablePatchSetInfo();
    pendingChecksInfo.patchSet.repository = repositoryName.get();
    pendingChecksInfo.patchSet.changeNumber = patchSet.id().changeId().get();
    pendingChecksInfo.patchSet.patchSetId = patchSet.number();

    pendingChecksInfo.pendingChecks =
        ImmutableMap.of(checkerUuid.get(), new PendingCheckInfo(check.state()));

    return pendingChecksInfo;
  }
}
