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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.CheckerRefMigration;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class CheckerRefMigrationIT extends AbstractCheckersTest {
  private CheckerOperationsImpl checkerOperations;

  @Before
  public void setUp() {
    checkerOperations = plugin.getSysInjector().getInstance(CheckerOperationsImpl.class);
  }

  @Inject private CheckerRefMigration checkerRefMigration;

  @Test
  public void migrateLegacyMetaRef() throws Exception {
    CheckerUuid checkerWithBadRef = CheckerUuid.parse("test:my-checker1");

    try (Repository repo = repoManager.openRepository(allProjects);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch("refs/meta/checkers/")
          .commit()
          .add(
              CheckersByRepositoryNotes.computeRepositorySha1(allProjects).getName(),
              checkerWithBadRef.toString())
          .create();
    }

    assertThat(repo().exactRef("refs/meta/checkers/")).isNotNull();
    assertThat(repo().exactRef("refs/meta/checkers")).isNull();
    checkerRefMigration.migrate();

    assertThat(repo().exactRef("refs/meta/checkers")).isNotNull();
    assertThat(repo().exactRef("refs/meta/checkers/")).isNull();
  }
}
