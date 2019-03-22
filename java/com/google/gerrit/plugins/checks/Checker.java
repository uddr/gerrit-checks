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

import com.google.auto.value.AutoValue;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.index.query.IndexPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.ProjectPredicate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;

/** Definition of a checker. */
@AutoValue
public abstract class Checker {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Returns the UUID of the checker.
   *
   * <p>The UUID is unique across all checkers.
   *
   * @return UUID
   */
  public abstract CheckerUuid getUuid();

  /**
   * Returns the display name of the checker.
   *
   * <p>Checkers may not have a name, in this case {@link Optional#empty()} is returned.
   *
   * @return display name of the checker
   */
  public abstract Optional<String> getName();

  /**
   * Returns the description of the checker.
   *
   * <p>Checkers may not have a description, in this case {@link Optional#empty()} is returned.
   *
   * @return the description of the checker
   */
  public abstract Optional<String> getDescription();

  /**
   * Returns the URL of the checker.
   *
   * <p>Checkers may not have a URL, in this case {@link Optional#empty()} is returned.
   *
   * @return the URL of the checker
   */
  public abstract Optional<String> getUrl();

  /**
   * Returns the repository to which the checker applies.
   *
   * <p>The repository is the exact name of a repository (no prefix, no regexp).
   *
   * @return the repository to which the checker applies
   */
  public abstract Project.NameKey getRepository();

  /**
   * Returns the status of the checker.
   *
   * @return the status of the checker.
   */
  public abstract CheckerStatus getStatus();

  /**
   * Returns the blocking conditions for the checker.
   *
   * @return the blocking conditions for the checker.
   */
  public abstract ImmutableSortedSet<BlockingCondition> getBlockingConditions();

  /**
   * Returns the query for the checker.
   *
   * <p>If set, represents a limited change query that all relevant changes will match.
   *
   * @return the query for the checker.
   */
  public abstract Optional<String> getQuery();

  /**
   * Returns the creation timestamp of the checker.
   *
   * @return the creation timestamp
   */
  public abstract Timestamp getCreated();

  /**
   * Returns the timestamp of when the checker was last updated.
   *
   * @return the last updated timestamp
   */
  public abstract Timestamp getUpdated();

  /**
   * Returns the ref state of the checker.
   *
   * @return the ref state
   */
  public abstract ObjectId getRefState();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_Checker.Builder();
  }

  public static Builder builder(CheckerUuid uuid) {
    return builder().setUuid(uuid);
  }

  public boolean isDisabled() {
    return CheckerStatus.DISABLED == getStatus();
  }

  public boolean isCheckerRelevant(ChangeData cd, ChangeQueryBuilder changeQueryBuilder)
      throws OrmException {
    if (!getQuery().isPresent()) {
      return cd.change().isNew();
    }

    Predicate<ChangeData> predicate;
    try {
      predicate = createQueryPredicate(changeQueryBuilder);
    } catch (ConfigInvalidException e) {
      logger.atWarning().withCause(e).log("skipping invalid query for checker %s", getUuid());
      return false;
    }

    return predicate.asMatchable().match(cd);
  }

  public List<ChangeData> queryMatchingChanges(
      RetryHelper retryHelper,
      ChangeQueryBuilder changeQueryBuilder,
      Provider<ChangeQueryProcessor> changeQueryProcessorProvider)
      throws ConfigInvalidException, OrmException {
    return executeIndexQueryWithRetry(
        retryHelper, changeQueryProcessorProvider, createQueryPredicate(changeQueryBuilder));
  }

  private Predicate<ChangeData> createQueryPredicate(ChangeQueryBuilder changeQueryBuilder)
      throws ConfigInvalidException {
    Predicate<ChangeData> predicate = new ProjectPredicate(getRepository().get());

    if (getQuery().isPresent()) {
      String query = getQuery().get();
      Predicate<ChangeData> predicateForQuery;
      try {
        predicateForQuery = changeQueryBuilder.parse(query);
      } catch (QueryParseException e) {
        throw new ConfigInvalidException(
            String.format("change query of checker %s is invalid: %s", getUuid(), query), e);
      }

      if (!predicateForQuery.isMatchable()) {
        // Assuming nobody modified the query behind Gerrit's back, this is programmer error:
        // CheckerQuery should not be able to produce non-matchable queries.
        throw new ConfigInvalidException(
            String.format("change query of checker %s is non-matchable: %s", getUuid(), query));
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
          .equals(ChangeField.STATUS.getName());
    }
    return predicate.getChildren().stream().anyMatch(Checker::hasStatusPredicate);
  }

  // TODO(ekempin): Retrying the query should be done by ChangeQueryProcessor.
  private List<ChangeData> executeIndexQueryWithRetry(
      RetryHelper retryHelper,
      Provider<ChangeQueryProcessor> changeQueryProcessorProvider,
      Predicate<ChangeData> predicate)
      throws OrmException {
    try {
      return retryHelper.execute(
          ActionType.INDEX_QUERY,
          () -> changeQueryProcessorProvider.get().query(predicate).entities(),
          OrmException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmException.class);
      throw new OrmException(e);
    }
  }

  /**
   * Checks whether a {@link Checker} is required for submission or not.
   *
   * @return true if the {@link Checker} required for submission.
   */
  public boolean isRequired() {
    ImmutableSet<BlockingCondition> blockingConditions = getBlockingConditions();
    if (blockingConditions.isEmpty()) {
      return false;
    } else if (blockingConditions.size() > 1
        || !blockingConditions.contains(BlockingCondition.STATE_NOT_PASSING)) {
      // When a new blocking condition is introduced, this needs to be adjusted to respect that.
      String errorMessage = String.format("illegal blocking conditions %s", blockingConditions);
      throw new IllegalStateException(errorMessage);
    }
    return true;
  }

  /** A builder for an {@link Checker}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setUuid(CheckerUuid uuid);

    public abstract CheckerUuid getUuid();

    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setUrl(String url);

    public abstract Builder setRepository(Project.NameKey repository);

    public abstract Builder setStatus(CheckerStatus status);

    public abstract Builder setBlockingConditions(Iterable<BlockingCondition> enumList);

    public abstract Builder setQuery(String query);

    public abstract Builder setCreated(Timestamp created);

    public abstract Builder setUpdated(Timestamp updated);

    public abstract Builder setRefState(ObjectId refState);

    public abstract Checker build();
  }
}
