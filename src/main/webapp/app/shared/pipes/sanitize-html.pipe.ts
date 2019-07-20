import { Pipe, PipeTransform } from '@angular/core';
import * as DOMPurify from 'dompurify';

@Pipe({
    name: 'sanitizeHtml',
})
export class SanitizeHtmlPipe implements PipeTransform {
    transform(html: string): string | null {
        return html ? DOMPurify.sanitize(html) : null;
    }
}
