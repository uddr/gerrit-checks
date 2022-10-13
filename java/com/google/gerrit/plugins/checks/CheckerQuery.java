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

package com.google.gerrit.plugins.checks;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.index.query.QueryParser.AND;
import static com.google.gerrit.index.query.QueryParser.DEFAULT_FIELD;
import static com.google.gerrit.index.query.QueryParser.FIELD_NAME;
import static com.google.gerrit.index.query.QueryParser.NOT;
import static com.google.gerrit.index.query.QueryParser.OR;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryParser;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction.ActionType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.antlr.runtime.tree.Tree;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * Utility for validating and executing relevancy queries for checkers.
 *
 * <p>Instances are not threadsafe and should not be reused across requests. However, they may be
 * reused within a single request.
 */
public class CheckerQuery {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Note that this list contains *operators*, not predicates. If there are multiple operators
  // aliased together to the same predicate ("f:", "file:"), they all need to be listed explicitly.
  //
  // We chose to list operators instead of predicates because:
  //  * It insulates us from changes in implementation details of the query system, such as
  //    predicate classes being renamed, or additional predicates being ORed into existing
  //    operators.
  //  * It's easier to keep in sync with the documentation.
  //
  // This doesn't rule out switching to predicates in the future, particularly if the predicate
  // classes gain some informative methods like "boolean matchMethodNeedsToQueryIndex()".
  //
  // Predicates that definitely cannot be allowed:
  //  * Anything where match() needs to query the index, i.e. any full-text fields. This includes
  //    the default field.
  //  * Anything where post-filtering is unreasonably expensive.
  //  * Any predicate over projects, since that may conflict with the projects field.
  //
  // Beyond that, this set is mostly based on what we subjectively consider useful for limiting the
  // changes that a checker runs on. It will probably grow, based on user feedback.
  private static final ImmutableSortedSet<String> ALLOWED_OPERATORS =
      ImmutableSortedSet.of(
          "added",
          "after",
          "age",
          "assignee",
          "author",
          "before",
          "branch",
          "committer",
          "deleted",
          "delta",
          "destination",
          "dir",
          "directory",
          "ext",
          "extension",
          "f",
          "file",
          "footer",
          "hashtag",
          "intopic",
          // TODO(dborowitz): Support some is: operators but not others. Definitely not is:starred.
          "label",
          "onlyextensions",
          "onlyexts",
          "ownerin",
          "path",
          "r",
          "ref",
          "reviewer",
          "reviewerin",
          "size",
          "status",
          "submittable",
          "topic",
          "unresolved",
          "wip");

  @VisibleForTesting
  public static String clean(String query) throws ConfigInvalidException {
    String trimmed = requireNonNull(query).trim();
    if (trimmed.isEmpty()) {
      return trimmed;
    }
    try {
      checkOperators(QueryParser.parse(query));
    } catch (QueryParseException e) {
      throw new ConfigInvalidException("Invalid query: " + query + "\n" + e.getMessage(), e);
    }

    return trimmed;
  }

  private static void checkOperators(Tree node) throws ConfigInvalidException {
    switch (node.getType()) {
      case AND:
      case OR:
      case NOT:
        for (int i = 0; i < node.getChildCount(); i++) {
          checkOperators(node.getChild(i));
        }
        break;

      case FIELD_NAME:
        if (!ALLOWED_OPERATORS.contains(node.getText())) {
          throw new ConfigInvalidException("Unsupported operator: " + node);
        }
        break;

      case DEFAULT_FIELD:
        throw new ConfigInvalidException(
            "Specific search operator required: " + getOnlyChild(node));

      default:
        throw new ConfigInvalidException("Unsupported query: " + node);
    }
  }

  private static Tree getOnlyChild(Tree node) {
    checkState(node.getChildCount() == 1, "expected 1 child: %s", node);
    return node.getChild(0);
  }

  private final RetryHelper retryHelper;
  private final Provider<ChangeQueryProcessor> changeQueryProcessorProvider;
  private final ChangeQueryBuilder queryBuilder;

  @Inject
  CheckerQuery(
      RetryHelper retryHelper,
      Provider<AnonymousUser> anonymousUserProvider,
      Provider<ChangeQueryBuilder> queryBuilderProvider,
      Provider<ChangeQueryProcessor> changeQueryProcessorProvider) {
    this.retryHelper = retryHelper;
    this.changeQueryProcessorProvider = changeQueryProcessorProvider;
    // The user passed to the ChangeQueryBuilder just controls how it parses "self". Anonymous means
    // "self" is disallowed, which is correct for checker queries, since the results should not
    // depend on the calling user. However, note that results are still filtered by visibility, but
    // visibility is controlled by ChangeQueryProcessor, which always uses the current user and
    // can't be overridden.
    this.queryBuilder = queryBuilderProvider.get().asUser(anonymousUserProvider.get());
  }

  public boolean isCheckerRelevant(Checker checker, ChangeData cd) throws StorageException {
    if (!checker.getQuery().isPresent()) {
      return cd.change().isNew();
    }

    Predicate<ChangeData> predicate;
    try {
      predicate =
          createQueryPredicate(checker.getUuid(), checker.getRepository(), checker.getQuery());
    } catch (ConfigInvalidException e) {
      logger.atWarning().withCause(e).log(
          "skipping invalid query for checker %s", checker.getUuid());
      return false;
    }

    return predicate.asMatchable().match(cd);
  }

  /**
   * Cleans and validates a query string for storage in the checker configuration.
   *
   * <p>The query string is interpreted as a change query. Only a subset of query operators are
   * supported, as listed in the REST API documentation and {@link #ALLOWED_OPERATORS}.
   *
   * <p>In addition to syntactic validation and checking for allowed operators, this method actually
   * performs a query against the index, to ensure it passes any restrictions imposed by the index
   * implementation, such as length limits.
   *
   * @param checkerUuid the checker UUID.
   * @param repository the checker repository.
   * @param query a change query string, either from the checker in storage or a proposed new value
   *     provided by a user.
   * @return the query string, trimmed. May be empty, which indicates all changes match.
   * @throws ConfigInvalidException if the query is not a valid query, or it uses operators outside
   *     of the allowed set.
   */
  public String validate(CheckerUuid checkerUuid, Project.NameKey repository, String query)
      throws ConfigInvalidException, StorageException {
    // This parses the query string twice, which is unavoidable since there is currently no
    // QueryProcessor API which takes an Antlr Tree. That's ok; the parse cost is vastly outweighed
    // by the actual query execution.
    query = clean(query);
    queryMatchingChanges(
        checkerUuid,
        repository,
        Optional.ofNullable(Strings.emptyToNull(query)),
        qp -> qp.setUserProvidedLimit(1));
    return query;
  }

  public ImmutableList<ImmutableList<ChangeData>> queryMatchingChanges(List<Checker> checkers)
      throws ConfigInvalidException {

    try {
      List<Predicate<ChangeData>> predicateList = new ArrayList<>();
      for (Checker checker : checkers) {
        predicateList.add(
            createQueryPredicate(checker.getUuid(), checker.getRepository(), checker.getQuery()));
      }
      return executeIndexQueryWithRetry("queryMatchingChangesForCheckers", qp -> {}, predicateList);
    } catch (QueryParseException e) {
      throw new ConfigInvalidException(
          String.format(
              "A checker in scheme %s has an invalid query (%s)",
              checkers.get(0).getUuid().scheme(), e.getMessage()));
    }
  }

  public ImmutableList<ChangeData> queryMatchingChanges(Checker checker)
      throws ConfigInvalidException, StorageException {
    return queryMatchingChanges(
        checker.getUuid(), checker.getRepository(), checker.getQuery(), qp -> {});
  }

  private ImmutableList<ChangeData> queryMatchingChanges(
      CheckerUuid checkerUuid,
      Project.NameKey repository,
      Optional<String> optionalQuery,
      Consumer<ChangeQueryProcessor> queryProcessorSetup)
      throws ConfigInvalidException, StorageException {
    try {
      return executeIndexQueryWithRetry(
          "queryMatchingChangesForChecker",
          queryProcessorSetup,
          createQueryPredicate(checkerUuid, repository, optionalQuery));
    } catch (QueryParseException e) {
      throw invalidQueryException(checkerUuid, optionalQuery, e);
    }
  }

  private Predicate<ChangeData> createQueryPredicate(
      CheckerUuid checkerUuid, Project.NameKey repository, Optional<String> optionalQuery)
      throws ConfigInvalidException {
    Predicate<ChangeData> predicate = ChangePredicates.project(Project.nameKey(repository.get()));

    if (optionalQuery.isPresent()) {
      String query = optionalQuery.get();
      Predicate<ChangeData> predicateForQuery;
      try {
        predicateForQuery = queryBuilder.parse(query);
      } catch (QueryParseException e) {
        throw invalidQueryException(checkerUuid, optionalQuery, e);
      }

      if (!predicateForQuery.isMatchable()) {
        // Assuming nobody modified the query behind Gerrit's back, this is programmer error:
        // CheckerQuery should not be able to produce non-matchable queries.
        logger.atWarning().log(
            "change query of checker %s is not matchable: %s", checkerUuid, optionalQuery.get());
        throw invalidQueryException(checkerUuid, optionalQuery, null);
      }

      predicate = Predicate.and(predicate, predicateForQuery);
    }

    if (!hasStatusPredicate(predicate)) {
      predicate = Predicate.and(ChangeStatusPredicate.open(), predicate);
    }

    return predicate;
  }

  private static boolean hasStatusPredicate(Predicate<ChangeData> predicate) {
    if (predicate instanceof IndexPredicate) {
      return ((IndexPredicate<ChangeData>) predicate)
          .getField()
          .getName()
          .equals(ChangeField.STATUS_SPEC.getName());
    }
    return predicate.getChildren().stream().anyMatch(CheckerQuery::hasStatusPredicate);
  }

  // TODO(ekempin): Retrying the query should be done by ChangeQueryProcessor.
  private ImmutableList<ChangeData> executeIndexQueryWithRetry(
      String actionName,
      Consumer<ChangeQueryProcessor> queryProcessorSetup,
      Predicate<ChangeData> predicate)
      throws StorageException, QueryParseException {
    return executeIndexQueryWithRetry(actionName, queryProcessorSetup, ImmutableList.of(predicate))
        .get(0);
  }

  private ImmutableList<ImmutableList<ChangeData>> executeIndexQueryWithRetry(
      String actionName,
      Consumer<ChangeQueryProcessor> queryProcessorSetup,
      List<Predicate<ChangeData>> predicateList)
      throws StorageException, QueryParseException {
    try {
      return retryHelper
          .action(
              ActionType.INDEX_QUERY,
              actionName,
              () -> {
                ChangeQueryProcessor qp = changeQueryProcessorProvider.get();
                queryProcessorSetup.accept(qp);
                return qp.query(predicateList).stream()
                    .map(predicate -> predicate.entities())
                    .collect(toImmutableList());
              })
          .call();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, QueryParseException.class);
      Throwables.throwIfInstanceOf(e, StorageException.class);
      throw new StorageException(e);
    }
  }

  private static ConfigInvalidException invalidQueryException(
      CheckerUuid checkerUuid,
      Optional<String> optionalQuery,
      @Nullable QueryParseException parseException) {
    String msg =
        String.format(
            "change query of checker %s is invalid: %s", checkerUuid, optionalQuery.orElse(""));
    if (parseException != null) {
      msg += " (" + parseException.getMessage() + ")";
    }
    return new ConfigInvalidException(msg, parseException);
  }
}
