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
import {queryAndAssert} from './test/test-util';
import './gr-repo-chip';
import {GrRepoChip} from './gr-repo-chip';

suite('gr-repo-chip tests', () => {
  let element: GrRepoChip;

  setup(async () => {
    element = document.createElement('gr-repo-chip');
    document.body.appendChild(element);
    await element.updateComplete;
  });

  teardown(() => {
    document.body.removeChild(element);
  });

  test('a button is rendered', () => {
    queryAndAssert<HTMLElement>(element, 'gr-button');
  });

  test('button click triggers remove event', () => {
    const spy = sinon.spy();
    element.addEventListener('remove', spy);
    const button = queryAndAssert<HTMLElement>(element, 'gr-button');
    assert.isFalse(spy.called);
    button.click();
    assert.isTrue(spy.called);
  });

  test('a span is rendered', () => {
    queryAndAssert<HTMLElement>(element, 'span');
  });
});
