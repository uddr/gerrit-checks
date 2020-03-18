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

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Formats a {@link Check} as JSON. */
public class CheckJson {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Singleton
  public static class Factory {
    private final AssistedFactory assistedFactory;

    @Inject
    Factory(AssistedFactory assistedFactory) {
      this.assistedFactory = assistedFactory;
    }

    public CheckJson create(Iterable<ListChecksOption> options) {
      return assistedFactory.create(options);
    }

    public CheckJson noOptions() {
      return create(ImmutableSet.of());
    }
  }

  interface AssistedFactory {
    CheckJson create(Iterable<ListChecksOption> options);
  }

  private final Checkers checkers;
  private final ImmutableSet<ListChecksOption> options;

  @Inject
  CheckJson(Checkers checkers, @Assisted Iterable<ListChecksOption> options) {
    this.checkers = checkers;
    this.options = ImmutableSet.copyOf(options);
  }

  public CheckInfo format(Check check) throws IOException {
    CheckInfo info = new CheckInfo();
    info.checkerUuid = check.key().checkerUuid().get();
    info.changeNumber = check.key().patchSet().changeId().get();
    info.repository = check.key().repository().get();
    info.patchSetId = check.key().patchSet().get();
    info.state = check.state();

    info.message = check.message().orElse(null);
    info.url = check.url().orElse(null);
    info.started = check.started().orElse(null);
    info.finished = check.finished().orElse(null);

    info.created = check.created();
    info.updated = check.updated();

    if (options.contains(ListChecksOption.CHECKER)) {
      populateCheckerFields(check.key().checkerUuid(), info);
    }
    return info;
  }

  private void populateCheckerFields(CheckerUuid checkerUuid, CheckInfo info) throws IOException {
    try {
      checkers
          .getChecker(checkerUuid)
          .ifPresent(
              checker -> {
                info.checkerName = checker.getName();
                info.checkerStatus = checker.getStatus();
                info.blocking = checker.getBlockingConditions();
                info.required = checker.isRequired() ? true : null;
                info.checkerDescription = checker.getDescription().orElse(null);
              });
    } catch (ConfigInvalidException e) {
      logger.atWarning().withCause(e).log("skipping invalid checker %s", checkerUuid);
    }
  }
}
