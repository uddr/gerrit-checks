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
        check => {
          return check.state == Statuses.FAILED;
        }
    );
    if (!hasFailedCheck) return '';
    const hasRequiredFailedCheck = checks.some(
        check => {
          return check.state == Statuses.FAILED &&
               check.blocking && check.blocking.length > 0;
        }
    );
    return hasRequiredFailedCheck ? '' : 'set';
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
      _failedRequiredChecksCount: Number,
      _status: {type: String, computed: '_computeStatus(_checkStatuses)'},
      _statusString: {
        type: String,
        computed: '_computeStatusString(_status, _checkStatuses,' +
         '_failedRequiredChecksCount)',
      },
      _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
      // Type is set as string so that it reflects on changes
      // Polymer does not support reflecting changes in Boolean property
      _downgradeFailureToWarning: {
        type: String,
        value: '',
      },
      pollChecksInterval: Object,
      visibilityChangeListenerAdded: {
        type: Boolean,
        value: false,
      },
    },

    detached() {
      clearInterval(this.pollChecksInterval);
      this.unlisten(document, 'visibilitychange', '_onVisibililityChange');
    },

    observers: [
      '_pollChecksRegularly(change, revision, getChecks)',
    ],

    listeners: {
      click: 'showChecksTable',
    },

    showChecksTable() {
      this.dispatchEvent(
          new CustomEvent(
              'show-checks-table',
              {
                bubbles: true,
                composed: true,
                detail: {
                  tab: 'change-view-tab-content-checks',
                },
              })
      );
    },

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revision, getChecks) {
      if (!getChecks || !change || !revision) return;

      getChecks(change._number, revision._number).then(checks => {
        this.set('_hasChecks', checks.length > 0);
        if (checks.length > 0) {
          this._downgradeFailureToWarning =
            downgradeFailureToWarning(checks);
          this._failedRequiredChecksCount =
            this.computeFailedRequiredChecksCount(checks);
          this._checkStatuses = computeCheckStatuses(checks);
        }
      }, error => {
        this.set('_hasChecks', false);
        console.error(error);
      });
    },

    _onVisibililityChange() {
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
      if (!this.visibilityChangeListenerAdded) {
        this.visibilityChangeListenerAdded = true;
        this.listen(document, 'visibilitychange', '_onVisibililityChange');
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

    computeFailedRequiredChecksCount(checks) {
      const failedRequiredChecks = checks.filter(
          check => {
            return check.state == Statuses.FAILED &&
            check.blocking && check.blocking.length > 0;
          }
      );
      return failedRequiredChecks.length;
    },

    /**
     * @param {string} status The overall status of the checks.
     * @param {!Object} checkStatuses The number of checks in each status.
     * @return {string}
     */
    _computeStatusString(status, checkStatuses, failedRequiredChecksCount) {
      if (!checkStatuses) return;
      if (checkStatuses.total === 0) return 'No checks';
      let statusString = `${checkStatuses[status]} of ${
        checkStatuses.total} checks ${HumanizedStatuses[status]}`;
      if (status === Statuses.FAILED && failedRequiredChecksCount > 0) {
        statusString += ` (${failedRequiredChecksCount} required)`;
      }
      return statusString;
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
