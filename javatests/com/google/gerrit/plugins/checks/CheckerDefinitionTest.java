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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.checks.api.BlockingCondition.STATE_NOT_PASSING;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.plugins.checks.api.BlockingCondition;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.util.time.TimeUtil;
import java.util.EnumSet;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class CheckerDefinitionTest {

  @Test
  public void notRequiredIfNoBlockingCondition() {
    Checker checker = newChecker().setBlockingConditions(ImmutableSortedSet.of()).build();

    assertThat(checker.isRequired()).isFalse();
  }

  @Test
  public void requiredIfHasBlockingConditionStateNotPassing() {
    Checker checker =
        newChecker().setBlockingConditions(ImmutableSortedSet.of(STATE_NOT_PASSING)).build();

    assertThat(checker.isRequired()).isTrue();
  }

  @Test
  public void allBlockingConditionsConsidered() {
    assertThat(EnumSet.allOf(BlockingCondition.class)).containsExactly(STATE_NOT_PASSING);
  }

  private Checker.Builder newChecker() {
    return Checker.builder()
        .setName("My Checker")
        .setRepository(Project.nameKey("test-repo"))
        .setStatus(CheckerStatus.ENABLED)
        .setUuid(CheckerUuid.parse("schema:any-id"))
        .setCreated(TimeUtil.nowTs())
        .setUpdated(TimeUtil.nowTs())
        .setRefState(ObjectId.zeroId());
  }
}
