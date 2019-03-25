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
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Optional;

@AutoValue
public abstract class CheckerUpdate {
  /** Defines the new name of the checker. If not specified, the name remains unchanged. */
  public abstract Optional<String> getName();

  /**
   * Defines the new description of the checker. If not specified, the description remains
   * unchanged.
   *
   * <p><strong>Note: </strong>Passing the empty string unsets the description.
   */
  public abstract Optional<String> getDescription();

  /**
   * Defines the new URL of the checker. If not specified, the URL remains unchanged.
   *
   * <p><strong>Note: </strong>Passing the empty string unsets the URL.
   */
  public abstract Optional<String> getUrl();

  /**
   * Defines the new repository for which the checker applies. If not specified, the repository
   * remains unchanged.
   */
  public abstract Optional<Project.NameKey> getRepository();

  /** Defines the new status for the checker. If not specified, the status remains unchanged. */
  public abstract Optional<CheckerStatus> getStatus();

  /**
   * Defines the new blocking conditions for the checker. Specifies the entire set, not a delta. If
   * not specified, the blocking conditions remain unchanged.
   */
  public abstract Optional<ImmutableSortedSet<BlockingCondition>> getBlockingConditions();

  /** Defines the new query for the checker. If not specified, the query remains unchanged. */
  public abstract Optional<String> getQuery();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_CheckerUpdate.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(String name);

    public abstract Builder setDescription(String description);

    public abstract Builder setUrl(String url);

    public abstract Builder setRepository(Project.NameKey repository);

    public abstract Builder setStatus(CheckerStatus status);

    public abstract Builder setBlockingConditions(
        ImmutableSortedSet<BlockingCondition> blockingConditions);

    public abstract Builder setQuery(String query);

    public abstract CheckerUpdate build();
  }
}
