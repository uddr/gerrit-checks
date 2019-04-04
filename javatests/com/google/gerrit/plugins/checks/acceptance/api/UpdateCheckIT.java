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

import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckTestData;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UpdateCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));

    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void updateCheckState() throws Exception {
    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.FAILED);
  }

  @Test
  public void cannotUpdateCheckerUuid() throws Exception {
    CheckInput input = new CheckInput();
    input.checkerUuid = "foo:bar";

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        "checker UUID in input must either be null or the same as on the resource");
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
  }

  @Test
  public void specifyingCheckerUuidInInputThatMatchesTheCheckerUuidInTheUrlIsOkay()
      throws Exception {
    CheckInput input = new CheckInput();
    input.checkerUuid = checkKey.checkerUuid().get();
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
  }

  @Test
  public void updateUrl() throws Exception {
    CheckInput input = new CheckInput();
    input.url = "http://example.com/my-check";

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.url).isEqualTo(input.url);
  }

  @Test
  public void cannotSetInvalidUrl() throws Exception {
    CheckInput input = new CheckInput();
    input.url = CheckTestData.INVALID_URL;

    exception.expect(BadRequestException.class);
    exception.expectMessage("only http/https URLs supported: " + input.url);
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
  }

  @Test
  public void updateStarted() throws Exception {
    CheckInput input = new CheckInput();
    input.started = TimeUtil.nowTs();

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.started).isEqualTo(input.started);
  }

  @Test
  public void updateFinished() throws Exception {
    CheckInput input = new CheckInput();
    input.finished = TimeUtil.nowTs();

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.finished).isEqualTo(input.finished);
  }

  @Test
  public void updateWithEmptyInput() throws Exception {
    assertThat(
            checksApiFactory
                .revision(patchSetId)
                .id(checkKey.checkerUuid())
                .update(new CheckInput()))
        .isNotNull();
  }

  @Test
  public void updateResultsInNewUpdatedTimestamp() throws Exception {
    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;

    Timestamp expectedUpdateTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.updated).isEqualTo(expectedUpdateTimestamp);
  }

  @Test
  public void noOpUpdateDoesntResultInNewUpdatedTimestamp() throws Exception {
    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    Timestamp expectedUpdateTimestamp = info.updated;

    info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.updated).isEqualTo(expectedUpdateTimestamp);
  }

  @Test
  public void canUpdateCheckForDisabledChecker() throws Exception {
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().disable().update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForCheckerWithUnsupportedOperatorInQuery() throws Exception {
    checkerOperations
        .checker(checkKey.checkerUuid())
        .forUpdate()
        .query(CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR)
        .update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForCheckerWithInvalidQuery() throws Exception {
    checkerOperations
        .checker(checkKey.checkerUuid())
        .forUpdate()
        .query(CheckerTestData.INVALID_QUERY)
        .update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForCheckerThatDoesNotApplyToTheChange() throws Exception {
    checkerOperations
        .checker(checkKey.checkerUuid())
        .forUpdate()
        .query("message:not-matching")
        .update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForNonExistingChecker() throws Exception {
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().deleteRef().update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForInvalidChecker() throws Exception {
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().forceInvalidConfig().update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void cannotUpdateCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("not permitted");
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(new CheckInput());
  }

  @Test
  public void cannotUpdateCheckAnonymously() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    exception.expect(AuthException.class);
    exception.expectMessage("Authentication required");
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(new CheckInput());
  }

  @Test
  public void otherPropertiesCanBeSetWithoutEverSettingTheState() throws Exception {
    // Create a new checker so that we know for sure that no other update ever happened for it.
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.url = "https://www.example.com";
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkerUuid).update(input);

    assertThat(info.url).isEqualTo("https://www.example.com");
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void stateCanBeSetToNotStarted() throws Exception {
    checkOperations.check(checkKey).forUpdate().setState(CheckState.FAILED).upsert();

    CheckInput input = new CheckInput();
    input.state = CheckState.NOT_STARTED;
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);

    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void otherPropertiesAreKeptWhenStateIsSetToNotStarted() throws Exception {
    checkOperations
        .check(checkKey)
        .forUpdate()
        .setState(CheckState.FAILED)
        .setUrl("https://www.example.com")
        .upsert();

    CheckInput input = new CheckInput();
    input.state = CheckState.NOT_STARTED;
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);

    assertThat(info.url).isEqualTo("https://www.example.com");
  }
}
