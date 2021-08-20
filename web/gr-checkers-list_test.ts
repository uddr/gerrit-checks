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
import './gr-checkers-list';
import {GrCheckersList} from './gr-checkers-list';
import {Checker} from './types';
import {queryAll, queryAndAssert} from './test/test-util';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';

const CHECKERS = [
  {
    uuid: 'C:D',
    name: 'A',
    description: 'B',
    repository: 'Backend',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-25 13:08:43.000000000',
    updated: '2019-07-25 13:08:43.000000000',
  },
  {
    uuid: 'aa:bb',
    name: 'n1',
    description: 'd1',
    repository: 'All-Users',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-29 13:07:17.000000000',
    updated: '2019-07-29 13:07:17.000000000',
  },
  {
    uuid: 'adsf:asdasdas',
    name: 'ds',
    description: 's',
    repository: 'Scripts',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-29 13:28:09.000000000',
    updated: '2019-07-29 13:28:09.000000000',
  },
  {
    uuid: 'ijkl:mnop',
    name: 'abcd',
    description: 'efgh',
    repository: 'All-Projects',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-29 09:33:25.000000000',
    updated: '2019-07-29 09:33:25.000000000',
  },
  {
    uuid: 'ngfnf:mhghgnhghn',
    name: 'nbvfg',
    description: 'fjhgj',
    repository: 'All-Users',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-08-06 14:21:34.000000000',
    updated: '2019-08-06 14:21:34.000000000',
  },
  {
    uuid: 'sdfsdf--:sdfsdf333',
    name: 'sdfsdf',
    description: 'sdfsdfsd',
    repository: 'Scripts',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-30 13:00:19.000000000',
    updated: '2019-07-30 13:00:19.000000000',
  },
  {
    uuid: 'test:checker1',
    name: 'Unit Tests',
    description: 'Random description that should be improved at some point',
    repository: 'Backend',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-22 13:16:52.000000000',
    updated: '2019-07-22 14:21:14.000000000',
  },
  {
    uuid: 'test:checker2',
    name: 'Code Style',
    repository: 'Backend',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-22 13:26:56.000000000',
    updated: '2019-07-22 13:26:56.000000000',
  },
  {
    uuid: 'xddf:sdfsdfsdf',
    name: 'sdfsdf',
    description: 'sdfsdf',
    repository: 'Scripts',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-29 14:11:59.000000000',
    updated: '2019-07-29 14:11:59.000000000',
  },
  {
    uuid: 'zxczxc:bnvnbvnbvn',
    name: 'zxc',
    description: 'zxc',
    repository: 'Scripts',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-29 14:00:24.000000000',
    updated: '2019-07-29 14:00:24.000000000',
  },
  {
    uuid: 'zxczxc:sdfsdf',
    name: 'zxc',
    description: 'zxc',
    repository: 'Scripts',
    status: 'ENABLED',
    blocking: [],
    query: 'status:open',
    created: '2019-07-29 13:30:47.000000000',
    updated: '2019-07-29 13:30:47.000000000',
  },
];

suite('gr-checkers-list tests', () => {
  let element: GrCheckersList;
  let getCheckersResolve: (value: Checker[]) => void;

  setup(async () => {
    const getCheckersStub = sinon.stub();
    getCheckersStub.returns(new Promise(r => (getCheckersResolve = r)));

    element = document.createElement('gr-checkers-list');
    element.plugin = {
      restApi: () => {
        return {get: getCheckersStub};
      },
    } as unknown as PluginApi;
    document.body.appendChild(element);
    await element.updateComplete;
  });

  teardown(() => {
    document.body.removeChild(element);
  });

  test('checker table headers render correctly', () => {
    const checkersList = queryAndAssert<HTMLElement>(element, 'table#list');
    const headings =
      checkersList.firstElementChild?.firstElementChild?.children;
    if (!headings) assert.fail('no headings found');
    const expectedHeadings = [
      'Checker Name',
      'Repository',
      'Status',
      'Required',
      'Checker Description',
      'Edit',
    ];
    assert.equal(headings.length, expectedHeadings.length);
    for (let i = 0; i < headings.length; i++) {
      const heading = headings[i] as HTMLElement;
      assert.equal(heading.innerText?.trim(), expectedHeadings[i]);
    }
  });

  test('create new checker button renders corrrectly', () => {
    const div = queryAndAssert<HTMLElement>(element, '#createNewContainer');
    const button = queryAndAssert<HTMLElement>(div, 'gr-button');
    assert.equal(button.innerText, 'Create New');
  });

  test('table of checkers renders correctly', async () => {
    getCheckersResolve(CHECKERS);
    element.requestUpdate();
    await element.updateComplete;

    const tbody = queryAndAssert(element, 'tbody#listBody');
    const rows = queryAll<HTMLElement>(tbody, 'tr');
    assert.equal(rows.length, CHECKERS.length);
    for (let i = 0; i < rows.length; i++) {
      const checkerDetails = rows[i].querySelectorAll('td');
      assert.equal(CHECKERS[i].name, checkerDetails[0].innerText);
      assert.equal(CHECKERS[i].repository, checkerDetails[1].innerText);
      const status = CHECKERS[i].status || 'NO';
      assert.equal(status, checkerDetails[2].innerText);
      const checkerRequired =
        CHECKERS[i].blocking && CHECKERS[i].blocking.length > 0 ? 'YES' : 'NO';
      assert.equal(checkerRequired, checkerDetails[3].innerText);
      const description = CHECKERS[i].description || '';
      assert.equal(description, checkerDetails[4].innerText);
    }
  });
});
