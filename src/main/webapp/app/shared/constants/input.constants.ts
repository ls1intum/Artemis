export const DATE_FORMAT = 'YYYY-MM-DD';
export const DATE_TIME_FORMAT = 'YYYY-MM-DDTHH:mm';

// Note: The values in Constants.java (server) need to be the same

/** Maximum file size: 20 MB (Spring-Boot interprets MB as 1024^2 bytes) **/
export const MAX_FILE_SIZE = 20 * 1024 * 1024;
/** Maximum submission file size: 4 MB **/
export const MAX_SUBMISSION_FILE_SIZE = 8 * 1024 * 1024;
/** Maximum text exercise submission character length: 30.000 **/
export const MAX_SUBMISSION_TEXT_LENGTH = 30 * 1000;
/** Maximum quiz exercise short answer character length: 255 **/
export const MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH = 255; // Must be consistent with database column definition
/** Short names must start with a letter and cannot contain special characters **/
export const SHORT_NAME_PATTERN = /^[a-zA-Z][a-zA-Z0-9]{2,}$/;
/** Programming exercise titles must only contain alphanumeric characters, or whitespaces, or '_' or '-' **/
export const EXERCISE_TITLE_NAME_PATTERN = '^[a-zA-Z0-9-_ ]+';
export const EXERCISE_TITLE_NAME_REGEX = new RegExp(EXERCISE_TITLE_NAME_PATTERN);
/** Prefixes must follow the login pattern **/
export const LOGIN_PATTERN = /^[_'.@A-Za-z0-9-]*$/;
export const MAX_QUIZ_QUESTION_POINTS = 9999;
export const MAX_QUIZ_QUESTION_LENGTH_THRESHOLD = 250;
export const MAX_QUIZ_QUESTION_EXPLANATION_LENGTH_THRESHOLD = 500;
export const MAX_QUIZ_QUESTION_HINT_LENGTH_THRESHOLD = 255;
export const ASSIGNMENT_REPO_NAME = 'assignment';
export const TEST_REPO_NAME = 'tests';
