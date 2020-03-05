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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class CheckOperationsImplTest extends AbstractCheckersTest {
  // Use specific subclass instead of depending on the interface field from the base class.
  @SuppressWarnings("hiding")
  private CheckOperationsImpl checkOperations;

  @Before
  public void setUp() {
    checkOperations = plugin.getSysInjector().getInstance(CheckOperationsImpl.class);
  }

  @Test
  public void checkCanBeCreatedWithSpecifyingKeyOnly() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    CheckInfo foundCheck = getCheckFromServer(checkKey);
    assertThat(foundCheck.repository).isEqualTo(project.get());
    assertThat(foundCheck.changeNumber).isEqualTo(checkKey.patchSet().changeId().get());
    assertThat(foundCheck.patchSetId).isEqualTo(checkKey.patchSet().get());
    assertThat(foundCheck.checkerUuid).isEqualTo(checkerUuid.get());
    assertThat(foundCheck.state).isNotNull();
    assertThat(foundCheck.message).isNull();
    assertThat(foundCheck.url).isNull();
    assertThat(foundCheck.started).isNull();
    assertThat(foundCheck.finished).isNull();
    assertThat(foundCheck.created).isNotNull();
    assertThat(foundCheck.updated).isNotNull();
    assertThat(foundCheck.checkerName).isNull();
    assertThat(foundCheck.checkerStatus).isNull();
    assertThat(foundCheck.blocking).isNull();
    assertThat(foundCheck.submitImpact).isNull();
  }

  @Test
  public void checkCannotBeCreatedForNonExistingRepo() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey =
        CheckKey.create(
            Project.nameKey("non-existing"), createChange().getPatchSetId(), checkerUuid);

    assertThrows(IllegalStateException.class, () -> checkOperations.newCheck(checkKey).upsert());
  }

  @Test
  public void checkCannotBeCreatedForNonExistingChange() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey = CheckKey.create(project, PatchSet.id(Change.id(1), 1), checkerUuid);

    assertThrows(IllegalStateException.class, () -> checkOperations.newCheck(checkKey).upsert());
  }

  @Test
  public void checkCannotBeCreatedForNonExistingPatchSet() throws Exception {
    Change.Id changeId = createChange().getChange().getId();
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey = CheckKey.create(project, PatchSet.id(changeId, 99), checkerUuid);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> checkOperations.newCheck(checkKey).upsert());
    assertThat(thrown).hasMessageThat().contains("patch set not found: " + changeId.get() + ",99");
  }

  @Test
  public void checkCannotBeCreatedForNonExistingChecker() throws Exception {
    CheckKey checkKey =
        CheckKey.create(project, createChange().getPatchSetId(), CheckerUuid.parse("foo:bar"));

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> checkOperations.newCheck(checkKey).upsert());
    assertThat(thrown.getCause(), instanceOf(IOException.class));
    assertThat(thrown).hasMessageThat().contains("checker foo:bar not found");
  }

  @Test
  public void checkCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    CheckInfo foundCheck = getCheckFromServer(checkKey);
    assertThat(foundCheck.repository).isEqualTo(project.get());
    assertThat(foundCheck.changeNumber).isEqualTo(checkKey.patchSet().changeId().get());
    assertThat(foundCheck.patchSetId).isEqualTo(checkKey.patchSet().get());
    assertThat(foundCheck.checkerUuid).isEqualTo(checkerUuid.get());
  }

  @Test
  public void specifiedMessageIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).message("some message").upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertThat(check.message).isEqualTo("some message");
  }

  @Test
  public void requestingNoMessageIsPossibleForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).clearMessage().upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertThat(check.message).isNull();
  }

  @Test
  public void specifiedUrlIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).url("http://example.com/my-check").upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertThat(check.url).isEqualTo("http://example.com/my-check");
  }

  @Test
  public void requestingNoUrlIsPossibleForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).clearUrl().upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertThat(check.url).isNull();
  }

  @Test
  public void specifiedStateIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertThat(check.state).isEqualTo(CheckState.FAILED);
  }

  @Test
  public void specifiedStartedIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp started = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).started(started).upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertTimestamp(check.started, started);
  }

  @Test
  public void specifiedFinishedIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp finished = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).finished(finished).upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertTimestamp(check.finished, finished);
  }

  @Test
  public void existingCheckCanBeCheckedForExistence() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey =
        createCheckInServer(
            project, createChange().getPatchSetId(), createArbitraryCheckInput(checkerUuid));

    boolean exists = checkOperations.check(checkKey).exists();

    assertThat(exists).isTrue();
  }

  @Test
  public void notExistingCheckCanBeCheckedForExistence() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);

    boolean exists = checkOperations.check(checkKey).exists();

    assertThat(exists).isFalse();
  }

  @Test
  public void retrievingNotExistingCheckOfNonRelevantCheckerFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(allProjects).create();
    CheckKey notExistingCheckKey =
        CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    assertThrows(
        IllegalStateException.class, () -> checkOperations.check(notExistingCheckKey).get());
  }

  @Test
  public void retrievingNotExistingCheckOfRelevantCheckerFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey notExistingCheckKey =
        CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);

    assertThrows(
        IllegalStateException.class, () -> checkOperations.check(notExistingCheckKey).get());
  }

  @Test
  public void checkNotCreatedByTestApiCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckInput input = createArbitraryCheckInput(checkerUuid);
    input.url = "http://unique-check-not-created-via-test-API";
    CheckKey checkKey = createCheckInServer(project, createChange().getPatchSetId(), input);

    Check foundCheck = checkOperations.check(checkKey).get();

    assertThat(foundCheck.key()).isEqualTo(checkKey);
    assertThat(foundCheck.url().get()).isEqualTo(input.url);
  }

  @Test
  public void keyOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    CheckKey foundCheckKey = checkOperations.check(checkKey).get().key();

    assertThat(foundCheckKey).isEqualTo(checkKey);
  }

  @Test
  public void stateOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();

    CheckState checkState = checkOperations.check(checkKey).get().state();

    assertThat(checkState).isEqualTo(CheckState.FAILED);
  }

  @Test
  public void messageOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).message("some message").upsert();

    Optional<String> message = checkOperations.check(checkKey).get().message();

    assertThat(message).hasValue("some message");
  }

  @Test
  public void emptyMessageOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).clearMessage().upsert();

    Optional<String> message = checkOperations.check(checkKey).get().message();

    assertThat(message).isEmpty();
  }

  @Test
  public void urlOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).url("http://example.com/my-check").upsert();

    Optional<String> url = checkOperations.check(checkKey).get().url();

    assertThat(url).hasValue("http://example.com/my-check");
  }

  @Test
  public void emptyUrlOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).clearUrl().upsert();

    Optional<String> url = checkOperations.check(checkKey).get().url();

    assertThat(url).isEmpty();
  }

  @Test
  public void startedOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp started = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).started(started).upsert();

    Optional<Timestamp> foundStarted = checkOperations.check(checkKey).get().started();

    assertTimestamp(foundStarted, started);
  }

  @Test
  public void finishedOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp finished = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).finished(finished).upsert();

    Optional<Timestamp> foundFinished = checkOperations.check(checkKey).get().finished();

    assertTimestamp(foundFinished, finished);
  }

  @Test
  public void createdOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    CheckInfo check =
        checksApiFactory.revision(patchSetId).create(createArbitraryCheckInput(checkerUuid)).get();

    Timestamp created = checkOperations.check(checkKey).get().created();

    assertThat(created).isEqualTo(check.created);
  }

  @Test
  public void updatedOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    CheckInfo check =
        checksApiFactory.revision(patchSetId).create(createArbitraryCheckInput(checkerUuid)).get();

    Timestamp updated = checkOperations.check(checkKey).get().updated();

    assertThat(updated).isEqualTo(check.updated);
  }

  @Test
  public void updateWithoutAnyParametersIsANoop() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    Check originalCheck = checkOperations.check(checkKey).get();

    checkOperations.check(checkKey).forUpdate().upsert();

    Check updatedCheck = checkOperations.check(checkKey).get();
    assertThat(updatedCheck).isEqualTo(originalCheck);
  }

  @Test
  public void updateWritesToInternalCheckSystem() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).url("http://original-url").upsert();

    checkOperations.check(checkKey).forUpdate().url("http://updated-url").upsert();

    String currentUrl = getCheckFromServer(checkKey).url;
    assertThat(currentUrl).isEqualTo("http://updated-url");
  }

  @Test
  public void stateCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();

    checkOperations.check(checkKey).forUpdate().state(CheckState.SUCCESSFUL).upsert();

    CheckState currentState = checkOperations.check(checkKey).get().state();
    assertThat(currentState).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void messageCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).message("original message").upsert();

    checkOperations.check(checkKey).forUpdate().message("updated message").upsert();

    Optional<String> currentMessage = checkOperations.check(checkKey).get().message();
    assertThat(currentMessage).hasValue("updated message");
  }

  @Test
  public void messageCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).message("original message").upsert();

    checkOperations.check(checkKey).forUpdate().clearMessage().upsert();

    Optional<String> currentMessage = checkOperations.check(checkKey).get().message();
    assertThat(currentMessage).isEmpty();
  }

  @Test
  public void urlCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).url("http://original-url").upsert();

    checkOperations.check(checkKey).forUpdate().url("http://updated-url").upsert();

    Optional<String> currentUrl = checkOperations.check(checkKey).get().url();
    assertThat(currentUrl).hasValue("http://updated-url");
  }

  @Test
  public void urlCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).url("http://original-url").upsert();

    checkOperations.check(checkKey).forUpdate().clearUrl().upsert();

    Optional<String> currentUrl = checkOperations.check(checkKey).get().url();
    assertThat(currentUrl).isEmpty();
  }

  @Test
  public void startedCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).started(new Timestamp(1234567L)).upsert();

    Timestamp updatedStarted = new Timestamp(7654321L);
    checkOperations.check(checkKey).forUpdate().started(updatedStarted).upsert();

    Optional<Timestamp> currentStarted = checkOperations.check(checkKey).get().started();
    assertTimestamp(currentStarted, updatedStarted);
  }

  @Test
  public void startedCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).started(TimeUtil.nowTs()).upsert();

    checkOperations.check(checkKey).forUpdate().clearStarted().upsert();

    Optional<Timestamp> currentStarted = checkOperations.check(checkKey).get().started();
    assertThat(currentStarted).isEmpty();
  }

  @Test
  public void finishedCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).finished(new Timestamp(1234567L)).upsert();

    Timestamp updatedFinished = new Timestamp(7654321L);
    checkOperations.check(checkKey).forUpdate().finished(updatedFinished).upsert();

    Optional<Timestamp> currentFinished = checkOperations.check(checkKey).get().finished();
    assertTimestamp(currentFinished, updatedFinished);
  }

  @Test
  public void finishedCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).finished(TimeUtil.nowTs()).upsert();

    checkOperations.check(checkKey).forUpdate().clearFinished().upsert();

    Optional<Timestamp> currentFinished = checkOperations.check(checkKey).get().finished();
    assertThat(currentFinished).isEmpty();
  }

  @Test
  public void getNotesAsText() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    CheckKey checkKey =
        createCheckInServer(project, patchSetId, createArbitraryCheckInput(checkerUuid));

    ImmutableMap<ObjectId, String> notesAsText = checkOperations.check(checkKey).notesAsText();
    ObjectId expectedCommitId = getCommitId(patchSetId);
    assertThat(notesAsText).containsExactly(expectedCommitId, readNoteText(checkKey));
  }

  private String readNoteText(CheckKey checkKey) throws Exception {
    try (Repository repo = repoManager.openRepository(checkKey.repository());
        RevWalk rw = new RevWalk(repo);
        ObjectReader reader = repo.newObjectReader()) {
      Ref checkRef =
          repo.getRefDatabase().exactRef(CheckerRef.checksRef(checkKey.patchSet().changeId()));
      checkNotNull(checkRef);

      NoteMap notes = NoteMap.read(reader, rw.parseCommit(checkRef.getObjectId()));
      ObjectId noteId = notes.get(getCommitId(checkKey.patchSet()));
      return new String(reader.open(noteId, OBJ_BLOB).getCachedBytes(Integer.MAX_VALUE), UTF_8);
    }
  }

  @Test
  public void asInfo() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);

    checkOperations
        .newCheck(checkKey)
        .state(CheckState.RUNNING)
        .message("some message")
        .url("http://example.com/my-check")
        .started(new Timestamp(1234567L))
        .finished(new Timestamp(7654321L))
        .upsert();
    Check check = checkOperations.check(checkKey).get();
    CheckInfo checkInfo = checkOperations.check(checkKey).asInfo();
    assertThat(checkInfo.repository).isEqualTo(check.key().repository().get());
    assertThat(checkInfo.changeNumber).isEqualTo(check.key().patchSet().changeId().get());
    assertThat(checkInfo.patchSetId).isEqualTo(check.key().patchSet().get());
    assertThat(checkInfo.checkerUuid).isEqualTo(check.key().checkerUuid().get());
    assertThat(checkInfo.state).isEqualTo(check.state());
    assertThat(checkInfo.message).isEqualTo(check.message().get());
    assertThat(checkInfo.url).isEqualTo(check.url().get());
    assertThat(checkInfo.started).isEqualTo(check.started().get());
    assertThat(checkInfo.finished).isEqualTo(check.finished().get());
    assertThat(checkInfo.created).isEqualTo(check.created());
    assertThat(checkInfo.updated).isEqualTo(check.updated());
  }

  private CheckInfo getCheckFromServer(CheckKey checkKey) throws RestApiException {
    return checksApiFactory.revision(checkKey.patchSet()).id(checkKey.checkerUuid()).get();
  }

  private CheckInput createArbitraryCheckInput(CheckerUuid checkerUuid) {
    CheckInput checkInput = new CheckInput();
    checkInput.checkerUuid = checkerUuid.get();
    checkInput.state = CheckState.SCHEDULED;
    checkInput.message = "some message";
    checkInput.url = "http://example.com/my-check";
    checkInput.started = TimeUtil.nowTs();
    return checkInput;
  }

  private CheckKey createCheckInServer(
      Project.NameKey repositoryName, PatchSet.Id patchSetId, CheckInput checkInput)
      throws RestApiException {
    checksApiFactory.revision(patchSetId).create(checkInput);
    return CheckKey.create(repositoryName, patchSetId, CheckerUuid.parse(checkInput.checkerUuid));
  }

  private ObjectId getCommitId(PatchSet.Id patchSetId) throws Exception {
    return ObjectId.fromString(
        gApi.changes()
            .id(patchSetId.changeId().get())
            .revision(patchSetId.get())
            .commit(false)
            .commit);
  }

  private static void assertTimestamp(Optional<Timestamp> actual, Timestamp expected) {
    assertThat(actual).isPresent();
    assertTimestamp(actual.get(), expected);
  }

  private static void assertTimestamp(Timestamp actual, Timestamp expected) {
    long timestampDiffMs = Math.abs(actual.getTime() - expected.getTime());
    assertThat(timestampDiffMs).isAtMost(SECONDS.toMillis(1));
  }
}
