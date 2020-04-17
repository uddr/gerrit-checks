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

/** All statuses */
export const Statuses = {
  // non-terminal statuses
  NOT_STARTED: 'NOT_STARTED',
  SCHEDULED: 'SCHEDULED',
  RUNNING: 'RUNNING',

  // terminal statuses
  SUCCESSFUL: 'SUCCESSFUL',
  FAILED: 'FAILED',
  NOT_RELEVANT: 'NOT_RELEVANT',

  STATUS_UNKNOWN: 'UNKNOWN',
};

/**
 * @param {string} status
 * @param {Array<string>} includedStatuses
 * @returns {boolean}
 */
function isStatus(status, includedStatuses) {
  return includedStatuses.includes(status);
}

/**
 * @param {string} status
 * @returns {boolean} if status is Unevaluated.
 */
export function isUnevaluated(status) {
  return isStatus(status, [Statuses.NOT_STARTED, Statuses.NOT_RELEVANT]);
}

/**
 * @param {string} status
 * @returns {boolean} if status is Scheduled.
 */
export function isScheduled(status) {
  return isStatus(status, [Statuses.SCHEDULED]);
}

/**
 * @param {string} status
 * @returns {boolean} if status is Running. */
export function isRunning(status) {
  return isStatus(status, [Statuses.RUNNING]);
}

/**
 * @param {string} status
 * @returns {boolean} if status is InProgress.
 */
export function isInProgress(status) {
  return isStatus(status, [Statuses.SCHEDULED, Statuses.RUNNING]);
}

/**
 * @param {string} status
 * @returns {boolean} if status is Successful.
 */
export function isSuccessful(status) {
  return isStatus(status, [Statuses.SUCCESSFUL]);
}

/**
 * @param {string} status
 * @returns {boolean} if status is Failed.
 */
export function isFailed(status) {
  return isStatus(status, [Statuses.FAILED]);
}

/**
 * @param {string} status
 * @returns {string} class of given status.
 */
export function statusClass(status) {
  if (isUnevaluated(status)) {
    return 'unevaluated';
  }
  if (isInProgress(status)) {
    return 'in-progress';
  }
  if (isSuccessful(status)) {
    return 'successful';
  }
  if (isFailed(status)) {
    return 'failed';
  }
  return 'unevaluated';
}