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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableListMultimap;
import java.util.Map;

/**
 * Combined state of multiple checks on a change.
 *
 * <p>This state combines multiple {@link CheckState}s together with the required/optional bit
 * associated with each check.
 *
 * <p>Ordering is not significant in this class, but for consistency's sake the ordering matches
 * {@code CheckState} where applicable.
 */
public enum CombinedCheckState {
  /** At least one required check failed; other checks may have passed, or still be running. */
  FAILED,

  /**
   * All relevant checks terminated, and at least one optional check failed, but no required checks
   * failed.
   */
  WARNING,

  /**
   * At least one relevant check is in a non-terminated state ({@link CheckState#NOT_STARTED},
   * {@link CheckState#SCHEDULED}, {@link CheckState#RUNNING}), and no required checks failed. Some
   * optional checks may have failed.
   */
  IN_PROGRESS,

  /** All relevant checks terminated successfully. */
  SUCCESSFUL,

  /** No checks are relevant to this change. */
  NOT_RELEVANT;

  /**
   * Combines multiple per-check states into a single combined state.
   *
   * <p>See documentation of specific enum values for precise semantics.
   *
   * @param statesAndRequired map of state to a list of booleans, one per check, indicating whether
   *     that particular check is required in the context of a particular change.
   * @return combined state.
   */
  public static CombinedCheckState combine(
      ImmutableListMultimap<CheckState, Boolean> statesAndRequired) {
    CheckStateCount checkStateCount = CheckStateCount.create(statesAndRequired);
    return combine(checkStateCount);
  }

  /**
   * Combines multiple per-check states into a single combined state based on the count result of
   * each check state.
   *
   * <p>See documentation of specific enum values for precise semantics.
   *
   * @param checkStateCount count of check states.
   * @return combined state.
   */
  private static CombinedCheckState combine(CheckStateCount checkStateCount) {
    if (checkStateCount.failedRequiredCount() > 0) {
      return FAILED;
    }

    if (checkStateCount.inProgressOptionalCount() > 0
        || checkStateCount.inProgressRequiredCount() > 0) {
      return IN_PROGRESS;
    }

    if (checkStateCount.failedOptionalCount() > 0) {
      return WARNING;
    }

    if (checkStateCount.successfulCount() > 0) {
      return SUCCESSFUL;
    }

    return NOT_RELEVANT;
  }

  @AutoValue
  public abstract static class CheckStateCount {
    /**
     * Get the count of each {@link CheckState}.
     *
     * @param statesAndRequired map of state to a list of booleans, one per check, indicating
     *     whether that particular check is required in the context of a particular change.
     * @return the {@link CheckStateCount} of the given state map.
     */
    public static CheckStateCount create(
        ImmutableListMultimap<CheckState, Boolean> statesAndRequired) {
      int failedRequiredCount = 0;
      int failedOptionalCount = 0;
      int inProgressRequiredCount = 0;
      int inProgressOptionalCount = 0;
      int successfulCount = 0;

      for (Map.Entry<CheckState, Boolean> checkStateAndRequiredState :
          statesAndRequired.entries()) {
        CheckState state = checkStateAndRequiredState.getKey();
        if (state.isInProgress()) {
          if (checkStateAndRequiredState.getValue()) {
            inProgressRequiredCount++;
          } else {
            inProgressOptionalCount++;
          }
        } else if (state == CheckState.FAILED) {
          if (checkStateAndRequiredState.getValue()) {
            failedRequiredCount++;
          } else {
            failedOptionalCount++;
          }
        } else if (state == CheckState.SUCCESSFUL) {
          successfulCount++;
        } else if (state != CheckState.NOT_RELEVANT) {
          throw new IllegalStateException("invalid state: " + state);
        }
      }

      return new AutoValue_CombinedCheckState_CheckStateCount.Builder()
          .failedRequiredCount(failedRequiredCount)
          .failedOptionalCount(failedOptionalCount)
          .inProgressRequiredCount(inProgressRequiredCount)
          .inProgressOptionalCount(inProgressOptionalCount)
          .successfulCount(successfulCount)
          .build();
    }

    /** Count of the failed check states which are required for submission. */
    public abstract int failedRequiredCount();

    /** Count of the failed check states which are not required for submission. */
    public abstract int failedOptionalCount();

    /** Count of the in-progress check states which are required for submission. */
    public abstract int inProgressRequiredCount();

    /** Count of the in-progress check states which are not required for submission. */
    public abstract int inProgressOptionalCount();

    /** Count of the successful check states. */
    public abstract int successfulCount();

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder inProgressRequiredCount(int inProgressRequiredCount);

      public abstract Builder inProgressOptionalCount(int inProgressOptionalCount);

      public abstract Builder failedRequiredCount(int failedRequiredCount);

      public abstract Builder failedOptionalCount(int failedOptionalCount);

      public abstract Builder successfulCount(int successfulCount);

      public abstract CheckStateCount build();
    }
  }
}
