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
import static com.google.common.truth.Truth8.assertThat;
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
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class ListChecksIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void listAll() throws Exception {
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).setState(CheckState.RUNNING).upsert();

    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).setState(CheckState.FAILED).upsert();

    Collection<CheckInfo> checks = checksApiFactory.revision(patchSetId).list();

    CheckInfo expected1 = new CheckInfo();
    expected1.repository = project.get();
    expected1.changeNumber = patchSetId.getParentKey().get();
    expected1.patchSetId = patchSetId.get();
    expected1.checkerUuid = checkerUuid1.get();
    expected1.state = CheckState.RUNNING;
    expected1.created = checkOperations.check(checkKey1).get().created();
    expected1.updated = expected1.created;

    CheckInfo expected2 = new CheckInfo();
    expected2.repository = project.get();
    expected2.changeNumber = patchSetId.getParentKey().get();
    expected2.patchSetId = patchSetId.get();
    expected2.checkerUuid = checkerUuid2.get();
    expected2.state = CheckState.FAILED;
    expected2.created = checkOperations.check(checkKey2).get().created();
    expected2.updated = expected2.created;

    assertThat(checks).containsExactly(expected1, expected2);
  }

  @Test
  public void listAllWithOptions() throws Exception {
    String checkerName1 = "Checker One";
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().name(checkerName1).repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).setState(CheckState.RUNNING).upsert();

    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).setState(CheckState.FAILED).upsert();

    Collection<CheckInfo> checks =
        checksApiFactory.revision(patchSetId).list(ListChecksOption.CHECKER);

    CheckInfo expected1 = new CheckInfo();
    expected1.repository = project.get();
    expected1.changeNumber = patchSetId.getParentKey().get();
    expected1.patchSetId = patchSetId.get();
    expected1.checkerUuid = checkerUuid1.get();
    expected1.state = CheckState.RUNNING;
    expected1.created = checkOperations.check(checkKey1).get().created();
    expected1.updated = expected1.created;
    expected1.checkerName = checkerName1;
    expected1.blocking = ImmutableSet.of();
    expected1.checkerStatus = CheckerStatus.ENABLED;

    CheckInfo expected2 = new CheckInfo();
    expected2.repository = project.get();
    expected2.changeNumber = patchSetId.getParentKey().get();
    expected2.patchSetId = patchSetId.get();
    expected2.checkerUuid = checkerUuid2.get();
    expected2.state = CheckState.FAILED;
    expected2.created = checkOperations.check(checkKey2).get().created();
    expected2.updated = expected2.created;
    expected2.blocking = ImmutableSet.of();
    expected2.checkerStatus = CheckerStatus.ENABLED;

    assertThat(checks).containsExactly(expected1, expected2);
  }

  @Test
  public void listAllWithOptionsSkipsPopulatingCheckerFieldsForInvalidCheckers() throws Exception {
    String checkerName1 = "Checker One";
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().name(checkerName1).repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid1)).upsert();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid2)).upsert();
    checkerOperations.checker(checkerUuid2).forUpdate().forceInvalidConfig().update();

    List<CheckInfo> checks = checksApiFactory.revision(patchSetId).list(ListChecksOption.CHECKER);

    assertThat(checks).hasSize(2);

    Optional<CheckInfo> maybeCheck1 =
        checks.stream().filter(c -> c.checkerUuid.equals(checkerUuid1.get())).findAny();
    assertThat(maybeCheck1).isPresent();
    CheckInfo check1 = maybeCheck1.get();
    assertThat(check1.checkerName).isEqualTo(checkerName1);
    assertThat(check1.blocking).isEmpty();
    assertThat(check1.checkerStatus).isEqualTo(CheckerStatus.ENABLED);

    Optional<CheckInfo> maybeCheck2 =
        checks.stream().filter(c -> c.checkerUuid.equals(checkerUuid2.get())).findAny();
    assertThat(maybeCheck2).isPresent();
    CheckInfo check2 = maybeCheck2.get();
    assertThat(check2.checkerName).isNull();
    assertThat(check2.blocking).isNull();
    assertThat(check2.checkerStatus).isNull();
  }

  @Test
  public void listIncludesCheckFromCheckerThatDoesNotApplyToTheProject() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    checkerOperations.checker(checkerUuid).forUpdate().repository(otherProject).update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromCheckerThatDoesNotApplyToTheChange() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().query("message:not-matching").update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listIncludesCheckFromNonExistingChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkOperations.newCheck(CheckKey.create(project, patchSetId, checkerUuid)).upsert();
    checkerOperations.checker(checkerUuid).forUpdate().deleteRef().update();

    assertThat(checksApiFactory.revision(patchSetId).list()).hasSize(1);
  }

  @Test
  public void listBackfillsForRelevantChecker() throws Exception {
    String topic = name("topic");
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).query("topic:" + topic).create();

    assertThat(checksApiFactory.revision(patchSetId).list()).isEmpty();

    // Update change to match checker's query.
    gApi.changes().id(patchSetId.getParentKey().get()).topic(topic);

    Timestamp psCreated = getPatchSetCreated(patchSetId.getParentKey());
    CheckInfo checkInfo = new CheckInfo();
    checkInfo.repository = project.get();
    checkInfo.changeNumber = patchSetId.getParentKey().get();
    checkInfo.patchSetId = patchSetId.get();
    checkInfo.checkerUuid = checkerUuid.get();
    checkInfo.state = CheckState.NOT_STARTED;
    checkInfo.created = psCreated;
    checkInfo.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).list()).containsExactly(checkInfo);
  }

  @Test
  public void listDoesntBackfillForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);

    Timestamp psCreated = getPatchSetCreated(patchSetId.getParentKey());
    CheckInfo checkInfo = new CheckInfo();
    checkInfo.repository = checkKey.repository().get();
    checkInfo.changeNumber = checkKey.patchSet().getParentKey().get();
    checkInfo.patchSetId = checkKey.patchSet().get();
    checkInfo.checkerUuid = checkKey.checkerUuid().get();
    checkInfo.state = CheckState.NOT_STARTED;
    checkInfo.created = psCreated;
    checkInfo.updated = psCreated;
    assertThat(checksApiFactory.revision(patchSetId).list()).containsExactly(checkInfo);

    // Disable checker.
    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    assertThat(checksApiFactory.revision(patchSetId).list()).isEmpty();
  }

  private Timestamp getPatchSetCreated(Change.Id changeId) throws RestApiException {
    return getOnlyElement(
            gApi.changes().id(changeId.get()).get(CURRENT_REVISION).revisions.values())
        .created;
  }
}
