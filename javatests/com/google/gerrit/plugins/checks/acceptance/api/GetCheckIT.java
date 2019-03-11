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

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Before;
import org.junit.Test;

public class GetCheckIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void getCheck() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    CheckInfo checkInfo = checksApiFactory.revision(patchSetId).id(checkerUuid).get();
    assertThat(checkInfo).isEqualTo(checkOperations.check(checkKey).asInfo());
  }

  @Test
  public void getCheckForCheckerThatDoesNotApplyToTheProject() throws Exception {
    Project.NameKey otherProject = createProjectOverAPI("other", null, true, null);
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(otherProject).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(
        String.format(
            "Patch set %s in project %s doesn't have check for checker %s.",
            patchSetId, project, checkerUuid));
    checksApiFactory.revision(patchSetId).id(checkerUuid);
  }

  // TODO(gerrit-team): I think according to our latest discussions it should be possible to
  // retrieve checks of disabled checkers, when we implement this, this test needs to be adapted
  @Test
  public void getCheckForDisabledChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();

    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).setState(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(
        String.format(
            "Patch set %s in project %s doesn't have check for checker %s.",
            patchSetId, project, checkerUuid));
    checksApiFactory.revision(patchSetId).id(checkerUuid);
  }

  @Test
  public void getCheckForInvalidChecker() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(key).setState(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    exception.expect(RestApiException.class);
    exception.expectMessage("Cannot retrieve checker " + checkerUuid);
    checksApiFactory.revision(patchSetId).id(checkerUuid.toString());
  }

  @Test
  public void getCheckForNonExistingChecker() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: test:non-existing");
    checksApiFactory.revision(patchSetId).id("test:non-existing");
  }

  @Test
  public void getCheckForInvalidCheckerUuid() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid checker UUID: malformed::checker*UUID");
    checksApiFactory.revision(patchSetId).id("malformed::checker*UUID");
  }
}
