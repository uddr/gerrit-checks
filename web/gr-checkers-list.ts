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
import './gr-create-checkers-dialog';
import {css, CSSResult, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import {Checker} from './types';
import {CancelEvent, GrCreateCheckersDialog} from './gr-create-checkers-dialog';
import {value} from './util';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

const CHECKERS_PER_PAGE = 15;
const GET_CHECKERS_URL = '/plugins/checks/checkers/';

// TODO: This should be defined and exposed by @gerritcodereview/typescript-api
// export for testing
export type GrOverlay = Element & {
  open(): void;
  close(): void;
  refit(): void;
};

function matches(target: string, keyword: string) {
  return target.toLowerCase().includes(keyword.toLowerCase().trim());
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checkers-list': GrCheckersList;
  }
}

/**
 * Show a list of all checkers along with creating/editing them
 */
@customElement('gr-checkers-list')
export class GrCheckersList extends LitElement {
  @query('#editModal')
  editModal?: GrCreateCheckersDialog;

  @query('#editOverlay')
  editOverlay?: GrOverlay;

  @query('#createNewModal')
  createNewModal?: GrCreateCheckersDialog;

  @query('#createOverlay')
  createOverlay?: GrOverlay;

  /** Guaranteed to be provided by the plugin endpoint. */
  @property()
  plugin!: PluginApi;

  @state()
  pluginRestApi?: RestPluginApi;

  // Checker that will be passed to the editOverlay modal
  @state()
  checker?: Checker;

  @state()
  checkers: Checker[] = [];

  @state()
  loading = true;

  @state()
  filter = '';

  @state()
  startingIndex = 0;

  override connectedCallback() {
    super.connectedCallback();
    this.pluginRestApi = this.plugin.restApi();
    this.loadCheckers();
  }

  static styles = [
    window.Gerrit.styles.table as CSSResult,
    css`
      #container {
        width: 80vw;
        height: 80vh;
        overflow: auto;
      }
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
      table td.name {
        white-space: nowrap;
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
    `,
  ];

  override render() {
    return html`
      <div id="container">
        <div id="topContainer">
          <div>
            <label>Filter:</label>
            <input
              .value="${this.filter}"
              @input="${(e: InputEvent) => {
                this.filter = value(e);
                this.startingIndex = 0;
              }}"
            />
          </div>
          ${this.renderCreateNewButton()}
        </div>
        <table id="list" class="genericList">
          <tr class="headerRow">
            <th class="name topHeader">Checker Name</th>
            <th class="name topHeader">Repository</th>
            <th class="name topHeader">Status</th>
            <th class="name topHeader">Required</th>
            <th class="topHeader description">Checker Description</th>
            <th class="name topHeader">Edit</th>
          </tr>
          <tbody id="listBody" class="${this.loading ? 'loading' : ''}">
            ${this.getVisibleCheckers().map(c => this.renderCheckerRow(c))}
          </tbody>
        </table>
        <nav>${this.renderPrevButton()}${this.renderNextButton()}</nav>
      </div>
      <gr-overlay id="createOverlay">
        <gr-dialog
          .confirmLabel="Create"
          @confirm="${() => this.createNewModal?.handleCreateChecker()}"
          @cancel="${this.handleCreateCancel}"
        >
          <div class="header" slot="header">Create Checkers</div>
          <div slot="main">
            <gr-create-checkers-dialog
              id="createNewModal"
              .pluginRestApi="${this.pluginRestApi}"
              @cancel="${this.handleEditCancel}"
            >
            </gr-create-checkers-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
      <gr-overlay id="editOverlay">
        <gr-dialog
          .confirmLabel="Save"
          @confirm="${() => this.editModal?.handleEditChecker()}"
          @cancel="${this.handleEditCancel}"
        >
          <div class="header" slot="header">Edit Checker</div>
          <div slot="main">
            <gr-create-checkers-dialog
              id="editModal"
              .checker="${this.checker}"
              .pluginRestApi="${this.pluginRestApi}"
              @cancel="${this.handleEditCancel}"
            >
            </gr-create-checkers-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  private renderCreateNewButton() {
    return html`
      <div id="createNewContainer">
        <gr-button primary link @click="${() => this.createOverlay?.open()}">
          Create New
        </gr-button>
      </div>
    `;
  }

  private renderPrevButton() {
    if (this.startingIndex < CHECKERS_PER_PAGE) return;
    const handleClick = () => {
      if (this.startingIndex >= CHECKERS_PER_PAGE) {
        this.startingIndex -= CHECKERS_PER_PAGE;
      }
    };
    return html`
      <a class="nav-buttons" @click="${handleClick}">
        <iron-icon
          class="nav-iron-icon"
          icon="gr-icons:chevron-left"
        ></iron-icon>
      </a>
    `;
  }

  private renderNextButton() {
    const checkerCount = this.getFilteredCheckers().length;
    if (this.startingIndex + CHECKERS_PER_PAGE >= checkerCount) return;
    const handleClick = () => {
      if (this.startingIndex + CHECKERS_PER_PAGE < checkerCount) {
        this.startingIndex += CHECKERS_PER_PAGE;
      }
    };
    return html`
      <a class="nav-buttons" @click="${handleClick}">
        <iron-icon
          class="nav-iron-icon"
          icon="gr-icons:chevron-right"
        ></iron-icon>
      </a>
    `;
  }

  private renderCheckerRow(checker: Checker) {
    const edit = () => {
      this.checker = checker;
      this.editOverlay?.open();
    };
    return html`
      <tr class="table">
        <td class="name">
          <a>${checker.name}</a>
        </td>
        <td class="name">${checker.repository}</td>
        <td class="name">${checker.status}</td>
        <td class="name">${checker?.blocking?.length ? 'YES' : 'NO'}</td>
        <td class="description">${checker.description}</td>
        <td @click="${edit}">
          <iron-icon icon="gr-icons:edit"></iron-icon>
        </td>
      </tr>
    `;
  }

  private getFilteredCheckers(): Checker[] {
    return (this.checkers ?? []).filter(
      checker =>
        matches(checker.name, this.filter) ||
        matches(checker.repository ?? '', this.filter)
    );
  }

  private getVisibleCheckers(): Checker[] {
    return this.getFilteredCheckers().slice(
      this.startingIndex,
      this.startingIndex + CHECKERS_PER_PAGE
    );
  }

  private loadCheckers() {
    this.loading = true;
    this.checkers = [];
    if (!this.pluginRestApi) throw new Error('pluginRestApi undefined');
    this.pluginRestApi.get<Checker[]>(GET_CHECKERS_URL).then(checkers => {
      if (!checkers) return;
      this.checkers = checkers;
      this.startingIndex = 0;
      this.loading = false;
    });
  }

  private handleEditCancel(e: CancelEvent) {
    if (e.detail?.reload) this.loadCheckers();
    this.editOverlay?.close();
  }

  private handleCreateCancel(e: CancelEvent) {
    if (e.detail?.reload) this.loadCheckers();
    this.createOverlay?.close();
  }
}
