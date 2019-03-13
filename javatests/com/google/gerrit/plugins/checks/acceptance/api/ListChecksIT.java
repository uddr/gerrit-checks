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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import java.sql.Timestamp;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

public class ListChecksIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;
  private CheckKey checkKey1;
  private CheckKey checkKey2;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checker1Uuid = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checker2Uuid =
        checkerOperations.newChecker().name("Checker Two").repository(project).create();

    checkKey1 = CheckKey.create(project, patchSetId, checker1Uuid);
    checkOperations.newCheck(checkKey1).setState(CheckState.RUNNING).upsert();

    checkKey2 = CheckKey.create(project, patchSetId, checker2Uuid);
    checkOperations.newCheck(checkKey2).setState(CheckState.RUNNING).upsert();
  }

  @Test
  public void listAll() throws Exception {
    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();

    CheckInfo expected1 = new CheckInfo();
    expected1.project = checkKey1.project().get();
    expected1.changeNumber = checkKey1.patchSet().getParentKey().get();
    expected1.patchSetId = checkKey1.patchSet().get();
    expected1.checkerUuid = checkKey1.checkerUuid().toString();
    expected1.state = CheckState.RUNNING;
    expected1.created = checkOperations.check(checkKey1).get().created();
    expected1.updated = expected1.created;

    CheckInfo expected2 = new CheckInfo();
    expected2.project = checkKey2.project().get();
    expected2.changeNumber = checkKey2.patchSet().getParentKey().get();
    expected2.patchSetId = checkKey2.patchSet().get();
    expected2.checkerUuid = checkKey2.checkerUuid().toString();
    expected2.state = CheckState.RUNNING;
    expected2.created = checkOperations.check(checkKey2).get().created();
    expected2.updated = expected2.created;

    assertThat(info).containsExactly(expected1, expected2);
  }

  @Test
  public void listAllWithOptions() throws Exception {
    Collection<CheckInfo> info =
        checksApiFactory.revision(patchSetId).list(ListChecksOption.CHECKER);

    Timestamp psCreated = getPatchSetCreated(patchSetId.getParentKey());
    CheckInfo expected1 = new CheckInfo();
    expected1.project = checkKey1.project().get();
    expected1.changeNumber = checkKey1.patchSet().getParentKey().get();
    expected1.patchSetId = checkKey1.patchSet().get();
    expected1.checkerUuid = checkKey1.checkerUuid().toString();
    expected1.state = CheckState.RUNNING;
    expected1.created = psCreated;
    expected1.updated = psCreated;
    expected1.blocking = ImmutableSet.of();
    expected1.checkerStatus = CheckerStatus.ENABLED;

    CheckInfo expected2 = new CheckInfo();
    expected2.project = checkKey2.project().get();
    expected2.changeNumber = checkKey2.patchSet().getParentKey().get();
    expected2.patchSetId = checkKey2.patchSet().get();
    expected2.checkerUuid = checkKey2.checkerUuid().toString();
    expected2.state = CheckState.RUNNING;
    expected2.created = psCreated;
    expected2.updated = psCreated;
    expected2.checkerName = "Checker Two";
    expected2.blocking = ImmutableSet.of();
    expected2.checkerStatus = CheckerStatus.ENABLED;

    assertThat(info).containsExactly(expected1, expected2);
  }

  @Test
  public void listIncludesCheckFromCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    checkerOperations
        .checker(checkKey2.checkerUuid())
        .forUpdate()
        .repository(otherProject)
        .update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listIncludesCheckFromCheckerThatDoesNotApplyToTheChange() throws Exception {
    checkerOperations
        .checker(checkKey2.checkerUuid())
        .forUpdate()
        .query("message:not-matching")
        .update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listIncludesCheckFromDisabledChecker() throws Exception {
    checkerOperations.checker(checkKey2.checkerUuid()).forUpdate().disable().update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listIncludesCheckFromInvalidChecker() throws Exception {
    checkerOperations.checker(checkKey2.checkerUuid()).forUpdate().forceInvalidConfig().update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listIncludesCheckFromNonExistingChecker() throws Exception {
    checkerOperations.checker(checkKey2.checkerUuid()).forUpdate().deleteRef().update();

    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
  }

  @Test
  public void listBackfillsForRelevantChecker() throws Exception {
    String topic = name("topic");
    Change.Id changeId = patchSetId.getParentKey();
    CheckerUuid checker3Uuid =
        checkerOperations.newChecker().repository(project).query("topic:" + topic).create();
    CheckKey checkKey3 = CheckKey.create(project, patchSetId, checker3Uuid);

    CheckInfo checkInfo1 = checkOperations.check(checkKey1).asInfo();
    CheckInfo checkInfo2 = checkOperations.check(checkKey2).asInfo();
    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(checkInfo1, checkInfo2);

    // Update change to match checker3's query.
    gApi.changes().id(changeId.get()).topic(topic);

    Timestamp psCreated = getPatchSetCreated(patchSetId.getParentKey());
    CheckInfo checkInfo3 = new CheckInfo();
    checkInfo3.project = checkKey3.project().get();
    checkInfo3.changeNumber = checkKey3.patchSet().getParentKey().get();
    checkInfo3.patchSetId = checkKey3.patchSet().get();
    checkInfo3.checkerUuid = checkKey3.checkerUuid().toString();
    checkInfo3.state = CheckState.NOT_STARTED;
    checkInfo3.created = psCreated;
    checkInfo3.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(checkInfo1, checkInfo2, checkInfo3);
  }

  @Test
  public void listDoesntBackfillForDisabledChecker() throws Exception {
    CheckerUuid checker3Uuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey3 = CheckKey.create(project, patchSetId, checker3Uuid);

    CheckInfo checkInfo1 = checkOperations.check(checkKey1).asInfo();
    CheckInfo checkInfo2 = checkOperations.check(checkKey2).asInfo();
    Timestamp psCreated = getPatchSetCreated(patchSetId.getParentKey());
    CheckInfo checkInfo3 = new CheckInfo();
    checkInfo3.project = checkKey3.project().get();
    checkInfo3.changeNumber = checkKey3.patchSet().getParentKey().get();
    checkInfo3.patchSetId = checkKey3.patchSet().get();
    checkInfo3.checkerUuid = checkKey3.checkerUuid().toString();
    checkInfo3.state = CheckState.NOT_STARTED;
    checkInfo3.created = psCreated;
    checkInfo3.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(checkInfo1, checkInfo2, checkInfo3);

    // Disable checker3.
    checkerOperations.checker(checker3Uuid).forUpdate().disable().update();

    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(checkInfo1, checkInfo2);
  }

  private Timestamp getPatchSetCreated(Change.Id changeId) throws RestApiException {
    return getOnlyElement(
            gApi.changes().id(changeId.get()).get(CURRENT_REVISION).revisions.values())
        .created;
  }
}
