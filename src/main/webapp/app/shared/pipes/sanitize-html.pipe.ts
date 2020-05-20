import { Pipe, PipeTransform } from '@angular/core';
import * as DOMPurify from 'dompurify';

@Pipe({
    name: 'sanitizeHtml',
})
export class SanitizeHtmlPipe implements PipeTransform {
    /**
     * Sanitize the given HTML string to prevent XSS attacks.
     * @param html that should be sanitized of type {string}
     * @returns {string}
     */
    transform(html: string): string | null {
        return html ? DOMPurify.sanitize(html) : null;
    }
}
