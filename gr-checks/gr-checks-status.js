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
import {htmlTemplate} from './gr-checks-status_html.js';
import {isUnevaluated, isInProgress, isRunning, isScheduled, isSuccessful, isFailed} from './gr-checks-all-statuses.js';

class GrChecksStatus extends Polymer.GestureEventListeners(
    Polymer.LegacyElementMixin(
        Polymer.Element)) {
  /** @returns {string} name of the component */
  static get is() { return 'gr-checks-status'; }

  /** @returns {?} template for this component */
  static get template() { return htmlTemplate; }

  /**
   * Defines properties of the component
   *
   * @returns {?}
   */
  static get properties() {
    return {
      showText: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      status: String,
      downgradeFailureToWarning: String,
    };
  }

  _isUnevaluated(status) {
    return isUnevaluated(status);
  }

  _isInProgress(status) {
    return isInProgress(status);
  }

  _isRunning(status) {
    return isRunning(status);
  }

  _isScheduled(status) {
    return isScheduled(status);
  }

  _isSuccessful(status) {
    return isSuccessful(status);
  }

  _isFailed(status) {
    return !this.downgradeFailureToWarning &&
        isFailed(status);
  }

  _isWarning(status) {
    return this.downgradeFailureToWarning &&
        isFailed(status);
  }
}

customElements.define(GrChecksStatus.is, GrChecksStatus);