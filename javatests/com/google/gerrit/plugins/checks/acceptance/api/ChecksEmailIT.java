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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
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
import java.util.List;
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
    bot = accountCreator.create("bot", "bot@test.com", "Bot");
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
    ignoringReviewer = accountCreator.create("ignorer", "ignorer@test.com", "Ignorer");
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
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.FAILED);
    assertThat(message.rcpt()).containsExactly(owner.getEmailAddress());
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
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.SUCCESSFUL);
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
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
    assertThat(message.rcpt()).containsExactly(owner.getEmailAddress());
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
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.SUCCESSFUL);
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
    assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
    assertThat(message.body())
        .contains("The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
    assertThat(message.rcpt()).containsExactly(owner.getEmailAddress());
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
      assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
      assertThat(message.body())
          .contains("The combined check state has been updated to " + CombinedCheckState.FAILED);
      assertThat(message.rcpt())
          .containsExactlyElementsIn(
              Arrays.stream(expectedRecipients)
                  .map(TestAccount::getEmailAddress)
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
      assertThat(message.from().getName()).isEqualTo(bot.fullName() + " (Code Review)");
      assertThat(message.body())
          .contains(
              "The combined check state has been updated to " + CombinedCheckState.IN_PROGRESS);
      assertThat(message.rcpt())
          .containsExactlyElementsIn(
              Arrays.stream(expectedRecipients)
                  .map(TestAccount::getEmailAddress)
                  .collect(toImmutableList()));
    }
  }

  @Test
  public void textEmailForCombinedCheckStateUpdated() throws Exception {
    CheckerUuid checkerUuid =
        checkerOperations.newChecker().repository(project).required().create();
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.IN_PROGRESS);

    sender.clear();
    postCheck(checkerUuid, CheckState.FAILED);
    assertThat(getCombinedCheckState()).isEqualTo(CombinedCheckState.FAILED);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);

    Message message = messages.get(0);
    assertThat(message.body())
        .isEqualTo(
            "The combined check state has been updated to "
                + CombinedCheckState.FAILED
                + " for patch set "
                + patchSetId.get()
                + " of this change ( "
                + changeUrl(change)
                + " ).\n"
                + emailFooterForCombinedCheckStateUpdate());
  }

  private String emailFooterForCombinedCheckStateUpdate() {
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
    requestScopeOperations.setApiUser(bot.id());
    CheckInput input = new CheckInput();
    input.checkerUuid = checkerUuid.get();
    input.state = checkState;
    checksApiFactory.revision(patchSetId).create(input).get();
  }
}
