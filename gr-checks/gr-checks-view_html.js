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

export const htmlTemplate = Polymer.html`
    <style>
      :host {
        display: block;
        width: 100%;
        overflow: auto;
      }

      table {
        width: 100%;
      }

      gr-checks-item {
        display: table-row;
      }

      gr-dropdown-list {
        --trigger-style: {
          color: var(--deemphasized-text-color);
          text-transform: none;
          font-family: var(--font-family);
        }
        --trigger-hover-color: rgba(0,0,0,.6);
      }
      @media screen and (max-width: 50em) {
        gr-dropdown-list {
          --native-select-style: {
            max-width: 5.25em;
          }
          --dropdown-content-stype: {
            max-width: 300px;
          }
        }
      }

      .headerRow {
        border-bottom: 1px solid #ddd;
      }

      .topHeader {
        padding-right: 32px;
        padding-bottom: 4px;
        text-align: left;
        white-space: nowrap;
      }

      th.topHeader:last-child {
        width: 100%;
      }

      h2 {
        font-size: 1.17em;
        font-weight: 500;
      }

      h3 {
        flex: 1;
        font-size: 1.17em;
        font-weight: 500;
        margin-bottom: 1.5rem;
      }

      header {
        display: flex;
        padding: var(--spacing-s) var(--spacing-m);
      }

      header.oldPatchset {
        background: var(--emphasis-color);
      }

      table {
        margin-bottom: 16px;
      }

      th:first-child {
        padding-left: 1rem;
      }

      .no-content {
        min-height: 106px;
        padding: 24px 0;
        text-align: center;
      }

      /* Add max-width 1px to check-message make sure content doesn't
         expand beyond the table width */
      .check-message {
        padding-left: 1rem;
        background: var(--table-subheader-background-color);
        max-width: 1px;
      }

      .message {
        white-space: pre-wrap;
        word-wrap: break-word;
      }

      .message-heading {
        font-weight: bold;
        margin-top: 1em;
      }

      .check-message-heading {
        padding-left: 1em;
      }

      .configure-button-container {
        text-align: right;
        flex: 1;
      }

      .filter {
        margin-left: 10px;
      }

      #listOverlay {
        width: 75%;
      }

      .container {
        display: flex;
        width: 100%;
      }

      .blocking-checks-container {
        margin-left: 10px;
        display: flex;
        align-items: center;
      }

      .show-more-checks {
        margin: 0 0 var(--spacing-l) var(--spacing-l);
      }

      .checks-table {
        border-collapse: collapse;
      }

    </style>

    <header class$="[[_computeHeaderClass(_currentPatchSet, _patchSetDropdownItems)]]">
      <div class="container">
        <template is="dom-if" if="[[_patchSetDropdownItems.length]]">
          <gr-dropdown-list
            class="patch-set-dropdown"
            items="[[_patchSetDropdownItems]]"
            on-value-change="_handlePatchSetChanged"
            value="[[_currentPatchSet]]">
          </gr-dropdown-list>
        </template>
        <template is="dom-if" if="[[_hasResults(_status)]]">
          <div class="filter">
            <span> Filter By: </span>
            <gr-dropdown-list
              class="check-state-filter"
              items="[[_statuses]]"
              on-value-change="_handleStatusFilterChanged"
              value="[[_currentStatus]]">
            </gr-dropdown-list>
          </div>
          <div class="blocking-checks-container">
            <input type="checkbox"
              name="blocking"
              checked$="[[_showBlockingChecksOnly]]"
              on-click="_handleBlockingCheckboxClicked"
              value="blocking">
              Required for Submit
          </div>
        </template>
        <template is="dom-if" if="[[_createCheckerCapability]]">
          <div class="configure-button-container">
            <gr-button on-click="_handleConfigureClicked"> Configure </gr-button>
          </div>
        </template>
      </div>
    </header>

    <template is="dom-if" if="[[_isLoading(_status)]]">
      <div class="no-content">
        <p>Loading...</p>
      </div>
    </template>

    <template is="dom-if" if="[[_isEmpty(_status)]]">
      <div class="no-content">
        <h2>No checks ran for this code review</h2>
        <p>Configure checkers to view the results here.</p>
      </div>
    </template>

    <template is="dom-if" if="[[_isNotConfigured(_status)]]">
      <div class="no-content">
        <h2>Code review checks not configured</h2>
        <p>Configure checkers to view the results here.</p>
      </div>
    </template>

    <template is="dom-if" if="[[_hasResults(_status)]]">
      <table class="checks-table">
        <thead>
          <tr class="headerRow">
            <th class="topHeader"></th>
            <th class="topHeader">Name</th>
            <th class="topHeader">For submit</th>
            <th class="topHeader">Status</th>
            <th class="topHeader">Started</th>
            <th class="topHeader">Duration</th>
            <th class="topHeader"><!-- actions --></th>
            <th class="topHeader"><!-- re-run --></th>
            <th class="topHeader">Description</th>
          </tr>
        </thead>
        <tbody>
          <template is="dom-repeat" items="[[_visibleChecks]]" as="check">
              <tr>
                <gr-checks-item on-retry-check="_handleRetryCheck" on-toggle-check-message="_toggleCheckMessage"
                  check="[[check]]">
                </gr-checks-item>
              </tr>
              <template is="dom-if" if="[[check.showCheckMessage]]">
                <tr>
                  <td colspan="10" class="check-message-heading">
                    <span class="message-heading">
                      Message
                    </span>
                  </td>
                </tr>
                <tr>
                  <td colspan="10" class="check-message">
                    <span class="message"> [[check.message]] </span>
                  </td>
                </tr>
              </template>
          </template>
        </tbody>
      </table>
      <template is="dom-if" if="[[_showMoreChecksButton]]">
        <gr-button class="show-more-checks" on-click="_toggleShowChecks">
          [[_computeShowText(_showAllChecks)]]
        </gr-button>
      </template>
    </template>

    <gr-checkers-list on-resize="_handleCheckersListResize" plugin-rest-api="[[pluginRestApi]]"></gr-checkers-list>
    `;