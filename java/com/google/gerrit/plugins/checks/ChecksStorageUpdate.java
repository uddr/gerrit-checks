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

import com.google.gerrit.exceptions.DuplicateKeyException;
import java.io.IOException;

/**
 * API for updating checks in the storage backend.
 *
 * <p>To be implemented by check storage backends.
 */
public interface ChecksStorageUpdate {
  /**
   * Creates a new check in the storage backend.
   *
   * @param key the key of the check that should be created
   * @param checkUpdate the update describing the properties of the new check
   * @return the newly created check
   * @throws DuplicateKeyException thrown if a check with the given key already exists
   * @throws IOException thrown in case of an I/O error
   */
  public Check createCheck(CheckKey key, CheckUpdate checkUpdate)
      throws DuplicateKeyException, IOException;

  /**
   * Updates an existing check in the storage backend.
   *
   * @param key the key of the check that should be updated
   * @param checkUpdate the update describing the check properties that should be updated
   * @return the updated check
   * @throws IOException thrown in case of an I/O error
   */
  public Check updateCheck(CheckKey key, CheckUpdate checkUpdate) throws IOException;
}
