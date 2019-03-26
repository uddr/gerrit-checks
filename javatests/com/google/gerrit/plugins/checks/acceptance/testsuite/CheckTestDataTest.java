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
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.UrlValidator;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import org.junit.Test;

public class CheckTestDataTest extends AbstractCheckersTest {
  @Test
  public void verifyTestUrls() throws Exception {
    try {
      UrlValidator.clean(CheckTestData.INVALID_URL);
      assert_().fail("expected BadRequestException");
    } catch (BadRequestException e) {
      assertMessage(e, "only http/https URLs supported", CheckTestData.INVALID_URL);
    }
  }

  private static void assertMessage(Exception e, String... expectedMessageParts) {
    for (String expectedMessagePart : expectedMessageParts) {
      assertThat(e).hasMessageThat().ignoringCase().contains(expectedMessagePart);
    }
  }
}
