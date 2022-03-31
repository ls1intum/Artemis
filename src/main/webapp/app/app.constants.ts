// These constants are injected via webpack environment variables.
// If you change the values in the webpack config files, you need to re run webpack to update the application

declare const __DEBUG_INFO_ENABLED__: boolean;
declare const __VERSION__: string;

export const VERSION = __VERSION__;
export const DEBUG_INFO_ENABLED = __DEBUG_INFO_ENABLED__;

export const MIN_SCORE_GREEN = 80;
export const MIN_SCORE_ORANGE = 40;

// NOTE: those values have to be the same as in Constants.java
export const USERNAME_MIN_LENGTH = 4;
export const USERNAME_MAX_LENGTH = 50;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 50;

// NOTE: These constants are used to define the maximum length of complaints and complaint responses.
// This is the maximum value allowed in our database. These values must be the same as in Constants.java
export const COMPLAINT_RESPONSE_TEXT_LIMIT = 5000;
export const COMPLAINT_TEXT_LIMIT = 5000;

// NOTE: These constants are used to define the length of the default complaint and complaint response.
export const DEFAULT_COMPLAINT_RESPONSE_TEXT_LIMIT = 2000;
export const DEFAULT_COMPLAINT_TEXT_LIMIT = 2000;

export const EXAM_START_WAIT_TIME_MINUTES = 5;

export const SCORE_PATTERN = '^[0-9]{1,2}$|^100$';

export const ARTEMIS_DEFAULT_COLOR = '#3E8ACC';
export const ARTEMIS_VERSION_HEADER = 'Content-Version';
