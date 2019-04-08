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

import com.google.common.base.Enums;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gwtorm.server.OrmException;
import java.util.Optional;

public class CheckStatePredicate extends CheckPredicate {
  public static CheckStatePredicate parse(String value) throws QueryParseException {
    return tryParse(value)
        .orElseThrow(
            () -> new QueryParseException(String.format("invalid check state: %s", value)));
  }

  public static Optional<CheckStatePredicate> tryParse(String value) {
    return Enums.getIfPresent(CheckState.class, value).toJavaUtil().map(CheckStatePredicate::new);
  }

  private final CheckState checkState;

  public CheckStatePredicate(CheckState checkState) {
    super(CheckQueryBuilder.FIELD_STATE, checkState.name());
    this.checkState = requireNonNull(checkState, "checkState");
  }

  @Override
  public boolean match(Check check) throws OrmException {
    return checkState.equals(check.state());
  }
}
