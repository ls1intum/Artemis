import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

@Pipe({ name: 'safeHtml' })
export class SafeHtmlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Bypasses the security checks for a specified HTML.
     * @param value The HTML that is considered safe.
     */
    transform(value: any) {
        return this.sanitizer.bypassSecurityTrustHtml(value);
    }
}
