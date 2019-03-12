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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import java.sql.Timestamp;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

public class ListChecksIT extends AbstractCheckersTest {
  private CheckJson checkJson;
  private PatchSet.Id patchSetId;
  private CheckKey checkKey1;
  private CheckKey checkKey2;

  @Before
  public void setUp() throws Exception {
    checkJson = plugin.getSysInjector().getInstance(CheckJson.class);
    patchSetId = createChange().getPatchSetId();

    CheckerUuid checker1Uuid = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checker2Uuid = checkerOperations.newChecker().repository(project).create();

    checkKey1 = CheckKey.create(project, patchSetId, checker1Uuid);
    checkOperations.newCheck(checkKey1).setState(CheckState.RUNNING).upsert();

    checkKey2 = CheckKey.create(project, patchSetId, checker2Uuid);
    checkOperations.newCheck(checkKey2).setState(CheckState.RUNNING).upsert();
  }

  @Test
  public void listAll() throws Exception {
    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info)
        .containsExactly(
            checkOperations.check(checkKey1).asInfo(), checkOperations.check(checkKey2).asInfo());
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

    // TODO(dborowitz): Get from checkOperations once we backfill the get API as well.
    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo checkInfo3 =
        checkJson.format(
            Check.builder(checkKey3)
                .setState(CheckState.NOT_STARTED)
                .setCreated(psCreated)
                .setUpdated(psCreated)
                .build());
    assertThat(checksApiFactory.revision(patchSetId).list())
        .containsExactly(checkInfo1, checkInfo2, checkInfo3);
  }

  @Test
  public void listDoesntBackfillForDisabledChecker() throws Exception {
    Change.Id changeId = patchSetId.getParentKey();
    CheckerUuid checker3Uuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey3 = CheckKey.create(project, patchSetId, checker3Uuid);

    CheckInfo checkInfo1 = checkOperations.check(checkKey1).asInfo();
    CheckInfo checkInfo2 = checkOperations.check(checkKey2).asInfo();

    // TODO(dborowitz): Get from checkOperations once we backfill the get API as well.
    Timestamp psCreated = getPatchSetCreated(changeId);
    CheckInfo checkInfo3 =
        checkJson.format(
            Check.builder(checkKey3)
                .setState(CheckState.NOT_STARTED)
                .setCreated(psCreated)
                .setUpdated(psCreated)
                .build());

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
