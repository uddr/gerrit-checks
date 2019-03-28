(function() {
  'use strict';
  const Statuses = window.Gerrit.Checks.Statuses;

  const StatusPriorityOrder = [
    Statuses.FAILED,
    Statuses.SCHEDULED,
    Statuses.RUNNING,
    Statuses.SUCCESSFUL,
    Statuses.NOT_STARTED,
    Statuses.NOT_RELEVANT,
  ];

  const HumanizedStatuses = {
    // non-terminal statuses
    NOT_STARTED: 'in progress',
    NOT_RELEVANT: 'not relevant',
    SCHEDULED: 'in progress',
    RUNNING: 'in progress',

    // terminal statuses
    SUCCESSFUL: 'successful',
    FAILED: 'failed',
  };

  const Defs = {};
  /**
   * @typedef {{
   *   _number: number,
   * }}
   */
  Defs.Change;
  /**
   * @typedef {{
   *   _number: number,
   * }}
   */
  Defs.Revision;

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
      /** @type {function(number, number): !Promise<!Object>} */
      getChecks: Function,
      _checkStatuses: Object,
      _hasChecks: Boolean,
      _status: {type: String, computed: '_computeStatus(_checkStatuses)'},
      _statusString: {
        type: String,
        computed: '_computeStatusString(_status, _checkStatuses)',
      },
      _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
    },

    observers: [
      '_fetchChecks(change, revision, getChecks)',
    ],

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revision, getChecks) {
      getChecks(change._number, revision._number).then(checks => {
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
