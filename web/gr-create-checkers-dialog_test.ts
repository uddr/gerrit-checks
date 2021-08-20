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
import {queryAll, queryAndAssert} from './test/test-util';
import './gr-create-checkers-dialog';
import {GrCreateCheckersDialog} from './gr-create-checkers-dialog';

suite('gr-create-checkers-dialog tests', () => {
  let element: GrCreateCheckersDialog;

  setup(async () => {
    element = document.createElement('gr-create-checkers-dialog');
    document.body.appendChild(element);
    await element.updateComplete;
  });

  teardown(() => {
    document.body.removeChild(element);
  });

  test('all sections are rendered', () => {
    const div = queryAndAssert<HTMLElement>(element, 'div.gr-form-styles');
    const sections = queryAll<HTMLElement>(div, 'section');
    assert.equal(sections.length, 11);
  });
});
