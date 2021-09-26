import { Pipe, PipeTransform } from '@angular/core';
import { sanitize } from 'dompurify';

@Pipe({
    name: 'sanitizeHtml',
})
export class SanitizeHtmlPipe implements PipeTransform {
    transform(html: string): string | null {
        return html ? sanitize(html) : null;
    }
}
