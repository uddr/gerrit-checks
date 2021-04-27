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
    <style include="gr-form-styles">
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
      gr-autocomplete {
        border: none;
        --gr-autocomplete: {
          border: 1px solid var(--border-color);
          border-radius: 2px;
          font-size: var(--font-size-normal);
          height: 2em;
          padding: 0 .15em;
          width: 20em;
        }
      }
      .error {
        color: red;
      }
      #checkerSchemaInput[disabled] {
        background-color: var(--table-subheader-background-color);
      }
      #checkerIdInput[disabled] {
        background-color: var(--table-subheader-background-color);
      }
      .uuid {
        overflow: scroll;
      }
    </style>

    <div class="gr-form-styles">
      <div id="form">
        <section hidden$="[[_errorMsg.length > 0]]">
          <span class="error"> {{_errorMsg}} </span>
        </section>
        <section>
          <span class="title">Name*</span>
          <iron-input autocomplete="on"
                      bind-value="{{_name}}">
            <input is="iron-input"
                   id="checkerNameInput"
                   autocomplete="on"
                   bind-value="{{_name}}">
          </iron-input>
        </section>
        <section>
          <span class="title">Description</span>
          <iron-input autocomplete="on"
                      bind-value="{{_description}}">
            <input is="iron-input"
                   id="checkerDescriptionInput"
                   autocomplete="on"
                   bind-value="{{_description}}">
          </iron-input>
        </section>
        <section>
          <span class="title">Repository*</span>
          <div class="list">
            <template id="chips" is="dom-repeat" items="[[_repos]]" as="repo">
              <gr-repo-chip
                  repo="[[repo]]"
                  on-keydown="_handleChipKeydown"
                  on-remove="_handleOnRemove"
                  tabindex="-1">
              </gr-repo-chip>
            </template>
          </div>
          <div hidden$="[[_repositorySelected]]">
            <gr-autocomplete
              id="input"
              threshold="[[suggestFrom]]"
              query="[[_getRepoSuggestions]]"
              on-commit="_handleRepositorySelected"
              clear-on-commit
              warn-uncommitted
              text="{{_inputText}}">
           </gr-autocomplete>
          </div>
        </section>

        <section>
          <span class="title">Scheme*</span>
          <iron-input autocomplete="on"
                      bind-value="{{_scheme}}">
            <input is="iron-input"
                   id="checkerSchemaInput"
                   disabled$="[[_edit]]"
                   autocomplete="on"
                   bind-value="{{_scheme}}">
          </iron-input>
        </section>

        <section>
          <span class="title">ID*</span>
          <iron-input autocomplete="on"
                      bind-value="{{_id}}">
            <input is="iron-input"
                   id="checkerIdInput"
                   disabled$="[[_edit]]"
                   autocomplete="on"
                   bind-value="{{_id}}">
          </iron-input>
        </section>

        <section>
          <span class="title">Url</span>
          <iron-input autocomplete="on"
                      bind-value="{{_url}}">
            <input is="iron-input"
                    id="checkerUrlInput"
                    autocomplete="on"
                    bind-value="{{_url}}">
          </iron-input>
        </section>

        <section>
          <span class="title"> UUID </span>
          <span class="title uuid"> {{_uuid}} </span>
        </section>

        <section>
          <span class="title">Status</span>
          <gr-dropdown-list
            items="[[_statuses]]"
            on-value-change="_handleStatusChange"
            text="Status"
            value="[[_status]]">
          </gr-dropdown-list>
        </section>

        <section>
          <span class="title">Required</span>
          <input
          on-click = "_handleRequiredCheckBoxClicked"
          type="checkbox"
          id="privateChangeCheckBox"
          checked$="[[_required]]">
        </section>

        <section>
          <span class="title">Query</span>
          <iron-input autocomplete="on"
                      bind-value="{{_query}}">
            <input is="iron-input"
                    id="checkerQueryInput"
                    autocomplete="on"
                    bind-value="{{_query}}">
          </iron-input>
        </section>

      </div>
    </div>
`;
