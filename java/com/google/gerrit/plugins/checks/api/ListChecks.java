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

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.db.CheckBackfiller;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class ListChecks implements RestReadView<RevisionResource> {
  private final CheckBackfiller checkBackfiller;
  private final CheckJson.Factory checkJsonFactory;
  private final Checkers checkers;
  private final Checks checks;

  private final EnumSet<ListChecksOption> options = EnumSet.noneOf(ListChecksOption.class);

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChecksOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListOption.fromBits(ListChecksOption.class, Integer.parseInt(hex, 16)));
  }

  @Inject
  ListChecks(
      CheckBackfiller checkBackfiller,
      CheckJson.Factory checkJsonFactory,
      Checkers checkers,
      Checks checks) {
    this.checkBackfiller = checkBackfiller;
    this.checkJsonFactory = checkJsonFactory;
    this.checkers = checkers;
    this.checks = checks;
  }

  @Override
  public ImmutableList<CheckInfo> apply(RevisionResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, OrmException,
          IOException {
    CheckJson checkJson = checkJsonFactory.create(options);
    Map<CheckerUuid, Checker> checkersByUuid =
        checkers.checkersOf(resource.getProject()).stream()
            .collect(toMap(Checker::getUuid, c -> c));

    ImmutableList.Builder<CheckInfo> result =
        ImmutableList.builderWithExpectedSize(checkersByUuid.size());
    for (Check check : checks.getChecks(resource.getProject(), resource.getPatchSet().getId())) {
      checkersByUuid.remove(check.key().checkerUuid());
      result.add(checkJson.format(check));
    }

    for (Check check :
        checkBackfiller.getBackfilledChecksForRelevantCheckers(
            checkersByUuid.values(), resource.getNotes(), resource.getPatchSet().getId())) {
      result.add(checkJson.format(check));
    }

    return result.build();
  }
}
