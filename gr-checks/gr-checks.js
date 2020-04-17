/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import './gr-checks-chip-view.js';
import './gr-checks-view.js';
import './gr-checks-change-view-tab-header-view.js';
import './gr-checks-change-list-header-view.js';
import './gr-checks-change-list-item-cell-view.js';
import './gr-checks-item.js';
import './gr-checks-status.js';

Gerrit.install(plugin => {
  const getChecks = (change, revision) => {
    return plugin.restApi().get(
        '/changes/' + change + '/revisions/' + revision + '/checks?o=CHECKER');
  };

  // TODO(brohlfs): Enable this dashboard column when search queries start
  // returning checks.
  // plugin.registerDynamicCustomComponent(
  //     'change-list-header',
  //     'gr-checks-change-list-header-viewgg');
  // plugin.registerDynamicCustomComponent(
  //     'change-list-item-cell',
  //     'gr-checks-change-list-item-cell-view');
  plugin.registerCustomComponent(
      'commit-container',
      'gr-checks-chip-view').onAttached(
      view => {
        view['getChecks'] = getChecks;
      }
  );
  plugin.registerDynamicCustomComponent(
      'change-view-tab-header',
      'gr-checks-change-view-tab-header-view'
  );
  plugin.registerDynamicCustomComponent(
      'change-view-tab-content',
      'gr-checks-view').onAttached(
      view => {
        view['isConfigured'] = repository => Promise.resolve(true);
        view['getChecks'] = getChecks;
      });
});