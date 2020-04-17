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
class GrChecksChangeListHeaderView extends Polymer.GestureEventListeners(
    Polymer.LegacyElementMixin(
        Polymer.Element)) {
  /** @returns {string} name of the component */
  static get is() { return 'gr-checks-change-list-header-view'; }

  /** @returns {?} template for this component */
  static get template() {
    return Polymer.html`
      <style>
        :host {
          display: table-cell;
          padding: 0 3px;
        }
      </style>
      Checks
    `;
  }
}

customElements.define(GrChecksChangeListHeaderView.is,
    GrChecksChangeListHeaderView);