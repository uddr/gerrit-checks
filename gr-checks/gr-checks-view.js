(function() {
  'use strict';

  const Defs = {};

  const Statuses = window.Gerrit.Checks.Statuses;
  const StatusPriorityOrder = [
    Statuses.FAILED,
    Statuses.SCHEDULED,
    Statuses.RUNNING,
    Statuses.SUCCESSFUL,
    Statuses.NOT_STARTED,
    Statuses.NOT_RELEVANT,
  ];

  const STATE_ALL = 'ALL';
  const CheckStateFilters = [STATE_ALL, ...StatusPriorityOrder];

  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;
  const CHECKS_LIMIT = 20;

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

  const LoadingStatus = {
    LOADING: 0,
    EMPTY: 1,
    RESULTS: 2,
    NOT_CONFIGURED: 3,
  };

  Polymer({
    is: 'gr-checks-view',

    properties: {
      revision: {
        type: Object,
        observer: '_handleRevisionUpdate',
      },
      change: Object,
      /** @type {function(number, number): !Promise<!Object>} */
      getChecks: Function,
      /** @type {function(string): !Promise<Boolean>} */
      isConfigured: Function,
      /** @type {function(string, string): !Promise<!Object>} */
      pluginRestApi: Object,
      _checks: Array,
      _status: {
        type: Object,
        value: LoadingStatus.LOADING,
      },
      _visibleChecks: {
        type: Array,
      },
      _statuses: Array,
      pollChecksInterval: Number,
      visibilityChangeListenerAdded: {
        type: Boolean,
        value: false,
      },
      _createCheckerCapability: {
        type: Boolean,
        value: false,
      },
      _patchSetDropdownItems: {
        type: Array,
        value() { return []; },
        computed: '_computePatchSetDropdownItems(change)',
      },
      _currentPatchSet: {
        type: Number,
      },
      _currentStatus: {
        type: String,
        value: STATE_ALL,
      },
      _showBlockingChecksOnly: {
        type: Boolean,
        value: false,
      },
      _showAllChecks: {
        type: Boolean,
        value: false,
      },
      _filteredChecks: Array,
      _showMoreChecksButton: {
        type: Boolean,
        value: false,
        notify: true,
      },
    },

    observers: [
      '_pollChecksRegularly(change, _currentPatchSet, getChecks)',
      '_updateVisibleChecks(_checks.*, _currentStatus, ' +
          '_showBlockingChecksOnly, _showAllChecks)',
    ],

    attached() {
      this.pluginRestApi = this.plugin.restApi();
      this._statuses = CheckStateFilters.map(state => {
        return {
          text: state,
          value: state,
        };
      });
      this._initCreateCheckerCapability();
    },

    detached() {
      clearInterval(this.pollChecksInterval);
      this.unlisten(document, 'visibilitychange', '_onVisibililityChange');
    },

    _toggleShowChecks() {
      this._showAllChecks = !this._showAllChecks;
    },

    _computePatchSetDropdownItems(change) {
      return Object.values(change.revisions)
          .filter(patch => patch._number !== 'edit')
          .map(patch => {
            return {
              text: 'Patchset ' + patch._number,
              value: patch._number,
            };
          })
          .sort((a, b) => b.value - a.value);
    },

    _computeShowText(showAllChecks) {
      return showAllChecks ? 'Show Less' : 'Show All';
    },

    _updateVisibleChecks(checksRecord, status, showBlockingChecksOnly,
        showAllChecks) {
      const checks = checksRecord.base;
      if (!checks) return [];
      this._filteredChecks = checks.filter(check => {
        if (showBlockingChecksOnly && (!check.blocking ||
            !check.blocking.length)) return false;
        return status === STATE_ALL || check.state === status;
      });
      /* The showCheckMessage property is notified for change because the
      changes to showCheckMessage are not reflected to the dom as the object
      check has the same reference as before
      If not notified, then the message for the check is not displayed after
      clicking the toggle
      */
      this._showMoreChecksButton = this._filteredChecks.length > CHECKS_LIMIT;
      this._visibleChecks = this._filteredChecks.slice(0, showAllChecks ?
        undefined : CHECKS_LIMIT);
      this._visibleChecks.forEach((val, idx) =>
        this.notifyPath(`_visibleChecks.${idx}.showCheckMessage`));
    },

    _handleRevisionUpdate(revision) {
      this._currentPatchSet = revision._number;
    },

    _handlePatchSetChanged(e) {
      // gr-dropdown-list returns value of type "String"
      const patchSet = parseInt(e.detail.value);
      if (patchSet === this._currentPatchSet) return;
      this._currentPatchSet = patchSet;
    },

    _handleBlockingCheckboxClicked() {
      this._showBlockingChecksOnly = !this._showBlockingChecksOnly;
    },

    _handleStatusFilterChanged(e) {
      const status = e.detail.value;
      if (status === this._currentStatus) return;
      this._currentStatus = status;
    },

    _handleCheckersListResize() {
      // Force polymer to recalculate position of overlay when length of
      // checkers changes
      this.$.listOverlay.refit();
    },

    _initCreateCheckerCapability() {
      return this.pluginRestApi.getAccount().then(account => {
        if (!account) { return; }
        return this.pluginRestApi
            .getAccountCapabilities(['checks-administrateCheckers'])
            .then(capabilities => {
              if (capabilities['checks-administrateCheckers']) {
                this._createCheckerCapability = true;
              }
            });
      });
    },

    _handleConfigureClicked() {
      this.$$('gr-checkers-list')._showConfigureOverlay();
    },

    _orderChecks(a, b) {
      if (a.state != b.state) {
        const indexA = StatusPriorityOrder.indexOf(a.state);
        const indexB = StatusPriorityOrder.indexOf(b.state);
        if (indexA != -1 && indexB != -1) {
          return indexA - indexB;
        }
        return indexA == -1 ? 1 : -1;
      }
      if (a.state === Statuses.FAILED) {
        if (a.blocking && b.blocking &&
            a.blocking.length !== b.blocking.length) {
          return a.blocking.length == 0 ? 1 : -1;
        }
      }
      return a.checker_name.localeCompare(b.checker_name);
    },

    _handleRetryCheck(e) {
      const uuid = e.detail.uuid;
      const retryCheck = (change, revision, uuid) => this.pluginRestApi.post(
          '/changes/' + change + '/revisions/' + revision + '/checks/' + uuid
              + '/rerun'
      );
      retryCheck(this.change._number, this.revision._number, uuid).then(
          res => {
            this._fetchChecks(this.change, this.revision._number,
                this.getChecks);
          }, error => {
            this.dispatchEvent(new CustomEvent('show-error',
                {
                  detail: {message: error.message},
                  bubbles: true,
                  composed: true,
                }));
          }
      );
    },

    /**
     * Merge new checks into old checks to maintain showCheckMessage
     * property
     * Loop over checks to make sure no new checks are missed
     * Merge new check object into prev check
     * Remove any check that is not returned the next time
     * Ensure message is updated
     */
    _updateChecks(checks) {
      return checks.map(
          check => {
            const prevCheck = this._checks.find(
                c => c.checker_uuid === check.checker_uuid
            );
            if (!prevCheck) return Object.assign({}, check);
            return Object.assign({}, prevCheck, check,
                {showCheckMessage: prevCheck.showCheckMessage});
          });
    },

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revisionNumber, getChecks) {
      if (!getChecks || !change || !revisionNumber) return;

      getChecks(change._number, revisionNumber).then(checks => {
        if (revisionNumber !== this._currentPatchSet) return;
        if (checks && checks.length) {
          checks.sort((a, b) => this._orderChecks(a, b));
          if (!this._checks) {
            this._checks = checks;
          } else {
            this._checks = this._updateChecks(checks);
          }
          this.set('_status', LoadingStatus.RESULTS);
        } else {
          this._checkConfigured();
        }
      }, error => {
        this._checks = [];
        this.set('_status', LoadingStatus.EMPTY);
      });
    },

    _onVisibililityChange() {
      if (document.hidden) {
        clearInterval(this.pollChecksInterval);
        return;
      }
      this._pollChecksRegularly(this.change, this._currentPatchSet,
          this.getChecks);
    },

    _toggleCheckMessage(e) {
      const uuid = e.detail.uuid;
      if (!uuid) {
        console.warn('uuid not found');
        return;
      }
      const idx = this._checks.findIndex(check => check.checker_uuid === uuid);
      if (idx == -1) {
        console.warn('check not found');
        return;
      }
      // Update subproperty of _checks[idx] so that it reflects to polymer
      this.set(`_checks.${idx}.showCheckMessage`,
          !this._checks[idx].showCheckMessage);
    },

    _pollChecksRegularly(change, revisionNumber, getChecks) {
      if (!change || !revisionNumber || !getChecks) return;
      if (this.pollChecksInterval) {
        clearInterval(this.pollChecksInterval);
      }
      const poll = () => this._fetchChecks(change, revisionNumber, getChecks);
      poll();
      this.pollChecksInterval = setInterval(poll, CHECKS_POLL_INTERVAL_MS);
      if (!this.visibilityChangeListenerAdded) {
        this.visibilityChangeListenerAdded = true;
        this.listen(document, 'visibilitychange', '_onVisibililityChange');
      }
    },

    _checkConfigured() {
      const repository = this.change['project'];
      this.isConfigured(repository).then(configured => {
        const status =
            configured ? LoadingStatus.EMPTY : LoadingStatus.NOT_CONFIGURED;
        this.set('_status', status);
      });
    },

    _isLoading(status) {
      return status === LoadingStatus.LOADING;
    },

    _isEmpty(status) {
      return status === LoadingStatus.EMPTY;
    },

    _hasResults(status) {
      return status === LoadingStatus.RESULTS;
    },

    _isNotConfigured(status) {
      return status === LoadingStatus.NOT_CONFIGURED;
    },

    _computeHeaderClass(currentPatchSet, sortedAllPatchsets) {
      if (!sortedAllPatchsets
         || sortedAllPatchsets.length < 1
          || !currentPatchSet) {
        return '';
      }
      return currentPatchSet === sortedAllPatchsets[0].value ?
        '' : 'oldPatchset';
    },
  });
})();
