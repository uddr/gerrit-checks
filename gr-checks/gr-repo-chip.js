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
    _handleRemove(e) {
      e.preventDefault();
      this.dispatchEvent(new CustomEvent('remove',
          {
            detail: {repo: this.repo},
            bubbles: true,
            composed: true,
          }));
    },
  });
})();