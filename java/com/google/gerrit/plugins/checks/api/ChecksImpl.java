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
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.PostCheck;
import com.google.gerrit.server.change.RevisionResource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Arrays;

class ChecksImpl implements com.google.gerrit.plugins.checks.api.Checks {

  interface Factory {
    ChecksImpl create(RevisionResource revisionResource);
  }

  private final CheckApiImpl.Factory checkApiImplFactory;
  private final ChecksCollection checksCollection;
  private final ListChecks listChecks;
  private final PostCheck postCheck;
  private final RevisionResource revisionResource;

  @Inject
  ChecksImpl(
      CheckApiImpl.Factory checkApiImplFactory,
      ChecksCollection checksCollection,
      ListChecks listChecks,
      PostCheck postCheck,
      @Assisted RevisionResource revisionResource) {
    this.checkApiImplFactory = checkApiImplFactory;
    this.checksCollection = checksCollection;
    this.listChecks = listChecks;
    this.postCheck = postCheck;
    this.revisionResource = revisionResource;
  }

  @Override
  public CheckApi id(CheckerUuid checkerUuid) throws RestApiException {
    try {
      return checkApiImplFactory.create(
          checksCollection.parse(revisionResource, IdString.fromDecoded(checkerUuid.toString())));
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
  public ImmutableList<CheckInfo> list(ListChecksOption... options) throws RestApiException {
    try {
      Arrays.stream(options).forEach(listChecks::addOption);
      return listChecks.apply(revisionResource);
    } catch (Exception e) {
      throw asRestApiException("Cannot list checks", e);
    }
  }
}
