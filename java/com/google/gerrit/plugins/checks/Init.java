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

package com.google.gerrit.plugins.checks;

import static com.google.gerrit.common.FileUtil.chmod;
import static com.google.gerrit.pgm.init.api.InitUtil.extract;

import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.plugins.checks.api.CheckerRefMigration;
import com.google.gerrit.plugins.checks.email.ChecksEmailModule;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Path;

public class Init implements InitStep {
  private final SitePaths site;
  private final CheckerRefMigration checkerRefMigration;

  @Inject
  Init(SitePaths site, CheckerRefMigration checkerRefMigration) {
    this.site = site;
    this.checkerRefMigration = checkerRefMigration;
  }

  @Override
  public void run() throws Exception {
    extractMailExample("CombinedCheckStateUpdated.soy");
    extractMailExample("CombinedCheckStateUpdatedHtml.soy");
    checkerRefMigration.migrate();
  }

  private void extractMailExample(String orig) throws Exception {
    Path ex = site.mail_dir.resolve(orig + ".example");
    extract(ex, ChecksEmailModule.class, orig);
    chmod(0444, ex);
  }
}
