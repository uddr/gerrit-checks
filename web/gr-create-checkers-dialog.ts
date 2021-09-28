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
import './gr-repo-chip';
import {customElement, property, state} from 'lit/decorators';
import {css, CSSResult, html, LitElement, PropertyValues} from 'lit';
import {HttpMethod, RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {Checker} from './types';
import {checked, fire, value} from './util';

const REPOS_PER_PAGE = 6;
const CREATE_CHECKER_URL = '/plugins/checks/checkers/';
const SCHEME_PATTERN = /^[\w-_.]*$/;

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-checkers-dialog': GrCreateCheckersDialog;
  }
}

export type CancelEvent = CustomEvent<CancelEventDetail>;

export interface CancelEventDetail {
  reload: boolean;
}

enum Status {
  ENABLED = 'ENABLED',
  DISABLED = 'DISABLED',
}

const statuses = [
  {
    text: Status.ENABLED,
    value: Status.ENABLED,
  },
  {
    text: Status.DISABLED,
    value: Status.DISABLED,
  },
];

@customElement('gr-create-checkers-dialog')
export class GrCreateCheckersDialog extends LitElement {
  @property()
  checker?: Checker;

  @property()
  pluginRestApi!: RestPluginApi;

  @state()
  name = '';

  @state()
  scheme = '';

  @state()
  checkerId = '';

  @state()
  url = '';

  @state()
  description = '';

  @state()
  repo = '';

  @state()
  errorMsg = '';

  @state()
  required = false;

  @state()
  query = '';

  @state()
  status = Status.ENABLED;

  /** True when the checker prop is set. */
  @state()
  editing = false;

  override connectedCallback() {
    super.connectedCallback();
    this.initCheckerValues();
  }

  override update(changedProperties: PropertyValues) {
    if (changedProperties.has('checker')) {
      this.initCheckerValues();
    }
    super.update(changedProperties);
  }

  static override styles = [
    window.Gerrit.styles.form as CSSResult,
    css`
      :host {
        display: inline-block;
      }
      input {
        width: 20em;
      }
      input[disabled] {
        background-color: var(--table-subheader-background-color);
      }
      gr-autocomplete {
        border: none;
        --gr-autocomplete: {
          border: 1px solid var(--border-color);
          border-radius: 2px;
          font-size: var(--font-size-normal);
          height: 2em;
          padding: 0 0.15em;
          width: 20em;
        }
      }
      .error {
        color: red;
      }
    `,
  ];

  override render() {
    return html`
      <div class="gr-form-styles">
        <section ?hidden="${this.errorMsg !== ''}">
          <span class="error">${this.errorMsg}</span>
        </section>
        <section>
          <span class="title">Name*</span>
          <input
            .value="${this.name}"
            @input="${(e: InputEvent) => (this.name = value(e))}"
            autocomplete="on"
          />
        </section>
        <section>
          <span class="title">Description</span>
          <input
            .value="${this.description}"
            @input="${(e: InputEvent) => (this.description = value(e))}"
            autocomplete="on"
          />
        </section>
        <section>
          <span class="title">Repository*</span>
          <div ?hidden="${this.repo === ''}">
            <gr-repo-chip
              .repo="${this.repo}"
              @remove="${() => (this.repo = '')}"
              tabindex="-1"
            ></gr-repo-chip>
          </div>
          <div ?hidden="${this.repo !== ''}">
            <gr-autocomplete
              .query="${(input: string) => this.repoSuggestions(input)}"
              @commit="${(e: CustomEvent<{value: string}>) => {
                this.repo = e.detail.value;
              }}"
              clearOnCommit
              warnUncommitted
            >
            </gr-autocomplete>
          </div>
        </section>

        <section>
          <span class="title">Scheme*</span>
          <input
            .value="${this.scheme}"
            @input="${(e: InputEvent) => (this.scheme = value(e))}"
            ?disabled="${this.editing}"
            autocomplete="on"
          />
        </section>

        <section>
          <span class="title">ID*</span>
          <input
            .value="${this.checkerId}"
            @input="${(e: InputEvent) => (this.checkerId = value(e))}"
            ?disabled="${this.editing}"
            autocomplete="on"
          />
        </section>

        <section>
          <span class="title">Url</span>
          <input
            .value="${this.url}"
            @input="${(e: InputEvent) => (this.url = value(e))}"
            autocomplete="on"
          />
        </section>

        <section>
          <span class="title">UUID</span>
          <span class="title uuid">${this.getUuid()}</span>
        </section>

        <section>
          <span class="title">Status</span>
          <gr-dropdown-list
            text="Status"
            .items="${statuses}"
            .value="${this.status}"
            @value-change="${(e: CustomEvent<{value: Status}>) =>
              (this.status = e.detail.value)}"
          >
          </gr-dropdown-list>
        </section>

        <section>
          <span class="title">Required</span>
          <input
            type="checkbox"
            ?checked="${this.required}"
            @input="${(e: InputEvent) => (this.required = checked(e))}"
          />
        </section>

        <section>
          <span class="title">Query</span>
          <input
            .value="${this.query}"
            @input="${(e: InputEvent) => (this.query = value(e))}"
            autocomplete="on"
          />
        </section>
      </div>
    `;
  }

  initCheckerValues() {
    if (!this.checker) return;
    // If a checker was set by the parent component, then that means that we are
    // in editing mode of an existing checker.
    this.editing = true;
    this.scheme = this.checker.uuid.split(':')[0];
    this.checkerId = this.checker.uuid.split(':')[1];
    this.name = this.checker.name ?? '';
    this.description = this.checker.description ?? '';
    this.url = this.checker.url ?? '';
    this.query = this.checker.query ?? '';
    this.required = !!this.checker.blocking?.length;
    this.repo = this.checker.repository ?? '';
    this.status = (this.checker.status as Status) ?? Status.ENABLED;
  }

  cleanUp() {
    this.name = '';
    this.scheme = '';
    this.checkerId = '';
    this.url = '';
    this.description = '';
    this.repo = '';
    this.errorMsg = '';
    this.required = false;
    this.status = Status.ENABLED;
    this.editing = false;
    this.query = '';
    fire(this, 'cancel', {reload: true});
  }

  getUuid(): string {
    return `${this.scheme}:${this.checkerId}`;
  }

  getCheckerRequestObject(): Checker {
    return {
      name: this.name ?? '',
      description: this.description ?? '',
      uuid: this.getUuid(),
      repository: this.repo,
      url: this.url,
      status: this.status,
      blocking: this.required ? ['STATE_NOT_PASSING'] : [],
      query: this.query,
    };
  }

  validateRequest() {
    if (!this.name) {
      this.errorMsg = 'Name cannot be empty';
      return false;
    }
    if (this.description.length > 1000) {
      this.errorMsg = 'Description should be less than 1000 characters';
      return false;
    }
    if (!this.repo) {
      this.errorMsg = 'Select a repository';
      return false;
    }
    if (!this.scheme) {
      this.errorMsg = 'Scheme cannot be empty.';
      return false;
    }
    if (this.scheme.match(SCHEME_PATTERN) === null) {
      this.errorMsg =
        'Scheme must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
      return false;
    }
    if (this.scheme.length > 100) {
      this.errorMsg = 'Scheme must be shorter than 100 characters';
      return false;
    }
    if (!this.checkerId) {
      this.errorMsg = 'ID cannot be empty.';
      return false;
    }
    if (this.checkerId.match(SCHEME_PATTERN) === null) {
      this.errorMsg =
        'ID must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
      return false;
    }
    return true;
  }

  handleEditChecker() {
    if (!this.validateRequest()) return;
    this.pluginRestApi
      .send(
        HttpMethod.POST,
        CREATE_CHECKER_URL + this.getCheckerRequestObject().uuid,
        this.getCheckerRequestObject()
      )
      .then(
        res => {
          if (res) {
            this.errorMsg = '';
            fire<CancelEventDetail>(this, 'cancel', {reload: true});
          }
        },
        error => {
          this.errorMsg = error;
        }
      );
  }

  handleCreateChecker() {
    if (!this.validateRequest()) return;
    // Currently after creating checker there is no reload happening (as
    // this would result in the user exiting the screen).
    this.pluginRestApi
      .send(HttpMethod.POST, CREATE_CHECKER_URL, this.getCheckerRequestObject())
      .then(
        res => {
          if (res) this.cleanUp();
        },
        error => {
          this.errorMsg = error;
        }
      );
  }

  repoSuggestions(filter: string) {
    return this.pluginRestApi.getRepos(filter, REPOS_PER_PAGE).then(repos =>
      ((repos ?? []) as {name: string}[]).map(repo => {
        return {name: repo.name, value: repo.name};
      })
    );
  }
}
