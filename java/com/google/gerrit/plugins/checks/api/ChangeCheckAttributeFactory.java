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

import com.google.gerrit.plugins.checks.Checks;
import com.google.gerrit.server.DynamicOptions.BeanProvider;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.kohsuke.args4j.Option;

/**
 * Factory that populates a single {@link ChangeCheckInfo} instance in the {@code plugins} field of
 * a {@code ChangeInfo}.
 */
@Singleton
public class ChangeCheckAttributeFactory implements ChangeAttributeFactory {
  public static class GetChangeOptions implements DynamicBean {
    @Option(name = "--combined", usage = "include combined check state")
    boolean combined;
  }

  private final Checks checks;

  @Inject
  ChangeCheckAttributeFactory(Checks checks) {
    this.checks = checks;
  }

  @Override
  public ChangeCheckInfo create(ChangeData cd, BeanProvider beanProvider, String plugin) {
    DynamicBean opts = beanProvider.getDynamicBean(plugin);
    if (opts == null) {
      return null;
    }
    try {
      if (opts instanceof GetChangeOptions) {
        return forGetChange(cd, (GetChangeOptions) opts);
      }
      // TODO(dborowitz): Compute from cache in query path.
    } catch (OrmException | IOException e) {
      throw new RuntimeException(e);
    }
    throw new IllegalStateException("unexpected options type: " + opts);
  }

  private ChangeCheckInfo forGetChange(ChangeData cd, GetChangeOptions opts)
      throws OrmException, IOException {
    if (opts == null || !opts.combined) {
      return null;
    }
    ChangeCheckInfo info = new ChangeCheckInfo();
    info.combinedState =
        checks.getCombinedCheckState(cd.project(), cd.change().currentPatchSetId());
    return info;
  }
}
