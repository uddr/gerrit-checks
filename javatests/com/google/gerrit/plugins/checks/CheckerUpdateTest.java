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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.reviewdb.client.Project;
import org.junit.Test;

public class CheckerUpdateTest {
  @Test
  public void nameCannotBeEmpty() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> CheckerUpdate.builder().setName("").build());
    assertThat(thrown).hasMessageThat().contains(String.format("checker name cannot be empty"));
  }

  @Test
  public void nameCannotBeEmptyAfterTrim() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class, () -> CheckerUpdate.builder().setName(" ").build());
    assertThat(thrown).hasMessageThat().contains(String.format("checker name cannot be empty"));
  }

  @Test
  public void repositoryCannotBeEmpty() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> CheckerUpdate.builder().setRepository(Project.nameKey("")).build());
    assertThat(thrown).hasMessageThat().contains(String.format("repository name cannot be empty"));
  }

  @Test
  public void repositoryCannotBeEmptyAfterTrim() {
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> CheckerUpdate.builder().setRepository(Project.nameKey(" ")).build());
    assertThat(thrown).hasMessageThat().contains(String.format("repository name cannot be empty"));
  }
}
