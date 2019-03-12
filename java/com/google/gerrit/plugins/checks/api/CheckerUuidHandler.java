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

import static com.google.gerrit.util.cli.Localizable.localizable;

import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class CheckerUuidHandler extends OptionHandler<CheckerUuid> {
  @Inject
  public CheckerUuidHandler(
      @Assisted CmdLineParser parser,
      @Assisted OptionDef option,
      @Assisted Setter<CheckerUuid> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String token = params.getParameter(0);

    if (!CheckerUuid.isUuid(token)) {
      throw new CmdLineException(owner, localizable("Invalid checker UUID: %s"), token);
    }

    CheckerUuid checkerUuid = CheckerUuid.parse(token);
    setter.addValue(checkerUuid);
    return 1;
  }

  @Override
  public String getDefaultMetaVariable() {
    return "UUID";
  }
}
