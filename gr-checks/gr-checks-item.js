(function() {
  'use strict';

  const Defs = {};
  /**
   * @typedef {{
   *   project: string,
   *   change_number: number,
   *   patch_set_id: number,
   *   checker_uuid: string,
   *   state: string,
   *   url: string,
   *   started: string,
   *   finished: string,
   *   created: string,
   *   updated: string,
   *   checker_name: string,
   *   checker_status: string,
   *   blocking: Array<Object>,
   * }}
   */
  Defs.Check;

  Polymer({
    is: 'gr-checks-item',

    properties: {
      /** @type {Defs.Check} */
      check: Object,
      /** @type {function(string): !Promise<!Object>} */
      _startTime: {
        type: String,
        computed: '_computeStartTime(check)',
      },
      _duration: {
        type: String,
        computed: '_computeDuration(check)',
      },
      _requiredForMerge: {
        type: String,
        computed: '_computeRequiredForMerge(check)',
      },
      showCheckMessage: {
        type: Boolean,
        value: false,
      },
    },

    /**
     * Fired when the retry check button is pressed.
     *
     * @event retry-check
     */

    /**
     * @param {!Defs.Check} check
     * @return {string}
     */
    _computeStartTime(check) {
      if (!check.started) return '-';
      return check.started;
    },

    _toggleMessageShown() {
      this.showCheckMessage = !this.showCheckMessage;
      this.dispatchEvent(new CustomEvent('toggle-check-message',
          {
            detail: {uuid: this.check.checker_uuid},
            bubbles: true,
            composed: true,
          }));
    },

    _computeExpandIcon(showCheckMessage) {
      return showCheckMessage ? 'gr-icons:expand-less': 'gr-icons:expand-more';
    },

    /**
     * @param {!Defs.Check} check
     * @return {string}
     */
    _computeDuration(check) {
      if (!check.started || !check.finished) {
        return '-';
      }
      const startTime = moment(check.started);
      const finishTime = check.finished ? moment(check.finished) : moment();
      return generateDurationString(
          moment.duration(finishTime.diff(startTime)));
    },

    /**
     * @param {!Defs.Check} check
     * @return {string}
     */
    _computeRequiredForMerge(check) {
      return (check.blocking && check.blocking.length === 0) ? 'Optional' :
        'Required';
    },
    _handleReRunClicked() {
      this.dispatchEvent(new CustomEvent('retry-check',
          {
            detail: {uuid: this.check.checker_uuid},
            bubbles: false,
            composed: true,
          }));
    },
  });

  const ZERO_SECONDS = '0 sec';

  /**
   * @param {!Moment.Duration} duration a moment object
   * @return {string}
   */
  function generateDurationString(duration) {
    if (duration.asSeconds() === 0) {
      return ZERO_SECONDS;
    }

    const durationSegments = [];
    if (duration.months()) {
      const months = pluralize(duration.months(), 'month', 'months');
      durationSegments.push(`${duration.months()} ${months}`);
    }
    if (duration.days()) {
      const days = pluralize(duration.days(), 'day', 'days');
      durationSegments.push(`${duration.days()} ${days}`);
    }
    if (duration.hours()) {
      const hours = pluralize(duration.hours(), 'hour', 'hours');
      durationSegments.push(`${duration.hours()} ${hours}`);
    }
    if (duration.minutes()) {
      durationSegments.push(`${duration.minutes()} min`);
    }
    if (duration.seconds()) {
      durationSegments.push(`${duration.seconds()} sec`);
    }
    return durationSegments.slice(0, 2).join(' ');
  }

  /**
   * @param {number} unit
   * @param {string} singular
   * @param {string} plural
   * @return {string}
   */
  function pluralize(unit, singular, plural) {
    return unit === 1 ? singular : plural;
  }
})();
