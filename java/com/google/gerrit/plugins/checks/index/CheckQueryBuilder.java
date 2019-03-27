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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryBuilder;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.inject.Inject;
import java.util.Arrays;

public class CheckQueryBuilder extends QueryBuilder<Check> {
  public static final String FIELD_CHECKER = "checker";
  public static final String FIELD_STATE = "state";

  private static final QueryBuilder.Definition<Check, CheckQueryBuilder> mydef =
      new QueryBuilder.Definition<>(CheckQueryBuilder.class);

  @Inject
  CheckQueryBuilder() {
    super(mydef);
  }

  @Operator
  public Predicate<Check> checker(String checkerUuid) throws QueryParseException {
    return CheckerPredicate.parse(checkerUuid);
  }

  @Operator
  public Predicate<Check> is(String value) throws QueryParseException {
    if ("inprogress".equalsIgnoreCase(value)) {
      return Predicate.or(
          Arrays.stream(CheckState.values())
              .filter(CheckState::isInProgress)
              .map(CheckStatePredicate::new)
              .collect(toList()));
    }

    return CheckStatePredicate.tryParse(value)
        .orElseThrow(
            () -> new QueryParseException(String.format("unsupported operator: is:%s", value)));
  }

  @Operator
  public Predicate<Check> state(String state) throws QueryParseException {
    return CheckStatePredicate.parse(state);
  }
}
