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
      <style>
        :host {
          display: inline-block;
        }
        i {
          border-radius: 50%;
          color: white;
          display: inline-block;
          font-style: normal;
          height: 16px;
          margin-right: 4px;
          text-align: center;
          width: 16px;
        }
        svg {
          display: inline-block;
          vertical-align: middle;
        }
      </style>
    <span>
      <template is="dom-if" if="[[_isUnevaluated(status)]]">
        <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fill-rule="evenodd">
            <path d="M0 0h18v18H0z"/>
            <path d="M9 11.8a2.8 2.8 0 1 1 0-5.6 2.8 2.8 0 0 1 0 5.6M9 2a7 7 0 1 0 0 14A7 7 0 0 0 9 2" fill="#9E9E9E"/>
          </g>
        </svg>
        <template is="dom-if" if="[[showText]]">
          <span>
            Unevaluated
          </span>
        </template>
      </template>
      <template is="dom-if" if="[[_isScheduled(status)]]">
        <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fill-rule="evenodd">
            <path d="M12.184 9.293c-.86 0-1.641-.39-2.15-.976L9.8 9.489l.82.78V13.2h-.78v-2.344l-.821-.781-.39 1.719-2.736-.547.157-.782 1.914.391.625-3.164-.703.273v1.328h-.782V7.457l2.032-.86c.117 0 .196-.039.313-.039.273 0 .508.157.664.391l.39.625c.313.547.939.938 1.68.938v.781zM10.034 4.8c.43 0 .782.352.782.782 0 .43-.352.781-.781.781a.783.783 0 0 1-.782-.781c0-.43.352-.782.782-.782zM9 2a7 7 0 1 0 .002 14.002A7 7 0 0 0 9 2z" fill="#9C27B0"/>
            <path d="M0 0h18v18H0z"/>
          </g>
        </svg>
        <template is="dom-if" if="[[showText]]">
          <span>
            Scheduled
          </span>
        </template>
      </template>
      <template is="dom-if" if="[[_isRunning(status)]]">
        <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fill-rule="evenodd">
            <path d="M12.184 9.293c-.86 0-1.641-.39-2.15-.976L9.8 9.489l.82.78V13.2h-.78v-2.344l-.821-.781-.39 1.719-2.736-.547.157-.782 1.914.391.625-3.164-.703.273v1.328h-.782V7.457l2.032-.86c.117 0 .196-.039.313-.039.273 0 .508.157.664.391l.39.625c.313.547.939.938 1.68.938v.781zM10.034 4.8c.43 0 .782.352.782.782 0 .43-.352.781-.781.781a.783.783 0 0 1-.782-.781c0-.43.352-.782.782-.782zM9 2a7 7 0 1 0 .002 14.002A7 7 0 0 0 9 2z" fill="#9C27B0"/>
            <path d="M0 0h18v18H0z"/>
          </g>
        </svg>
        <template is="dom-if" if="[[showText]]">
          <span>
            Running
          </span>
        </template>
      </template>
      <template is="dom-if" if="[[_isSuccessful(status)]]">
        <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fill-rule="evenodd">
            <path d="M9 2C5.136 2 2 5.136 2 9s3.136 7 7 7 7-3.136 7-7-3.136-7-7-7zM7.6 12.5L4.1 9l.987-.987L7.6 10.519l5.313-5.313.987.994-6.3 6.3z" fill="#00C752"/>
            <path d="M0 0h18v18H0z"/>
          </g>
        </svg>
        <template is="dom-if" if="[[showText]]">
          <span>
            Successful
          </span>
        </template>
      </template>
      <template is="dom-if" if="[[_isFailed(status)]]">
        <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fill-rule="evenodd">
            <path d="M9 2a7 7 0 1 0 0 14A7 7 0 0 0 9 2zm-1 8V5h2v5H8zm0 3v-2h2v2H8z" fill="#DA4236"/>
            <path d="M0 0h18v18H0z"/>
          </g>
        </svg>
        <template is="dom-if" if="[[showText]]">
          <span>
            Failed
          </span>
        </template>
      </template>
      <template is="dom-if" if="[[_isWarning(status)]]">
        <svg width="18" height="18" xmlns="http://www.w3.org/2000/svg">
          <g fill="none" fill-rule="evenodd">
            <g transform="translate(2 2)">
              <circle fill="#F29900" cx="6.921" cy="6.921" r="6.921"/>
              <path d="M6.92 1.3l4.686 8.2H2.235L6.92 1.3zm-.584 5.271h1.171V3.643H6.336V6.57zm0 2.258h1.171V7.657H6.336V8.83z" fill="#F8F9FA"/>
            </g>
            <path d="M0 0h18v18H0z"/>
          </g>
        </svg>
        <template is="dom-if" if="[[showText]]">
          <span>
            Failed
          </span>
        </template>
      </template>
    </span>
`;