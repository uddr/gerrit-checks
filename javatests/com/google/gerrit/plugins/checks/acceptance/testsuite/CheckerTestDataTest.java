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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.CheckerQuery;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.UrlValidator;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Test;

public class CheckerTestDataTest extends AbstractCheckersTest {
  @Test
  public void verifyTestUuids() throws Exception {
    assertThat(CheckerUuid.isUuid(CheckerTestData.INVALID_UUID)).isFalse();
  }

  @Test
  public void verifyTestUrls() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> UrlValidator.clean(CheckerTestData.INVALID_URL));
    assertMessage(thrown, "only http/https URLs supported", CheckerTestData.INVALID_URL);
  }

  @Test
  public void verifyTestQueries() throws Exception {
    assertInvalidQuery(
        CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR,
        "unsupported operator",
        CheckerTestData.UNSUPPORTED_OPERATOR);
    assertInvalidQuery(CheckerTestData.INVALID_QUERY, "invalid", CheckerTestData.INVALID_QUERY);
  }

  @Test
  public void verifyTooLongQuery() throws Exception {
    String query = CheckerTestData.longQueryWithSupportedOperators(5);
    assertThat(query).isEqualTo("file:foo file:foo file:foo file:foo file:foo");
    CheckerQuery.clean(query);
  }

  private static void assertInvalidQuery(String query, String... expectedMessageParts) {
    ConfigInvalidException thrown =
        assertThrows(ConfigInvalidException.class, () -> CheckerQuery.clean(query));
    assertMessage(thrown, expectedMessageParts);
  }

  private static void assertMessage(Exception e, String... expectedMessageParts) {
    for (String expectedMessagePart : expectedMessageParts) {
      assertThat(e).hasMessageThat().ignoringCase().contains(expectedMessagePart);
    }
  }
}
