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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SkipProjectClone;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.reviewdb.client.BranchNameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

@SkipProjectClone
public class CheckerRefsIT extends AbstractCheckersTest {
  @Inject private Sequences seq;
  @Inject private ChangeInserter.Factory changeInserterFactory;
  @Inject private BatchUpdate.Factory updateFactory;

  @Test
  public void cannotCreateCheckerRef() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();

    String checkerRef = CheckerUuid.parse("test:my-checker").toRefName();

    TestRepository<InMemoryRepository> testRepo = cloneProject(allProjects);
    PushOneCommit.Result r = pushFactory.create(admin.newIdent(), testRepo).to(checkerRef);
    r.assertErrorStatus();
    assertThat(r.getMessage()).contains("Not allowed to create checker ref.");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertThat(repo.exactRef(checkerRef)).isNull();
    }
  }

  @Test
  public void canCreateCheckerLikeRef() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();

    String checkerRef = CheckerUuid.parse("test:my-checker").toRefName();

    // checker ref can be created in any project except All-Projects
    TestRepository<InMemoryRepository> testRepo = cloneProject(project);
    PushOneCommit.Result r = pushFactory.create(admin.newIdent(), testRepo).to(checkerRef);
    r.assertOkStatus();

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(checkerRef)).isNotNull();
    }
  }

  @Test
  public void cannotDeleteCheckerRef() throws Exception {
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(
            allow(Permission.DELETE)
                .ref(CheckerRef.REFS_CHECKERS + "*")
                .group(REGISTERED_USERS)
                .force(true))
        .update();

    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = checkerUuid.toRefName();

    TestRepository<InMemoryRepository> testRepo = cloneProject(allProjects);
    PushResult r = deleteRef(testRepo, checkerRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(checkerRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(refUpdate.getMessage()).contains("Not allowed to delete checker ref.");

    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertThat(repo.exactRef(checkerRef)).isNotNull();
    }
  }

  @Test
  public void canDeleteCheckerLikeRef() throws Exception {
    String checkerRef = CheckerUuid.parse("foo:bar").toRefName();
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allow(Permission.DELETE)
                .ref(CheckerRef.REFS_CHECKERS + "*")
                .group(REGISTERED_USERS)
                .force(true))
        .add(allow(Permission.CREATE).ref(checkerRef).group(adminGroupUuid()))
        .update();
    createBranch(BranchNameKey.create(project, checkerRef));

    // checker ref can be deleted in any project except All-Projects
    TestRepository<InMemoryRepository> testRepo = cloneProject(project);
    PushResult r = deleteRef(testRepo, checkerRef);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(checkerRef);
    assertThat(refUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(checkerRef)).isNull();
    }
  }

  @Test
  public void updateCheckerRefsByPushIsDisabled() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = checkerUuid.toRefName();

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.PUSH).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r = pushFactory.create(admin.newIdent(), repo).to(checkerRef);
    r.assertErrorStatus();
    r.assertMessage("direct update of checker ref not allowed");
  }

  @Test
  public void updateCheckerLikeRefByPush() throws Exception {
    String checkerRef = CheckerUuid.parse("foo:bar").toRefName();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(checkerRef).group(adminGroupUuid()))
        .update();
    createBranch(BranchNameKey.create(project, checkerRef));

    TestRepository<InMemoryRepository> repo = cloneProject(project, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r = pushFactory.create(admin.newIdent(), repo).to(checkerRef);
    r.assertOkStatus();
  }

  @Test
  public void submitToCheckerRefsIsDisabled() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().disable().create();
    String checkerRef = checkerUuid.toRefName();
    String changeId = createChangeWithoutCommitValidation(allProjects, checkerRef);

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(
            allowLabel("Code-Review")
                .ref(CheckerRef.REFS_CHECKERS + "*")
                .group(adminGroupUuid())
                .range(-2, 2))
        .update();
    approve(changeId);

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(changeId).current().submit());
    assertThat(thrown).hasMessageThat().contains("submit to checker ref not allowed");
  }

  @Test
  public void submitToCheckerLikeRef() throws Exception {
    String checkerRef = CheckerUuid.parse("foo:bar").toRefName();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(checkerRef).group(adminGroupUuid()))
        .update();
    createBranch(BranchNameKey.create(project, checkerRef));

    String changeId = createChangeWithoutCommitValidation(project, checkerRef);

    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel("Code-Review")
                .ref(CheckerRef.REFS_CHECKERS + "*")
                .group(adminGroupUuid())
                .range(-2, 2))
        .update();
    approve(changeId);

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();

    // submitting to a checker ref should work in any project except All-Projects
    gApi.changes().id(changeId).current().submit();

    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void createChangeForCheckerRefsByPushIsDisabled() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = checkerUuid.toRefName();

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");

    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allow(Permission.PUSH).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r =
        pushFactory.create(admin.newIdent(), repo).to("refs/for/" + checkerRef);
    r.assertErrorStatus();
    r.assertMessage("creating change for checker ref not allowed");
  }

  @Test
  public void createChangeForCheckerLikeRefByPush() throws Exception {
    String checkerRef = CheckerUuid.parse("foo:bar").toRefName();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(checkerRef).group(adminGroupUuid()))
        .update();
    createBranch(BranchNameKey.create(project, checkerRef));

    TestRepository<InMemoryRepository> repo = cloneProject(project, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");

    // creating a change on a checker ref by push should work in any project except All-Projects
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref(CheckerRef.REFS_CHECKERS + "*").group(adminGroupUuid()))
        .update();
    PushOneCommit.Result r =
        pushFactory.create(admin.newIdent(), repo).to("refs/for/" + checkerRef);
    r.assertOkStatus();
  }

  @Test
  public void createChangeForCheckerRefsViaApiIsDisabled() throws Exception {
    CheckerUuid checkerUuid = checkerOperations.newChecker().create();
    String checkerRef = checkerUuid.toRefName();

    TestRepository<InMemoryRepository> repo = cloneProject(allProjects, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");
    RevCommit head = getHead(repo.getRepository(), "HEAD");

    ChangeInput input = new ChangeInput();
    input.project = allProjects.get();
    input.branch = checkerRef;
    input.baseCommit = head.name();
    input.subject = "A change.";

    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.changes().create(input));
    assertThat(thrown).hasMessageThat().contains("creating change for checker ref not allowed");
  }

  @Test
  public void createChangeForCheckerLikeRefViaApi() throws Exception {
    String checkerRef = CheckerUuid.parse("foo:bar").toRefName();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(checkerRef).group(adminGroupUuid()))
        .update();
    createBranch(BranchNameKey.create(project, checkerRef));

    TestRepository<InMemoryRepository> repo = cloneProject(project, admin);
    fetch(repo, checkerRef + ":checkerRef");
    repo.reset("checkerRef");
    RevCommit head = getHead(repo.getRepository(), "HEAD");

    // creating a change on a checker ref via API should work in any project except All-Projects
    ChangeInput input = new ChangeInput();
    input.project = project.get();
    input.branch = checkerRef;
    input.baseCommit = head.name();
    input.subject = "A change.";
    assertThat(gApi.changes().create(input).get()).isNotNull();
  }

  private String createChangeWithoutCommitValidation(Project.NameKey project, String targetRef)
      throws Exception {
    try (Repository git = repoManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        ObjectReader reader = oi.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RevCommit head = rw.parseCommit(git.exactRef(targetRef).getObjectId());
      RevCommit commit =
          new TestRepository<>(git)
              .commit()
              .author(admin.newIdent())
              .message("A change.")
              .insertChangeId()
              .parent(head)
              .create();

      Change.Id changeId = Change.id(seq.nextChangeId());
      ChangeInserter ins = changeInserterFactory.create(changeId, commit, targetRef);
      ins.setValidate(false);
      ins.setMessage(String.format("Uploaded patch set %s.", ins.getPatchSetId().get()));
      try (BatchUpdate bu =
          updateFactory.create(
              project, identifiedUserFactory.create(admin.id()), TimeUtil.nowTs())) {
        bu.setRepository(git, rw, oi);
        bu.insertChange(ins);
        bu.execute();
      }
      return changeId.toString();
    }
  }
}
