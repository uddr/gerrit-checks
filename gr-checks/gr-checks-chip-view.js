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
import './gr-checks-status.js';
import {statusClass, Statuses} from './gr-checks-all-statuses.js';

const StatusPriorityOrder = [
  Statuses.FAILED,
  Statuses.SCHEDULED,
  Statuses.RUNNING,
  Statuses.SUCCESSFUL,
  Statuses.NOT_STARTED,
  Statuses.NOT_RELEVANT,
];

const HumanizedStatuses = {
  // non-terminal statuses
  NOT_STARTED: 'in progress',
  NOT_RELEVANT: 'not relevant',
  SCHEDULED: 'in progress',
  RUNNING: 'in progress',

  // terminal statuses
  SUCCESSFUL: 'successful',
  FAILED: 'failed',
};

const CHECKS_POLL_INTERVAL_MS = 60 * 1000;

const Defs = {};
/**
 * @typedef {{
 *   _number: number,
 * }}
 */
Defs.Change;
/**
 * @typedef {{
 *   _number: number,
 * }}
 */
Defs.Revision;

/**
 * @param {Array<Object>} checks
 * @returns {Object}
 */
function computeCheckStatuses(checks) {
  return checks.reduce((accum, check) => {
    accum[check.state] || (accum[check.state] = 0);
    accum[check.state]++;
    return accum;
  }, {total: checks.length});
}

/**
 * @param {Array<Object>} checks
 * @returns {string}
 */
function downgradeFailureToWarning(checks) {
  const hasFailedCheck = checks.some(
      check => {
        return check.state == Statuses.FAILED;
      }
  );
  if (!hasFailedCheck) return '';
  const hasRequiredFailedCheck = checks.some(
      check => {
        return check.state == Statuses.FAILED &&
               check.blocking && check.blocking.length > 0;
      }
  );
  return hasRequiredFailedCheck ? '' : 'set';
}

class GrChecksChipView extends Polymer.GestureEventListeners(
    Polymer.LegacyElementMixin(
        Polymer.Element)) {
  /** @returns {string} name of the component */
  static get is() { return 'gr-checks-chip-view'; }

  /** @returns {?} template for this component */
  static get template() {
    return Polymer.html`<style>

      :host {
        display: inline-block;
        cursor: pointer;
      }

      .chip {
        border-color: #D0D0D0;
        border-radius: 4px;
        border-style: solid;
        border-width: 1px;
        padding: 4px 8px;
      }
      .chip.failed {
        border-color: #DA4236;
      }
    </style>
    <template is="dom-if" if="[[_hasChecks]]">
      Checks:
      <span class$="[[_chipClasses]]">
        <gr-checks-status
          status="[[_status]]"
          downgrade-failure-to-warning="[[_downgradeFailureToWarning]]">
        </gr-checks-status>
        [[_statusString]]
      </span>
    </template>`;
  }

  /**
   * Defines properties of the component
   *
   * @returns {?}
   */
  static get properties() {
    return {
      revision: Object,
      change: Object,
      /** @type {function(number, number): !Promise<!Object>} */
      getChecks: Function,
      _checkStatuses: Object,
      _hasChecks: Boolean,
      _failedRequiredChecksCount: Number,
      _status: {type: String, computed: '_computeStatus(_checkStatuses)'},
      _statusString: {
        type: String,
        computed: '_computeStatusString(_status, _checkStatuses,' +
         '_failedRequiredChecksCount)',
      },
      _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
      // Type is set as string so that it reflects on changes
      // Polymer does not support reflecting changes in Boolean property
      _downgradeFailureToWarning: {
        type: String,
        value: '',
      },
      pollChecksInterval: Object,
      visibilityChangeListenerAdded: {
        type: Boolean,
        value: false,
      },
    };
  }

  created() {
    super.created();
    this.addEventListener('click',
        () => this.showChecksTable());
  }

  detached() {
    super.detached();
    clearInterval(this.pollChecksInterval);
    this.unlisten(document, 'visibilitychange', '_onVisibililityChange');
  }

  static get observers() {
    return [
      '_pollChecksRegularly(change, getChecks)',
    ];
  }

  showChecksTable() {
    this.dispatchEvent(
        new CustomEvent(
            'show-checks-table',
            {
              bubbles: true,
              composed: true,
              detail: {
                tab: 'change-view-tab-header-checks',
                scrollIntoView: true,
              },
            })
    );
  }

  /**
   * @param {!Defs.Change} change
   * @param {function(number, number): !Promise<!Object>} getChecks
   */
  _fetchChecks(change, getChecks) {
    if (!getChecks || !change) return;

    // change.current_revision always points to latest patchset
    getChecks(change._number, change.revisions[change.current_revision]
        ._number).then(checks => {
      this.set('_hasChecks', checks.length > 0);
      if (checks.length > 0) {
        this._downgradeFailureToWarning =
            downgradeFailureToWarning(checks);
        this._failedRequiredChecksCount =
            this.computeFailedRequiredChecksCount(checks);
        this._checkStatuses = computeCheckStatuses(checks);
      }
    }, error => {
      this.set('_hasChecks', false);
      console.error(error);
    });
  }

  _onVisibililityChange() {
    if (document.hidden) {
      clearInterval(this.pollChecksInterval);
      return;
    }
    this._pollChecksRegularly(this.change, this.getChecks);
  }

  _pollChecksRegularly(change, getChecks) {
    if (this.pollChecksInterval) {
      clearInterval(this.pollChecksInterval);
    }
    const poll = () => this._fetchChecks(change, getChecks);
    poll();
    this.pollChecksInterval = setInterval(poll, CHECKS_POLL_INTERVAL_MS);
    if (!this.visibilityChangeListenerAdded) {
      this.visibilityChangeListenerAdded = true;
      this.listen(document, 'visibilitychange', '_onVisibililityChange');
    }
  }

  /**
   * @param {!Object} checkStatuses The number of checks in each status.
   * @return {string}
   */
  _computeStatus(checkStatuses) {
    return StatusPriorityOrder.find(
        status => checkStatuses[status] > 0) ||
          Statuses.STATUS_UNKNOWN;
  }

  computeFailedRequiredChecksCount(checks) {
    const failedRequiredChecks = checks.filter(
        check => {
          return check.state == Statuses.FAILED &&
            check.blocking && check.blocking.length > 0;
        }
    );
    return failedRequiredChecks.length;
  }

  /**
   * @param {string} status The overall status of the checks.
   * @param {!Object} checkStatuses The number of checks in each status.
   * @param {number} failedRequiredChecksCount The number of failed required checks.
   * @return {string}
   */
  _computeStatusString(status, checkStatuses, failedRequiredChecksCount) {
    if (!checkStatuses) return;
    if (checkStatuses.total === 0) return 'No checks';
    let statusString = `${checkStatuses[status]} of ${
      checkStatuses.total} checks ${HumanizedStatuses[status]}`;
    if (status === Statuses.FAILED && failedRequiredChecksCount > 0) {
      statusString += ` (${failedRequiredChecksCount} required)`;
    }
    return statusString;
  }

  /**
   * @param {string} status The overall status of the checks.
   * @return {string}
   */
  _computeChipClass(status) {
    return `chip ${statusClass(status)}`;
  }
}

customElements.define(GrChecksChipView.is, GrChecksChipView);