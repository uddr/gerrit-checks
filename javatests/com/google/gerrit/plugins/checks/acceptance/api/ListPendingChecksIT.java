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

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.inject.Inject;
import org.junit.Test;

public class ListPendingChecksIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void specifyingCheckerUuidIsRequired() throws Exception {
    // The extension API doesn't allow to not specify a checker UUID. Call the endpoint over REST to
    // test this.
    RestResponse response = adminRestSession.get("/plugins/checks/checks.pending/");
    response.assertBadRequest();
    assertThat(response.getEntityContent()).isEqualTo("checker UUID is required");
  }

  @Test
  public void cannotListPendingChecksForMalformedCheckerUuid() throws Exception {
    exception.expect(BadRequestException.class);
    exception.expectMessage("invalid checker UUID: malformed::checker*UUID");
    pendingChecksApi.list("malformed::checker*UUID");
  }

  @Test
  public void cannotListPendingChecksWithoutAdministrateCheckers() throws Exception {
    requestScopeOperations.setApiUser(user.getId());

    exception.expect(AuthException.class);
    exception.expectMessage("not permitted");
    pendingChecksApi.list("foo:bar");
  }

  @Test
  public void listPendingChecksForCheckerNotImplemented() throws Exception {
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("not implemented");
    pendingChecksApi.list("foo:bar");
  }
}
