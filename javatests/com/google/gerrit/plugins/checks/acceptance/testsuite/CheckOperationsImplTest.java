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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.hamcrest.CoreMatchers.instanceOf;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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
    assertThat(foundCheck.changeNumber).isEqualTo(checkKey.patchSet().getParentKey().get());
    assertThat(foundCheck.patchSetId).isEqualTo(checkKey.patchSet().get());
    assertThat(foundCheck.checkerUuid).isEqualTo(checkerUuid.get());
    assertThat(foundCheck.state).isNotNull();
    assertThat(foundCheck.url).isNull();
    assertThat(foundCheck.started).isNull();
    assertThat(foundCheck.finished).isNull();
    assertThat(foundCheck.created).isNotNull();
    assertThat(foundCheck.updated).isNotNull();
    assertThat(foundCheck.checkerName).isNull();
    assertThat(foundCheck.checkerStatus).isNull();
    assertThat(foundCheck.blocking).isNull();
  }

  @Test
  public void checkCannotBeCreatedForNonExistingRepo() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey =
        CheckKey.create(
            new Project.NameKey("non-existing"), createChange().getPatchSetId(), checkerUuid);

    exception.expect(IllegalStateException.class);
    exception.expectCause(instanceOf(RepositoryNotFoundException.class));
    checkOperations.newCheck(checkKey).upsert();
  }

  @Test
  public void checkCannotBeCreatedForNonExistingPatchSet() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey checkKey = CheckKey.create(project, new PatchSet.Id(new Change.Id(1), 1), checkerUuid);

    exception.expect(IllegalStateException.class);
    exception.expectCause(instanceOf(IOException.class));
    exception.expectMessage("patchset 1,1 not found");
    checkOperations.newCheck(checkKey).upsert();
  }

  @Test
  public void checkCannotBeCreatedForNonExistingChecker() throws Exception {
    CheckKey checkKey =
        CheckKey.create(project, createChange().getPatchSetId(), CheckerUuid.parse("foo:bar"));

    exception.expect(IllegalStateException.class);
    exception.expectCause(instanceOf(IOException.class));
    exception.expectMessage("checker foo:bar not found");
    checkOperations.newCheck(checkKey).upsert();
  }

  @Test
  public void checkCreatedByTestApiCanBeRetrievedViaOfficialApi() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    CheckInfo foundCheck = getCheckFromServer(checkKey);
    assertThat(foundCheck.repository).isEqualTo(project.get());
    assertThat(foundCheck.changeNumber).isEqualTo(checkKey.patchSet().getParentKey().get());
    assertThat(foundCheck.patchSetId).isEqualTo(checkKey.patchSet().get());
    assertThat(foundCheck.checkerUuid).isEqualTo(checkerUuid.get());
  }

  @Test
  public void specifiedUrlIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setUrl("http://example.com/my-check").upsert();

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
    checkOperations.newCheck(checkKey).setState(CheckState.FAILED).upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertThat(check.state).isEqualTo(CheckState.FAILED);
  }

  @Test
  public void specifiedStartedIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp started = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).setStarted(started).upsert();

    CheckInfo check = getCheckFromServer(checkKey);
    assertTimestamp(check.started, started);
  }

  @Test
  public void specifiedFinishedIsRespectedForCheckCreation() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp finished = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).setFinished(finished).upsert();

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

    exception.expect(IllegalStateException.class);
    checkOperations.check(notExistingCheckKey).get();
  }

  @Test
  public void retrievingNotExistingCheckOfRelevantCheckerFails() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey notExistingCheckKey =
        CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);

    exception.expect(IllegalStateException.class);
    checkOperations.check(notExistingCheckKey).get();
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
    checkOperations.newCheck(checkKey).setState(CheckState.FAILED).upsert();

    CheckState checkState = checkOperations.check(checkKey).get().state();

    assertThat(checkState).isEqualTo(CheckState.FAILED);
  }

  @Test
  public void urlOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setUrl("http://example.com/my-check").upsert();

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
    checkOperations.newCheck(checkKey).setStarted(started).upsert();

    Optional<Timestamp> foundStarted = checkOperations.check(checkKey).get().started();

    assertTimestamp(foundStarted, started);
  }

  @Test
  public void finishedOfExistingCheckCanBeRetrieved() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    Timestamp finished = new Timestamp(1234567L);
    checkOperations.newCheck(checkKey).setFinished(finished).upsert();

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
    checkOperations.newCheck(checkKey).setUrl("http://original-url").upsert();

    checkOperations.check(checkKey).forUpdate().setUrl("http://updated-url").upsert();

    String currentUrl = getCheckFromServer(checkKey).url;
    assertThat(currentUrl).isEqualTo("http://updated-url");
  }

  @Test
  public void stateCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.FAILED).upsert();

    checkOperations.check(checkKey).forUpdate().setState(CheckState.SUCCESSFUL).upsert();

    CheckState currentState = checkOperations.check(checkKey).get().state();
    assertThat(currentState).isEqualTo(CheckState.SUCCESSFUL);
  }

  @Test
  public void urlCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setUrl("http://original-url").upsert();

    checkOperations.check(checkKey).forUpdate().setUrl("http://updated-url").upsert();

    Optional<String> currentUrl = checkOperations.check(checkKey).get().url();
    assertThat(currentUrl).hasValue("http://updated-url");
  }

  @Test
  public void urlCanBeCleared() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setUrl("http://original-url").upsert();

    checkOperations.check(checkKey).forUpdate().clearUrl().upsert();

    Optional<String> currentUrl = checkOperations.check(checkKey).get().url();
    assertThat(currentUrl).isEmpty();
  }

  @Test
  public void startedCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setStarted(new Timestamp(1234567L)).upsert();

    Timestamp updatedStarted = new Timestamp(7654321L);
    checkOperations.check(checkKey).forUpdate().setStarted(updatedStarted).upsert();

    Optional<Timestamp> currentStarted = checkOperations.check(checkKey).get().started();
    assertTimestamp(currentStarted, updatedStarted);
  }

  @Test
  public void finishedCanBeUpdated() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(checkKey).setFinished(new Timestamp(1234567L)).upsert();

    Timestamp updatedFinished = new Timestamp(7654321L);
    checkOperations.check(checkKey).forUpdate().setFinished(updatedFinished).upsert();

    Optional<Timestamp> currentFinished = checkOperations.check(checkKey).get().finished();
    assertTimestamp(currentFinished, updatedFinished);
  }

  @Test
  public void getNotesAsText() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    CheckKey checkKey =
        createCheckInServer(project, patchSetId, createArbitraryCheckInput(checkerUuid));

    ImmutableMap<RevId, String> notesAsText = checkOperations.check(checkKey).notesAsText();
    RevId expectedRevId = getRevId(patchSetId);
    assertThat(notesAsText).containsExactly(expectedRevId, readNoteText(checkKey));
  }

  private String readNoteText(CheckKey checkKey) throws Exception {
    try (Repository repo = repoManager.openRepository(checkKey.repository());
        RevWalk rw = new RevWalk(repo);
        ObjectReader reader = repo.newObjectReader()) {
      Ref checkRef =
          repo.getRefDatabase().exactRef(CheckerRef.checksRef(checkKey.patchSet().changeId));
      checkNotNull(checkRef);

      NoteMap notes = NoteMap.read(reader, rw.parseCommit(checkRef.getObjectId()));
      ObjectId noteId = notes.get(ObjectId.fromString(getRevId(checkKey.patchSet()).get()));
      return new String(reader.open(noteId, OBJ_BLOB).getCachedBytes(Integer.MAX_VALUE), UTF_8);
    }
  }

  @Test
  public void asInfo() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);

    checkOperations
        .newCheck(checkKey)
        .setState(CheckState.RUNNING)
        .setUrl("http://example.com/my-check")
        .setStarted(new Timestamp(1234567L))
        .setFinished(new Timestamp(7654321L))
        .upsert();
    Check check = checkOperations.check(checkKey).get();
    CheckInfo checkInfo = checkOperations.check(checkKey).asInfo();
    assertThat(checkInfo.repository).isEqualTo(check.key().repository().get());
    assertThat(checkInfo.changeNumber).isEqualTo(check.key().patchSet().getParentKey().get());
    assertThat(checkInfo.patchSetId).isEqualTo(check.key().patchSet().get());
    assertThat(checkInfo.checkerUuid).isEqualTo(check.key().checkerUuid().get());
    assertThat(checkInfo.state).isEqualTo(check.state());
    assertThat(checkInfo.url).isEqualTo(check.url().get());
    assertThat(checkInfo.started).isEqualTo(check.started().get());
    assertThat(checkInfo.finished).isEqualTo(check.finished().get());
    assertThat(checkInfo.created).isEqualTo(check.created());
    assertThat(checkInfo.updated).isEqualTo(check.updated());
  }

  private CheckInfo getCheckFromServer(CheckKey checkKey) throws RestApiException, OrmException {
    return checksApiFactory.revision(checkKey.patchSet()).id(checkKey.checkerUuid()).get();
  }

  private CheckInput createArbitraryCheckInput(CheckerUuid checkerUuid) {
    CheckInput checkInput = new CheckInput();
    checkInput.checkerUuid = checkerUuid.get();
    checkInput.state = CheckState.SCHEDULED;
    checkInput.url = "http://example.com/my-check";
    checkInput.started = TimeUtil.nowTs();
    return checkInput;
  }

  private CheckKey createCheckInServer(
      Project.NameKey repositoryName, PatchSet.Id patchSetId, CheckInput checkInput)
      throws RestApiException, OrmException {
    checksApiFactory.revision(patchSetId).create(checkInput);
    return CheckKey.create(repositoryName, patchSetId, CheckerUuid.parse(checkInput.checkerUuid));
  }

  private RevId getRevId(PatchSet.Id patchSetId) throws Exception {
    return new RevId(
        gApi.changes()
            .id(patchSetId.changeId.get())
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
