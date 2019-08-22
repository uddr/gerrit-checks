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

import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.ListChecksOption;

/** Java API to interact with single {@code Check}s. */
public interface CheckApi {
  /** Returns a {@link CheckInfo} for the scoped resource with the given options. */
  CheckInfo get(ListChecksOption... options) throws RestApiException;

  /** Updates a check and returns the {@link CheckInfo} for the updated resource. */
  CheckInfo update(CheckInput input) throws RestApiException;

  /** Reruns the check and returns the {@link CheckInfo} for the updated check. */
  CheckInfo rerun() throws RestApiException;
  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements CheckApi {
    @Override
    public CheckInfo get(ListChecksOption... options) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CheckInfo update(CheckInput input) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CheckInfo rerun() throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
