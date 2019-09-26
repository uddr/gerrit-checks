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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.server.mail.send.MailSoyTemplateProvider;
import com.google.inject.Singleton;
import java.util.Set;

@Singleton
public class ChecksMailSoyTemplateProvider implements MailSoyTemplateProvider {
  @Override
  public String getPath() {
    return "com/google/gerrit/plugins/checks/email/";
  }

  @Override
  public Set<String> getFileNames() {
    return ImmutableSet.of("CombinedCheckStateUpdated.soy", "CombinedCheckStateUpdatedHtml.soy");
  }
}
