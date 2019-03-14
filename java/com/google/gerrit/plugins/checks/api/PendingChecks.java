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

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckerUuid;
import java.util.List;

public interface PendingChecks {
  /**
   * Lists the pending checks for the specified checker.
   *
   * @param checkerUuid the UUID of the checker for which pending checks should be listed
   * @param checkStates the states that should be considered as pending, if not specified {@link
   *     CheckState#NOT_STARTED} is assumed.
   * @return the pending checks
   */
  List<PendingChecksInfo> list(CheckerUuid checkerUuid, CheckState... checkStates)
      throws RestApiException;

  /**
   * Lists the pending checks for the specified checker.
   *
   * @param checkerUuidString the UUID of the checker for which pending checks should be listed
   * @param checkStates the states that should be considered as pending, if not specified {@link
   *     CheckState#NOT_STARTED} is assumed.
   * @return the pending checks
   */
  default List<PendingChecksInfo> list(String checkerUuidString, CheckState... checkStates)
      throws RestApiException {
    return list(
        CheckerUuid.tryParse(checkerUuidString)
            .orElseThrow(
                () ->
                    new BadRequestException(
                        String.format("invalid checker UUID: %s", checkerUuidString))),
        checkStates);
  }

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements PendingChecks {
    @Override
    public List<PendingChecksInfo> list(CheckerUuid checkerUuid, CheckState... checkStates) {
      throw new NotImplementedException();
    }
  }
}
