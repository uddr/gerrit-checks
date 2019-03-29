(function() {
'use strict';

window.Gerrit = (window.Gerrit || {});
window.Gerrit.Checks = (window.Gerrit.Checks || {});

// Prevent redefinition.
if (window.Gerrit.Checks.Statuses) return;

const Statuses = {
  // non-terminal statuses
  STATUS_UNKNOWN: 'STATUS_UNKNOWN',
  QUEUING: 'QUEUING',
  QUEUED: 'QUEUED',
  WORKING: 'WORKING',

  // terminal statuses
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
  TIMEOUT: 'TIMEOUT',
  CANCELLED: 'CANCELLED',
};


function isStatus(status, includedStatuses) {
  return includedStatuses.includes(status);
}


function isUnevaluated(status) {
  return isStatus(status, [Statuses.STATUS_UNKNOWN, Statuses.CANCELLED]);
}

function isInProgress(status) {
  return isStatus(
      status, [Statuses.QUEUING, Statuses.QUEUED, Statuses.WORKING]);
}

function isSuccessful(status) {
  return isStatus(status, [Statuses.SUCCESS]);
}

function isFailed(status) {
  return isStatus(
      status, [Statuses.FAILURE, Statuses.INTERNAL_ERROR, Statuses.TIMEOUT]);
}


function statusClass(status) {
  if (isUnevaluated(status)) {
    return 'unevaluated';
  }
  if (isInProgress(status)) {
    return 'in-progress';
  }
  if (isSuccessful(status)) {
    return 'successful';
  }
  if (isFailed(status)) {
    return 'failed';
  }
  return 'unevaluated';
}

window.Gerrit.Checks.Statuses = Statuses;
window.Gerrit.Checks.isUnevaluated = isUnevaluated;
window.Gerrit.Checks.isInProgress = isInProgress;
window.Gerrit.Checks.isSuccessful = isSuccessful;
window.Gerrit.Checks.isFailed = isFailed;
window.Gerrit.Checks.statusClass = statusClass;
})();
