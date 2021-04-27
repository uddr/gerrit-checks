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
function pluralize(count, noun) {
  if (count === 0) return '';
  return `${count} ${noun}` + (count > 1 ? 's' : '');
}

function generateDurationString(startTime, endTime) {
  const secondsAgo = Math.round((endTime - startTime) / 1000);
  if (secondsAgo === 0) {
    return '0 sec';
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
    durationSegments.push(`${hours}`);
  }
  const daysAgo = Math.floor(hoursAgo / 24);
  if (daysAgo % 30 !== 0) {
    const days = pluralize(daysAgo % 30, 'day', 'days');
    durationSegments.push(`${days}`);
  }
  const monthsAgo = Math.floor(daysAgo / 30);
  if (monthsAgo > 0) {
    const months = pluralize(monthsAgo, 'month', 'months');
    durationSegments.push(`${months}`);
  }
  return durationSegments.reverse().slice(0, 2).join(' ');
}

export function computeDuration(check) {
  if (!check.started || !check.finished) {
    return '-';
  }
  const startTime = new Date(check.started);
  const finishTime = check.finished ? new Date(check.finished) : new Date();
  return generateDurationString(startTime, finishTime);
}
