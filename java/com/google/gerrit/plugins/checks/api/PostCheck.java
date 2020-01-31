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

import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.checks.AdministrateCheckersPermission;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.UrlValidator;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PostCheck
    implements RestCollectionModifyView<RevisionResource, CheckResource, CheckInput> {
  private final Provider<CurrentUser> self;
  private final PermissionBackend permissionBackend;
  private final AdministrateCheckersPermission permission;
  private final Checkers checkers;
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;
  private final PluginConfigFactory pluginConfigFactory;

  @Inject
  PostCheck(
      Provider<CurrentUser> self,
      PermissionBackend permissionBackend,
      AdministrateCheckersPermission permission,
      Checkers checkers,
      Checks checks,
      @UserInitiated Provider<ChecksUpdate> checksUpdate,
      CheckJson.Factory checkJsonFactory,
      PluginConfigFactory pluginConfigFactory) {
    this.self = self;
    this.permissionBackend = permissionBackend;
    this.permission = permission;
    this.checkers = checkers;
    this.checks = checks;
    this.checksUpdate = checksUpdate;
    this.checkJsonFactory = checkJsonFactory;
    this.pluginConfigFactory = pluginConfigFactory;
  }

  @Override
  public Response<CheckInfo> apply(RevisionResource rsrc, CheckInput input)
      throws StorageException, IOException, RestApiException, PermissionBackendException,
          ConfigInvalidException {
    if (!self.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }
    permissionBackend.currentUser().check(permission);

    if (rsrc.getEdit().isPresent()) {
      throw new ResourceConflictException("checks are not supported on a change edit");
    }

    if (input == null) {
      input = new CheckInput();
    }
    if (input.checkerUuid == null) {
      throw new BadRequestException("checker UUID is required");
    }
    if (!CheckerUuid.isUuid(input.checkerUuid)) {
      throw new BadRequestException(String.format("invalid checker UUID: %s", input.checkerUuid));
    }

    CheckerUuid checkerUuid = CheckerUuid.parse(input.checkerUuid);

    CheckKey key = CheckKey.create(rsrc.getProject(), rsrc.getPatchSet().id(), checkerUuid);
    Optional<Check> check = checks.getCheck(key, GetCheckOptions.defaults());
    Check updatedCheck;
    CheckUpdate checkUpdate = toCheckUpdate(input);
    if (!check.isPresent()) {
      checkers
          .getChecker(checkerUuid)
          .orElseThrow(
              () ->
                  new UnprocessableEntityException(
                      String.format("checker %s not found", checkerUuid)));
      updatedCheck =
          checksUpdate.get().createCheck(key, checkUpdate, input.notify, input.notifyDetails);
    } else {
      updatedCheck =
          checksUpdate.get().updateCheck(key, checkUpdate, input.notify, input.notifyDetails);
    }
    return Response.ok(checkJsonFactory.noOptions().format(updatedCheck));
  }

  private CheckUpdate toCheckUpdate(CheckInput input) throws BadRequestException {
    CheckUpdate.Builder checkUpdateBuilder = CheckUpdate.builder();

    if (input.state != null) {
      checkUpdateBuilder.setState(input.state);
    }

    if (input.message != null) {
      String message = input.message.trim();
      checkMessageSizeLimit(message.length());
      checkUpdateBuilder.setMessage(message);
    }

    if (input.url != null) {
      checkUpdateBuilder.setUrl(UrlValidator.clean(input.url));
    }

    if (input.started != null) {
      checkUpdateBuilder.setStarted(input.started);
    }

    if (input.finished != null) {
      checkUpdateBuilder.setFinished(input.finished);
    }

    return checkUpdateBuilder.build();
  }

  private void checkMessageSizeLimit(int messageSize) throws BadRequestException {
    int messageSizeLimit =
        pluginConfigFactory.getFromGerritConfig("checks").getInt("messageSizeLimit", 10_000);
    if (messageSize > messageSizeLimit) {
      throw new BadRequestException(
          String.format(
              "Field \"message\" exceeds size limit (%d > %d)", messageSize, messageSizeLimit));
    }
  }
}
