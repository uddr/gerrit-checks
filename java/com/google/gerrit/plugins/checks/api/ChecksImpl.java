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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

class ChecksImpl implements com.google.gerrit.plugins.checks.api.Checks {

  interface Factory {
    ChecksImpl create(RevisionResource revisionResource);
  }

  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson checkJson;
  private final CheckApiImpl.Factory checkApiImplFactory;
  private final RevisionResource revisionResource;

  @Inject
  ChecksImpl(
      Checks checks,
      CheckApiImpl.Factory checkApiImplFactory,
      CheckJson checkJson,
      @UserInitiated Provider<ChecksUpdate> checksUpdate,
      @Assisted RevisionResource revisionResource) {
    this.checks = checks;
    this.checkApiImplFactory = checkApiImplFactory;
    this.checkJson = checkJson;
    this.checksUpdate = checksUpdate;
    this.revisionResource = revisionResource;
  }

  @Override
  public CheckApi id(String checkerUuid) throws RestApiException, IOException, OrmException {
    if (checkerUuid == null || checkerUuid.isEmpty()) {
      throw new BadRequestException("checkerUuid is required");
    }

    CheckKey checkKey =
        CheckKey.create(
            revisionResource.getProject(), revisionResource.getPatchSet().getId(), checkerUuid);
    Optional<Check> check = checks.getCheck(checkKey);
    return checkApiImplFactory.create(
        new CheckResource(
            revisionResource,
            check.orElseThrow(() -> new ResourceNotFoundException("Not found: " + checkerUuid))));
  }

  @Override
  public CheckApi create(CheckInput input) throws RestApiException, IOException, OrmException {
    if (input == null) {
      throw new BadRequestException("input is required");
    }
    if (input.checkerUuid == null) {
      throw new BadRequestException("checkerUuid is required");
    }
    if (input.state == null) {
      throw new BadRequestException("state is required");
    }

    CheckKey checkKey =
        CheckKey.create(
            revisionResource.getProject(),
            revisionResource.getPatchSet().getId(),
            input.checkerUuid);
    CheckUpdate checkUpdate =
        CheckUpdate.builder()
            .setState(input.state)
            .setUrl(Optional.ofNullable(input.url))
            .setStarted(Optional.ofNullable(input.started))
            .setFinished(Optional.ofNullable(input.finished))
            .build();
    Check newCheck = checksUpdate.get().createCheck(checkKey, checkUpdate);
    return checkApiImplFactory.create(new CheckResource(revisionResource, newCheck));
  }

  @Override
  public ImmutableList<CheckInfo> list() throws RestApiException, IOException, OrmException {
    return getChecks().stream().map(checkJson::format).collect(toImmutableList());
  }

  private List<Check> getChecks() throws IOException, OrmException {
    return checks.getChecks(revisionResource.getProject(), revisionResource.getPatchSet().getId());
  }
}
