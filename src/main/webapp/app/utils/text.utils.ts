export const escapeString = (input: string): string => input.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');

export const convertToHtmlLinebreaks = (input: string): string => input.replace(/(?:\r\n|\r|\n)/g, '<br>');
