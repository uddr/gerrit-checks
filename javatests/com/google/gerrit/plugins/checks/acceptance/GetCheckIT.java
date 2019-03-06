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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
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
    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid.toString());
    checkOperations.newCheck(key).setState(CheckState.RUNNING).upsert();

    CheckInfo info = checksApiFactory.revision(patchSetId).id(checkerUuid.toString()).get();
    assertThat(info).isEqualTo(checkOperations.check(key).asInfo());
  }

  @Test
  public void getCheckForDisabledCheckerThrowsNotFound() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid.toString());
    checkOperations.newCheck(key).setState(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().disable().update();

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + checkerUuid);
    checksApiFactory.revision(patchSetId).id(checkerUuid.toString()).get();
  }

  @Test
  public void getCheckForInvalidCheckerThrowsNotFound() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey key = CheckKey.create(project, patchSetId, checkerUuid.toString());
    checkOperations.newCheck(key).setState(CheckState.RUNNING).upsert();

    checkerOperations.checker(checkerUuid).forUpdate().forceInvalidConfig().update();

    exception.expect(RestApiException.class);
    exception.expectMessage("Cannot retrieve checker " + checkerUuid);
    checksApiFactory.revision(patchSetId).id(checkerUuid.toString()).get();
  }

  @Test
  public void getNonExistingCheckFails() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: non-existing");

    checksApiFactory.revision(patchSetId).id("non-existing").get();
  }

  @Test
  public void getNonExistingCheckWithInvalidUuidFails() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: n0n-e#isting");

    checksApiFactory.revision(patchSetId).id("n0n-e#isting").get();
  }
}
