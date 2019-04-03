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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.StringSubject;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.util.TimeZone;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class CheckersByRepositoryNotesTest {
  private final TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

  private AllProjectsName projectName;
  private Repository repository;

  @Before
  public void setUp() throws Exception {
    projectName = new AllProjectsName("Test Repository");
    repository = new InMemoryRepository(new DfsRepositoryDescription("Test Repository"));
  }

  @Test
  public void getEmptyCheckersList() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();
    assertThat(checkersByRepositoryNotes.get(new Project.NameKey("some-project"))).isEmpty();
  }

  @Test
  public void insertCheckers() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project1 = new Project.NameKey("some-project");
    Project.NameKey project2 = new Project.NameKey("other-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("foo:bar");
    checkersByRepositoryNotes.insert(checkerUuid1, project1);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1)).containsExactly(checkerUuid1);
    assertThat(checkersByRepositoryNotes.get(project2)).isEmpty();

    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:baz");
    checkersByRepositoryNotes.insert(checkerUuid2, project1);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1))
        .containsExactly(checkerUuid1, checkerUuid2)
        .inOrder();
    assertThat(checkersByRepositoryNotes.get(project2)).isEmpty();

    CheckerUuid checkerUuid3 = CheckerUuid.parse("bar:baz");
    checkersByRepositoryNotes.insert(checkerUuid3, project2);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1))
        .containsExactly(checkerUuid1, checkerUuid2)
        .inOrder();
    assertThat(checkersByRepositoryNotes.get(project2)).containsExactly(checkerUuid3);
  }

  @Test
  public void insertCheckerTwice() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    CheckerUuid checkerUuid = CheckerUuid.parse("foo:bar");
    Project.NameKey project = new Project.NameKey("some-project");

    checkersByRepositoryNotes.insert(checkerUuid, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project)).containsExactly(checkerUuid);

    ObjectId commitId = getRefsMetaCheckersState();
    checkersByRepositoryNotes.insert(checkerUuid, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project)).containsExactly(checkerUuid);
    assertThat(getRefsMetaCheckersState()).isEqualTo(commitId);
  }

  @Test
  public void removeCheckers() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project = new Project.NameKey("some-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("bar:baz");
    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:bar");
    CheckerUuid checkerUuid3 = CheckerUuid.parse("foo:baz");

    checkersByRepositoryNotes.insert(checkerUuid1, project);
    checkersByRepositoryNotes.insert(checkerUuid2, project);
    checkersByRepositoryNotes.insert(checkerUuid3, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project))
        .containsExactly(checkerUuid1, checkerUuid2, checkerUuid3)
        .inOrder();

    checkersByRepositoryNotes.remove(checkerUuid2, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project))
        .containsExactly(checkerUuid1, checkerUuid3)
        .inOrder();

    checkersByRepositoryNotes.remove(checkerUuid1, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project)).containsExactly(checkerUuid3).inOrder();

    checkersByRepositoryNotes.remove(checkerUuid3, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project)).isEmpty();
  }

  @Test
  public void removeNonExistingChecker() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project1 = new Project.NameKey("some-project");
    Project.NameKey project2 = new Project.NameKey("other-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("foo:bar");
    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:baz");

    checkersByRepositoryNotes.insert(checkerUuid1, project1);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1)).containsExactly(checkerUuid1);
    assertThat(checkersByRepositoryNotes.get(project2)).isEmpty();

    ObjectId commitId = getRefsMetaCheckersState();
    checkersByRepositoryNotes.remove(checkerUuid2, project1);
    checkersByRepositoryNotes.remove(checkerUuid1, project2);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1)).containsExactly(checkerUuid1);
    assertThat(checkersByRepositoryNotes.get(project2)).isEmpty();
    assertThat(getRefsMetaCheckersState()).isEqualTo(commitId);
  }

  @Test
  public void updateCheckers() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project1 = new Project.NameKey("some-project");
    Project.NameKey project2 = new Project.NameKey("other-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("foo:bar");
    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:baz");

    checkersByRepositoryNotes.insert(checkerUuid1, project1);
    checkersByRepositoryNotes.insert(checkerUuid2, project1);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1))
        .containsExactly(checkerUuid1, checkerUuid2)
        .inOrder();
    assertThat(checkersByRepositoryNotes.get(project2)).isEmpty();

    checkersByRepositoryNotes.update(checkerUuid1, project1, project2);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1)).containsExactly(checkerUuid2);
    assertThat(checkersByRepositoryNotes.get(project2)).containsExactly(checkerUuid1);

    checkersByRepositoryNotes.update(checkerUuid2, project1, project2);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project1)).isEmpty();
    assertThat(checkersByRepositoryNotes.get(project2))
        .containsExactly(checkerUuid1, checkerUuid2)
        .inOrder();
  }

  @Test
  public void sortOrder() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project = new Project.NameKey("some-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("foo:bar");
    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:baz");
    checkersByRepositoryNotes.insert(checkerUuid1, project);
    checkersByRepositoryNotes.insert(checkerUuid2, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project))
        .containsExactly(checkerUuid1, checkerUuid2)
        .inOrder();

    CheckerUuid checkerUuid3 = CheckerUuid.parse("abc:xyz");
    CheckerUuid checkerUuid4 = CheckerUuid.parse("xyz:abc");

    checkersByRepositoryNotes.insert(checkerUuid3, project);
    checkersByRepositoryNotes.insert(checkerUuid4, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project))
        .containsExactly(checkerUuid3, checkerUuid1, checkerUuid2, checkerUuid4)
        .inOrder();
  }

  @Test
  public void commitMessage() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    CheckerUuid checkerUuid = CheckerUuid.parse("foo:bar");
    Project.NameKey project = new Project.NameKey("some-project");

    checkersByRepositoryNotes.insert(checkerUuid, project);
    commit(checkersByRepositoryNotes);
    assertThatCommitMessage()
        .isEqualTo(
            "Update checkers by repository\n\nChecker: "
                + checkerUuid.toString()
                + "\nRepository: "
                + project.get());
  }

  @Test
  public void noOpUpdate() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project = new Project.NameKey("some-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("foo:bar");
    checkersByRepositoryNotes.insert(checkerUuid1, project);
    commit(checkersByRepositoryNotes);

    ObjectId commitId = getRefsMetaCheckersState();

    // Set the repository of the checker to the same value that it currently has.
    checkersByRepositoryNotes.update(checkerUuid1, project, project);
    commit(checkersByRepositoryNotes);
    assertThat(getRefsMetaCheckersState()).isEqualTo(commitId);

    // Insert a new checker and remove it before commit.
    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:baz");
    checkersByRepositoryNotes.insert(checkerUuid2, project);
    checkersByRepositoryNotes.remove(checkerUuid2, project);
    commit(checkersByRepositoryNotes);
    assertThat(getRefsMetaCheckersState()).isEqualTo(commitId);
  }

  @Test
  public void getCheckersFromOldRevision() throws Exception {
    CheckersByRepositoryNotes checkersByRepositoryNotes = loadCheckersByRepositoryNotes();

    Project.NameKey project = new Project.NameKey("some-project");

    CheckerUuid checkerUuid1 = CheckerUuid.parse("foo:bar");
    checkersByRepositoryNotes.insert(checkerUuid1, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project)).containsExactly(checkerUuid1);

    ObjectId oldRevision = getRefsMetaCheckersState();

    CheckerUuid checkerUuid2 = CheckerUuid.parse("foo:baz");
    checkersByRepositoryNotes.insert(checkerUuid2, project);
    commit(checkersByRepositoryNotes);
    assertThat(checkersByRepositoryNotes.get(project))
        .containsExactly(checkerUuid1, checkerUuid2)
        .inOrder();

    assertThat(CheckersByRepositoryNotes.load(projectName, repository, oldRevision).get(project))
        .containsExactly(checkerUuid1);
  }

  @Test
  public void getCheckersFromZeroIdRevision() throws Exception {
    assertThat(
            CheckersByRepositoryNotes.load(projectName, repository, ObjectId.zeroId())
                .get(new Project.NameKey("some-project")))
        .isEmpty();
  }

  @Test
  public void getCheckersFromNullRevision() throws Exception {
    assertThat(
            CheckersByRepositoryNotes.load(projectName, repository, null)
                .get(new Project.NameKey("some-project")))
        .isEmpty();
  }

  private CheckersByRepositoryNotes loadCheckersByRepositoryNotes() throws IOException {
    return CheckersByRepositoryNotes.load(projectName, repository);
  }

  private void commit(CheckersByRepositoryNotes checkersByRepositoryNotes) throws IOException {
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate()) {
      checkersByRepositoryNotes.commit(metaDataUpdate);
    }
  }

  private MetaDataUpdate createMetaDataUpdate() {
    PersonIdent serverIdent =
        new PersonIdent(
            "Gerrit Server", "noreply@gerritcodereview.com", TimeUtil.nowTs(), timeZone);

    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(
            GitReferenceUpdated.DISABLED, new Project.NameKey("Test Repository"), repository);
    metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
    metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
    return metaDataUpdate;
  }

  private ObjectId getRefsMetaCheckersState() throws IOException {
    return repository.exactRef(CheckerRef.REFS_META_CHECKERS).getObjectId();
  }

  private StringSubject assertThatCommitMessage() throws IOException {
    try (RevWalk rw = new RevWalk(repository)) {
      RevCommit commit = rw.parseCommit(getRefsMetaCheckersState());
      return assertThat(commit.getFullMessage()).named("commit message");
    }
  }
}
