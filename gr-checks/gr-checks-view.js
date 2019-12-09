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

  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;

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
      revision: Object,
      change: Object,
      /** @type {function(number, number): !Promise<!Object>} */
      getChecks: Function,
      /** @type {function(string): !Promise<Boolean>} */
      isConfigured: Function,
      /** @type {function(string, string): !Promise<!Object>} */
      pluginRestApi: Object,
      _checks: Object,
      _status: {
        type: Object,
        value: LoadingStatus.LOADING,
      },
      pollChecksInterval: Number,
      visibilityChangeListenerAdded: {
        type: Boolean,
        value: false,
      },
      _createCheckerCapability: {
        type: Boolean,
        value: false,
      },
    },

    observers: [
      '_pollChecksRegularly(change, revision, getChecks)',
    ],

    attached() {
      this.pluginRestApi = this.plugin.restApi();
      this._initCreateCheckerCapability();
    },

    detached() {
      clearInterval(this.pollChecksInterval);
      this.unlisten(document, 'visibilitychange', '_onVisibililityChange');
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
      const retryCheck = (change, revision, uuid) => {
        return this.pluginRestApi.post(
            '/changes/' + change + '/revisions/' + revision + '/checks/' + uuid
              + '/rerun'
        );
      };
      retryCheck(this.change._number, this.revision._number, uuid).then(
          res => {
            this._fetchChecks(this.change, this.revision, this.getChecks);
          }, e => {
            console.error(e);
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
                c => { return c.checker_uuid === check.checker_uuid; }
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
    _fetchChecks(change, revision, getChecks) {
      if (!getChecks || !change || !revision) return;

      getChecks(change._number, revision._number).then(checks => {
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
      });
    },

    _onVisibililityChange() {
      if (document.hidden) {
        clearInterval(this.pollChecksInterval);
        return;
      }
      this._pollChecksRegularly(this.change, this.revision, this.getChecks);
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
  });
})();
