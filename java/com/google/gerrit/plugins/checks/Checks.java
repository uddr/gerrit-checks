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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * A database accessor for read calls related to checks.
 *
 * <p>All calls which read checks related details from the database are gathered here. Other classes
 * should always use this class instead of accessing the database directly.
 *
 * <p>This is an interface so that the implementation can be swapped if needed.
 */
public interface Checks {
  /**
   * Returns a {@link List} of {@link Check}s for the given change and patchset.
   *
   * <p>If no checks exist for the given change and patchset, an empty list is returned.
   *
   * @param projectName the name of the project
   * @param patchSetId the ID of the patch set
   * @param options options for getting checks.
   * @return the checks, {@link Optional#empty()} if no checks with the given UUID exists
   * @throws StorageException if the checks couldn't be retrieved from the storage
   */
  ImmutableList<Check> getChecks(
      Project.NameKey projectName, PatchSet.Id patchSetId, GetCheckOptions options)
      throws StorageException, IOException;

  /**
   * Returns a {@link Optional} holding a single check. {@code Optional.empty()} if the check does
   * not exist.
   *
   * @param checkKey the key of the target check.
   * @param options options for getting a check.
   * @return the target check if it exists. A backfilled check will be returned if {@link
   *     GetCheckOptions#backfillChecks()} is true.
   * @throws StorageException if the check couldn't be retrieved from the storage
   * @throws IOException if the check couldn't be retrieved from the storage
   */
  Optional<Check> getCheck(CheckKey checkKey, GetCheckOptions options)
      throws StorageException, IOException;

  /**
   * Returns the combined check state of a given patch set.
   *
   * <p>Most callers should prefer {@link
   * com.google.gerrit.plugins.checks.CombinedCheckStateCache#reload} to automatically fix up the
   * cache in case primary storage differs from the cached value.
   *
   * @param projectName the name of the project.
   * @param patchSetId the ID of the patch set
   * @return the {@link CombinedCheckState} of the current patch set.
   * @throws IOException if failed to get the {@link CombinedCheckState}.
   * @throws StorageException if failed to get the {@link CombinedCheckState}.
   */
  CombinedCheckState getCombinedCheckState(Project.NameKey projectName, PatchSet.Id patchSetId)
      throws IOException, StorageException;

  /**
   * Returns whether all required checks have passed.
   *
   * @param projectName the name of the project.
   * @param patchSetId the ID of the patch set
   * @return true if all required checks have passed.
   * @throws IOException if failed to check if all required checks have passed.
   * @throws StorageException if failed to check if all required checks have passed.
   */
  boolean areAllRequiredCheckersPassing(Project.NameKey projectName, PatchSet.Id patchSetId)
      throws IOException, StorageException;

  /**
   * Computes an ETag for the checks of the given change.
   *
   * @param projectName the name of the project that contains the change
   * @param changeId ID of the change for which the ETag should be computed
   * @return ETag for the checks of the given change
   * @throws IOException if failed to access the checks data
   */
  String getETag(Project.NameKey projectName, Change.Id changeId) throws IOException;

  /**
   * Returns whether the checker is required for submission for this change.
   *
   * @param checker The checker that is being checked whether it's required for submission.
   * @param changeId The changeId of the change which submission requirement is in question.
   * @return True if the checker is required for submit, false otherwise.
   */
  boolean isRequiredForSubmit(Checker checker, Change.Id changeId);

  @AutoValue
  abstract class GetCheckOptions {
    public static GetCheckOptions defaults() {
      return new AutoValue_Checks_GetCheckOptions(false);
    }

    public static GetCheckOptions withBackfilling() {
      return new AutoValue_Checks_GetCheckOptions(true);
    }

    /** Backfills checks for relevant checkers with default when they don't exist yet. */
    public abstract boolean backfillChecks();
  }
}
