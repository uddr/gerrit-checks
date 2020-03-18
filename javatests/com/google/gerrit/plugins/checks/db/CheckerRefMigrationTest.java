// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class CheckerRefMigrationTest {
  private InMemoryRepositoryManager inMemoryRepositoryManager;
  private AllProjectsName allProjectsName;
  private Repository allProjectsRepo;

  @Before
  public void setUp() throws Exception {
    inMemoryRepositoryManager = new InMemoryRepositoryManager();
    allProjectsName = new AllProjectsName("Test Repository");
    allProjectsRepo = inMemoryRepositoryManager.createRepository(allProjectsName);
  }

  @Test
  public void migrateLegacyMetaRef() throws Exception {
    CheckerUuid checkerWithBadRef = CheckerUuid.parse("test:my-checker1");
    try (TestRepository<Repository> testRepo = new TestRepository<>(allProjectsRepo)) {
      testRepo
          .branch("refs/meta/checkers/")
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(allProjectsName).getName(),
              checkerWithBadRef.toString())
          .create();
    }

    assertThat(allProjectsRepo.exactRef("refs/meta/checkers/")).isNotNull();
    assertThat(allProjectsRepo.exactRef("refs/meta/checkers")).isNull();
    CheckerRefMigration checkerRefMigration =
        new CheckerRefMigration(inMemoryRepositoryManager, allProjectsName);
    checkerRefMigration.migrate();
    assertThat(allProjectsRepo.exactRef("refs/meta/checkers")).isNotNull();
    assertThat(allProjectsRepo.exactRef("refs/meta/checkers/")).isNull();
  }

  @Test
  public void readFromLegacyRefUntilMigrated() throws Exception {
    CheckerUuid checkerUuid = CheckerUuid.parse("test:my-checker1");
    Project.NameKey projectName = Project.nameKey("foo");
    // Create a checker map on the legacy ref
    try (TestRepository<Repository> repo = new TestRepository<>(allProjectsRepo)) {
      repo.branch("refs/meta/checkers/")
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(projectName).getName(),
              checkerUuid.toString())
          .create();
    }
    // Assert that the map gets loaded even though it's on the legacy ref.
    // This is important to allow for an online migration.
    assertThat(CheckersByRepositoryNotes.load(allProjectsName, allProjectsRepo).get(projectName))
        .containsExactly(checkerUuid);
  }

  @Test
  public void readFromNewRefWhenMigrated() throws Exception {
    CheckerUuid checkerUuid = CheckerUuid.parse("test:my-checker1");
    Project.NameKey projectName = Project.nameKey("foo");
    // Create a checker map on the new ref
    try (TestRepository<Repository> repo = new TestRepository<>(allProjectsRepo)) {
      repo.branch("refs/meta/checkers")
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(projectName).getName(),
              checkerUuid.toString())
          .create();
    }
    // Assert that the map gets loaded
    assertThat(CheckersByRepositoryNotes.load(allProjectsName, allProjectsRepo).get(projectName))
        .containsExactly(checkerUuid);
  }
}
