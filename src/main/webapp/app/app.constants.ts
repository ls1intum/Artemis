// These constants are injected via webpack environment variables.
// You can add more variables in webpack.common.ts or in profile specific webpack.<dev|prod>.ts files.
// If you change the values in the webpack config files, you need to re run webpack to update the application

export const VERSION = process.env.VERSION;
// TODO: this value is undefined due to the latest webpack update
export const DEBUG_INFO_ENABLED = Boolean(process.env.DEBUG_INFO_ENABLED);
// TODO: this value is undefined due to the latest webpack update
// export const SERVER_API_URL = process.env.SERVER_API_URL ?? '';
export const SERVER_API_URL = '';

export const MIN_SCORE_GREEN = 80;
export const MIN_SCORE_ORANGE = 40;

// NOTE: those values have to be the same as in Constant.java
export const USERNAME_MIN_LENGTH = 4;
export const USERNAME_MAX_LENGTH = 50;
export const PASSWORD_MIN_LENGTH = 4;
export const PASSWORD_MAX_LENGTH = 50;

export const EXAM_START_WAIT_TIME_MINUTES = 5;

export const SCORE_PATTERN = '^[0-9]{1,2}$|^100$';

export const ARTEMIS_DEFAULT_COLOR = '#3E8ACC';
export const ARTEMIS_VERSION_HEADER = 'Content-Version';
