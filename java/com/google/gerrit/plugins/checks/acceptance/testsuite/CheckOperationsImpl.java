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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckUpdate;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.plugins.checks.Checks.GetCheckOptions;
import com.google.gerrit.plugins.checks.ChecksUpdate;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

public final class CheckOperationsImpl implements CheckOperations {
  private final Checks checks;
  private final Provider<ChecksUpdate> checksUpdate;
  private final CheckJson.Factory checkJsonFactory;
  private final GitRepositoryManager repoManager;

  @Inject
  public CheckOperationsImpl(
      Checks checks,
      GitRepositoryManager repoManager,
      CheckJson.Factory checkJsonFactory,
      @ServerInitiated Provider<ChecksUpdate> checksUpdate) {
    this.checks = checks;
    this.repoManager = repoManager;
    this.checkJsonFactory = checkJsonFactory;
    this.checksUpdate = checksUpdate;
  }

  @Override
  public PerCheckOperations check(CheckKey key) {
    return new PerCheckOperationsImpl(key);
  }

  @Override
  public TestCheckUpdate.Builder newCheck(CheckKey key) {
    return TestCheckUpdate.builder(key)
        .checkUpdater(u -> checksUpdate.get().createCheck(key, toCheckUpdate(u)));
  }

  final class PerCheckOperationsImpl implements PerCheckOperations {
    private final CheckKey key;

    PerCheckOperationsImpl(CheckKey key) {
      this.key = key;
    }

    @Override
    public boolean exists() throws Exception {
      return checks.getCheck(key, GetCheckOptions.defaults()).isPresent();
    }

    @Override
    public Check get() throws Exception {
      return checks
          .getCheck(key, GetCheckOptions.defaults())
          .orElseThrow(() -> new IllegalStateException("Tried to get non-existing test check"));
    }

    @Override
    public ImmutableMap<RevId, String> notesAsText() throws Exception {
      try (Repository repo = repoManager.openRepository(key.repository());
          RevWalk rw = new RevWalk(repo)) {
        Ref checkRef =
            repo.getRefDatabase().exactRef(CheckerRef.checksRef(key.patchSet().changeId));
        checkNotNull(checkRef);

        NoteMap notes = NoteMap.read(rw.getObjectReader(), rw.parseCommit(checkRef.getObjectId()));
        ImmutableMap.Builder<RevId, String> raw = ImmutableMap.builder();

        for (Note note : notes) {
          raw.put(
              new RevId(note.name()),
              new String(notes.getCachedBytes(note.toObjectId(), Integer.MAX_VALUE)));
        }
        return raw.build();
      }
    }

    @Override
    public CheckInfo asInfo(ListChecksOption... options) throws Exception {
      return checkJsonFactory.create(ImmutableSet.copyOf(options)).format(get());
    }

    @Override
    public TestCheckUpdate.Builder forUpdate() {
      return TestCheckUpdate.builder(key)
          .checkUpdater(
              testUpdate -> checksUpdate.get().updateCheck(key, toCheckUpdate(testUpdate)));
    }
  }

  private static CheckUpdate toCheckUpdate(TestCheckUpdate testUpdate) {
    CheckUpdate.Builder update = CheckUpdate.builder();
    testUpdate.message().ifPresent(update::setMessage);
    testUpdate.state().ifPresent(update::setState);
    testUpdate.started().ifPresent(update::setStarted);
    testUpdate.finished().ifPresent(update::setFinished);
    testUpdate.url().ifPresent(update::setUrl);
    return update.build();
  }
}
