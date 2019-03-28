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
  is: 'build-results-view',

  properties: {
    revision: Object,
    change: Object,
    // TODO(brohlfs): Implement getBuildResults based on Checks Rest API.
    /** @type {function(string, (string|undefined)): !Promise<!Object>} */
    getBuildResults: Function,
    // TODO(brohlfs): Implement isConfigured based on Checks Rest API.
    /** @type {function(string): !Promise<!Object>} */
    isConfigured: Function,
    // TODO(brohlfs): Implement getTrigger based on Checks Rest API.
    /** @type {function(string, string): !Promise<!Object>} */
    getTrigger: Function,
    // TODO(brohlfs): Implement retryBuild based on Checks Rest API.
    /** @type {function(string, string): !Promise<!Object>} */
    retryBuild: Function,
    // TODO(brohlfs): Implement configurePath based on Checks Rest API.
    // The url path to configure code review triggers.
    configurePath: String,
    _buildResults: Object,
    _status: {
      type: Object,
      value: LoadingStatus.LOADING,
    },
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
      if (buildResults && buildResults.length) {
        this.set('_buildResults', buildResults);
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
