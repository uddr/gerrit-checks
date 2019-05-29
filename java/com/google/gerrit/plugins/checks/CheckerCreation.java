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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;

@AutoValue
public abstract class CheckerCreation {
  /**
   * Defines the UUID the checker should have.
   *
   * <p>Must be a unique across all checkers.
   */
  public abstract CheckerUuid getCheckerUuid();

  /**
   * Defines the name the checker should have.
   *
   * <p>The same name may be used by multiple checkers.
   */
  public abstract String getName();

  /** Defines the repository for which the checker applies. */
  public abstract Project.NameKey getRepository();

  public static Builder builder() {
    return new AutoValue_CheckerCreation.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setCheckerUuid(CheckerUuid checkerUuid);

    public abstract Builder setName(String name);

    public abstract Builder setRepository(Project.NameKey repository);

    abstract CheckerCreation autoBuild();

    public CheckerCreation build() {
      CheckerCreation checkerCreation = autoBuild();
      checkState(!checkerCreation.getName().trim().isEmpty(), "checker name cannot be empty");
      checkState(
          !checkerCreation.getRepository().get().trim().isEmpty(),
          "repository name cannot be empty");
      return checkerCreation;
    }

    @VisibleForTesting
    public CheckerCreation buildWithoutValidationForTesting() {
      return autoBuild();
    }
  }
}
