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

package com.google.gerrit.plugins.checks.api;

import com.google.gerrit.extensions.client.ListOption;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.plugins.checks.CheckJson;
import com.google.gerrit.plugins.checks.ListChecksOption;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.EnumSet;
import org.kohsuke.args4j.Option;

public class GetCheck implements RestReadView<CheckResource> {
  private final CheckJson.Factory checkJsonFactory;

  private final EnumSet<ListChecksOption> options = EnumSet.noneOf(ListChecksOption.class);

  @Option(name = "-o", usage = "Output options")
  void addOption(ListChecksOption o) {
    options.add(o);
  }

  @Option(name = "-O", usage = "Output option flags, in hex")
  void setOptionFlagsHex(String hex) {
    options.addAll(ListOption.fromBits(ListChecksOption.class, Integer.parseInt(hex, 16)));
  }

  @Inject
  GetCheck(CheckJson.Factory checkJsonFactory) {
    this.checkJsonFactory = checkJsonFactory;
  }

  @Override
  public CheckInfo apply(CheckResource resource)
      throws AuthException, BadRequestException, ResourceConflictException, IOException {
    return checkJsonFactory.create(options).format(resource.getCheck());
  }
}
