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

package com.google.gerrit.plugins.checks;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.plugins.checks.api.CheckApi;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckResource;
import com.google.gerrit.plugins.checks.api.Checks;
import com.google.gerrit.plugins.checks.api.ChecksFactory;
import com.google.gerrit.server.change.RevisionResource;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PostCheck
    implements RestCollectionModifyView<RevisionResource, CheckResource, CheckInput> {

  private final ChecksFactory checksApiFactory;

  @Inject
  PostCheck(ChecksFactory checksApiFactory) {
    this.checksApiFactory = checksApiFactory;
  }

  @Override
  public CheckInfo apply(RevisionResource rsrc, CheckInput input) throws Exception {
    // Allow both creation and update on this endpoint (post on collection).
    Checks checksApi = checksApiFactory.revision(rsrc.getPatchSet().getId());
    try {
      CheckApi checkApi = checksApi.id(input.checkerUuid);
      return checkApi.update(input);
    } catch (ResourceNotFoundException e) {
      return checksApi.create(input).get();
    }
  }
}
