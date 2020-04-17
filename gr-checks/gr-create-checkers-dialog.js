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
import './gr-repo-chip.js';
import {htmlTemplate} from './gr-create-checkers-dialog_html.js';

const REPOS_PER_PAGE = 6;
const CREATE_CHECKER_URL = '/plugins/checks/checkers/';
const SCHEME_PATTERN = /^[\w-_.]*$/;

class GrCreateCheckersDialog extends Polymer.GestureEventListeners(
    Polymer.LegacyElementMixin(
        Polymer.Element)) {
  /** @returns {string} name of the component */
  static get is() { return 'gr-create-checkers-dialog'; }

  /** @returns {?} template for this component */
  static get template() { return htmlTemplate; }

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  /**
   * Defines properties of the component
   *
   * @returns {?}
   */
  static get properties() {
    return {
      checker: {
        type: Object,
        observer: '_checkerChanged',
      },
      _name: String,
      _scheme: String,
      _id: String,
      _uuid: {
        type: String,
        value: '',
      },
      pluginRestApi: Object,
      _url: String,
      _description: String,
      _getRepoSuggestions: {
        type: Function,
        value() {
          return this._repoSuggestions.bind(this);
        },
      },
      // The backend might support multiple repos in the future
      // which is why I decided to keep it as an array.
      _repos: {
        type: Array,
        value: [],
        notify: true,
      },
      _repositorySelected: {
        type: Boolean,
        value: false,
      },
      _handleOnRemove: Function,
      _errorMsg: {
        type: String,
        value: '',
      },
      _statuses: {
        type: Array,
        value: [
          {
            text: 'ENABLED',
            value: 'ENABLED',
          },
          {
            text: 'DISABLED',
            value: 'DISABLED',
          },
        ],
        readOnly: true,
      },
      _required: {
        type: Boolean,
        value: false,
      },
      _status: String,
      _edit: {
        type: Boolean,
        value: false,
      },
      _query: String,
    };
  }

  static get observers() {
    return [
      '_updateUUID(_scheme, _id)',
    ];
  }

  _checkerChanged() {
    if (!this.checker) {
      console.warn('checker not set');
      return;
    }
    this._edit = true;
    this._scheme = this.checker.uuid.split(':')[0];
    this._id = this.checker.uuid.split(':')[1];
    this._name = this.checker.name;
    this._description = this.checker.description || '';
    this._url = this.checker.url || '';
    this._query = this.checker.query || '';
    this._required = this.checker.blocking &&
                        this.checker.blocking.length > 0;
    if (this.checker.repository) {
      this._repositorySelected = true;
      this.set('_repos', [{name: this.checker.repository}]);
    }
    this._status = this.checker.status;
  }

  _updateUUID(_scheme, _id) {
    this._uuid = _scheme + ':' + _id;
  }

  _handleStatusChange(e) {
    this._status = e.detail.value;
  }

  _validateRequest() {
    if (!this._name) {
      this._errorMsg = 'Name cannot be empty';
      return false;
    }
    if (this._description && this._description.length > 1000) {
      this._errorMsg = 'Description should be less than 1000 characters';
      return false;
    }
    if (!this._repositorySelected) {
      this._errorMsg = 'Select a repository';
      return false;
    }
    if (!this._scheme) {
      this._errorMsg = 'Scheme cannot be empty.';
      return false;
    }
    if (this._scheme.match(SCHEME_PATTERN) == null) {
      this._errorMsg =
          'Scheme must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
      return false;
    }
    if (this._scheme.length > 100) {
      this._errorMsg = 'Scheme must be shorter than 100 characters';
      return false;
    }
    if (!this._id) {
      this._errorMsg = 'ID cannot be empty.';
      return false;
    }
    if (this._id.match(SCHEME_PATTERN) == null) {
      this._errorMsg =
          'ID must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
      return false;
    }
    return true;
  }

  // TODO(dhruvsri): make sure dialog is scrollable.

  _createChecker(checker) {
    return this.pluginRestApi.send(
        'POST',
        CREATE_CHECKER_URL,
        checker
    );
  }

  _editChecker(checker) {
    const url = CREATE_CHECKER_URL + checker.uuid;
    return this.pluginRestApi.send(
        'POST',
        url,
        checker
    );
  }

  handleEditChecker() {
    if (!this._validateRequest()) return;
    this._editChecker(this._getCheckerRequestObject()).then(
        res => {
          if (res) {
            this._errorMsg = '';
            this.dispatchEvent(new CustomEvent('cancel',
                {
                  detail: {reload: true},
                  bubbles: true,
                  composed: true,
                }
            ));
          }
        },
        error => {
          this._errorMsg = error;
        }
    );
  }

  _getCheckerRequestObject() {
    return {
      name: this._name,
      description: this._description || '',
      uuid: this._uuid,
      repository: this._repos[0].name,
      url: this._url,
      status: this._status,
      blocking: this._required ? ['STATE_NOT_PASSING'] : [],
      query: this._query,
    };
  }

  handleCreateChecker() {
    if (!this._validateRequest()) return;
    // Currently after creating checker there is no reload happening (as
    // this would result in the user exiting the screen).
    this._createChecker(this._getCheckerRequestObject()).then(
        res => {
          if (res) this._cleanUp();
        },
        error => {
          this._errorMsg = error;
        }
    );
  }

  _cleanUp() {
    this._name = '';
    this._scheme = '';
    this._id = '';
    this._uuid = '';
    this._description = '';
    this._repos = [];
    this._repositorySelected = false;
    this._errorMsg = '';
    this._required = false;
    this._query = '';
    this._status = '';
    this.dispatchEvent(new CustomEvent('cancel',
        {
          detail: {reload: true},
          bubbles: true,
          composed: true,
        }
    ));
  }

  _repoSuggestions(filter) {
    const _makeSuggestion = repo => {
      return {name: repo.name, value: repo};
    };
    return this.pluginRestApi.getRepos(filter, REPOS_PER_PAGE).then(
        repos => repos.map(repo => _makeSuggestion(repo))
    );
  }

  _handleRepositorySelected(e) {
    this.push('_repos', e.detail.value);
    this._repositorySelected = true;
  }

  _handleRequiredCheckBoxClicked() {
    this._required = !this._required;
  }

  _handleOnRemove(e) {
    const idx = this._repos.indexOf(e.detail.repo);
    if (idx == -1) return;
    this.splice('_repos', idx, 1);
    if (this._repos.length == 0) {
      this._repositorySelected = false;
    }
  }
}

customElements.define(GrCreateCheckersDialog.is, GrCreateCheckersDialog);
