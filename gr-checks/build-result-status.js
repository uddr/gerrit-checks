(function() {
'use strict';

Polymer({
  is: 'build-result-status',

  properties: {
    showText: {
      type: Boolean,
      value: false,
      reflectToAttribute: true,
    },
    status: String,
    _className: {type: String, computed: '_computeClassName(status)'},
  },

  _isUnevaluated(status) {
    return window.Gerrit.BuildResults.isUnevaluated(status);
  },

  _isInProgress(status) {
    return window.Gerrit.BuildResults.isInProgress(status);
  },

  _isSuccessful(status) {
    return window.Gerrit.BuildResults.isSuccessful(status);
  },

  _isFailed(status) {
    return window.Gerrit.BuildResults.isFailed(status);
  },

  _computeClassName(status) {
    return window.Gerrit.BuildResults.statusClass(status);
  },
});
})();
