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

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.checks.CheckKey;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.api.ChangeCheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ChecksEmailIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;

  private TestAccount bot;
  private CheckerUuid checkerUuid1;
  private CheckerUuid checkerUuid2;
  private TestAccount owner;
  private TestAccount reviewer;
  private TestAccount starrer;
  private TestAccount watcher;
  private PatchSet.Id patchSetId;

  @Before
  public void setup() throws Exception {
    // Create a bot account, create a bots group and add the bot as member and allow the bots group
    // to post checks.
    bot = accountCreator.create("bot", "bot@test.com", "Bot");
    AccountGroup.UUID botsAccountGroupUuid =
        groupOperations.newGroup().name("bots").addMember(bot.id()).create();
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability("checks-administrateCheckers").group(botsAccountGroupUuid))
        .update();

    // Create two required checkers.
    checkerUuid1 = checkerOperations.newChecker().repository(project).required().create();
    checkerUuid2 = checkerOperations.newChecker().repository(project).required().create();

    // Create a change.
    owner = admin;
    patchSetId = createChange().getPatchSetId();

    // Add a reviewer.
    reviewer = accountCreator.create("reviewer", "reviewer@test.com", "Reviewer");
    gApi.changes().id(patchSetId.changeId().get()).addReviewer(reviewer.username());

    // Star the change from some user.
    starrer = accountCreator.create("starred", "starrer@test.com", "Starrer");
    requestScopeOperations.setApiUser(starrer.id());
    gApi.accounts().self().starChange(patchSetId.changeId().toString());

    // Watch all comments of change from some user.
    watcher = accountCreator.create("watcher", "watcher@test.com", "Watcher");
    requestScopeOperations.setApiUser(watcher.id());
    ProjectWatchInfo projectWatchInfo = new ProjectWatchInfo();
    projectWatchInfo.project = project.get();
    projectWatchInfo.filter = "*";
    projectWatchInfo.notifyAllComments = true;
    gApi.accounts().self().setWatchedProjects(ImmutableList.of(projectWatchInfo));

    // Watch only change creations from some user --> user doesn't get notified by checks plugin.
    TestAccount changeCreationWatcher =
        accountCreator.create(
            "changeCreationWatcher", "changeCreationWatcher@test.com", "Change Creation Watcher");
    requestScopeOperations.setApiUser(changeCreationWatcher.id());
    projectWatchInfo = new ProjectWatchInfo();
    projectWatchInfo.project = project.get();
    projectWatchInfo.filter = "*";
    projectWatchInfo.notifyNewChanges = true;
    gApi.accounts().self().setWatchedProjects(ImmutableList.of(projectWatchInfo));

    // Add a reviewer that ignores the change --> user doesn't get notified by checks plugin.
    TestAccount ignorer = accountCreator.create("ignorer", "ignorer@test.com", "Ignorer");
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(patchSetId.changeId().get()).addReviewer(ignorer.username());
    requestScopeOperations.setApiUser(ignorer.id());
    gApi.changes().id(patchSetId.changeId().get()).ignore(true);

    // Reset request scope to admin.
    requestScopeOperations.setApiUser(admin.id());
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckCreation() throws Exception {
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();

    // Post a new check that changes the combined check state to FAILED.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid1.get();
    input.state = CheckState.FAILED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    // Except one email because the combined check state was updated.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.FAILED);
    assertThat(message.rcpt())
        .containsExactly(
            owner.getEmailAddress(),
            reviewer.getEmailAddress(),
            starrer.getEmailAddress(),
            watcher.getEmailAddress());
  }

  @Test
  public void noCombinedCheckUpdatedEmailOnCheckCreationIfCombinedCheckStateIsNotChanged()
      throws Exception {
    // Create a check that sets the combined check state to FAILED.
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Post a new check that doesn't change the combined check state..
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid2.get();
    input.state = CheckState.SCHEDULED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    // Except that no email was sent because the combined check state was not updated.
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckUpdate() throws Exception {
    // Create a check that sets the combined check state to FAILED.
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Update the new check so that the combined check state is changed to IN_PROGRESS.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid1.get();
    input.state = CheckState.RUNNING;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    // Except one email because the combined check state was updated.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
    assertThat(message.rcpt())
        .containsExactly(
            owner.getEmailAddress(),
            reviewer.getEmailAddress(),
            starrer.getEmailAddress(),
            watcher.getEmailAddress());
  }

  @Test
  public void noCombinedCheckUpdatedEmailOnCheckUpdateIfCombinedCheckStateIsNotChanged()
      throws Exception {
    // Create 2 checks that set the combined check state to FAILED.
    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).state(CheckState.FAILED).upsert();
    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Update one of the checks in a way so that doesn't change the combined check state..
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid2.get();
    input.state = CheckState.SCHEDULED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    // Except that no email was sent because the combined check state was not updated.
    assertThat(sender.getMessages()).isEmpty();
  }

  private CombinedCheckState getCombinedCheckState() throws RestApiException {
    ChangeInfo changeInfo =
        gApi.changes()
            .id(patchSetId.changeId().get())
            .get(ImmutableListMultimap.of("checks--combined", "true"));
    ImmutableList<PluginDefinedInfo> infos =
        changeInfo.plugins.stream().filter(i -> i.name.equals("checks")).collect(toImmutableList());
    assertThat(infos).hasSize(1);
    assertThat(infos.get(0)).isInstanceOf(ChangeCheckInfo.class);
    return ((ChangeCheckInfo) infos.get(0)).combinedState;
  }
}
