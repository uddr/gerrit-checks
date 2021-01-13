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

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.plugins.checks.api.ApiModule;
import com.google.gerrit.plugins.checks.api.ChangeCheckAttributeFactory;
import com.google.gerrit.plugins.checks.api.ChangeCheckAttributeFactory.GetChangeOptions;
import com.google.gerrit.plugins.checks.api.ChangeCheckAttributeFactory.QueryChangesOptions;
import com.google.gerrit.plugins.checks.db.NoteDbCheckersModule;
import com.google.gerrit.plugins.checks.email.ChecksEmailModule;
import com.google.gerrit.plugins.checks.rules.ChecksSubmitRule;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.change.ChangeETagComputation;
import com.google.gerrit.server.change.ChangePluginDefinedInfoFactory;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.MergeValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.restapi.change.GetChange;
import com.google.gerrit.server.restapi.change.QueryChanges;
import com.google.gerrit.sshd.commands.Query;
import com.google.inject.Provides;

public class Module extends FactoryModule {
  @Override
  protected void configure() {
    factory(CheckJson.AssistedFactory.class);
    factory(ChecksUpdate.Factory.class);
    install(new NoteDbCheckersModule());
    install(CombinedCheckStateCache.module());

    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(AdministrateCheckersCapability.NAME))
        .to(AdministrateCheckersCapability.class);

    DynamicSet.bind(binder(), CommitValidationListener.class)
        .to(CheckerCommitValidator.class)
        .in(SINGLETON);
    DynamicSet.bind(binder(), MergeValidationListener.class)
        .to(CheckerMergeValidator.class)
        .in(SINGLETON);
    DynamicSet.bind(binder(), RefOperationValidationListener.class)
        .to(CheckerRefOperationValidator.class)
        .in(SINGLETON);

    DynamicSet.bind(binder(), ChangeETagComputation.class)
        .to(ChecksETagComputation.class)
        .in(SINGLETON);

    DynamicSet.bind(binder(), ChangePluginDefinedInfoFactory.class)
        .to(ChangeCheckAttributeFactory.class);
    bind(DynamicOptions.DynamicBean.class)
        .annotatedWith(Exports.named(GetChange.class))
        .to(GetChangeOptions.class);
    bind(DynamicOptions.DynamicBean.class)
        .annotatedWith(Exports.named(QueryChanges.class))
        .to(QueryChangesOptions.class);
    bind(DynamicOptions.DynamicBean.class)
        .annotatedWith(Exports.named(Query.class))
        .to(QueryChangesOptions.class);

    install(new ApiModule());
    install(new ChecksSubmitRule.Module());
    install(new ChecksEmailModule());
  }

  @Provides
  @ServerInitiated
  ChecksUpdate provideServerInitiatedChecksUpdate(ChecksUpdate.Factory factory) {
    return factory.createWithServerIdent();
  }

  @Provides
  @UserInitiated
  ChecksUpdate provideUserInitiatedChecksUpdate(
      ChecksUpdate.Factory factory, IdentifiedUser currentUser) {
    return factory.create(currentUser);
  }
}
