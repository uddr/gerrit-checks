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

import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gwtorm.server.OrmException;

public class CheckerPredicate extends CheckPredicate {
  public static CheckerPredicate parse(String value) throws QueryParseException {
    return new CheckerPredicate(
        CheckerUuid.tryParse(value)
            .orElseThrow(
                () -> new QueryParseException(String.format("invalid checker UUID: %s", value))));
  }

  private final CheckerUuid checkerUuid;

  public CheckerPredicate(CheckerUuid checkerUuid) {
    super(CheckQueryBuilder.FIELD_CHECKER, checkerUuid.toString());
    this.checkerUuid = requireNonNull(checkerUuid, "checkerUuid");
  }

  @Override
  public boolean match(Check check) throws OrmException {
    return checkerUuid.equals(check.key().checkerUuid());
  }

  public CheckerUuid getCheckerUuid() {
    return checkerUuid;
  }
}
