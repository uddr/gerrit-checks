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

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.plugins.checks.api.CombinedCheckState;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.mail.send.ChangeEmail;
import com.google.gerrit.server.mail.send.EmailArguments;
import com.google.gerrit.server.mail.send.ReplyToChangeSender;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Send notice about an update of the combined check state of a change. */
public class CombinedCheckStateUpdatedSender extends ReplyToChangeSender {
  public interface Factory extends ReplyToChangeSender.Factory<CombinedCheckStateUpdatedSender> {
    @Override
    CombinedCheckStateUpdatedSender create(Project.NameKey project, Change.Id changeId);
  }

  private CombinedCheckState combinedCheckState;

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
    removeUsersThatIgnoredTheChange();
  }

  public void setCombinedCheckState(CombinedCheckState combinedCheckState) {
    this.combinedCheckState = combinedCheckState;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();

    if (combinedCheckState != null) {
      soyContext.put("combinedCheckState", combinedCheckState.name());
    }
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("CombinedCheckStateUpdated"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("CombinedCheckStateUpdatedHtml"));
    }
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
