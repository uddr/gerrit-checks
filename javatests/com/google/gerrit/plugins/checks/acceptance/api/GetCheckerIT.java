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

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.restapi.config.ListCapabilities;
import com.google.gerrit.server.restapi.config.ListCapabilities.CapabilityInfo;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GetCheckerIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ListCapabilities listCapabilities;

  @Before
  public void setUp() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void getCheckerReturnsUuid() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    assertThat(getCheckerInfo(checkerUuid).uuid).isEqualTo(checkerUuid.get());
  }

  @Test
  public void getCheckerReturnsName() throws Exception {
    String name = "My Checker";
    CheckerUuid checkerUuid = checkerOperations.newChecker().name(name).create();

    assertThat(getCheckerInfo(checkerUuid).name).isEqualTo(name);
  }

  @Test
  public void getCheckerReturnsDescription() throws Exception {
    String description = "some description";
    CheckerUuid checkerUuid = checkerOperations.newChecker().description(description).create();

    assertThat(getCheckerInfo(checkerUuid).description).isEqualTo(description);
  }

  @Test
  public void getCheckerWithoutDescription() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearDescription().create();
    assertThat(getCheckerInfo(checkerUuid).description).isNull();
  }

  @Test
  public void getCheckerReturnsUrl() throws Exception {
    String url = "http://example.com/my-checker";
    CheckerUuid checkerUuid = checkerOperations.newChecker().url(url).create();

    assertThat(getCheckerInfo(checkerUuid).url).isEqualTo(url);
  }

  @Test
  public void getCheckerWithoutUrl() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearUrl().create();
    assertThat(getCheckerInfo(checkerUuid).url).isNull();
  }

  @Test
  public void getCheckerWithInvalidUrl() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().url(CheckerTestData.INVALID_URL).create();
    assertThat(getCheckerInfo(checkerUuid).url).isEqualTo(CheckerTestData.INVALID_URL);
  }

  @Test
  public void getCheckerReturnsRepository() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    assertThat(getCheckerInfo(checkerUuid).repository).isEqualTo(project.get());
  }

  @Test
  public void getCheckerReturnsStatus() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().status(CheckerStatus.ENABLED).create();
    assertThat(getCheckerInfo(checkerUuid).status).isEqualTo(CheckerStatus.ENABLED);
  }

  @Test
  public void getDisabledChecker() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().status(CheckerStatus.DISABLED).create();
    assertThat(getCheckerInfo(checkerUuid).status).isEqualTo(CheckerStatus.DISABLED);
  }

  @Test
  public void getCheckerReturnsBlockingCondition() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .blockingConditions(BlockingCondition.STATE_NOT_PASSING)
            .create();
    assertThat(getCheckerInfo(checkerUuid).blocking)
        .containsExactly(BlockingCondition.STATE_NOT_PASSING);
  }

  @Test
  public void getCheckerWithoutBlockingCondition() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().blockingConditions(ImmutableSortedSet.of()).create();
    assertThat(getCheckerInfo(checkerUuid).blocking).isEmpty();
  }

  @Test
  public void getCheckerReturnsQuery() throws Exception {
    String query = "message:foo footer:bar";
    CheckerUuid checkerUuid = checkerOperations.newChecker().query(query).create();

    assertThat(getCheckerInfo(checkerUuid).query).isEqualTo(query);
  }

  @Test
  public void getCheckerWithoutQuery() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().clearQuery().create();
    assertThat(getCheckerInfo(checkerUuid).query).isNull();
  }

  @Test
  public void getCheckerReturnsCreationTimestamp() throws Exception {
    Timestamp expectedCreationTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    assertThat(getCheckerInfo(checkerUuid).created).isEqualTo(expectedCreationTimestamp);
  }

  @Test
  public void getCheckerReturnsUpdatedTimestamp() throws Exception {
    Timestamp expectedUpdatedTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();

    assertThat(getCheckerInfo(checkerUuid).updated).isEqualTo(expectedUpdatedTimestamp);
  }

  @Test
  public void getCheckerWithUnsupportedOperatorInQuery() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .query(CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR)
            .create();

    CheckerInfo info = checkersApi.id(checkerUuid).get();
    assertThat(info.query).isEqualTo(CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR);
  }

  @Test
  public void getCheckerWithInvalidQuery() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().query(CheckerTestData.INVALID_QUERY).create();

    CheckerInfo info = checkersApi.id(checkerUuid).get();
    assertThat(info.query).isEqualTo(CheckerTestData.INVALID_QUERY);
  }

  @Test
  public void getNonExistingCheckerFails() throws Exception {
    CheckerUuid checkerUuid = CheckerUuid.parse("test:non-existing");

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + checkerUuid);
    getCheckerInfo(checkerUuid);
  }

  @Test
  public void getInvalidCheckerFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    exception.expect(RestApiException.class);
    exception.expectMessage("Cannot retrieve checker " + checkerUuid);
    getCheckerInfo(checkerUuid);
  }

  @Test
  public void getCheckerByNameFails() throws Exception {
    String name = "my-checker";
    checkerOperations.newChecker().name(name).create();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + name);
    checkersApi.id(name);
  }

  @Test
  public void getCheckerWithoutAdministrateCheckersCapabilityFails() throws Exception {
    String name = "my-checker";
    CheckerUuid checkerUuid = checkerOperations.newChecker().name(name).create();

    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("administrateCheckers for plugin checks not permitted");
    getCheckerInfo(checkerUuid);
  }

  @Test
  public void administrateCheckersCapabilityIsAdvertised() throws Exception {
    Map<String, CapabilityInfo> capabilities = listCapabilities.apply(new ConfigResource());
    String capability = "checks-administrateCheckers";
    assertThat(capabilities).containsKey(capability);
    CapabilityInfo info = capabilities.get(capability);
    assertThat(info.id).isEqualTo(capability);
    assertThat(info.name).isEqualTo("Administrate Checkers");
  }

  private CheckerInfo getCheckerInfo(CheckerUuid checkerUuid) throws RestApiException {
    return checkersApi.id(checkerUuid).get();
  }
}
