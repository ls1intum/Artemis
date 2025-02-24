import { Injectable } from '@angular/core';

const CHAR_CODE_TAB = 9;
const CHAR_CODE_LINE_FEED = 10;
const CHAR_CODE_CARRIAGE_RETURN = 13;
const CHAR_CODE_SPACE = 32;
const IGNORED_CHAR_CODES = [CHAR_CODE_TAB, CHAR_CODE_LINE_FEED, CHAR_CODE_CARRIAGE_RETURN];
@Injectable({
    providedIn: 'root',
})
export class FileTypeService {
    /**
     * Determines for a string whether it represents the content of a binary file.
     * This is done by checking for characters that would not typically be found in plain text files, e.g. the 0-byte.
     * @param content The content to check.
     */
    isBinaryContent(content: string): boolean {
        for (let i = 0; i < content.length; i++) {
            const charCode = content.charCodeAt(i);
            // Check for control characters which are typically not found in text files.
            if (charCode < CHAR_CODE_SPACE && !IGNORED_CHAR_CODES.includes(charCode)) {
                return true;
            }
        }
        return false;
    }
}
