export const DATE_FORMAT = 'YYYY-MM-DD';
export const DATE_TIME_FORMAT = 'YYYY-MM-DDTHH:mm';
/** Maximum File Size: 20 MB (Spring-Boot interprets MB as 1024^2 bytes) **/
// Note: The values in Constants.java (server) need to be the same
export const MAX_FILE_SIZE = 20 * 1024 * 1024;
/** Maximum submission File Size: 4 MB **/
export const MAX_SUBMISSION_FILE_SIZE = 8 * 1024 * 1024;
/** Short names must start with a letter and cannot contain special characters **/
export const shortNamePattern = /^[a-zA-Z][a-zA-Z0-9]{2,}$/;
