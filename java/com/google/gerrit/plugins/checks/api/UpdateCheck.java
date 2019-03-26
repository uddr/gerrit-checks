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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.plugins.checks.PostCheck;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class UpdateCheck implements RestModifyView<CheckResource, CheckInput> {
  private final PostCheck postCheck;

  @Inject
  UpdateCheck(PostCheck postCheck) {
    this.postCheck = postCheck;
  }

  @Override
  public CheckInfo apply(CheckResource checkResource, CheckInput input)
      throws RestApiException, IOException, OrmException, PermissionBackendException,
          ConfigInvalidException {
    if (input == null) {
      input = new CheckInput();
    }
    if (input.checkerUuid == null) {
      input.checkerUuid = checkResource.getCheckerUuid().get();
    } else if (!checkResource.getCheckerUuid().get().equals(input.checkerUuid)) {
      throw new BadRequestException(
          String.format(
              "checker UUID in input must either be null or the same as on the resource:\n"
                  + "the check resource belongs to checker %s,"
                  + " but in the input checker %s was specified",
              checkResource.getCheckerUuid(), input.checkerUuid));
    }

    return postCheck.apply(checkResource.getRevisionResource(), input);
  }
}
