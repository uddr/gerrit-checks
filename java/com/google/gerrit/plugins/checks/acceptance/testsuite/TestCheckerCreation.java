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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import java.util.Optional;

@AutoValue
public abstract class TestCheckerCreation {
  public abstract Optional<CheckerUuid> uuid();

  public abstract Optional<String> name();

  public abstract Optional<String> description();

  public abstract Optional<String> url();

  public abstract Optional<Project.NameKey> repository();

  public abstract Optional<CheckerStatus> status();

  public abstract Optional<ImmutableSortedSet<BlockingCondition>> blockingConditions();

  public abstract Optional<String> query();

  abstract ThrowingFunction<TestCheckerCreation, CheckerUuid> checkerCreator();

  public static Builder builder(ThrowingFunction<TestCheckerCreation, CheckerUuid> checkerCreator) {
    return new AutoValue_TestCheckerCreation.Builder().checkerCreator(checkerCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder uuid(CheckerUuid uuid);

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    public abstract Builder url(String url);

    public Builder clearUrl() {
      return url("");
    }

    public abstract Builder repository(Project.NameKey repository);

    public Builder enable() {
      return status(CheckerStatus.ENABLED);
    }

    public Builder disable() {
      return status(CheckerStatus.DISABLED);
    }

    abstract Builder status(CheckerStatus status);

    public Builder optional() {
      return blockingConditions(ImmutableSortedSet.of());
    }

    public Builder required() {
      return blockingConditions(ImmutableSortedSet.of(BlockingCondition.STATE_NOT_PASSING));
    }

    public abstract Builder blockingConditions(
        ImmutableSortedSet<BlockingCondition> blockingConditions);

    public abstract Builder query(String query);

    public Builder clearQuery() {
      return query("");
    }

    abstract Builder checkerCreator(
        ThrowingFunction<TestCheckerCreation, CheckerUuid> checkerCreator);

    abstract TestCheckerCreation autoBuild();

    /**
     * Executes the checker creation as specified.
     *
     * @return the UUID of the created checker
     */
    public CheckerUuid create() {
      TestCheckerCreation checkerCreation = autoBuild();
      return checkerCreation.checkerCreator().applyAndThrowSilently(checkerCreation);
    }
  }
}
