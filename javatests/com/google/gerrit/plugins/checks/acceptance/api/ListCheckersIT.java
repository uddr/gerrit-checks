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

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

public class ListCheckersIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void listAll() throws Exception {
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().name("checker-with-name-only").create();
    CheckerUuid checkerUuid2 =
        checkerOperations
            .newChecker()
            .name("checker-with-description")
            .description("A description.")
            .create();
    CheckerUuid checkerUuid3 =
        checkerOperations
            .newChecker()
            .name("checker-with-url")
            .url("http://example.com/my-checker")
            .create();
    List<CheckerInfo> expectedCheckerInfos =
        ImmutableList.of(checkerUuid1, checkerUuid2, checkerUuid3).stream()
            .sorted()
            .map(uuid -> checkerOperations.checker(uuid).asInfo())
            .collect(toList());

    List<CheckerInfo> allCheckers = checkersApi.all();
    assertThat(allCheckers).isEqualTo(expectedCheckerInfos);
  }

  @Test
  public void listWithoutAdministrateCheckersCapabilityFails() throws Exception {
    checkerOperations.newChecker().name("my-checker").create();

    requestScopeOperations.setApiUser(user.getId());

    try {
      checkersApi.all();
      assert_().fail("expected AuthException");
    } catch (AuthException e) {
      assertThat(e.getMessage()).isEqualTo("administrateCheckers for plugin checks not permitted");
    }
  }

  @Test
  public void listCheckersAnonymouslyFails() throws Exception {
    checkerOperations.newChecker().name("my-checker").create();

    requestScopeOperations.setApiUserAnonymous();

    try {
      checkersApi.all();
      assert_().fail("expected AuthException");
    } catch (AuthException e) {
      assertThat(e.getMessage()).isEqualTo("Authentication required");
    }
  }

  @Test
  public void listIgnoresInvalidCheckers() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name("checker-with-name-only").create();
    CheckerUuid invalidCheckerUuid = checkerOperations.newChecker().create();
    checkerOperations.checker(invalidCheckerUuid).forUpdate().forceInvalidConfig().update();

    List<CheckerInfo> allCheckers = checkersApi.all();
    assertThat(allCheckers).containsExactly(checkerOperations.checker(checkerUuid).asInfo());
  }
}
