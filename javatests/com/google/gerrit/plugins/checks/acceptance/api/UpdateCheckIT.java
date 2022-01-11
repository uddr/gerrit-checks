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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckTestData;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckerTestData;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;

@UseClockStep(startAtEpoch = true)
public class UpdateCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();
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

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("checker UUID in input must either be null or the same as on the resource");
  }

  @Test
  public void specifyingCheckerUuidInInputThatMatchesTheCheckerUuidInTheUrlIsOkay()
      throws Exception {
    CheckInput input = new CheckInput();
    input.checkerUuid = checkKey.checkerUuid().get();
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
  }

  @Test
  public void updateMessage() throws Exception {
    CheckInput input = new CheckInput();
    input.message = "some message";

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.message).isEqualTo(input.message);
  }

  @Test
  public void updateMessage_rejected_tooLong() {
    CheckInput input = new CheckInput();
    input.message = new String(new char[10_001]).replace('\0', 'x');

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input));
    assertThat(thrown).hasMessageThat().contains("exceeds size limit (10001 > 10000)");
  }

  @Test
  @GerritConfig(name = "plugin.checks.messageSizeLimit", value = "5")
  public void updateMessage_rejected_configureLimit() {
    CheckInput input = new CheckInput();
    input.message = "foobar";

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input));
    assertThat(thrown).hasMessageThat().contains("exceeds size limit (6 > 5)");
  }

  @Test
  public void unsetMessage() throws Exception {
    checkOperations.check(checkKey).forUpdate().message("some message").upsert();

    CheckInput input = new CheckInput();
    input.message = "";

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.message).isNull();
  }

  @Test
  public void updateUrl() throws Exception {
    CheckInput input = new CheckInput();
    input.url = "http://example.com/my-check";

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.url).isEqualTo(input.url);
  }

  @Test
  public void unsetUrl() throws Exception {
    checkOperations.check(checkKey).forUpdate().url("http://example.com/my-check").upsert();

    CheckInput input = new CheckInput();
    input.url = "";

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.url).isNull();
  }

  @Test
  public void cannotSetInvalidUrl() throws Exception {
    CheckInput input = new CheckInput();
    input.url = CheckTestData.INVALID_URL;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input));
    assertThat(thrown).hasMessageThat().contains("only http/https URLs supported: " + input.url);
  }

  @Test
  public void updateStarted() throws Exception {
    CheckInput input = new CheckInput();
    input.started = new Timestamp(TimeUtil.nowMs());

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.started).isEqualTo(input.started);
  }

  @Test
  public void unsetStarted() throws Exception {
    checkOperations.check(checkKey).forUpdate().started(new Timestamp(TimeUtil.nowMs())).upsert();

    CheckInput input = new CheckInput();
    input.started = Timestamp.from(Instant.EPOCH);
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.started).isNull();
  }

  @Test
  public void updateFinished() throws Exception {
    CheckInput input = new CheckInput();
    input.finished = new Timestamp(TimeUtil.nowMs());

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.finished).isEqualTo(input.finished);
  }

  @Test
  public void unsetFinished() throws Exception {
    checkOperations.check(checkKey).forUpdate().finished(new Timestamp(TimeUtil.nowMs())).upsert();

    CheckInput input = new CheckInput();
    input.finished = Timestamp.from(Instant.EPOCH);
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.finished).isNull();
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
  public void canUpdateCheckForCheckerThatDoesNotApplyToTheProjectAndCheckExists()
      throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void
      throwExceptionForUpdateCheckForCheckerThatDoesNotApplyToTheProjectAndCheckDoesNotExist()
          throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(otherProject).create();
    CheckKey checkKey = CheckKey.create(otherProject, patchSetId, checkerUuid);
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            checksApiFactory
                .revision(patchSetId)
                .id(checkKey.checkerUuid())
                .update(new CheckInput()));
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
    checkerOperations.checker(checkKey.checkerUuid()).forInvalidation().deleteRef().invalidate();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void canUpdateCheckForInvalidChecker() throws Exception {
    checkerOperations
        .checker(checkKey.checkerUuid())
        .forInvalidation()
        .nonParseableConfig()
        .invalidate();

    CheckInput input = new CheckInput();
    input.state = CheckState.SUCCESSFUL;

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);
    assertThat(info.state).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void cannotUpdateCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .update(new CheckInput()));
    assertThat(thrown).hasMessageThat().contains("not permitted");
  }

  @Test
  public void cannotUpdateCheckAnonymously() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                checksApiFactory
                    .revision(patchSetId)
                    .id(checkKey.checkerUuid())
                    .update(new CheckInput()));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
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
    checkOperations.check(checkKey).forUpdate().state(CheckState.FAILED).upsert();

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
        .state(CheckState.FAILED)
        .message("some message")
        .url("https://www.example.com")
        .upsert();

    CheckInput input = new CheckInput();
    input.state = CheckState.NOT_STARTED;
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);

    assertThat(info.message).isEqualTo("some message");
    assertThat(info.url).isEqualTo("https://www.example.com");
  }

  @Test
  public void updateOfCheckChangesETagOfChange() throws Exception {
    String oldETag = parseChangeResource(patchSetId.changeId().toString()).getETag();

    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);

    String newETag = parseChangeResource(patchSetId.changeId().toString()).getETag();
    assertThat(newETag).isNotEqualTo(oldETag);
  }

  @Test
  public void noOpUpdateOfCheckDoesNotChangeETagOfChange() throws Exception {
    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);

    String oldETag = parseChangeResource(patchSetId.changeId().toString()).getETag();

    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).update(input);

    String newETag = parseChangeResource(patchSetId.changeId().toString()).getETag();
    assertThat(newETag).isEqualTo(oldETag);
  }
}
