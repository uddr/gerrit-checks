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

  async fetchCurrent() {
    return this.fetch(this.changeNumber, this.patchsetNumber);
  }

  async fetch(changeData) {
    const {changeNumber, patchsetNumber} = changeData;
    this.changeNumber = changeNumber;
    this.patchsetNumber = patchsetNumber;
    console.log('Issuing REBOOT http call.');
    const checks = await this.apiGet('?o=CHECKER');
    console.log('Returning REBOOT response.');
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
      run.statusDescription = check.message;
      if (check.url) {
        run.statusLink = check.url;
      }
    } else if (check.state === 'FAILED') {
      run.results = [{
        category: 'ERROR',
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
    if (status !== 'RUNNING') {
      run.actions = [{
        name: "Run",
        primary: true,
        callback: () => this.run(check.checker_uuid),
      }];
    }
    return run;
  }

  run(uuid) {
    this.apiPost('/' + uuid + '/rerun')
        .then(() => this.fetchCurrent().then(() => {}))
        .catch(e => {errorMessage: e.message});
  }
}
