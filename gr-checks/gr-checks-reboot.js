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
 * Heads up! Everything in this file is still in flux. The new reboot checks API
 * is still in development. So everything in this file can change. And it is
 * expected that the amount of comments and tests is limited for the time being.
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

function computeDuration(check) {
  if (!check.started || !check.finished) {
    return '-';
  }
  const startTime = new Date(check.started);
  const finishTime = check.finished ? new Date(check.finished) : new Date();
  return generateDurationString(startTime, finishTime);
}

export class RebootFetcher {
  constructor(restApi) {
    this.restApi = restApi;
  }

  async fetchCurrent() {
    return this.fetch(this.changeNumber, this.patchsetNumber);
  }

  async fetch(changeData) {
    const {changeNumber, patchsetNumber} = changeData;
    this.changeNumber = changeNumber;
    this.patchsetNumber = patchsetNumber;
    const checks = await this.apiGet('?o=CHECKER');
    return {
      responseCode: 'OK',
      runs: checks.map(check => this.convert(check)),
    };
  }

  async apiGet(suffix) {
    return this.restApi.get(
        '/changes/' + this.changeNumber + '/revisions/' + this.patchsetNumber
        + '/checks' + suffix);
  }

  async apiPost(suffix) {
    return this.restApi.post(
        '/changes/' + this.changeNumber + '/revisions/' + this.patchsetNumber
        + '/checks' + suffix);
  }

  /**
   * Converts a Checks Plugin CheckInfo object into a Reboot Checks API Run
   * object.
   *
   * TODO(brohlfs): Refine this conversion and add tests.
   */
  convert(check) {
    let status = 'RUNNABLE';
    if (check.state === 'RUNNING' || check.state === 'SCHEDULED') {
      status = 'RUNNING';
    } else if (check.state === 'FAILED' || check.state === 'SUCCESSFUL') {
      status = 'COMPLETED';
    }
    const run = {
      checkName: check.checker_name,
      checkDescription: check.checker_description,
      externalId: check.checker_uuid,
      status,
    };
    if (check.started) run.startedTimestamp = new Date(check.started);
    if (check.finished) run.finishedTimestamp = new Date(check.finished);
    if (status === 'RUNNING') {
      run.statusDescription = check.message;
    } else if (check.state === 'SUCCESSFUL') {
      run.statusDescription =
          check.message || `Passed (${computeDuration(check)})`;
      if (check.url) {
        run.statusLink = check.url;
      }
    } else if (check.state === 'FAILED') {
      run.results = [{
        category: 'ERROR',
        summary: check.message || `Failed (${computeDuration(check)})`,
      }];
      if (check.url) {
        run.results[0].links = [{
          url: check.url,
          primary: true,
          icon: 'EXTERNAL',
        }];
      }
    }
    if (status !== 'RUNNING') {
      run.actions = [{
        name: 'Run',
        primary: true,
        callback: () => this.run(check.checker_uuid),
      }];
    }
    return run;
  }

  run(uuid) {
    return this.apiPost('/' + uuid + '/rerun')
        .catch(e => {
          return {errorMessage: `Triggering the run failed: ${e.message}`};
        });
  }
}
