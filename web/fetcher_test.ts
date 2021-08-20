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
import './test/test-setup';
import {ChecksFetcher} from './fetcher';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {
  Category,
  CheckRun,
  LinkIcon,
  ResponseCode,
  RunStatus,
} from '@gerritcodereview/typescript-api/checks';
import {Check} from './types';
import {ChangeInfo} from '@gerritcodereview/typescript-api/rest-api';

const check1: Check = {
  state: 'SUCCESSFUL',
  checker_name: 'my-test-name',
  checker_description: 'my-test-description',
  checker_uuid: 'my-test-uuid',
  message: 'my-test-message',
  url: 'http://my-test-url.com',
};

const check2: Check = {
  state: 'RUNNING',
  checker_name: 'my-test-name-2',
  checker_description: 'my-test-description-2',
  checker_uuid: 'my-test-uuid-2',
  message: 'my-test-message-2',
  url: 'http://my-test-url-2.com',
};

const check3: Check = {
  state: 'FAILED',
  checker_name: 'my-test-name-3',
  checker_description: 'my-test-description-3',
  checker_uuid: 'my-test-uuid-3',
  message: 'my-test-message-3',
  url: 'http://my-test-url-3.com',
};

suite('ChecksFetcher tests', () => {
  let fetcher: ChecksFetcher;
  let getChecksResolve: (value: Check[]) => void;
  let postChecksStub: sinon.SinonStub<string[], unknown>;

  setup(async () => {
    const getChecksStub = sinon.stub();
    getChecksStub.returns(new Promise(r => (getChecksResolve = r)));
    postChecksStub = sinon.stub<string[], unknown>();
    postChecksStub.returns(Promise.resolve({}));

    const fakePlugin = {
      restApi: () => {
        return {
          get: getChecksStub,
          post: postChecksStub,
        };
      },
    } as unknown as PluginApi;
    fetcher = new ChecksFetcher(fakePlugin);
  });

  test('fetch', async () => {
    getChecksResolve([check1, check2, check3]);
    const response = await fetcher.fetch({
      changeNumber: 123,
      patchsetNumber: 3,
      patchsetSha: 'my-test-sha',
      repo: 'my-test-repo',
      changeInfo: {} as ChangeInfo,
    });
    assert.equal(response.responseCode, ResponseCode.OK);
    assert.equal(response.actions.length, 1);
    assert.equal(response.runs.length, 3);
  });

  test('convert check1', () => {
    const converted: CheckRun = fetcher.convert(check1);
    assert.equal(converted.checkDescription, check1.checker_description);
    assert.equal(converted.checkName, check1.checker_name);
    assert.equal(converted.externalId, check1.checker_uuid);
    assert.equal(converted.status, RunStatus.COMPLETED);
    assert.equal(converted.statusLink, check1.url);
    assert.equal(converted.statusDescription, check1.message);
    assert.equal(converted.actions?.length, 1);
    assert.equal(converted.actions?.[0].name, 'Rerun');
    assert.isTrue(converted.actions?.[0].primary);
    assert.isFalse(postChecksStub.called);
    converted.actions?.[0].callback(1, 1, 1, '', '', '');
    assert.isTrue(postChecksStub.called);
    assert.isTrue(
      String(postChecksStub.lastCall.firstArg).includes(check1.checker_uuid)
    );
  });

  test('convert check2', () => {
    const converted: CheckRun = fetcher.convert(check2);
    assert.equal(converted.status, RunStatus.RUNNING);
    assert.equal(converted.statusLink, check2.url);
    assert.equal(converted.statusDescription, check2.message);
    assert.equal(converted.actions?.length, 1);
    assert.equal(converted.actions?.[0].name, 'Stop and Rerun');
  });

  test('convert check3', () => {
    const converted: CheckRun = fetcher.convert(check3);
    assert.equal(converted.status, RunStatus.COMPLETED);
    assert.equal(converted.actions?.length, 1);
    assert.equal(converted.results?.length, 1);
    const result = converted.results?.[0];
    assert.equal(result?.category, Category.ERROR);
    assert.equal(result?.summary, check3.message);
    assert.equal(result?.links?.length, 1);
    const link = result?.links?.[0];
    assert.equal(link?.url, check3.url);
    assert.isTrue(link?.primary);
    assert.equal(link?.icon, LinkIcon.EXTERNAL);
    assert.equal(converted.actions?.[0].name, 'Rerun');
  });
});
