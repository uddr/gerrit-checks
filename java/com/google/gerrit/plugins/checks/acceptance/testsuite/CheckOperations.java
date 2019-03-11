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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.reviewdb.client.RevId;

/**
 * An aggregation of operations on checks for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface CheckOperations {

  /**
   * Starts the fluent chain for querying or modifying a check. Please see the methods of {@link
   * PerCheckOperations} for details on possible operations.
   *
   * @param key key of the check
   * @return an aggregation of operations on a specific check
   */
  PerCheckOperations check(CheckKey key);

  /**
   * Starts the fluent chain to create a check. The returned builder can be used to specify the
   * attributes of the new check. To create the check in the storage for real, {@link
   * TestCheckUpdate.Builder#upsert()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * checkOperations
   *     .newCheck(checkKey)
   *     .setState(CheckState.RUNNING)
   *     .upsert();
   * </pre>
   *
   * <p><strong>Note:</strong> If a check with the provided key already exists, the check creation
   * fails.
   *
   * @return a builder to create the new check
   */
  TestCheckUpdate.Builder newCheck(CheckKey key);

  /** An aggregation of methods on a specific check. */
  interface PerCheckOperations {

    /**
     * Checks whether the check exists.
     *
     * @return {@code true} if the check exists
     */
    boolean exists() throws Exception;

    /**
     * Retrieves the check.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested check
     * doesn't exist. If you want to check for the existence of a check, use {@link #exists()}
     * instead.
     *
     * @return the corresponding {@code Check}
     */
    Check get() throws Exception;

    /**
     * Retrieves the checks of the change as text in a map keyed by the revision IDs.
     *
     * <p>This call reads the notes with the checks by patch set from the change check ref.
     *
     * <p><strong>Note:</strong>The returned text contains all checks that exist on the change that
     * is specified by the check key. Filtering by the patch set and checker that is specified in
     * the check key is not done.
     *
     * <p><strong>Note:</strong>This call will fail with an exception if the change that is
     * specified by the check key has no checks.
     *
     * @return the map with the checks of the change as text in a map keyed by the revision IDs
     */
    ImmutableMap<RevId, String> notesAsText() throws Exception;

    /**
     * Returns this check as {@link CheckInfo}.
     *
     * <p><strong>Note:</strong>This call will fail with an exception if the check doesn't exist.
     *
     * @return this check as {@link CheckInfo}
     */
    CheckInfo asInfo() throws Exception;

    /**
     * Starts the fluent chain to update a check. The returned builder can be used to specify how
     * the attributes of the check should be modified. To update the check for real, {@link
     * TestCheckUpdate.Builder#upsert()} must be called.
     *
     * <p>Example:
     *
     * <pre>
     * checkOperations.forUpdate()..setState(CheckState.SUCCESSFUL).upsert();
     * </pre>
     *
     * <p><strong>Note:</strong> The update will fail with an exception if the check to update
     * doesn't exist. If you want to check for the existence of a check, use {@link #exists()}.
     *
     * @return a builder to update the check
     */
    TestCheckUpdate.Builder forUpdate();
  }
}
