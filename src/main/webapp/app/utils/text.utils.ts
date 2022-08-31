export const escapeString = (input: string): string => input.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');

export const convertToHtmlLinebreaks = (input: string): string => input.replace(/(?:\r\n|\r|\n)/g, '<br>');

/**
 * If the string has more characters than the defined maximum length, the string is cut and an ellipsis is added.
 * @param input The string to shorten (if needed)
 * @param maxLength The maximum length in characters
 */
export const abbreviateString = (input: string, maxLength: number): string => {
    return input.length > maxLength ? input.substring(0, maxLength) + '…' : input;
};
