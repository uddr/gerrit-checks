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
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;

@AutoValue
public abstract class TestCheckerInvalidation {
  public abstract boolean nonParseableConfig();

  public abstract boolean invalidUuid();

  public abstract boolean invalidBlockingCondition();

  public abstract boolean invalidStatus();

  public abstract boolean deleteRef();

  abstract ThrowingConsumer<TestCheckerInvalidation> checkerInvalidator();

  public static Builder builder(ThrowingConsumer<TestCheckerInvalidation> checkerInvalidator) {
    return new AutoValue_TestCheckerInvalidation.Builder()
        .checkerInvalidator(checkerInvalidator)
        .nonParseableConfig(false)
        .invalidUuid(false)
        .invalidBlockingCondition(false)
        .invalidStatus(false)
        .deleteRef(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public Builder nonParseableConfig() {
      return nonParseableConfig(true);
    }

    abstract Builder nonParseableConfig(boolean nonParseableConfig);

    public Builder invalidUuid() {
      return invalidUuid(true);
    }

    abstract Builder invalidUuid(boolean invalidUuid);

    public Builder invalidBlockingCondition() {
      return invalidBlockingCondition(true);
    }

    abstract Builder invalidBlockingCondition(boolean invalidBlockingCondition);

    public Builder invalidStatus() {
      return invalidStatus(true);
    }

    abstract Builder invalidStatus(boolean invalidStatus);

    public Builder deleteRef() {
      return deleteRef(true);
    }

    abstract Builder deleteRef(boolean deleteRef);

    abstract Builder checkerInvalidator(
        ThrowingConsumer<TestCheckerInvalidation> checkerInvalidator);

    abstract TestCheckerInvalidation autoBuild();

    /** Executes the checker invalidation as specified. */
    public void invalidate() {
      TestCheckerInvalidation checkerInvalidation = autoBuild();
      checkerInvalidation.checkerInvalidator().acceptAndThrowSilently(checkerInvalidation);
    }
  }
}
