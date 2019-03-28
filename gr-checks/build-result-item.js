(function() {
'use strict';

const Defs = {};
/**
 * @typedef {{
 *   id: string,
 *   projectId: string,
 *   buildTriggerId: string,
 *   startTime: string,
 *   finishTime: string,
 * }}
 */
Defs.Build;

Polymer({
  is: 'build-result-item',

  properties: {
    build: Object,
    /** @type {function(string): !Promise<!Object>} */
    getTrigger: Function,
    /** @type {function(string): !Promise<!Object>} */
    retryBuild: Function,
    _triggerDescription: String,
    _startTime: {
      type: String,
      computed: '_computeStartTime(build)',
    },
    _duration: {
      type: String,
      computed: '_computeDuration(build)',
    },
  },

  observers: [
    '_updateTriggerName(build, getTrigger)',
  ],

  /**
   * @param {!Defs.Build} build
   * @param {function(string): !Promise<!Object>} getTrigger
   */
  _updateTriggerName(build, getTrigger) {
    const triggerId = build.buildTriggerId;
    getTrigger(triggerId)
        .then(
            trigger => trigger && trigger.description || triggerId,
            () => triggerId)
        .then(triggerDescription => {
          this.set('_triggerDescription', triggerDescription);
        });
  },

  /**
   * @param {!Defs.Build} build
   * @return {string}
   */
  _computeStartTime(build) {
    return moment(build.startTime).format('l');
  },

  /**
   * @param {!Defs.Build} build
   * @return {string}
   */
  _computeDuration(build) {
    const startTime = moment(build.startTime);
    const finishTime = moment(build.finishTime);
    return generateDurationString(moment.duration(finishTime.diff(startTime)));
  },

  handleClick(event) {
    event.preventDefault();
    this.retryBuild(this.build.id);
  }
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
