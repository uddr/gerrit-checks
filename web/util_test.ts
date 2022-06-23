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
import {pluralize, generateDurationString} from './util';

suite('util tests', () => {
  test('pluralize', () => {
    assert.equal(pluralize(0, 'bag'), '');
    assert.equal(pluralize(1, 'bag'), '1 bag');
    assert.equal(pluralize(2, 'bag'), '2 bags');
  });

  test('generateDurationString', () => {
    assert.equal(
      generateDurationString(
        new Date('1995-12-17T03:24:00'),
        new Date('1995-12-17T03:24:01')
      ),
      '1 sec'
    );
    assert.equal(
      generateDurationString(
        new Date('1995-12-17T03:24:00'),
        new Date('1995-12-17T03:25:00')
      ),
      '1 min'
    );
    assert.equal(
      generateDurationString(
        new Date('1995-12-17T03:24:00'),
        new Date('1995-12-17T04:24:00')
      ),
      '1 hour'
    );
  });
});
