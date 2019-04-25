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
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRecord.Status;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.plugins.checks.CombinedCheckStateCache;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;

@Singleton
public class ChecksSubmitRule implements SubmitRule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final SubmitRequirement DEFAULT_SUBMIT_REQUIREMENT_FOR_CHECKS =
      SubmitRequirement.builder()
          .setFallbackText("All required checks must pass")
          .setType("checks_pass")
          .build();

  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("ChecksSubmitRule"))
          .to(ChecksSubmitRule.class);
    }
  }

  private final CombinedCheckStateCache combinedCheckStateCache;

  @Inject
  public ChecksSubmitRule(CombinedCheckStateCache combinedCheckStateCache) {
    this.combinedCheckStateCache = combinedCheckStateCache;
  }

  @Override
  public Collection<SubmitRecord> evaluate(ChangeData changeData, SubmitRuleOptions options) {
    Project.NameKey project = changeData.project();
    Change.Id changeId = changeData.getId();

    PatchSet.Id currentPathSetId;
    try {
      currentPathSetId = changeData.currentPatchSet().id();
    } catch (RuntimeException e) {
      String errorMessage =
          String.format("failed to load the current patch set of change %s", changeId);
      logger.atSevere().withCause(e).log(errorMessage);
      return singletonRecordForRuleError(errorMessage);
    }

    CombinedCheckState combinedCheckState;
    try {
      // Reload value in cache to fix up inconsistencies between cache and actual state.
      combinedCheckState = combinedCheckStateCache.reload(project, currentPathSetId);
    } catch (RuntimeException e) {
      String errorMessage =
          String.format("failed to evaluate check states for change %s", changeId);
      logger.atSevere().withCause(e).log(errorMessage);
      return singletonRecordForRuleError(errorMessage);
    }

    SubmitRecord submitRecord = new SubmitRecord();
    if (combinedCheckState.isPassing()) {
      submitRecord.status = Status.OK;
      return ImmutableList.of(submitRecord);
    }

    submitRecord.status = Status.NOT_READY;
    submitRecord.requirements = ImmutableList.of(DEFAULT_SUBMIT_REQUIREMENT_FOR_CHECKS);
    return ImmutableSet.of(submitRecord);
  }

  private static Collection<SubmitRecord> singletonRecordForRuleError(String reason) {
    SubmitRecord submitRecord = new SubmitRecord();
    submitRecord.errorMessage = reason;
    submitRecord.status = SubmitRecord.Status.RULE_ERROR;
    return ImmutableList.of(submitRecord);
  }
}
