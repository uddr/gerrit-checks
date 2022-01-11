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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.CheckOperations.PerCheckOperations;
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
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

@UseClockStep(startAtEpoch = true)
public class CreateCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private PatchSet.Id patchSetId;
  private ObjectId commitId;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();
    commitId =
        ObjectId.fromString(
            gApi.changes().id(patchSetId.changeId().get()).current().commit(false).commit);
  }

  @Test
  public void createCheck() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    Timestamp expectedCreationTimestamp = TestTimeUtil.getCurrentTimestamp();
    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.checkerUuid).isEqualTo(checkerUuid.get());
    assertThat(info.state).isEqualTo(CheckState.RUNNING);
    assertThat(info.message).isNull();
    assertThat(info.url).isNull();
    assertThat(info.started).isNull();
    assertThat(info.finished).isNull();
    assertThat(info.created).isEqualTo(expectedCreationTimestamp);
    assertThat(info.updated).isEqualTo(info.created);

    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid);
    PerCheckOperations perCheckOps = checkOperations.check(key);

    // TODO(gerrit-team) Add a Truth subject for the notes map
    Map<ObjectId, String> notes = perCheckOps.notesAsText();
    assertThat(notes)
        .containsExactly(
            commitId,
            noteDbContent(checkerUuid.get(), expectedCreationTimestamp, expectedCreationTimestamp));
  }

  @Test
  public void cannotCreateCheckWithoutCheckerUuid() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> checksApiFactory.revision(patchSetId).create(new CheckInput()));
    assertThat(thrown).hasMessageThat().contains("checker UUID is required");
  }

  @Test
  public void createCheckWithStateNotStarted() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.NOT_STARTED;

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.state).isEqualTo(input.state);
    assertThat(getCheck(project, patchSetId, checkerUuid).state()).isEqualTo(input.state);
  }

  @Test
  public void createCheckWithoutState() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
    assertThat(getCheck(project, patchSetId, checkerUuid).state())
        .isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void createCheckWithMessage() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.message = "some message";

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.message).isEqualTo(input.message);
    assertThat(getCheck(project, patchSetId, checkerUuid).message()).hasValue(input.message);
  }

  @Test
  public void createCheckWithUrl() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.url = "http://example.com/my-check";

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.url).isEqualTo(input.url);
    assertThat(getCheck(project, patchSetId, checkerUuid).url()).hasValue(input.url);
  }

  @Test
  public void cannotCreateCheckWithInvalidUrl() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.url = CheckTestData.INVALID_URL;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> checksApiFactory.revision(patchSetId).create(input));
    assertThat(thrown).hasMessageThat().contains("only http/https URLs supported: " + input.url);
  }

  @Test
  public void createCheckWithStarted() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.started = new Timestamp(TimeUtil.nowMs());

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.started).isEqualTo(input.started);
    assertThat(getCheck(project, patchSetId, checkerUuid).started()).hasValue(input.started);
  }

  @Test
  public void createCheckWithFinished() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.finished = new Timestamp(TimeUtil.nowMs());

    CheckInfo info = checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(info.finished).isEqualTo(input.finished);
    assertThat(getCheck(project, patchSetId, checkerUuid).finished()).hasValue(input.finished);
  }

  @Test
  public void cannotCreateCheckForInvalidCheckerUuid() throws Exception {
    CheckInput input = new CheckInput();
    input.checkerUuid = CheckerTestData.INVALID_UUID;
    input.state = CheckState.RUNNING;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> checksApiFactory.revision(patchSetId).create(input));
    assertThat(thrown).hasMessageThat().contains("invalid checker UUID: " + input.checkerUuid);
  }

  @Test
  public void cannotCreateCheckForNonExistingChecker() throws Exception {
    CheckInput input = new CheckInput();
    input.checkerUuid = "foo:non-existing";
    input.state = CheckState.RUNNING;

    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> checksApiFactory.revision(patchSetId).create(input));
    assertThat(thrown).hasMessageThat().contains("checker " + input.checkerUuid + " not found");
  }

  @Test
  public void cannotCreateCheckForInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkerOperations.checker(checkerUuid).forInvalidation().nonParseableConfig().invalidate();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    RestApiException thrown =
        assertThrows(
            RestApiException.class, () -> checksApiFactory.revision(patchSetId).create(input));
    assertThat(thrown).hasMessageThat().contains("Cannot create check");
  }

  @Test
  public void canCreateCheckForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).disable().create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    checksApiFactory.revision(patchSetId).create(input);

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    assertThat(checkOperations.check(checkKey).exists()).isTrue();
  }

  @Test
  public void canCreateCheckForCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(otherProject).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    checksApiFactory.revision(patchSetId).create(input);

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    assertThat(checkOperations.check(checkKey).exists()).isTrue();
  }

  @Test
  public void canCreateCheckForCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("message:not-matching").create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    checksApiFactory.revision(patchSetId).create(input);

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    assertThat(checkOperations.check(checkKey).exists()).isTrue();
  }

  @Test
  public void canCreateCheckForCheckerWithUnsupportedOperatorInQuery() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .query(CheckerTestData.QUERY_WITH_UNSUPPORTED_OPERATOR)
            .create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    checksApiFactory.revision(patchSetId).create(input);

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    assertThat(checkOperations.check(checkKey).exists()).isTrue();
  }

  @Test
  public void canCreateCheckForCheckerWithInvalidQuery() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .query(CheckerTestData.INVALID_QUERY)
            .create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    checksApiFactory.revision(patchSetId).create(input);

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    assertThat(checkOperations.check(checkKey).exists()).isTrue();
  }

  @Test
  public void cannotCreateCheckWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    AuthException thrown =
        assertThrows(
            AuthException.class, () -> checksApiFactory.revision(patchSetId).create(input));
    assertThat(thrown).hasMessageThat().contains("not permitted");
  }

  @Test
  public void cannotCreateCheckAnonymously() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;

    AuthException thrown =
        assertThrows(
            AuthException.class, () -> checksApiFactory.revision(patchSetId).create(input));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void createCheckOnChangeEditRejected() throws Exception {
    int numChangeId = patchSetId.changeId().get();
    String changeId = gApi.changes().id(numChangeId).get().changeId;
    gApi.changes()
        .id(numChangeId)
        .edit()
        .modifyCommitMessage("Change edit\n\nChange-Id: " + changeId);
    assertThat(gApi.changes().id(numChangeId).edit().get()).isPresent();
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;
    RestResponse response =
        adminRestSession.post("/changes/" + numChangeId + "/revisions/edit/checks~checks", input);

    response.assertConflict();
    assertThat(response.getEntityContent()).isEqualTo("checks are not supported on a change edit");
  }

  @Test
  public void creationOfCheckChangesETagOfChange() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    String oldETag = parseChangeResource(patchSetId.changeId().toString()).getETag();

    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;
    checksApiFactory.revision(patchSetId).create(input).get();

    String newETag = parseChangeResource(patchSetId.changeId().toString()).getETag();
    assertThat(newETag).isNotEqualTo(oldETag);
  }

  // TODO(gerrit-team) More tests, especially for multiple checkers and PS and how commits behave

  private Check getCheck(Project.NameKey project, PatchSet.Id patchSetId, CheckerUuid checkerUuid)
      throws Exception {
    return checkOperations.check(CheckKey.create(project, patchSetId, checkerUuid)).get();
  }

  private String noteDbContent(String uuid, Timestamp created, Timestamp updated) {
    return ""
        + "{\n"
        + "  \"checks\": {\n"
        + "    \""
        + uuid
        + "\": {\n"
        + "      \"state\": \"RUNNING\",\n"
        + "      \"created\": \""
        + Instant.ofEpochMilli(created.getTime())
        + "\",\n"
        + "      \"updated\": \""
        + Instant.ofEpochMilli(updated.getTime())
        + "\"\n"
        + "    }\n"
        + "  }\n"
        + "}";
  }
}
