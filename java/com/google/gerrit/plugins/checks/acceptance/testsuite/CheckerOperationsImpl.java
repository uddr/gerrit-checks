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

package com.google.gerrit.plugins.checks.acceptance.testsuite;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerJson;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.plugins.checks.api.CheckerInfo;
import com.google.gerrit.plugins.checks.db.CheckerConfig;
import com.google.gerrit.plugins.checks.db.CheckersByRepositoryNotes;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * The implementation of {@code CheckerOperations}.
 *
 * <p>There is only one implementation of {@code CheckerOperations}. Nevertheless, we keep the
 * separation between interface and implementation to enhance clarity.
 */
@Singleton
public class CheckerOperationsImpl implements CheckerOperations {
  private final Checkers checkers;
  private final Provider<CheckersUpdate> checkersUpdate;
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final CheckerJson checkerJson;
  private final AtomicInteger checkerCounter;

  @Inject
  public CheckerOperationsImpl(
      Checkers checkers,
      @ServerInitiated Provider<CheckersUpdate> checkersUpdate,
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      CheckerJson checkerJson) {
    this.checkers = checkers;
    this.checkersUpdate = checkersUpdate;
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.checkerJson = checkerJson;
    this.checkerCounter = new AtomicInteger();
  }

  @Override
  public PerCheckerOperations checker(CheckerUuid checkerUuid) {
    return new PerCheckerOperationsImpl(checkerUuid);
  }

  @Override
  public TestCheckerCreation.Builder newChecker() {
    return TestCheckerCreation.builder(this::createNewChecker);
  }

  private CheckerUuid createNewChecker(TestCheckerCreation testCheckerCreation)
      throws OrmDuplicateKeyException, ConfigInvalidException, IOException {
    CheckerCreation checkerCreation = toCheckerCreation(testCheckerCreation);
    CheckerUpdate checkerUpdate = toCheckerUpdate(testCheckerCreation);
    Checker checker = checkersUpdate.get().createChecker(checkerCreation, checkerUpdate);
    return checker.getUuid();
  }

  private CheckerCreation toCheckerCreation(TestCheckerCreation checkerCreation) {
    CheckerUuid checkerUuid =
        checkerCreation
            .uuid()
            .orElseGet(() -> CheckerUuid.parse("test:checker-" + checkerCounter.incrementAndGet()));
    Project.NameKey repository = checkerCreation.repository().orElse(allProjectsName);
    return CheckerCreation.builder().setCheckerUuid(checkerUuid).setRepository(repository).build();
  }

  private static CheckerUpdate toCheckerUpdate(TestCheckerCreation checkerCreation) {
    CheckerUpdate.Builder builder = CheckerUpdate.builder();
    checkerCreation.name().ifPresent(builder::setName);
    checkerCreation.description().ifPresent(builder::setDescription);
    checkerCreation.url().ifPresent(builder::setUrl);
    checkerCreation.repository().ifPresent(builder::setRepository);
    checkerCreation.status().ifPresent(builder::setStatus);
    checkerCreation.blockingConditions().ifPresent(builder::setBlockingConditions);
    checkerCreation.query().ifPresent(builder::setQuery);
    return builder.build();
  }

  @Override
  public ImmutableSet<CheckerUuid> checkersOf(Project.NameKey repositoryName) throws IOException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo);
        ObjectReader or = repo.newObjectReader()) {
      Ref ref = repo.exactRef(CheckerRef.REFS_META_CHECKERS);
      if (ref == null) {
        return ImmutableSet.of();
      }

      RevCommit c = rw.parseCommit(ref.getObjectId());
      try (TreeWalk tw =
          TreeWalk.forPath(
              or,
              CheckersByRepositoryNotes.computeRepositorySha1(repositoryName).getName(),
              c.getTree())) {
        if (tw == null) {
          return ImmutableSet.of();
        }

        return Streams.stream(
                Splitter.on('\n')
                    .split(new String(or.open(tw.getObjectId(0), OBJ_BLOB).getBytes(), UTF_8)))
            .map(CheckerUuid::parse)
            .collect(toImmutableSet());
      }
    }
  }

  @Override
  public ImmutableSet<ObjectId> sha1sOfRepositoriesWithCheckers() throws IOException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(CheckerRef.REFS_META_CHECKERS);
      if (ref == null) {
        return ImmutableSet.of();
      }

      return Streams.stream(NoteMap.read(rw.getObjectReader(), rw.parseCommit(ref.getObjectId())))
          .map(ObjectId::copy)
          .collect(toImmutableSet());
    }
  }

  private class PerCheckerOperationsImpl implements PerCheckerOperations {
    private final CheckerUuid checkerUuid;

    PerCheckerOperationsImpl(CheckerUuid checkerUuid) {
      this.checkerUuid = checkerUuid;
    }

    @Override
    public boolean exists() {
      return getChecker(checkerUuid).isPresent();
    }

    @Override
    public Checker get() {
      return getChecker(checkerUuid)
          .orElseThrow(() -> new IllegalStateException("Tried to get non-existing test checker"));
    }

    private Optional<Checker> getChecker(CheckerUuid checkerUuid) {
      try {
        return checkers.getChecker(checkerUuid);
      } catch (IOException | ConfigInvalidException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public RevCommit commit() throws IOException {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get commit for a non-existing test checker");

      try (Repository repo = repoManager.openRepository(allProjectsName);
          RevWalk rw = new RevWalk(repo)) {
        return rw.parseCommit(checker.get().getRefState());
      }
    }

    @Override
    public String configText() throws IOException, ConfigInvalidException {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get config text for a non-existing test checker");

      try (Repository repo = repoManager.openRepository(allProjectsName)) {
        // Parse as Config to ensure it's a valid config file.
        return new BlobBasedConfig(
                null, repo, checker.get().getRefState(), CheckerConfig.CHECKER_CONFIG_FILE)
            .toText();
      }
    }

    @Override
    public CheckerInfo asInfo() {
      Optional<Checker> checker = getChecker(checkerUuid);
      checkState(checker.isPresent(), "Tried to get a non-existing test checker as CheckerInfo");
      return checkerJson.format(checker.get());
    }

    @Override
    public TestCheckerUpdate.Builder forUpdate() {
      return TestCheckerUpdate.builder(this::updateChecker);
    }

    private void updateChecker(TestCheckerUpdate testCheckerUpdate) throws Exception {
      CheckerUpdate checkerUpdate = toCheckerUpdate(testCheckerUpdate);
      checkersUpdate.get().updateChecker(checkerUuid, checkerUpdate);

      if (testCheckerUpdate.forceInvalidConfig()) {
        try (Repository repo = repoManager.openRepository(allProjectsName)) {
          new TestRepository<>(repo)
              .branch(checkerUuid.toRefName())
              .commit()
              .add(CheckerConfig.CHECKER_CONFIG_FILE, "invalid-config")
              .create();
        }
      }

      if (testCheckerUpdate.forceInvalidBlockingCondition()) {
        try (Repository repo = repoManager.openRepository(allProjectsName)) {
          TestRepository<Repository> testRepo = new TestRepository<>(repo);
          Config checkerConfig =
              readConfig(testRepo, checkerUuid.toRefName(), CheckerConfig.CHECKER_CONFIG_FILE);
          List<String> blocking =
              new ArrayList<>(
                  Arrays.asList(checkerConfig.getStringList("checker", null, "blocking")));
          blocking.add("invalid");
          checkerConfig.setStringList("checker", null, "blocking", blocking);
          testRepo
              .branch(checkerUuid.toRefName())
              .commit()
              .add(CheckerConfig.CHECKER_CONFIG_FILE, checkerConfig.toText())
              .create();
        }
      }

      if (testCheckerUpdate.deleteRef()) {
        try (Repository repo = repoManager.openRepository(allProjectsName)) {
          RefUpdate ru =
              new TestRepository<>(repo).getRepository().updateRef(checkerUuid.toRefName(), true);
          ru.setForceUpdate(true);
          ru.delete();
        }
      }
    }

    private CheckerUpdate toCheckerUpdate(TestCheckerUpdate checkerUpdate) {
      CheckerUpdate.Builder builder = CheckerUpdate.builder();
      checkerUpdate.name().ifPresent(builder::setName);
      checkerUpdate.description().ifPresent(builder::setDescription);
      checkerUpdate.url().ifPresent(builder::setUrl);
      checkerUpdate.repository().ifPresent(builder::setRepository);
      checkerUpdate.status().ifPresent(builder::setStatus);
      checkerUpdate.blockingConditions().ifPresent(builder::setBlockingConditions);
      checkerUpdate.query().ifPresent(builder::setQuery);
      return builder.build();
    }

    private Config readConfig(TestRepository<?> testRepo, String ref, String fileName)
        throws Exception {
      Repository repo = testRepo.getRepository();
      return new BlobBasedConfig(null, repo, repo.resolve(ref), fileName);
    }
  }
}
