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

import static com.google.gerrit.plugins.checks.api.CheckResource.CHECK_KIND;
import static com.google.gerrit.plugins.checks.api.CheckerResource.CHECKER_KIND;
import static com.google.gerrit.plugins.checks.api.PendingCheckResource.PENDING_CHECK_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.inject.AbstractModule;

// TODO(gerrit-team): This should move into HttpModule, but a core bug prevents the bindings from
//  ever making it into RestApiServlet if we just put it there. Currently, bindings are only
//  accepted if they are in the class that is referenced as 'Module'
public class ApiModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(CheckersCollection.class);
    bind(Checkers.class).to(CheckersImpl.class);

    bind(PendingChecksCollection.class);
    bind(PendingChecks.class).to(PendingChecksImpl.class);

    install(
        new RestApiModule() {
          @Override
          public void configure() {
            DynamicMap.mapOf(binder(), CHECKER_KIND);
            postOnCollection(CHECKER_KIND).to(CreateChecker.class);
            get(CHECKER_KIND).to(GetChecker.class);
            post(CHECKER_KIND).to(UpdateChecker.class);

            DynamicMap.mapOf(binder(), CHECK_KIND);
            child(REVISION_KIND, "checks").to(ChecksCollection.class);
            postOnCollection(CHECK_KIND).to(PostCheck.class);
            get(CHECK_KIND).to(GetCheck.class);
            post(CHECK_KIND).to(UpdateCheck.class);
            post(CHECK_KIND, "rerun").to(RerunCheck.class);
            DynamicMap.mapOf(binder(), PENDING_CHECK_KIND);
          }
        });

    install(
        new FactoryModule() {
          @Override
          public void configure() {
            factory(CheckerApiImpl.Factory.class);
            factory(CheckApiImpl.Factory.class);
            factory(ChecksImpl.Factory.class);
          }
        });
  }
}
