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
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
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
import com.google.gerrit.plugins.checks.api.RerunInput;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class ChecksEmailIT extends AbstractCheckersTest {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;

  private TestAccount bot;
  private TestAccount owner;
  private TestAccount ignoringReviewer;
  private TestAccount reviewer;
  private TestAccount starrer;
  private TestAccount watcher;
  private Change change;
  private PatchSet.Id patchSetId;

  @Before
  public void setup() throws Exception {
    // Create a bot account, create a bots group and add the bot as member and allow the bots group
    // to post checks.
    bot = accountCreator.create("bot", "bot@example.com", "Bot", null);
    AccountGroup.UUID botsAccountGroupUuid =
        groupOperations.newGroup().name("bots").addMember(bot.id()).create();
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(allowCapability("checks-administrateCheckers").group(botsAccountGroupUuid))
        .update();

    // Create a change.
    owner = admin;
    PushOneCommit.Result result = createChange();
    change = result.getChange().change();
    patchSetId = result.getPatchSetId();

    // Add a reviewer.
    reviewer = accountCreator.create("reviewer", "reviewer@example.com", "Reviewer", null);
    gApi.changes().id(patchSetId.changeId().get()).addReviewer(reviewer.username());

    // Star the change from some user.
    starrer = accountCreator.create("starred", "starrer@example.com", "Starrer", null);
    requestScopeOperations.setApiUser(starrer.id());
    gApi.accounts().self().starChange(patchSetId.changeId().toString());

    // Watch all comments of change from some user.
    watcher = accountCreator.create("watcher", "watcher@example.com", "Watcher", null);
    requestScopeOperations.setApiUser(watcher.id());
    ProjectWatchInfo projectWatchInfo = new ProjectWatchInfo();
    projectWatchInfo.project = project.get();
    projectWatchInfo.filter = "*";
    projectWatchInfo.notifyAllComments = true;
    gApi.accounts().self().setWatchedProjects(ImmutableList.of(projectWatchInfo));

    // Watch only change creations from some user --> user doesn't get notified by checks plugin.
    TestAccount changeCreationWatcher =
        accountCreator.create(
            "changeCreationWatcher",
            "changeCreationWatcher@example.com",
            "Change Creation Watcher",
            null);
    requestScopeOperations.setApiUser(changeCreationWatcher.id());
    projectWatchInfo = new ProjectWatchInfo();
    projectWatchInfo.project = project.get();
    projectWatchInfo.filter = "*";
    projectWatchInfo.notifyNewChanges = true;
    gApi.accounts().self().setWatchedProjects(ImmutableList.of(projectWatchInfo));

    // Add a reviewer that ignores the change --> user doesn't get notified by checks plugin.
    ignoringReviewer = accountCreator.create("ignorer", "ignorer@example.com", "Ignorer", null);
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(patchSetId.changeId().get()).addReviewer(ignoringReviewer.username());
    requestScopeOperations.setApiUser(ignoringReviewer.id());
    gApi.changes().id(patchSetId.changeId().get()).ignore(true);

    // Reset request scope to admin.
    requestScopeOperations.setApiUser(admin.id());
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckCreationToOwnerOnly() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();

    // Post a new check that changes the combined check state to FAILED.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.FAILED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    // Expect email because the combined check state was updated.
    // The email is only sent to the change owner because the new combined check state !=
    // SUCCESSFUL.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.FAILED);
    assertThat(message.rcpt()).containsExactly(owner.getNameEmail());
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckCreationToAll() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();

    // Post a check that changes the combined check state to SUCCESSFUL.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.SUCCESSFUL;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.SUCCESSFUL);

    // Expect email because the combined check state was updated.
    // The email is only sent to all users that are involved in the change because the new combined
    // check state = SUCCESSFUL.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.SUCCESSFUL);
    assertThat(message.rcpt())
        .containsExactly(
            owner.getNameEmail(),
            reviewer.getNameEmail(),
            starrer.getNameEmail(),
            watcher.getNameEmail());
  }

  @Test
  public void noCombinedCheckUpdatedEmailOnCheckCreationIfCombinedCheckStateIsNotChanged()
      throws Exception {
    // Create two required checkers.
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().repository(project).required().create();
    CheckerUuid checkerUuid2 =
        checkerOperations.newChecker().repository(project).required().create();

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

    // Expect that no email was sent because the combined check state was not updated.
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckUpdateToOwnerOnly() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    // Create a check that sets the combined check state to FAILED.
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Update the new check so that the combined check state is changed to IN_PROGRESS.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.RUNNING;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    // Expect email because the combined check state was updated.
    // The email is only sent to the change owner because the new combined check state !=
    // SUCCESSFUL.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
    assertThat(message.rcpt()).containsExactly(owner.getNameEmail());
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckUpdateToAll() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    // Create a check that sets the combined check state to FAILED.
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Update the new check so that the combined check state is changed to IN_PROGRESS.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.SUCCESSFUL;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.SUCCESSFUL);

    // Expect email because the combined check state was updated.
    // The email is only sent to all users that are involved in the change because the new combined
    // check state = SUCCESSFUL.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.SUCCESSFUL);
    assertThat(message.rcpt())
        .containsExactly(
            owner.getNameEmail(),
            reviewer.getNameEmail(),
            starrer.getNameEmail(),
            watcher.getNameEmail());
  }

  @Test
  public void noCombinedCheckUpdatedEmailOnCheckUpdateIfCombinedCheckStateIsNotChanged()
      throws Exception {
    // Create two required checkers.
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().repository(project).required().create();
    CheckerUuid checkerUuid2 =
        checkerOperations.newChecker().repository(project).required().create();

    // Create 2 checks that set the combined check state to FAILED.
    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).state(CheckState.FAILED).upsert();
    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Update one of the checks in a way so that doesn't change the combined check state.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid2.get();
    input.state = CheckState.SCHEDULED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    // Expect that no email was sent because the combined check state was not updated.
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void combinedCheckUpdatedEmailAfterCheckRerunToOwnerOnly() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    // Create a check that sets the combined check state to FAILED.
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.newCheck(checkKey).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Rerun the check so that the combined check state is changed to IN_PROGRESS.
    requestScopeOperations.setApiUser(bot.id());
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    // Expect email because the combined check state was updated.
    // The email is only sent to the change owner because the new combined check state !=
    // SUCCESSFUL.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
    assertThat(message.rcpt()).containsExactly(owner.getNameEmail());
  }

  @Test
  public void noCombinedCheckUpdatedEmailOnCheckRerunIfCombinedCheckStateIsNotChanged()
      throws Exception {
    // Create two required checkers.
    CheckerUuid checkerUuid1 =
        checkerOperations.newChecker().repository(project).required().create();
    CheckerUuid checkerUuid2 =
        checkerOperations.newChecker().repository(project).required().create();

    // Create 2 checks that set the combined check state to FAILED.
    CheckKey checkKey1 = CheckKey.create(project, patchSetId, checkerUuid1);
    checkOperations.newCheck(checkKey1).state(CheckState.FAILED).upsert();
    CheckKey checkKey2 = CheckKey.create(project, patchSetId, checkerUuid2);
    checkOperations.newCheck(checkKey2).state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Rerun only one check so that the combined check state stays FAILED.
    requestScopeOperations.setApiUser(bot.id());
    checksApiFactory.revision(patchSetId).id(checkKey1.checkerUuid()).rerun();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    // Expect that no email was sent because the combined check state was not updated.
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void postCheckRespectsNotifySettings() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    testNotifySettingsForPostCheck(
        checkerUuid, NotifyHandling.ALL, owner, reviewer, starrer, watcher);
    testNotifySettingsForPostCheck(checkerUuid, NotifyHandling.OWNER, owner);
    testNotifySettingsForPostCheck(checkerUuid, NotifyHandling.OWNER_REVIEWERS, owner, reviewer);
    testNotifySettingsForPostCheck(checkerUuid, NotifyHandling.NONE);

    testNotifySettingsForPostCheck(
        checkerUuid,
        ImmutableSet.of(user),
        NotifyHandling.ALL,
        user,
        owner,
        reviewer,
        starrer,
        watcher);
    testNotifySettingsForPostCheck(
        checkerUuid, ImmutableSet.of(user), NotifyHandling.OWNER, user, owner);
    testNotifySettingsForPostCheck(
        checkerUuid, ImmutableSet.of(user), NotifyHandling.OWNER_REVIEWERS, user, owner, reviewer);
    testNotifySettingsForPostCheck(checkerUuid, ImmutableSet.of(user), NotifyHandling.NONE, user);
  }

  private void testNotifySettingsForPostCheck(
      CheckerUuid checkerUuid, NotifyHandling notify, TestAccount... expectedRecipients)
      throws RestApiException {
    testNotifySettingsForPostCheck(checkerUuid, ImmutableSet.of(), notify, expectedRecipients);
  }

  private void testNotifySettingsForPostCheck(
      CheckerUuid checkerUuid,
      Set<TestAccount> accountsToNotify,
      NotifyHandling notify,
      TestAccount... expectedRecipients)
      throws RestApiException {
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();

    // Post a new check that changes the combined check state to FAILED.
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    if (!accountsToNotify.isEmpty()) {
      input.notifyDetails =
          ImmutableMap.of(
              RecipientType.TO,
              new NotifyInfo(
                  accountsToNotify.stream().map(TestAccount::username).collect(toImmutableList())));
    }
    input.notify = notify;
    input.checkerUuid = checkerUuid.get();
    input.state = CheckState.FAILED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    if (expectedRecipients.length == 0) {
      assertThat(messages).isEmpty();
    } else {
      assertThat(messages).hasSize(1);

      Message message = messages.get(0);
      assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
      assertThat(message.body())
          .contains("The combined check state has been updated to " + CombinedCheckState.FAILED);
      assertThat(message.rcpt())
          .containsExactlyElementsIn(
              Arrays.stream(expectedRecipients)
                  .map(TestAccount::getNameEmail)
                  .collect(toImmutableList()));
    }

    // reset combined check state
    input.state = CheckState.SCHEDULED;
    checksApiFactory.revision(patchSetId).create(input).get();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);
  }

  @Test
  public void rerunCheckRespectsNotifySettings() throws Exception {
    // Create a required checker.
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();

    testNotifySettingsForRerunCheck(
        checkerUuid, NotifyHandling.ALL, owner, reviewer, starrer, watcher);
    testNotifySettingsForRerunCheck(checkerUuid, NotifyHandling.OWNER, owner);
    testNotifySettingsForRerunCheck(checkerUuid, NotifyHandling.OWNER_REVIEWERS, owner, reviewer);
    testNotifySettingsForRerunCheck(checkerUuid, NotifyHandling.NONE);

    testNotifySettingsForRerunCheck(
        checkerUuid,
        ImmutableSet.of(user),
        NotifyHandling.ALL,
        user,
        owner,
        reviewer,
        starrer,
        watcher);
    testNotifySettingsForRerunCheck(
        checkerUuid, ImmutableSet.of(user), NotifyHandling.OWNER, user, owner);
    testNotifySettingsForRerunCheck(
        checkerUuid, ImmutableSet.of(user), NotifyHandling.OWNER_REVIEWERS, user, owner, reviewer);
    testNotifySettingsForRerunCheck(checkerUuid, ImmutableSet.of(user), NotifyHandling.NONE, user);
  }

  private void testNotifySettingsForRerunCheck(
      CheckerUuid checkerUuid, NotifyHandling notify, TestAccount... expectedRecipients)
      throws RestApiException {
    testNotifySettingsForPostCheck(checkerUuid, ImmutableSet.of(), notify, expectedRecipients);
  }

  private void testNotifySettingsForRerunCheck(
      CheckerUuid checkerUuid,
      Set<TestAccount> accountsToNotify,
      NotifyHandling notify,
      TestAccount... expectedRecipients)
      throws RestApiException {
    // Create a check that sets the combined check state to FAILED.
    CheckKey checkKey = CheckKey.create(project, patchSetId, checkerUuid);
    checkOperations.check(checkKey).forUpdate().state(CheckState.FAILED).upsert();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();

    // Post a new check that changes the combined check state to FAILED.
    requestScopeOperations.setApiUser(bot.id());
    RerunInput rerunInput = new RerunInput();
    if (!accountsToNotify.isEmpty()) {
      rerunInput.notifyDetails =
          ImmutableMap.of(
              RecipientType.TO,
              new NotifyInfo(
                  accountsToNotify.stream().map(TestAccount::username).collect(toImmutableList())));
    }
    rerunInput.notify = notify;
    checksApiFactory.revision(patchSetId).id(checkKey.checkerUuid()).rerun(rerunInput);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    List<Message> messages = sender.getMessages();
    if (expectedRecipients.length == 0) {
      assertThat(messages).isEmpty();
    } else {
      assertThat(messages).hasSize(1);

      Message message = messages.get(0);
      assertThat(message.from().name()).isEqualTo(bot.fullName() + " (Code Review)");
      assertThat(message.body())
          .contains(
              "The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
      assertThat(message.rcpt())
          .containsExactlyElementsIn(
              Arrays.stream(expectedRecipients)
                  .map(TestAccount::getNameEmail)
                  .collect(toImmutableList()));
    }
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedDefaultEmail() throws Exception {
    String checkerName = "My Checker";
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name(checkerName).repository(project).required().create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    postCheck(checkerUuid, CheckState.SUCCESSFUL);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.SUCCESSFUL);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.SUCCESSFUL)
                + allChecksOverviewText(
                    ImmutableMap.of(CheckState.SUCCESSFUL, ImmutableList.of(checkerName)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.SUCCESSFUL)
                + allChecksOverviewHtml(
                    ImmutableMap.of(CheckState.SUCCESSFUL, ImmutableList.of(checkerName)))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedToFailedEmail() throws Exception {
    String checkerName = "My Checker";
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name(checkerName).required().create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    postCheck(checkerUuid, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerName
                + " updated the check state to "
                + CheckState.FAILED
                + ".\n"
                + allChecksOverviewText(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <strong>"
                + checkerName
                + "</strong> updated the check state to "
                + CheckState.FAILED
                + ".</p>"
                + allChecksOverviewHtml(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedToFailedEmailWithCheckerUrl()
      throws Exception {
    String checkerName = "My Checker";
    String checkerUrl = "http://my-checker/";
    CheckerUuid checkerUuid =
        checkerOperations
            .newChecker()
            .repository(project)
            .name(checkerName)
            .url(checkerUrl)
            .required()
            .create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    postCheck(checkerUuid, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerName
                + " ( "
                + checkerUrl
                + " ) updated the check state to "
                + CheckState.FAILED
                + ".\n"
                + allChecksOverviewText(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <a href=\""
                + checkerUrl
                + "\">"
                + checkerName
                + "</a> updated the check state to "
                + CheckState.FAILED
                + ".</p>"
                + allChecksOverviewHtml(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedToFailedEmailWithCheckMessage()
      throws Exception {
    String checkerName = "My Checker";
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name(checkerName).required().create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    String checkMessage = "foo bar baz";
    postCheck(checkerUuid, CheckState.FAILED, checkMessage);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerName
                + " updated the check state to "
                + CheckState.FAILED
                + ":\n"
                + checkMessage
                + "\n"
                + allChecksOverviewText(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <strong>"
                + checkerName
                + "</strong> updated the check state to "
                + CheckState.FAILED
                + ":<br>"
                + checkMessage
                + "</p>"
                + allChecksOverviewHtml(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedToFailedEmailWithCheckUrl()
      throws Exception {
    String checkerName = "My Checker";
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name(checkerName).required().create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    String checkUrl = "http://my-checker/12345";
    postCheck(checkerUuid, CheckState.FAILED, null, checkUrl);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerName
                + " updated the check state to "
                + CheckState.FAILED
                + " ( "
                + checkUrl
                + " ).\n"
                + allChecksOverviewText(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <strong>"
                + checkerName
                + "</strong> updated the check state to <a href=\""
                + checkUrl
                + "\">"
                + CheckState.FAILED
                + "</a>.</p>"
                + allChecksOverviewHtml(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)),
                    ImmutableMap.of(checkerName, checkUrl))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedToWarningEmail() throws Exception {
    String checkerName = "My Checker";
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).name(checkerName).create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    postCheck(checkerUuid, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.WARNING);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.WARNING)
                + "\n"
                + "Checker "
                + checkerName
                + " updated the check state to "
                + CheckState.FAILED
                + ".\n"
                + allChecksOverviewText(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.WARNING)
                + "<p>Checker <strong>"
                + checkerName
                + "</strong> updated the check state to "
                + CheckState.FAILED
                + ".</p>"
                + allChecksOverviewHtml(
                    ImmutableMap.of(CheckState.FAILED, ImmutableList.of(checkerName)))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedToWarningFromFailedEmail()
      throws Exception {
    String checkerNameRequired = "My Required Checker";
    CheckerUuid checkerUuidRequired =
        checkerOperations
            .newChecker()
            .name(checkerNameRequired)
            .repository(project)
            .required()
            .create();
    postCheck(checkerUuidRequired, CheckState.FAILED);

    String checkerNameOptional = "My Optional Checker";
    CheckerUuid checkerUuidOptional =
        checkerOperations.newChecker().name(checkerNameOptional).repository(project).create();
    postCheck(checkerUuidOptional, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    sender.clear();
    postCheck(checkerUuidRequired, CheckState.SUCCESSFUL);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.WARNING);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.WARNING)
                + allChecksOverviewText(
                    ImmutableMap.of(
                        CheckState.SUCCESSFUL,
                        ImmutableList.of(checkerNameRequired),
                        CheckState.FAILED,
                        ImmutableList.of(checkerNameOptional)))
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.WARNING)
                + allChecksOverviewHtml(
                    ImmutableMap.of(
                        CheckState.SUCCESSFUL,
                        ImmutableList.of(checkerNameRequired),
                        CheckState.FAILED,
                        ImmutableList.of(checkerNameOptional)))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedEmailWithBackfilledCheck()
      throws Exception {
    String checkerNameNotStartedBackfilled = "My Backfilled Checker";
    checkerOperations
        .newChecker()
        .name(checkerNameNotStartedBackfilled)
        .repository(project)
        .create();

    String checkerNameFailed = "My Failed Checker";
    CheckerUuid checkerUuidFailed =
        checkerOperations
            .newChecker()
            .name(checkerNameFailed)
            .repository(project)
            .required()
            .create();

    sender.clear();
    postCheck(checkerUuidFailed, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Map<CheckState, List<String>> expectedCheckersByState = new HashMap<>();
    expectedCheckersByState.put(
        CheckState.NOT_STARTED, ImmutableList.of(checkerNameNotStartedBackfilled));
    expectedCheckersByState.put(CheckState.FAILED, ImmutableList.of(checkerNameFailed));

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerNameFailed
                + " updated the check state to "
                + CheckState.FAILED
                + ".\n"
                + allChecksOverviewText(expectedCheckersByState)
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <strong>"
                + checkerNameFailed
                + "</strong> updated the check state to "
                + CheckState.FAILED
                + ".</p>"
                + allChecksOverviewHtml(expectedCheckersByState)
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedEmailWithChecksOfDifferentStates()
      throws Exception {
    String checkerNameNotStarted = "My Not Started Checker";
    CheckerUuid checkerUuidNotStarted =
        checkerOperations.newChecker().name(checkerNameNotStarted).repository(project).create();
    postCheck(checkerUuidNotStarted, CheckState.NOT_STARTED);

    String checkerNameScheduled = "My Scheduled Checker";
    CheckerUuid checkerUuidScheduled =
        checkerOperations.newChecker().name(checkerNameScheduled).repository(project).create();
    postCheck(checkerUuidScheduled, CheckState.SCHEDULED);

    String checkerNameRunning = "My Running Checker";
    CheckerUuid checkerUuidRunning =
        checkerOperations.newChecker().name(checkerNameRunning).repository(project).create();
    postCheck(checkerUuidRunning, CheckState.RUNNING);

    String checkerNameNotRelevant = "My Not Relevant Checker";
    CheckerUuid checkerUuidNotRelevant =
        checkerOperations.newChecker().name(checkerNameNotRelevant).repository(project).create();
    postCheck(checkerUuidNotRelevant, CheckState.NOT_RELEVANT);

    String checkerNameSuccesful = "My Successful Checker";
    CheckerUuid checkerUuidSuccessful =
        checkerOperations.newChecker().name(checkerNameSuccesful).repository(project).create();
    postCheck(checkerUuidSuccessful, CheckState.SUCCESSFUL);

    String checkerNameFailed = "My Failed Checker";
    CheckerUuid checkerUuidFailed =
        checkerOperations
            .newChecker()
            .name(checkerNameFailed)
            .repository(project)
            .required()
            .create();

    sender.clear();
    postCheck(checkerUuidFailed, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Map<CheckState, List<String>> expectedCheckersByState = new HashMap<>();
    expectedCheckersByState.put(CheckState.NOT_STARTED, ImmutableList.of(checkerNameNotStarted));
    expectedCheckersByState.put(CheckState.SCHEDULED, ImmutableList.of(checkerNameScheduled));
    expectedCheckersByState.put(CheckState.RUNNING, ImmutableList.of(checkerNameRunning));
    expectedCheckersByState.put(CheckState.NOT_RELEVANT, ImmutableList.of(checkerNameNotRelevant));
    expectedCheckersByState.put(CheckState.SUCCESSFUL, ImmutableList.of(checkerNameSuccesful));
    expectedCheckersByState.put(CheckState.FAILED, ImmutableList.of(checkerNameFailed));

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerNameFailed
                + " updated the check state to "
                + CheckState.FAILED
                + ".\n"
                + allChecksOverviewText(expectedCheckersByState)
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <strong>"
                + checkerNameFailed
                + "</strong> updated the check state to "
                + CheckState.FAILED
                + ".</p>"
                + allChecksOverviewHtml(expectedCheckersByState)
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void verifyMessageBodiesForCombinedCheckStateUpdatedEmailWithMultipleChecksOfSameState()
      throws Exception {
    String checkerNameRunningFoo = "Foo Checker";
    CheckerUuid checkerUuidRunningFoo =
        checkerOperations.newChecker().name(checkerNameRunningFoo).repository(project).create();
    String checkUrlFoo = "http://foo-checker/12345";
    postCheck(checkerUuidRunningFoo, CheckState.RUNNING, null, checkUrlFoo);

    String checkerNameRunningBar = "Bar Checker";
    CheckerUuid checkerUuidRunningBar =
        checkerOperations.newChecker().name(checkerNameRunningBar).repository(project).create();
    String checkUrlBar = "http://bar-checker/67890";
    postCheck(checkerUuidRunningBar, CheckState.RUNNING, null, checkUrlBar);

    String checkerNameRunningBaz = "Baz Checker";
    CheckerUuid checkerUuidRunningBaz =
        checkerOperations.newChecker().name(checkerNameRunningBaz).repository(project).create();
    // This check doesn't have an URL, so that we test a mix of checks with and without URL.
    postCheck(checkerUuidRunningBaz, CheckState.RUNNING);

    String checkerNameFailed = "My Failed Checker";
    CheckerUuid checkerUuidFailed =
        checkerOperations
            .newChecker()
            .name(checkerNameFailed)
            .repository(project)
            .required()
            .create();

    sender.clear();
    postCheck(checkerUuidFailed, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Map<CheckState, List<String>> expectedCheckersByState = new HashMap<>();
    expectedCheckersByState.put(
        CheckState.RUNNING,
        ImmutableList.of(checkerNameRunningBar, checkerNameRunningBaz, checkerNameRunningFoo));
    expectedCheckersByState.put(CheckState.FAILED, ImmutableList.of(checkerNameFailed));

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            combinedCheckStateUpdatedText(CombinedCheckState.FAILED)
                + "\n"
                + "Checker "
                + checkerNameFailed
                + " updated the check state to "
                + CheckState.FAILED
                + ".\n"
                + allChecksOverviewText(expectedCheckersByState)
                + textEmailFooterForCombinedCheckStateUpdate());

    assertThat(message.htmlBody())
        .isEqualTo(
            combinedCheckStateUpdatedHtml(CombinedCheckState.FAILED)
                + "<p>Checker <strong>"
                + checkerNameFailed
                + "</strong> updated the check state to "
                + CheckState.FAILED
                + ".</p>"
                + allChecksOverviewHtml(
                    expectedCheckersByState,
                    ImmutableMap.of(
                        checkerNameRunningFoo, checkUrlFoo, checkerNameRunningBar, checkUrlBar))
                + htmlEmailFooterForCombinedCheckStateUpdate());
  }

  @Test
  public void noEmailForCombinedCheckStateUpdatesOfNonCurrentPatchSet() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().name("My Checker").repository(project).create();

    // push a new patch set
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "new content " + System.nanoTime(),
            change.getKey().get());
    push.to("refs/for/master").assertOkStatus();

    sender.clear();

    // post check on old patch set
    postCheck(checkerUuid, CheckState.SUCCESSFUL);

    assertThat(sender.getMessages()).isEmpty();
  }

  private String combinedCheckStateUpdatedText(CombinedCheckState combinedCheckState) {
    return "The combined check state has been updated to "
        + combinedCheckState
        + " for patch set "
        + patchSetId.get()
        + " of this change ( "
        + changeUrl(change)
        + " ).\n";
  }

  private String allChecksOverviewText(Map<CheckState, List<String>> checkersByState) {
    StringBuilder b = new StringBuilder();
    b.append("\nAll checks:\n");
    if (checkersByState.containsKey(CheckState.SUCCESSFUL)) {
      b.append("Successful: ")
          .append(Joiner.on(", ").join(checkersByState.get(CheckState.SUCCESSFUL)))
          .append("\n");
    }
    if (checkersByState.containsKey(CheckState.NOT_RELEVANT)) {
      b.append("Not Relevant: ")
          .append(Joiner.on(", ").join(checkersByState.get(CheckState.NOT_RELEVANT)))
          .append("\n");
    }
    if (checkersByState.containsKey(CheckState.FAILED)) {
      b.append("Failed: ")
          .append(Joiner.on(", ").join(checkersByState.get(CheckState.FAILED)))
          .append("\n");
    }
    if (checkersByState.containsKey(CheckState.RUNNING)) {
      b.append("Running: ")
          .append(Joiner.on(", ").join(checkersByState.get(CheckState.RUNNING)))
          .append("\n");
    }
    if (checkersByState.containsKey(CheckState.SCHEDULED)) {
      b.append("Scheduled: ")
          .append(Joiner.on(", ").join(checkersByState.get(CheckState.SCHEDULED)))
          .append("\n");
    }
    if (checkersByState.containsKey(CheckState.NOT_STARTED)) {
      b.append("Not Started: ")
          .append(Joiner.on(", ").join(checkersByState.get(CheckState.NOT_STARTED)))
          .append("\n");
    }
    return b.toString();
  }

  private String textEmailFooterForCombinedCheckStateUpdate() {
    return "\n"
        + "Change subject: "
        + change.getSubject()
        + "\n"
        + "......................................................................\n"
        + "-- \n"
        + "To view, visit "
        + changeUrl(change)
        + "\n"
        + "To unsubscribe, or for help writing mail filters, visit "
        + canonicalWebUrl.get()
        + "settings\n"
        + "\n"
        + "Gerrit-Project: "
        + project.get()
        + "\n"
        + "Gerrit-Branch: "
        + change.getDest().shortName()
        + "\n"
        + "Gerrit-Change-Id: "
        + change.getKey().get()
        + "\n"
        + "Gerrit-Change-Number: "
        + change.getChangeId()
        + "\n"
        + "Gerrit-PatchSet: "
        + patchSetId.get()
        + "\n"
        + "Gerrit-Owner: "
        + owner.fullName()
        + " <"
        + owner.email()
        + ">\n"
        + "Gerrit-Reviewer: "
        + ignoringReviewer.fullName()
        + " <"
        + ignoringReviewer.email()
        + ">\n"
        + "Gerrit-Reviewer: "
        + reviewer.fullName()
        + " <"
        + reviewer.email()
        + ">\n"
        + "Gerrit-MessageType: combinedCheckStateUpdate\n";
  }

  private String combinedCheckStateUpdatedHtml(CombinedCheckState combinedCheckState) {
    return "<p>The combined check state has been updated to <strong>"
        + combinedCheckState
        + "</strong> for patch set "
        + patchSetId.get()
        + " of this <a href=\""
        + changeUrl(change)
        + "\">change</a>.</p>";
  }

  private String allChecksOverviewHtml(Map<CheckState, List<String>> checkersByState) {
    return allChecksOverviewHtml(checkersByState, ImmutableMap.of());
  }

  private String allChecksOverviewHtml(
      Map<CheckState, List<String>> checkersByState, Map<String, String> urlsByChecker) {
    Map<CheckState, List<String>> checkersByStateFormatted =
        checkersByState.entrySet().stream()
            .collect(
                toMap(
                    e -> e.getKey(),
                    e ->
                        e.getValue().stream()
                            .map(
                                c ->
                                    urlsByChecker.containsKey(c)
                                        ? "<a href=\"" + urlsByChecker.get(c) + "\">" + c + "</a>"
                                        : c)
                            .collect(toList())));

    StringBuilder b = new StringBuilder();
    b.append("<p><u><strong>All checks:</strong></u><br>");
    if (checkersByState.containsKey(CheckState.SUCCESSFUL)) {
      b.append("<strong>Successful:</strong> ")
          .append(Joiner.on(", ").join(checkersByStateFormatted.get(CheckState.SUCCESSFUL)))
          .append("<br>");
    }
    if (checkersByState.containsKey(CheckState.NOT_RELEVANT)) {
      b.append("<strong>Not Relevant:</strong> ")
          .append(Joiner.on(", ").join(checkersByStateFormatted.get(CheckState.NOT_RELEVANT)))
          .append("<br>");
    }
    if (checkersByState.containsKey(CheckState.FAILED)) {
      b.append("<strong>Failed:</strong> ")
          .append(Joiner.on(", ").join(checkersByStateFormatted.get(CheckState.FAILED)))
          .append("<br>");
    }
    if (checkersByState.containsKey(CheckState.RUNNING)) {
      b.append("<strong>Running:</strong> ")
          .append(Joiner.on(", ").join(checkersByStateFormatted.get(CheckState.RUNNING)))
          .append("<br>");
    }
    if (checkersByState.containsKey(CheckState.SCHEDULED)) {
      b.append("<strong>Scheduled:</strong> ")
          .append(Joiner.on(", ").join(checkersByStateFormatted.get(CheckState.SCHEDULED)))
          .append("<br>");
    }
    if (checkersByState.containsKey(CheckState.NOT_STARTED)) {
      b.append("<strong>Not Started:</strong> ")
          .append(Joiner.on(", ").join(checkersByStateFormatted.get(CheckState.NOT_STARTED)))
          .append("<br>");
    }
    b.append("</p>");
    return b.toString();
  }

  private String htmlViewChangeButton() {
    return "<p><a href=\"" + changeUrl(change) + "\">View Change</a></p>";
  }

  private String htmlEmailFooterForCombinedCheckStateUpdate() {
    return htmlViewChangeButton()
        + "<p>To view, visit <a href=\""
        + changeUrl(change)
        + "\">change "
        + change.getChangeId()
        + "</a>."
        + " To unsubscribe, or for help writing mail filters, visit <a href=\""
        + canonicalWebUrl.get()
        + "settings\">settings</a>.</p>"
        + "<div itemscope itemtype=\"http://schema.org/EmailMessage\">"
        + "<div itemscope itemprop=\"action\" itemtype=\"http://schema.org/ViewAction\">"
        + "<link itemprop=\"url\" href=\""
        + changeUrl(change)
        + "\"/>"
        + "<meta itemprop=\"name\" content=\"View Change\"/>"
        + "</div>"
        + "</div>\n\n"
        + "<div style=\"display:none\"> Gerrit-Project: "
        + project.get()
        + " </div>\n"
        + "<div style=\"display:none\"> Gerrit-Branch: "
        + change.getDest().shortName()
        + " </div>\n"
        + "<div style=\"display:none\"> Gerrit-Change-Id: "
        + change.getKey().get()
        + " </div>\n"
        + "<div style=\"display:none\"> Gerrit-Change-Number: "
        + change.getChangeId()
        + " </div>\n"
        + "<div style=\"display:none\"> Gerrit-PatchSet: "
        + patchSetId.get()
        + " </div>\n"
        + "<div style=\"display:none\"> Gerrit-Owner: Administrator &lt;admin@example.com&gt; </div>\n"
        + "<div style=\"display:none\"> Gerrit-Reviewer: "
        + ignoringReviewer.fullName()
        + " &lt;"
        + ignoringReviewer.email()
        + "&gt; </div>\n"
        + "<div style=\"display:none\"> Gerrit-Reviewer: "
        + reviewer.fullName()
        + " &lt;"
        + reviewer.email()
        + "&gt; </div>\n"
        + "<div style=\"display:none\"> Gerrit-MessageType: combinedCheckStateUpdate </div>\n"
        + "\n";
  }

  private String changeUrl(Change change) {
    return canonicalWebUrl.get() + "c/" + change.getProject().get() + "/+/" + change.getChangeId();
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

  private void postCheck(CheckerUuid checkerUuid, CheckState checkState) throws RestApiException {
    postCheck(checkerUuid, checkState, null);
  }

  private void postCheck(CheckerUuid checkerUuid, CheckState checkState, @Nullable String message)
      throws RestApiException {
    postCheck(checkerUuid, checkState, message, null);
  }

  private void postCheck(
      CheckerUuid checkerUuid,
      CheckState checkState,
      @Nullable String message,
      @Nullable String url)
      throws RestApiException {
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = checkState;
    input.message = message;
    input.url = url;
    checksApiFactory.revision(patchSetId).create(input).get();
  }
}
