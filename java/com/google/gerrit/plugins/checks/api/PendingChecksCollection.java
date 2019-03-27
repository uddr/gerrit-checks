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
// limitations under the License

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class PendingChecksCollection
    implements ChildCollection<TopLevelResource, PendingCheckResource> {
  private final DynamicMap<RestView<PendingCheckResource>> views;
  private final QueryPendingChecks queryPendingChecks;

  @Inject
  public PendingChecksCollection(
      DynamicMap<RestView<PendingCheckResource>> views, QueryPendingChecks queryPendingChecks) {
    this.views = views;
    this.queryPendingChecks = queryPendingChecks;
  }

  @Override
  public RestView<TopLevelResource> list() throws RestApiException {
    return queryPendingChecks;
  }

  @Override
  public PendingCheckResource parse(TopLevelResource parent, IdString id)
      throws ResourceNotFoundException {
    throw new ResourceNotFoundException(id);
  }

  @Override
  public DynamicMap<RestView<PendingCheckResource>> views() {
    return views;
  }
}
