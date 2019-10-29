(function() {
  'use strict';

  /**
   * autocomplete chip for getting repository suggestions
   */
  Polymer({
    is: 'gr-repo-chip',
    properties: {
      // repo type is ProjectInfo
      repo: Object,
      removable: {
        type: Boolean,
        value: true,
      },
    },
    _handleRemoveTap(e) {
      e.preventDefault();
      this.fire('remove', {repo: this.repo});
    },
  })

})();