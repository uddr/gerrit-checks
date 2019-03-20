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

import com.google.gerrit.plugins.checks.CombinedCheckStateCache;
import com.google.gerrit.server.DynamicOptions.BeanProvider;
import com.google.gerrit.server.DynamicOptions.DynamicBean;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.kohsuke.args4j.Option;

/**
 * Factory that populates a single {@link ChangeCheckInfo} instance in the {@code plugins} field of
 * a {@code ChangeInfo}.
 */
@Singleton
public class ChangeCheckAttributeFactory implements ChangeAttributeFactory {
  private static final String COMBINED_OPTION_NAME = "--combined";
  private static final String COMBINED_OPTION_USAGE = "include combined check state";

  public static class GetChangeOptions implements DynamicBean {
    @Option(name = COMBINED_OPTION_NAME, usage = COMBINED_OPTION_USAGE)
    boolean combined;
  }

  public static class QueryChangesOptions implements DynamicBean {
    @Option(name = COMBINED_OPTION_NAME, usage = COMBINED_OPTION_USAGE)
    boolean combined;
  }

  private final CombinedCheckStateCache combinedCheckStateCache;

  @Inject
  ChangeCheckAttributeFactory(CombinedCheckStateCache combinedCheckStateCache) {
    this.combinedCheckStateCache = combinedCheckStateCache;
  }

  @Override
  public ChangeCheckInfo create(ChangeData cd, BeanProvider beanProvider, String plugin) {
    DynamicBean opts = beanProvider.getDynamicBean(plugin);
    if (opts == null) {
      return null;
    }
    if (opts instanceof GetChangeOptions) {
      return forGetChange(cd, (GetChangeOptions) opts);
    } else if (opts instanceof QueryChangesOptions) {
      return forQueryChanges(cd, (QueryChangesOptions) opts);
    }
    throw new IllegalStateException("unexpected options type: " + opts);
  }

  private ChangeCheckInfo forGetChange(ChangeData cd, GetChangeOptions opts) {
    if (opts == null || !opts.combined) {
      return null;
    }
    // Reload value in cache to fix up inconsistencies between cache and actual state.
    return new ChangeCheckInfo(
        combinedCheckStateCache.reload(cd.project(), cd.change().currentPatchSetId()));
  }

  private ChangeCheckInfo forQueryChanges(ChangeData cd, QueryChangesOptions opts) {
    if (!opts.combined) {
      return null;
    }
    return new ChangeCheckInfo(
        combinedCheckStateCache.get(cd.project(), cd.change().currentPatchSetId()));
  }
}
