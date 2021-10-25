/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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
import './gr-checkers-list';
import {computeDuration} from './util';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {
  Category,
  ChangeData,
  CheckRun,
  ChecksProvider,
  LinkIcon,
  ResponseCode,
  RunStatus,
} from '@gerritcodereview/typescript-api/checks';
import {Check} from './types';

function convertStatus(check: Check): RunStatus {
  if (check.state === 'RUNNING' || check.state === 'SCHEDULED') {
    return RunStatus.RUNNING;
  }
  if (check.state === 'FAILED' || check.state === 'SUCCESSFUL') {
    return RunStatus.COMPLETED;
  }
  return RunStatus.RUNNABLE;
}

export class ChecksFetcher implements ChecksProvider {
  private restApi: RestPluginApi;

  private changeNumber?: number;

  private patchsetNumber?: number;

  constructor(private readonly plugin: PluginApi) {
    this.restApi = plugin.restApi();
  }

  async fetch(changeData: ChangeData) {
    const {changeNumber, patchsetNumber} = changeData;
    this.changeNumber = changeNumber;
    this.patchsetNumber = patchsetNumber;
    const checks = await this.apiGet('?o=CHECKER');
    return {
      responseCode: ResponseCode.OK,
      actions: [
        {
          name: 'Configure Checkers',
          primary: true,
          callback: () => {
            this.plugin.popup('gr-checkers-list');
            return undefined;
          },
        },
      ],
      runs: checks.map(check => this.convert(check)),
    };
  }

  async apiGet(suffix: string) {
    return await this.restApi.get<Check[]>(
      `/changes/${this.changeNumber}/revisions/${this.patchsetNumber}/checks${suffix}`
    );
  }

  async apiPost(suffix: string) {
    return await this.restApi.post(
      `/changes/${this.changeNumber}/revisions/${this.patchsetNumber}/checks${suffix}`
    );
  }

  convertToLabelName(checkerName: string) {
    if (checkerName === 'Code Style') {
      return 'Code-Style';
    }
    if (
      checkerName === 'PolyGerrit UI Tests' ||
      checkerName === 'Build/Tests' ||
      checkerName === 'RBE Build/Tests'
    ) {
      return 'Verified';
    }
    return checkerName;
  }

  /**
   * Converts a Checks Plugin CheckInfo object into a Checks API Run object.
   */
  convert(check: Check): CheckRun {
    const status = convertStatus(check);
    const run: CheckRun = {
      checkName: check.checker_name,
      checkDescription: check.checker_description,
      externalId: check.checker_uuid,
      status,
      labelName: this.convertToLabelName(check.checker_name),
    };
    if (check.started) run.startedTimestamp = new Date(check.started);
    if (check.finished) run.finishedTimestamp = new Date(check.finished);
    if (status === 'RUNNING') {
      if (check.message) {
        run.statusDescription = check.message;
      } else if (check.state === 'SCHEDULED') {
        run.statusDescription = 'scheduled only, not yet running';
      }
      if (check.url) {
        run.statusLink = check.url;
      }
    } else if (check.state === 'SUCCESSFUL') {
      run.statusDescription =
        check.message || `Passed (${computeDuration(check)})`;
      if (check.url) {
        run.statusLink = check.url;
      }
    } else if (check.state === 'FAILED') {
      run.results = [
        {
          category: Category.ERROR,
          summary: check.message || `Failed (${computeDuration(check)})`,
        },
      ];
      if (check.url) {
        run.results[0].links = [
          {
            url: check.url,
            primary: true,
            icon: LinkIcon.EXTERNAL,
          },
        ];
      }
    }
    // We also provide the "run" action for running checks, because the "/rerun"
    // endpoint just updates the state to NOT_STARTED, which for running checks
    // means that they are stopped and rerun.
    let actionName = 'Run';
    if (status === 'RUNNING') actionName = 'Stop and Rerun';
    if (status === 'COMPLETED') actionName = 'Rerun';
    run.actions = [
      {
        name: actionName,
        primary: true,
        callback: () => this.run(check.checker_uuid),
      },
    ];
    return run;
  }

  run(uuid: string) {
    return this.apiPost(`/${uuid}/rerun`)
      .then(_ => {
        return {message: 'Run triggered.', shouldReload: true};
      })
      .catch(e => {
        return {message: `Triggering the run failed: ${e.message}`};
      });
  }
}
