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

import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class CheckApiImpl implements CheckApi {
  public interface Factory {
    CheckApiImpl create(CheckResource c);
  }

  private final GetCheck getCheck;
  private final UpdateCheck updateCheck;
  private final CheckResource checkResource;

  @Inject
  CheckApiImpl(GetCheck getCheck, UpdateCheck updateCheck, @Assisted CheckResource checkResource) {
    this.getCheck = getCheck;
    this.updateCheck = updateCheck;
    this.checkResource = checkResource;
  }

  @Override
  public CheckInfo get() throws RestApiException {
    return getCheck.apply(checkResource);
  }

  @Override
  public CheckInfo update(CheckInput input) throws RestApiException {
    try {
      return updateCheck.apply(checkResource, input);
    } catch (Exception e) {
      throw asRestApiException("Cannot update check", e);
    }
  }
}
