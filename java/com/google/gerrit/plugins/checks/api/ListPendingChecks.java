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

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

public class ListPendingChecks implements RestReadView<TopLevelResource> {
  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;

  private CheckerUuid checkerUuid;
  private String scheme;
  private List<CheckState> states = new ArrayList<>(CheckState.values().length);

  @Option(
      name = "--checker",
      metaVar = "UUID",
      usage = "checker UUID formated as '<scheme>:<id>'",
      handler = CheckerUuidHandler.class)
  public void setChecker(CheckerUuid checkerUuid) {
    this.checkerUuid = checkerUuid;
  }

  @Option(name = "--scheme", metaVar = "SCHEME", usage = "checker scheme")
  public void setScheme(String scheme) {
    this.scheme = scheme;
  }

  @Option(name = "--state", metaVar = "STATE", usage = "check state")
  public void addState(CheckState state) {
    this.states.add(state);
  }

  @Inject
  public ListPendingChecks(
      PermissionBackend permissionBackend, AdministrateCheckersPermission permission) {
    this.permissionBackend = permissionBackend;
    this.permission = permission;
  }

  @Override
  public List<PendingChecksInfo> apply(TopLevelResource resource)
      throws RestApiException, PermissionBackendException {
    permissionBackend.currentUser().check(permission);

    if (states.isEmpty()) {
      // If no state was specified, assume NOT_STARTED by default.
      states.add(CheckState.NOT_STARTED);
    }

    if (checkerUuid == null && scheme == null) {
      throw new BadRequestException("checker or scheme is required");
    }

    if (checkerUuid != null && scheme != null) {
      throw new BadRequestException("checker and scheme are mutually exclusive");
    }

    // TODO(ekempin): Implement this REST endpoint
    throw new MethodNotAllowedException("not implemented");
  }
}
