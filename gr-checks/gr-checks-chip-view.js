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

  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;

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

  function downgradeFailureToWarning(checks) {
    const hasFailedCheck = checks.some(
      (check) => {
        return check.state == Statuses.FAILED;
      }
    )
    if (!hasFailedCheck) return false;
    const hasRequiredFailedCheck = checks.some(
      (check) => {
        return check.state == Statuses.FAILED && check.blocking && check.blocking.length > 0;
      }
    )
    return !hasRequiredFailedCheck;
  }


  Polymer({
    is: 'gr-checks-chip-view',
    _legacyUndefinedCheck: true,

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
      _downgradeFailureToWarning: {
        type: Boolean,
        value: false
      },
      pollChecksInterval: Object,
      visibilityChangeListener: Object
    },

    observers: [
      '_pollChecksRegularly(change, revision, getChecks)',
    ],

    listeners: {
      'tap': 'showChecksTable'
    },

    showChecksTable() {
      this.dispatchEvent(
        new CustomEvent(
          'show-checks-table',
          {
            bubbles: true,
            composed: true,
            detail: {
              tab: 'change-view-tab-content-checks'
            }
          })
        );
    },

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revision, getChecks) {
      getChecks(change._number, revision._number).then(checks => {
        this.set('_hasChecks', checks.length > 0);
        if (checks.length > 0) {
          this.set('_checkStatuses', computeCheckStatuses(checks));
          this.set('_downgradeFailureToWarning', downgradeFailureToWarning(checks));
        }
      });
    },

    onVisibililityChange() {
      if (document.hidden) {
        clearInterval(this.pollChecksInterval);
        return;
      }
      this._pollChecksRegularly(this.change, this.revision, this.getChecks);
    },

    _pollChecksRegularly(change, revision, getChecks) {
      if (this.pollChecksInterval) {
        clearInterval(this.pollChecksInterval);
      }
      const poll = () => this._fetchChecks(change, revision, getChecks);
      poll();
      this.pollChecksInterval = setInterval(poll, CHECKS_POLL_INTERVAL_MS);
      if (!this.visibilityChangeListener) {
        this.visibilityChangeListener = document.addEventListener(
          'visibilitychange',
          this.onVisibililityChange.bind(this)
        );
      }
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
