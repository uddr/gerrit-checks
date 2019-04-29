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

package com.google.gerrit.plugins.checks.acceptance;

import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.reviewdb.client.Project;
import java.util.stream.Stream;
import org.junit.Test;

public class CheckersIT extends AbstractCheckersTest {
  @Test
  public void checkersOfListsCheckersOfRepository() throws Exception {
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(allProjects).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checkerUuid3 = checkerOperations.newChecker().repository(project).create();

    assertThat(getCheckerUuidsOf(allProjects)).containsExactly(checkerUuid1);
    assertThat(getCheckerUuidsOf(project)).containsExactly(checkerUuid2, checkerUuid3);
  }

  @Test
  public void checkersOfOmitsInvalidCheckers() throws Exception {
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(project).create();
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();
    checkerOperations.checker(checkerUuid1).forInvalidation().nonParseableConfig().invalidate();

    assertThat(getCheckerUuidsOf(project)).containsExactly(checkerUuid2);
  }

  @Test
  public void checkersOfOmitsDisabledCheckers() throws Exception {
    // Creates a disabled checker.
    checkerOperations.newChecker().repository(project).disable().create();
    // Creates an enabled checker and then disabled it by an update.
    CheckerUuid checkerUuid1 = checkerOperations.newChecker().repository(project).create();
    checkerOperations.checker(checkerUuid1).forUpdate().disable().update();
    // Creates an enabled checker.
    CheckerUuid checkerUuid2 = checkerOperations.newChecker().repository(project).create();

    assertThat(getCheckerUuidsOf(project)).containsExactly(checkerUuid2);
  }

  private Stream<CheckerUuid> getCheckerUuidsOf(Project.NameKey projectName) throws Exception {
    return plugin.getSysInjector().getInstance(Checkers.class).checkersOf(projectName).stream()
        .map(Checker::getUuid);
  }
}
