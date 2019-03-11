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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.PostCheck;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;

class ChecksImpl implements com.google.gerrit.plugins.checks.api.Checks {

  interface Factory {
    ChecksImpl create(RevisionResource revisionResource);
  }

  private final Checkers checkers;
  private final Checks checks;
  private final ListChecks listChecks;
  private final PostCheck postCheck;
  private final CheckApiImpl.Factory checkApiImplFactory;
  private final RevisionResource revisionResource;

  @Inject
  ChecksImpl(
      CheckApiImpl.Factory checkApiImplFactory,
      Checks checks,
      Checkers checkers,
      ListChecks listChecks,
      PostCheck postCheck,
      @Assisted RevisionResource revisionResource) {
    this.checkApiImplFactory = checkApiImplFactory;
    this.checks = checks;
    this.postCheck = postCheck;
    this.checkers = checkers;
    this.listChecks = listChecks;
    this.revisionResource = revisionResource;
  }

  @Override
  public CheckApi id(CheckerUuid checkerUuid) throws RestApiException {
    // Ensure that the checker exists and throw a RestApiException if not.
    checkers.id(checkerUuid).get();

    try {
      CheckKey checkKey =
          CheckKey.create(
              revisionResource.getProject(), revisionResource.getPatchSet().getId(), checkerUuid);
      Optional<Check> check = checks.getCheck(checkKey);
      return checkApiImplFactory.create(
          new CheckResource(
              revisionResource,
              check.orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          String.format(
                              "Patch set %s in project %s doesn't have check for checker %s.",
                              revisionResource.getPatchSet().getId(),
                              revisionResource.getProject(),
                              checkerUuid)))));
    } catch (Exception e) {
      throw asRestApiException("Cannot parse check", e);
    }
  }

  @Override
  public CheckApi create(CheckInput input) throws RestApiException {
    try {
      CheckInfo checkInfo = postCheck.apply(revisionResource, input);
      return id(CheckerUuid.parse(checkInfo.checkerUuid));
    } catch (Exception e) {
      throw asRestApiException("Cannot create check", e);
    }
  }

  @Override
  public ImmutableList<CheckInfo> list() throws RestApiException {
    try {
      return listChecks.apply(revisionResource);
    } catch (Exception e) {
      throw asRestApiException("Cannot list checks", e);
    }
  }
}
