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

import static java.util.stream.Collectors.joining;

import java.util.stream.Stream;

public final class CheckerTestData {
  /** An invalid checker UUID. */
  public static final String INVALID_UUID = "notauuid";

  /** An invalid checker URL that is not allowed to be set for a checker. */
  public static final String INVALID_URL = "ftp://example.com/my-checker";

  /**
   * The unsupported operator that is used in {@link
   * CheckerTestData#QUERY_WITH_UNSUPPORTED_OPERATOR}.
   */
  public static final String UNSUPPORTED_OPERATOR = "project";

  /**
   * Query that contains an operator that is not allowed to be used in checker queries.
   *
   * @see com.google.gerrit.plugins.checks.CheckerQuery#ALLOWED_OPERATORS
   */
  @SuppressWarnings("javadoc")
  public static final String QUERY_WITH_UNSUPPORTED_OPERATOR = UNSUPPORTED_OPERATOR + ":foo";

  /** Query that is not parsable. */
  public static final String INVALID_QUERY = ":foo :bar";

  /** Query with {@code numTerms} total operators, all of which are supported in checker queries. */
  public static String longQueryWithSupportedOperators(int numTerms) {
    return Stream.generate(() -> "file:foo").limit(numTerms).collect(joining(" "));
  }

  private CheckerTestData() {}
}
