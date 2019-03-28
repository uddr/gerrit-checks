(function() {
'use strict';
const Statuses = window.Gerrit.BuildResults.Statuses;

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

function computeBuildResultStatuses(buildResults) {
  return buildResults.reduce((accum, buildResult) => {
    accum[buildResult.status] || (accum[buildResult.status] = 0);
    accum[buildResult.status]++;
    return accum;
  }, {total: buildResults.length});
}

Polymer({
  is: 'builds-chip-view',

  properties: {
    revision: Object,
    change: Object,
    // TODO(brohlfs): Implement getBuildResults based on new Rest APIs.
    /** @type {function(string, (string|undefined)): !Promise<!Object>} */
    getBuildResults: Function,
    _buildResultStatuses: Object,
    _hasBuilds: Boolean,
    _status: {type: String, computed: '_computeStatus(_buildResultStatuses)'},
    _statusString: {
      type: String,
      computed: '_computeStatusString(_status, _buildResultStatuses)'
    },
    _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
  },

  observers: [
    '_fetchBuildResults(change, revision, getBuildResults)',
  ],

  /**
   * @param {!Defs.Change} change The current CL.
   * @param {!Object} revision The current patchset.
   * @param {function(string, (string|undefined)): !Promise<!Object>}
   *     getBuildResults function to get build results.
   */
  _fetchBuildResults(change, revision, getBuildResults) {
    const repository = change['project'];
    const gitSha = currentRevisionSha(change, revision);

    getBuildResults(repository, gitSha).then(buildResults => {
      this.set('_hasBuilds', buildResults.length > 0);
      if (buildResults.length > 0) {
        this.set(
            '_buildResultStatuses', computeBuildResultStatuses(buildResults));
      }
    });
  },

  /**
   * @param {!Object} buildResultStatuses The number of builds in each status.
   * @return {string}
   */
  _computeStatus(buildResultStatuses) {
    return StatusPriorityOrder.find(
               status => buildResultStatuses[status] > 0) ||
        Statuses.STATUS_UNKNOWN;
  },

  /**
   * @param {string} status The overall status of the build results.
   * @param {!Object} buildResultStatuses The number of builds in each status.
   * @return {string}
   */
  _computeStatusString(status, buildResultStatuses) {
    if (buildResultStatuses.total === 0) return 'No builds';
    return `${buildResultStatuses[status]} of ${
        buildResultStatuses.total} builds ${HumanizedStatuses[status]}`;
  },

  /**
   * @param {string} status The overall status of the build results.
   * @return {string}
   */
  _computeChipClass(status) {
    return `chip ${window.Gerrit.BuildResults.statusClass(status)}`;
  },
});
})();
