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
    <style include="shared-styles"></style>
    <style include="gr-table-styles"></style>
    <style>
      iron-icon {
        cursor: pointer;
      }
      #filter {
        font-size: var(--font-size-normal);
        max-width: 25em;
      }
      #filter:focus {
        outline: none;
      }
      #topContainer {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: space-between;
        margin: 0 1em;
      }
      #createNewContainer:not(.show) {
        display: none;
      }
      a {
        color: var(--primary-text-color);
        text-decoration: none;
      }
      nav {
        align-items: center;
        display: flex;
        height: 3rem;
        justify-content: flex-end;
        margin-right: 20px;
      }
      nav,
      iron-icon {
        color: var(--deemphasized-text-color);
      }
      .nav-iron-icon {
        height: 1.85rem;
        margin-left: 16px;
        width: 1.85rem;
      }
      .nav-buttons:hover {
        text-decoration: underline;
        cursor: pointer;
      }
      #listOverlay {
        max-width: 90%;
        max-height: 90%;
        overflow: auto;
      }
    </style>

    <gr-overlay on-fullscreen-overlay-closed="_handleOverlayClosed" id="listOverlay" with-backdrop>
      <div id="topContainer">
        <div>
          <label>Filter:</label>
          <iron-input
              type="text"
              bind-value="{{_filter}}">
            <input
                is="iron-input"
                type="text"
                id="filter"
                bind-value="{{_filter}}">
          </iron-input>
        </div>
        <div id="createNewContainer"
            class$="[[_computeCreateClass(_createNewCapability)]]">
          <gr-button primary link id="createNew" on-click="_handleCreateClicked">
            Create New
          </gr-button>
        </div>
      </div>

      <table id="list" class="genericList">
        <tr class="headerRow">
          <th class="name topHeader">Checker Name</th>
          <th class="name topHeader">Repository</th>
          <th class="name topHeader">Status</th>
          <th class="name topHeader">Required</th>
          <th class="topHeader description">Checker Description</th>
          <th class="name topHeader"> Edit </th>
        </tr>
        <tbody id="listBody" class$="[[computeLoadingClass(_loading)]]">
          <template is="dom-repeat" items="[[_visibleCheckers]]">
            <tr class="table">
              <td class="name">
                <a>[[item.name]]</a>
              </td>
              <td class="name">[[item.repository]]</td>
              <td class="name">[[item.status]]</td>
              <td class="name">[[_computeBlocking(item)]]</td>
              <td class="description">[[item.description]]</td>
              <td on-click="_handleEditIconClicked">
                <iron-icon icon="gr-icons:edit"></iron-icon>
              </td>
            </tr>
          </template>
        </tbody>
      </table>

      <nav>
        <template is="dom-if" if="[[_showPrevButton]]">
          <a class="nav-buttons" id="prevArrow"
            on-click="_handlePrevClicked">
            <iron-icon class="nav-iron-icon" icon="gr-icons:chevron-left"></iron-icon>
          </a>
        </template>
        <template is="dom-if" if="[[_showNextButton]]">
          <a class="nav-buttons" id="nextArrow"
            on-click="_handleNextClicked">
            <iron-icon icon="gr-icons:chevron-right"></iron-icon>
          </a>
        </template>
      </nav>

      <gr-overlay id="createOverlay">
        <gr-dialog
            id="createDialog"
            confirm-label="Create"
            on-confirm="_handleCreateConfirm"
            on-cancel="_handleCreateCancel">
          <div class="header" slot="header">
            Create Checkers
          </div>
          <div slot="main">
            <gr-create-checkers-dialog
              id="createNewModal"
              plugin-rest-api="[[pluginRestApi]]">
            </gr-create-checkers-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
      <gr-overlay id="editOverlay">
        <gr-dialog
            id="editDialog"
            confirm-label="Save"
            on-confirm="_handleEditConfirm"
            on-cancel="_handleEditCancel">
          <div class="header" slot="header">
            Edit Checker
          </div>
          <div slot="main">
            <gr-create-checkers-dialog
                checker="[[checker]]"
                plugin-rest-api="[[pluginRestApi]]"
                on-cancel="_handleEditCancel"
                id="editModal">
            </gr-create-checkers-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
    </gr-overlay>
    <gr-rest-api-interface id="restAPI"></gr-rest-api-interface>
`;