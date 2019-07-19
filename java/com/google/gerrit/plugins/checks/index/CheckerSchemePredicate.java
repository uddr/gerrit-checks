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

package com.google.gerrit.plugins.checks.index;

import static java.util.Objects.requireNonNull;

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.plugins.checks.Check;

public class CheckerSchemePredicate extends CheckPredicate {
  private final String checkerScheme;

  public CheckerSchemePredicate(String checkerScheme) {
    super(CheckQueryBuilder.FIELD_SCHEME, checkerScheme);
    this.checkerScheme = requireNonNull(checkerScheme, "checkerScheme");
  }

  @Override
  public boolean match(Check check) throws StorageException {
    return checkerScheme.equals(check.key().checkerUuid().scheme());
  }

  public String getCheckerScheme() {
    return checkerScheme;
  }
}
