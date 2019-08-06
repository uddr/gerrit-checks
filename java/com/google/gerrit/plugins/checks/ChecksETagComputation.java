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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeETagComputation;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

/**
 * Ensures that the ETag of a change changes when there was any check update for the change.
 *
 * <p>This prevents callers using ETags from potentially seeing outdated submittability and combined
 * check state information in {@link com.google.gerrit.extensions.common.ChangeInfo} when a check
 * for the change was created or updated.
 */
@Singleton
public class ChecksETagComputation implements ChangeETagComputation {
  private final Checks checks;

  @Inject
  ChecksETagComputation(Checks checks) {
    this.checks = checks;
  }

  @Override
  public String getETag(Project.NameKey projectName, Change.Id changeId) {
    try {
      return checks.getETag(projectName, changeId);
    } catch (IOException e) {
      // The change ETag will be invalidated if the checks can be accessed the next time.
      throw new StorageException(
          String.format(
              "Failed to compute ETag for checks of change %s in project %s",
              changeId, projectName),
          e);
    }
  }
}
