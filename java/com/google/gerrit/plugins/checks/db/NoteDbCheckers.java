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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Class to read checkers from NoteDb. */
@Singleton
class NoteDbCheckers implements Checkers {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;

  @Inject
  NoteDbCheckers(GitRepositoryManager repoManager, AllProjectsName allProjectsName) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
  }

  @Override
  public Optional<Checker> getChecker(CheckerUuid checkerUuid)
      throws IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      CheckerConfig checkerConfig =
          CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, checkerUuid);
      return checkerConfig.getLoadedChecker();
    }
  }

  @Override
  public ImmutableList<Checker> listCheckers() throws IOException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      return allProjectsRepo
          .getRefDatabase()
          .getRefsByPrefix(CheckerRef.REFS_CHECKERS)
          .stream()
          .flatMap(ref -> Streams.stream(tryLoadChecker(allProjectsRepo, ref)))
          .sorted(comparing(Checker::getUuid))
          .collect(toImmutableList());
    }
  }

  private Optional<Checker> tryLoadChecker(Repository allProjectsRepo, Ref ref) {
    if (CheckerRef.isRefsCheckers(ref.getName())) {
      try {
        return CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, ref)
            .getLoadedChecker();
      } catch (ConfigInvalidException | IOException e) {
        logger.atWarning().withCause(e).log(
            "Ignore invalid checker in %s while listing checkers", ref.getName());
      }
    }
    return Optional.empty();
  }

  @Override
  public ImmutableSortedSet<Checker> checkersOf(Project.NameKey repositoryName)
      throws IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      ImmutableSortedSet<CheckerUuid> checkerUuidStrings =
          CheckersByRepositoryNotes.load(allProjectsName, allProjectsRepo).get(repositoryName);
      ImmutableSortedSet.Builder<Checker> checkers =
          ImmutableSortedSet.orderedBy(comparing(Checker::getUuid));
      for (CheckerUuid checkerUuid : checkerUuidStrings) {
        try {
          CheckerConfig checkerConfig =
              CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, checkerUuid);
          checkerConfig.getLoadedChecker().ifPresent(checkers::add);
        } catch (ConfigInvalidException e) {
          logger.atWarning().withCause(e).log(
              "Ignore invalid checker %s on listing checkers for repository %s",
              checkerUuid, repositoryName);
        }
      }
      return checkers.build();
    }
  }
}
