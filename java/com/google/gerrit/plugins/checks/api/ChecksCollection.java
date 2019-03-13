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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class ChecksCollection implements ChildCollection<RevisionResource, CheckResource> {
  private final CheckBackfiller checkBackfiller;
  private final Checks checks;
  private final DynamicMap<RestView<CheckResource>> views;
  private final ListChecks listChecks;

  @Inject
  ChecksCollection(
      CheckBackfiller checkBackfiller,
      Checks checks,
      DynamicMap<RestView<CheckResource>> views,
      ListChecks listChecks) {
    this.checkBackfiller = checkBackfiller;
    this.checks = checks;
    this.views = views;
    this.listChecks = listChecks;
  }

  @Override
  public RestReadView<RevisionResource> list() throws RestApiException {
    return listChecks;
  }

  @Override
  public CheckResource parse(RevisionResource parent, IdString id)
      throws RestApiException, PermissionBackendException, IOException, OrmException {
    CheckerUuid checkerUuid =
        CheckerUuid.tryParse(id.get())
            .orElseThrow(
                () -> new BadRequestException(String.format("invalid checker UUID: %s", id.get())));
    CheckKey checkKey =
        CheckKey.create(parent.getProject(), parent.getPatchSet().getId(), checkerUuid);
    Optional<Check> check = checks.getCheck(checkKey);
    if (!check.isPresent()) {
      check =
          checkBackfiller.getBackfilledCheckForRelevantChecker(
              checkerUuid, parent.getNotes(), checkKey.patchSet());
    }
    return new CheckResource(
        parent,
        check.orElseThrow(
            () ->
                new ResourceNotFoundException(
                    String.format(
                        "Patch set %s in project %s doesn't have check for checker %s.",
                        checkKey.patchSet(), checkKey.project(), checkerUuid))));
  }

  @Override
  public DynamicMap<RestView<CheckResource>> views() {
    return views;
  }
}
