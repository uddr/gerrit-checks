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

const Defs = {};
/**
 * @typedef {{
 *   project: string,
 *   change_number: number,
 *   patch_set_id: number,
 *   checker_uuid: string,
 *   state: string,
 *   url: string,
 *   started: string,
 *   finished: string,
 *   created: string,
 *   updated: string,
 *   checker_name: string,
 *   checker_status: string,
 *   blocking: Array<Object>,
 * }}
 */
Defs.Check;

class GrChecksItem extends Polymer.GestureEventListeners(
    Polymer.LegacyElementMixin(
        Polymer.Element)) {
  /** @returns {string} name of the component */
  static get is() { return 'gr-checks-item'; }

  /** @returns {?} template for this component */
  static get template() {
    return Polymer.html`
          <style>
      :host {
        border-top: 1px solid var(--border-color);
      }

      td:first-child {
        padding-left: 1rem;
      }

      td {
        padding: 1px 32px 1px 0;
        white-space: nowrap;
      }

      a.log {
        margin-right: 16px;
        display: inline-block;
      }
      .nav-icon {
        cursor: pointer;
      }
      .duration {
        text-align: center;
      }
    </style>
    <td>
      <template is="dom-if" if="[[check.message]]">
          <iron-icon
            class="nav-icon expand-message"
            on-click="_toggleMessageShown"
            icon="[[_computeExpandIcon(showCheckMessage)]]">
          </iron-icon>
      </template>
    </td>
    <td>[[check.checker_name]]</td>
    <td>[[_requiredForMerge]]</td>
    <td>
      <gr-checks-status
        show-text
        status="[[check.state]]"
        downgrade-failure-to-warning="[[false]]">
      </gr-checks-status>
    </td>
    <td>
      <gr-date-formatter
        has-tooltip
        show-date-and-time
        date-str="[[_startTime]]">
      </gr-date-formatter>
    </td>
    <td class="duration">[[_duration]]</td>
    <td>
      <a href$="[[check.url]]" target="_blank" class="log">
        <gr-button link no-uppercase disabled="[[!check.url]]">
          Details
        </gr-button>
      </a>
    </td>
    <td>
      <gr-button
        link
        no-uppercase
        on-click="_handleReRunClicked">
        Re-run
      </gr-button>
    </td>
    <td>[[check.checker_description]]</td>
      `;
  }

  /**
   * Defines properties of the component
   *
   * @returns {?}
   */
  static get properties() {
    return {
      /** @type {Defs.Check} */
      check: Object,
      /** @type {function(string): !Promise<!Object>} */
      _startTime: {
        type: String,
        computed: '_computeStartTime(check)',
      },
      _duration: {
        type: String,
        computed: '_computeDuration(check)',
      },
      _requiredForMerge: {
        type: String,
        computed: '_computeRequiredForMerge(check)',
      },
      showCheckMessage: {
        type: Boolean,
        value: false,
      },
    };
  }

  /**
   * Fired when the retry check button is pressed.
   *
   * @event retry-check
   */

  /**
   * @param {!Defs.Check} check
   * @return {string}
   */
  _computeStartTime(check) {
    if (!check.started) return '-';
    return check.started;
  }

  _toggleMessageShown() {
    this.showCheckMessage = !this.showCheckMessage;
    this.dispatchEvent(new CustomEvent('toggle-check-message',
        {
          detail: {uuid: this.check.checker_uuid},
          bubbles: true,
          composed: true,
        }));
  }

  _computeExpandIcon(showCheckMessage) {
    return showCheckMessage ? 'gr-icons:expand-less': 'gr-icons:expand-more';
  }

  /**
   * @param {!Defs.Check} check
   * @return {string}
   */
  _computeDuration(check) {
    if (!check.started || !check.finished) {
      return '-';
    }
    const startTime = new Date(check.started);
    const finishTime = check.finished ? new Date(check.finished) : new Date();
    return generateDurationString(startTime, finishTime);
  }

  /**
   * @param {!Defs.Check} check
   * @return {string}
   */
  _computeRequiredForMerge(check) {
    return (check.blocking && check.blocking.length === 0) ? 'Optional' :
      'Required';
  }

  _handleReRunClicked() {
    this.dispatchEvent(new CustomEvent('retry-check',
        {
          detail: {uuid: this.check.checker_uuid},
          bubbles: false,
          composed: true,
        }));
  }
}

customElements.define(GrChecksItem.is, GrChecksItem);

const ZERO_SECONDS = '0 sec';

/**
 * @param {Date} startTime
 * @param {Date} endTime
 * @return {string}
 */
function generateDurationString(startTime, endTime) {
  const secondsAgo = Math.round((endTime - startTime) / 1000);

  if (secondsAgo === 0) {
    return ZERO_SECONDS;
  }

  const durationSegments = [];
  if (secondsAgo % 60 !== 0) {
    durationSegments.push(`${secondsAgo % 60} sec`);
  }
  const minutesAgo = Math.floor(secondsAgo / 60);
  if (minutesAgo % 60 !== 0) {
    durationSegments.push(`${minutesAgo % 60} min`);
  }
  const hoursAgo = Math.floor(minutesAgo / 60);
  if (hoursAgo % 24 !== 0) {
    const hours = pluralize(hoursAgo % 24, 'hour', 'hours');
    durationSegments.push(`${hoursAgo % 24} ${hours}`);
  }
  const daysAgo = Math.floor(hoursAgo / 24);
  if (daysAgo % 30 !== 0) {
    const days = pluralize(daysAgo % 30, 'day', 'days');
    durationSegments.push(`${daysAgo % 30} ${days}`);
  }
  const monthsAgo = Math.floor(daysAgo / 30);
  if (monthsAgo > 0) {
    const months = pluralize(monthsAgo, 'month', 'months');
    durationSegments.push(`${monthsAgo} ${months}`);
  }
  return durationSegments.reverse().slice(0, 2).join(' ');
}

/**
 * @param {number} unit
 * @param {string} singular
 * @param {string} plural
 * @return {string}
 */
function pluralize(unit, singular, plural) {
  return unit === 1 ? singular : plural;
}
