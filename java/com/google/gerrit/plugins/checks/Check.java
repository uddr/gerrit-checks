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
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.checks.api.CheckState;
import java.sql.Timestamp;
import java.util.Optional;

@AutoValue
public abstract class Check {
  public static Check newBackfilledCheck(Project.NameKey project, PatchSet ps, Checker checker) {
    return Check.builder(CheckKey.create(project, ps.id(), checker.getUuid()))
        .setState(CheckState.NOT_STARTED)
        .setCreated(Timestamp.from(ps.createdOn()))
        .setUpdated(Timestamp.from(ps.createdOn()))
        .build();
  }

  /** The key of the Check. */
  public abstract CheckKey key();

  /** State that this check is in. */
  public abstract CheckState state();

  /** Short message explaining the check state. */
  public abstract Optional<String> message();

  /** Fully qualified URL to detailed result on the Checker's service. */
  public abstract Optional<String> url();

  /** Timestamp of when this check started. */
  public abstract Optional<Timestamp> started();

  /** Timestamp of when this check finished. */
  public abstract Optional<Timestamp> finished();

  /** Timestamp of when this check was created. */
  public abstract Timestamp created();

  /** Timestamp of when this check was last updated. */
  public abstract Timestamp updated();

  public abstract Builder toBuilder();

  public static Builder builder(CheckKey key) {
    return new AutoValue_Check.Builder().setKey(key);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setKey(CheckKey key);

    public abstract Builder setState(CheckState state);

    public abstract Builder setMessage(String message);

    public abstract Builder setUrl(String url);

    public abstract Builder setStarted(Timestamp started);

    public abstract Builder setFinished(Timestamp finished);

    public abstract Builder setCreated(Timestamp created);

    public abstract Builder setUpdated(Timestamp updated);

    public abstract Check build();
  }
}
