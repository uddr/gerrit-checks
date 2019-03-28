(function() {
'use strict';

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
    // TODO(brohlfs): Implement getChecks based on Checks Rest API.
    /** @type {function(string, (string|undefined)): !Promise<!Object>} */
    getChecks: Function,
    // TODO(brohlfs): Implement isConfigured based on Checks Rest API.
    /** @type {function(string): !Promise<!Object>} */
    isConfigured: Function,
    // TODO(brohlfs): Implement getChecker based on Checks Rest API.
    /** @type {function(string, string): !Promise<!Object>} */
    getChecker: Function,
    // TODO(brohlfs): Implement retryCheck based on Checks Rest API.
    /** @type {function(string, string): !Promise<!Object>} */
    retryCheck: Function,
    // TODO(brohlfs): Implement configurePath based on Checks Rest API.
    // The url path to configure checkers.
    configurePath: String,
    _checks: Object,
    _status: {
      type: Object,
      value: LoadingStatus.LOADING,
    },
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
      if (checks && checks.length) {
        this.set('_checks', checks);
        this.set('_status', LoadingStatus.RESULTS);
      } else {
        this._checkConfigured();
      }
    });
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
