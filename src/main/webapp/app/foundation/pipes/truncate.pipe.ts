import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'truncate' })
export class TruncatePipe implements PipeTransform {
    /**
     * Truncate a given text.
     * @param value The text to be truncated.
     * @param limit The length of the truncated text. The default value is 25 chars.
     * @param completeWords Whether or not to maintain the complete word. The default is false.
     * @param ellipsis The symbol to show truncation. Default symbol is '...'
     */
    transform(value: string, limit = 25, completeWords = false, ellipsis = '...') {
        if (completeWords) {
            if (value.slice(0, limit).indexOf(' ') >= 0) {
                limit = value.slice(0, limit).lastIndexOf(' ');
            }
        }
        if (value.length <= limit) {
            ellipsis = '';
        }
        return `${value.slice(0, limit)}${ellipsis}`;
    }
}
