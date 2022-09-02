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

package com.google.gerrit.plugins.checks.email;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.plugins.checks.Check;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.EmailArguments;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.HashMap;
import java.util.Map;

/** Send notice about an update of the combined check state of a change. */
public class CombinedCheckStateUpdatedSender extends ReplyToChangeSender {
  public interface Factory extends ReplyToChangeSender.Factory<CombinedCheckStateUpdatedSender> {
    @Override
    CombinedCheckStateUpdatedSender create(Project.NameKey project, Change.Id changeId);
  }

  private CombinedCheckState oldCombinedCheckState;
  private CombinedCheckState newCombinedCheckState;
  private Checker checker;
  private Check check;
  private ImmutableMap<Checker, Check> checksByChecker;

  @Inject
  public CombinedCheckStateUpdatedSender(
      EmailArguments args, @Assisted Project.NameKey project, @Assisted Change.Id changeId) {
    super(args, "combinedCheckStateUpdate", ChangeEmail.newChangeData(args, project, changeId));
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ALL_COMMENTS);
  }

  public void setCombinedCheckState(
      CombinedCheckState oldCombinedCheckState, CombinedCheckState newCombinedCheckState) {
    this.oldCombinedCheckState = requireNonNull(oldCombinedCheckState);
    this.newCombinedCheckState = requireNonNull(newCombinedCheckState);
  }

  public void setCheck(Checker checker, Check check) {
    requireNonNull(check, "check is missing");
    requireNonNull(checker, "checker is missing");
    checkState(
        check.key().checkerUuid().equals(checker.getUuid()),
        "checker %s doesn't match check %s",
        checker.getUuid(),
        check.key());

    this.checker = checker;
    this.check = check;
  }

  public void setChecksByChecker(Map<Checker, Check> checksByChecker) {
    this.checksByChecker = ImmutableMap.copyOf(requireNonNull(checksByChecker));
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    if (oldCombinedCheckState != null) {
      soyContext.put("oldCombinedCheckState", oldCombinedCheckState.name());
    }

    if (newCombinedCheckState != null) {
      soyContext.put("newCombinedCheckState", newCombinedCheckState.name());
    }

    if (checker != null && check != null) {
      soyContext.put("checker", getCheckerData(checker, check));
    }

    if (checksByChecker != null) {
      Map<String, Object> allCheckersData = new HashMap<>();
      for (CheckState checkState : CheckState.values()) {
        allCheckersData.put(
            CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, checkState.name()),
            getCheckerDataForCheckState(checkState));
      }
      soyContext.put("allCheckers", allCheckersData);
    }
  }

  /**
   * Creates a map with the checker data that can be fed into the soy template.
   *
   * <p>The map keys are the names of the checker properties and the map values are the checker
   * property values as types that are compatible with soy templates. The value of the {@code check}
   * field is a map with the check data. The map with the check data maps the names of the check
   * properties to the check property values as types that are compatible with soy.
   *
   * @param checker the checker
   * @param check the check
   * @return the checker data as a map that can be fed into a soy template
   */
  private static Map<String, Object> getCheckerData(Checker checker, Check check) {
    Map<String, Object> checkData = new HashMap<>();
    checkData.put("change", check.key().patchSet().changeId().get());
    checkData.put("patchSet", check.key().patchSet().get());
    checkData.put("repository", check.key().repository().get());
    checkData.put("state", check.state().name());
    check.message().ifPresent(message -> checkData.put("message", message));
    check.url().ifPresent(url -> checkData.put("url", url));

    Map<String, Object> checkerData = new HashMap<>();
    checkerData.put("check", checkData);
    checkerData.put("uuid", checker.getUuid().get());
    checkerData.put("name", checker.getName());
    checkerData.put("repository", checker.getRepository().get());
    checker.getDescription().ifPresent(description -> checkerData.put("description", description));
    checker.getUrl().ifPresent(url -> checkerData.put("url", url));

    return checkerData;
  }

  private ImmutableList<Map<String, Object>> getCheckerDataForCheckState(CheckState checkState) {
    return checksByChecker.entrySet().stream()
        .filter(e -> e.getValue().state() == checkState)
        .sorted(comparing(e -> e.getKey().getName()))
        .map(e -> getCheckerData(e.getKey(), e.getValue()))
        .collect(toImmutableList());
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("CombinedCheckStateUpdated"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("CombinedCheckStateUpdatedHtml"));
    }
  }
}
