import { Injectable } from '@angular/core';

const CHAR_CODE_TAB = 9;
const CHAR_CODE_LINE_FEED = 10;
const CHAR_CODE_CARRIAGE_RETURN = 13;
const CHAR_CODE_SPACE = 32;
const IGNORED_CHAR_CODES = [CHAR_CODE_TAB, CHAR_CODE_LINE_FEED, CHAR_CODE_CARRIAGE_RETURN];

const IMAGE_EXTENSIONS_TO_MIME_TYPE: Readonly<Record<string, string>> = {
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    bmp: 'image/bmp',
    webp: 'image/webp',
    svg: 'image/svg+xml',
    ico: 'image/x-icon',
};

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

    /**
     * Determines whether the given file name has an extension that is recognized as an image.
     * @param fileName The name (or path) of the file to check.
     */
    isImageFile(fileName: string): boolean {
        return this.getImageMimeType(fileName) !== undefined;
    }

    /**
     * Returns the image MIME type for a file name based on its extension, or undefined if the extension is not a recognized image type.
     * @param fileName The name (or path) of the file to check.
     */
    getImageMimeType(fileName: string): string | undefined {
        const extension = fileName.split('.').pop()?.toLowerCase();
        if (!extension) {
            return undefined;
        }
        return IMAGE_EXTENSIONS_TO_MIME_TYPE[extension];
    }
}
