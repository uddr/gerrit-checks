(function() {
  'use strict';

  const Defs = {};
  /**
   * @typedef {{
 *   id: string,
 *   projectId: string,
 *   checkerId: string,
 *   startTime: string,
 *   finishTime: string,
 * }}
   */
  Defs.Check;

  Polymer({
    is: 'gr-checks-item',

    properties: {
      check: Object,
      /** @type {function(string): !Promise<!Object>} */
      getChecker: Function,
      /** @type {function(string): !Promise<!Object>} */
      retryCheck: Function,
      _checkerDescription: String,
      _startTime: {
        type: String,
        computed: '_computeStartTime(check)',
      },
      _duration: {
        type: String,
        computed: '_computeDuration(check)',
      },
    },

    observers: [
      '_updateCheckerName(check, getChecker)',
    ],

    /**
     * @param {!Defs.Check} check
     * @param {function(string): !Promise<!Object>} getChecker
     */
    _updateCheckerName(check, getChecker) {
      const checkerId = check.checker_uuid;
      getChecker(checkerId).then(
          checker => checker && checker.description || checkerId,
          () => checkerId).then(checkerDescription => {
        this.set('_checkerDescription', checkerDescription);
      });
    },

    /**
     * @param {!Defs.Check} check
     * @return {string}
     */
    _computeStartTime(check) {
      return moment(check.created).format('l');
    },

    /**
     * @param {!Defs.Check} check
     * @return {string}
     */
    _computeDuration(check) {
      const startTime = moment(check.created);
      const finishTime = moment(check.updated);
      return generateDurationString(
          moment.duration(finishTime.diff(startTime)));
    },

    handleClick(event) {
      event.preventDefault();
      this.retryCheck(this.check.checker_uuid);
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
    if (duration.seconds()) {
      durationSegments.push(`${duration.seconds()} sec`);
    }
    if (duration.minutes()) {
      durationSegments.push(`${duration.minutes()} min`);
    }
    if (duration.hours()) {
      const hours = pluralize(duration.hours(), 'hour', 'hours');
      durationSegments.push(`${duration.hours()} ${hours}`);
    }
    if (duration.days()) {
      const days = pluralize(duration.days(), 'day', 'days');
      durationSegments.push(`${duration.days()} ${days}`);
    }
    if (duration.months()) {
      const months = pluralize(duration.months(), 'month', 'months');
      durationSegments.push(`${duration.months()} ${months}`);
    }
    return durationSegments.join(' ');
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
