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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class ListChecks implements RestReadView<RevisionResource> {

  private final Checks checks;
  private final CheckJson checkJson;

  @Inject
  ListChecks(Checks checks, CheckJson checkJson) {
    this.checks = checks;
    this.checkJson = checkJson;
  }

  @Override
  public ImmutableList<CheckInfo> apply(RevisionResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, OrmException,
          IOException {
    return checks
        .getChecks(resource.getProject(), resource.getPatchSet().getId())
        .stream()
        .map(checkJson::format)
        .collect(toImmutableList());
  }
}
