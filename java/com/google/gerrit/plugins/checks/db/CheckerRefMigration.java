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

import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class CheckerRefMigration {
  private static final String TMP_REF = "refs/tmp/checker-migration";
  private static final String LEGACY_REFS_META_CHECKERS = "refs/meta/checkers/";

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;

  @Inject
  CheckerRefMigration(GitRepositoryManager repoManager, AllProjectsName allProjectsName) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
  }

  public void migrate() throws Exception {
    try (Repository repo = repoManager.openRepository(allProjectsName)) {

      // This part is specifically for cases where the rename failed half-way last time.
      Ref ref = repo.exactRef(TMP_REF);
      if (ref != null) {
        renameRef(TMP_REF, CheckerRef.REFS_META_CHECKERS, repo);
      }

      ref = repo.exactRef(LEGACY_REFS_META_CHECKERS);
      if (ref == null) {
        return;
      }
      renameRef(LEGACY_REFS_META_CHECKERS, TMP_REF, repo);
      renameRef(TMP_REF, CheckerRef.REFS_META_CHECKERS, repo);
    } catch (Exception ex) {
      throw new IllegalStateException(
          ex.getMessage()
              + " Ensure that refs/tmp/checker-migration doesn't exist. Also,"
              + "ensure that refs/meta/checkers/ (with trailing '/') has been migrated to refs/meta/checkers (without "
              + "trailing '/'). Consider documentation for updating refs manually: https://git-scm.com/docs/git-update-ref");
    }
  }

  private void renameRef(String currentName, String targetName, Repository repo)
      throws IOException {
    RefRename refRename = repo.renameRef(currentName, targetName);
    Result result = refRename.rename();
    if (result != Result.RENAMED) {
      throw new IllegalStateException(
          String.format(
              "Rename of %s to %s failed with the failure: %s.",
              currentName, targetName, result.name()));
    }
    // After the rename, we need to delete. For same reason, due to a bug in JGit, the rename
    // doesn't delete the previous
    // name.
    RefUpdate update = repo.updateRef(currentName);
    update.setExpectedOldObjectId(repo.exactRef(currentName).getObjectId());
    update.setNewObjectId(ObjectId.zeroId());
    update.setForceUpdate(true);
    update.delete();
  }
}
