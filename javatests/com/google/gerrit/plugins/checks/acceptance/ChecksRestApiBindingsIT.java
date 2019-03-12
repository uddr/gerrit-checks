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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import org.junit.Test;

public class ChecksRestApiBindingsIT extends AbstractCheckersTest {
  private static final ImmutableList<RestCall> ROOT_ENDPOINTS =
      ImmutableList.of(
          RestCall.post("/plugins/checks/checkers/"),
          RestCall.get("/plugins/checks/checks.pending/"));

  private static final ImmutableList<RestCall> CHECKER_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/plugins/checks/checkers/%s"),
          RestCall.post("/plugins/checks/checkers/%s"));

  private static final ImmutableList<RestCall> CHECK_ENDPOINTS =
      ImmutableList.of(RestCall.get("/changes/%s/revisions/%s/checks~checks"));

  private static final ImmutableList<RestCall> SCOPED_CHECK_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s/revisions/%s/checks~checks/%s"),
          RestCall.post("/changes/%s/revisions/%s/checks~checks/%s"));

  @Test
  public void rootEndpoints() throws Exception {
    RestApiCallHelper.execute(adminRestSession, ROOT_ENDPOINTS);
  }

  @Test
  public void checkerEndpoints() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    RestApiCallHelper.execute(adminRestSession, CHECKER_ENDPOINTS, checkerUuid.toString());
  }

  @Test
  public void checkEndpoints() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    CheckKey key = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(key).setState(CheckState.RUNNING).upsert();

    RestApiCallHelper.execute(
        adminRestSession,
        CHECK_ENDPOINTS,
        String.valueOf(key.patchSet().changeId.id),
        String.valueOf(key.patchSet().patchSetId));
  }

  @Test
  public void scopedCheckEndpoints() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().repository(project).create();
    CheckKey key = CheckKey.create(project, createChange().getPatchSetId(), checkerUuid);
    checkOperations.newCheck(key).setState(CheckState.RUNNING).upsert();
    RestApiCallHelper.execute(
        adminRestSession,
        SCOPED_CHECK_ENDPOINTS,
        String.valueOf(key.patchSet().changeId.id),
        String.valueOf(key.patchSet().patchSetId),
        checkerUuid.toString());
  }

  @Test
  public void postOnCheckCollectionForCreate() throws Exception {
    PatchSet.Id patchSetId = createChange().getPatchSetId();
    RestApiCallHelper.execute(
        adminRestSession,
        RestCall.post("/changes/%s/revisions/%s/checks~checks/"),
        String.valueOf(patchSetId.changeId.id),
        String.valueOf(patchSetId.patchSetId));
  }
}
