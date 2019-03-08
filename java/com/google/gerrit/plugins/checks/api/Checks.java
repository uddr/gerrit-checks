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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;

/** Java API to interact with {@code Check}s. */
public interface Checks {
  CheckApi id(CheckerUuid checkerUuid) throws RestApiException, IOException, OrmException;

  default CheckApi id(String uuidString) throws RestApiException, IOException, OrmException {
    return id(
        CheckerUuid.tryParse(uuidString)
            .orElseThrow(() -> new ResourceNotFoundException("Not found: " + uuidString)));
  }

  CheckApi create(CheckInput input) throws RestApiException, IOException, OrmException;

  ImmutableList<CheckInfo> list() throws RestApiException, IOException, OrmException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements Checks {
    @Override
    public CheckApi id(CheckerUuid checkerUuid) throws RestApiException, IOException, OrmException {
      throw new NotImplementedException();
    }

    @Override
    public CheckApi create(CheckInput input) throws RestApiException, IOException, OrmException {
      throw new NotImplementedException();
    }

    @Override
    public ImmutableList<CheckInfo> list() throws RestApiException, IOException, OrmException {
      throw new NotImplementedException();
    }
  }
}
