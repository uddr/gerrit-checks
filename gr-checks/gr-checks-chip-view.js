(function() {
'use strict';
const Statuses = window.Gerrit.Checks.Statuses;

const StatusPriorityOrder = [
  Statuses.INTERNAL_ERROR, Statuses.TIMEOUT, Statuses.FAILURE,
  Statuses.STATUS_UNKNOWN, Statuses.CANCELLED, Statuses.QUEUED,
  Statuses.QUEUING, Statuses.WORKING, Statuses.SUCCESS
];

const HumanizedStatuses = {
  // non-terminal statuses
  STATUS_UNKNOWN: 'unevaluated',
  QUEUING: 'in progress',
  QUEUED: 'in progress',
  WORKING: 'in progress',

  // terminal statuses
  SUCCESS: 'successful',
  FAILURE: 'failed',
  INTERNAL_ERROR: 'failed',
  TIMEOUT: 'failed',
  CANCELLED: 'unevaluated',
};


const Defs = {};
/**
 * @typedef {{
 *   revisions: !Object<string, !Object>,
 * }}
 */
Defs.Change;

/**
 * @param {!Defs.Change} change The current CL.
 * @param {!Object} revision The current patchset.
 * @return {string|undefined}
 */
function currentRevisionSha(change, revision) {
  return Object.keys(change.revisions)
      .find(sha => change.revisions[sha] === revision);
}

function computeCheckStatuses(checks) {
  return checks.reduce((accum, check) => {
    accum[check.state] || (accum[check.state] = 0);
    accum[check.state]++;
    return accum;
  }, {total: checks.length});
}

Polymer({
  is: 'gr-checks-chip-view',

  properties: {
    revision: Object,
    change: Object,
    // TODO(brohlfs): Implement getChecks based on new Rest APIs.
    /** @type {function(string, (string|undefined)): !Promise<!Object>} */
    getChecks: Function,
    _checkStatuses: Object,
    _hasChecks: Boolean,
    _status: {type: String, computed: '_computeStatus(_checkStatuses)'},
    _statusString: {
      type: String,
      computed: '_computeStatusString(_status, _checkStatuses)'
    },
    _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
  },

  observers: [
    '_fetchChecks(change, revision, getChecks)',
  ],

  /**
   * @param {!Defs.Change} change The current CL.
   * @param {!Object} revision The current patchset.
   * @param {function(string, (string|undefined)): !Promise<!Object>}
   *     getChecks function to get checks.
   */
  _fetchChecks(change, revision, getChecks) {
    const repository = change['project'];
    const gitSha = currentRevisionSha(change, revision);

    getChecks(repository, gitSha).then(checks => {
      this.set('_hasChecks', checks.length > 0);
      if (checks.length > 0) {
        this.set(
            '_checkStatuses', computeCheckStatuses(checks));
      }
    });
  },

  /**
   * @param {!Object} checkStatuses The number of checks in each status.
   * @return {string}
   */
  _computeStatus(checkStatuses) {
    return StatusPriorityOrder.find(
               status => checkStatuses[status] > 0) ||
        Statuses.STATUS_UNKNOWN;
  },

  /**
   * @param {string} status The overall status of the checks.
   * @param {!Object} checkStatuses The number of checks in each status.
   * @return {string}
   */
  _computeStatusString(status, checkStatuses) {
    if (checkStatuses.total === 0) return 'No checks';
    return `${checkStatuses[status]} of ${
        checkStatuses.total} checks ${HumanizedStatuses[status]}`;
  },

  /**
   * @param {string} status The overall status of the checks.
   * @return {string}
   */
  _computeChipClass(status) {
    return `chip ${window.Gerrit.Checks.statusClass(status)}`;
  },
});
})();
