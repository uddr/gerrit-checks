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

package com.google.gerrit.plugins.checks.rules;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class ChecksSubmitRule implements SubmitRule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final LegacySubmitRequirement DEFAULT_SUBMIT_REQUIREMENT_FOR_CHECKS =
      LegacySubmitRequirement.builder()
          .setFallbackText("All required checks must pass")
          .setType("checks_pass")
          .build();

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("ChecksSubmitRule"))
          .to(ChecksSubmitRule.class);
    }
  }

  private final Checks checks;

  @Inject
  public ChecksSubmitRule(Checks checks) {
    this.checks = checks;
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData changeData) {
    Project.NameKey project = changeData.project();
    Change.Id changeId = changeData.getId();

    PatchSet.Id currentPatchSetId;
    try {
      currentPatchSetId = changeData.currentPatchSet().id();
    } catch (RuntimeException e) {
      String errorMessage =
          String.format("failed to load the current patch set of change %s", changeId);
      logger.atSevere().withCause(e).log(errorMessage);
      return recordForRuleError(errorMessage);
    }

    boolean areAllRequiredCheckersPassing;
    try {
      areAllRequiredCheckersPassing =
          checks.areAllRequiredCheckersPassing(project, currentPatchSetId);
    } catch (IOException e) {
      String errorMessage =
          String.format("failed to evaluate check states for change %s", changeId);
      logger.atSevere().withCause(e).log(errorMessage);
      return recordForRuleError(errorMessage);
    }

    SubmitRecord submitRecord = new SubmitRecord();
    if (areAllRequiredCheckersPassing) {
      submitRecord.status = Status.OK;
      return Optional.of(submitRecord);
    }

    submitRecord.status = Status.NOT_READY;
    submitRecord.requirements = ImmutableList.of(DEFAULT_SUBMIT_REQUIREMENT_FOR_CHECKS);
    return Optional.of(submitRecord);
  }

  private static Optional<SubmitRecord> recordForRuleError(String reason) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.errorMessage = reason;
    submitRecord.status = SubmitRecord.Status.RULE_ERROR;
    return Optional.of(submitRecord);
  }
}
