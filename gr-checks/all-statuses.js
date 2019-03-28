(function() {
'use strict';

window.Gerrit = (window.Gerrit || {});
window.Gerrit.BuildResults = (window.Gerrit.BuildResults || {});

// Prevent redefinition.
if (window.Gerrit.BuildResults.Statuses) return;

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

window.Gerrit.BuildResults.Statuses = Statuses;
window.Gerrit.BuildResults.isUnevaluated = isUnevaluated;
window.Gerrit.BuildResults.isInProgress = isInProgress;
window.Gerrit.BuildResults.isSuccessful = isSuccessful;
window.Gerrit.BuildResults.isFailed = isFailed;
window.Gerrit.BuildResults.statusClass = statusClass;
})();
