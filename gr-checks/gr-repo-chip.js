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
/**
 * autocomplete chip for getting repository suggestions
 */
class GrRepoChip extends Polymer.GestureEventListeners(
    Polymer.LegacyElementMixin(
        Polymer.Element)) {
  /** @returns {string} name of the component */
  static get is() { return 'gr-repo-chip'; }

  /** @returns {?} template for this component */
  static get template() {
    return Polymer.html`
    <style>
      iron-icon {
        height: 1.2rem;
        width: 1.2rem;
      }
      :host {
        display: inline-block;
      }
    </style>
    <span> {{repo.name}} </span>
    <gr-button
      id="remove"
      link
      hidden$="[[!removable]]"
      tabindex="-1"
      aria-label="Remove"
      class="remove"
      on-click="_handleRemove">
      <iron-icon icon="gr-icons:close"></iron-icon>
    </gr-button>`;
  }

  /**
   * Defines properties of the component
   *
   * @returns {?}
   */
  static get properties() {
    return {
      // repo type is ProjectInfo
      repo: Object,
      removable: {
        type: Boolean,
        value: true,
      },
    };
  }

  /**
   * @param {Event} e
   */
  _handleRemove(e) {
    e.preventDefault();
    this.dispatchEvent(new CustomEvent('remove',
        {
          detail: {repo: this.repo},
          bubbles: true,
          composed: true,
        }));
  }
}

customElements.define(GrRepoChip.is, GrRepoChip);