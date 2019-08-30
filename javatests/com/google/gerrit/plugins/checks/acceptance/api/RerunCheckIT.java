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

import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
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

public class RerunCheckIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ProjectOperations projectOperations;

  private PatchSet.Id patchSetId;
  private CheckKey checkKey;

  @Before
  public void setUp() throws Exception {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    TestTimeUtil.setClock(Timestamp.from(Instant.EPOCH));

    patchSetId = createChange().getPatchSetId();

    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    checkKey = CheckKey.create(project, patchSetId, checkerUuid);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void rerunResetsCheckInfo() throws Exception {
    checkOperations
        .newCheck(checkKey)
        .state(CheckState.FAILED)
        .started(TimeUtil.nowTs())
        .finished(TimeUtil.nowTs())
        .message("message")
        .url("url.com")
        .upsert();
    Timestamp created = checkOperations.check(checkKey).get().created();
    Timestamp updated = checkOperations.check(checkKey).get().updated();
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
    assertThat(info.message).isEqualTo(null);
    assertThat(info.url).isEqualTo(null);
    assertThat(info.started).isEqualTo(null);
    assertThat(info.finished).isEqualTo(null);
    assertThat(info.created).isEqualTo(created);
    assertThat(info.updated).isGreaterThan(updated);
  }

  @Test
  public void rerunNotStartedCheck() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.NOT_STARTED).upsert();
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void rerunFinishedCheck() throws Exception {
    checkOperations.newCheck(checkKey).state(CheckState.SUCCESSFUL).upsert();
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
    assertThat(info.updated).isGreaterThan(info.created);
  }

  @Test
  public void rerunCheckNotExistingButBackfilled() throws Exception {
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
    assertThat(checkOperations.check(checkKey).exists()).isFalse();
  }

  @Test
  public void rerunExistingCheckWithCheckerNotAppliedToChange() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();
    checkOperations.newCheck(checkKey).upsert();
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }

  @Test
  public void rerunNonExistingCheckWithCheckerNotAppliedToChange() throws Exception {
    Project.NameKey otherProject = projectOperations.newProject().create();
    checkerOperations.checker(checkKey.checkerUuid()).forUpdate().repository(otherProject).update();
    assertThrows(
        ResourceNotFoundException.class,
        () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun());
    assertThat(checkOperations.check(checkKey).exists()).isFalse();
  }

  @Test
  public void cannotUpdateCheckAnonymously() throws Exception {
    requestScopeOperations.setApiUserAnonymous();
    checkOperations.newCheck(checkKey).state(CheckState.SUCCESSFUL).upsert();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun());
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void canRerunWithoutPermissions() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    checkOperations.newCheck(checkKey).state(CheckState.SUCCESSFUL).upsert();
    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(info.state).isEqualTo(CheckState.NOT_STARTED);
  }
}
