// These constants are injected via webpack environment variables.
// You can add more variables in webpack.common.js or in profile specific webpack.<dev|prod>.js files.
// If you change the values in the webpack config files, you need to re run webpack to update the application

export const VERSION = process.env.VERSION;
export const DEBUG_INFO_ENABLED: boolean = !!process.env.DEBUG_INFO_ENABLED;
export const SERVER_API_URL = process.env.SERVER_API_URL;
export const BUILD_TIMESTAMP = process.env.BUILD_TIMESTAMP;

export const MIN_POINTS_GREEN = 80;
export const MIN_POINTS_ORANGE = 40;

export const TUM_REGEX = /^([a-z]{2}\d{2}[a-z]{3})$/;

export const ARTEMIS_DEFAULT_COLOR = '#3E8ACC';
