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

export class RebootFetcher {
  constructor(restApi) {
    this.restApi = restApi;
  }

  async fetch(changeNumber, patchsetNumber) {
    console.log('Issuing REBOOT http call.');
    const checks = await this.restApi.get(
        '/changes/' + changeNumber + '/revisions/' + patchsetNumber
        + '/checks?o=CHECKER');
    console.log('Returning REBOOT response.');
    return {
      responseCode: 'OK',
      runs: checks.map(convert),
    };
  }
}

/**
 * Converts a Checks Plugin CheckInfo object into a Reboot Checks API Run
 * object.
 *
 * TODO(brohlfs): Refine this conversion and add tests.
 */
function convert(check) {
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
  } else if (status === 'COMPLETED') {
    run.results = [{
      category: check.state === 'FAILED' ? 'ERROR' : 'INFO',
      summary: check.message,
    }];
    if (check.url) {
      run.results[0].links = [{
        url: check.url,
        primary: true,
        icon: 'EXTERNAL',
      }];
    }
  }
  return run;
}
