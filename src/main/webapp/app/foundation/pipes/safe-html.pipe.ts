import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';

@Pipe({ name: 'safeHtml' })
export class SafeHtmlPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    /**
     * Sanitizes the given HTML with DOMPurify and marks the result as trusted so Angular renders it via
     * [innerHTML]. Sanitizing inside the pipe makes it safe regardless of the caller: a caller that passes
     * user-controlled HTML can no longer introduce XSS through this pipe. DOMPurify keeps benign inline
     * markup and entities (e.g. <sub>, <sup>, <strong>, &infin;) that existing callers rely on.
     * @param value The HTML to sanitize and render.
     */
    transform(value: string): SafeHtml {
        return this.sanitizer.bypassSecurityTrustHtml(DOMPurify.sanitize(value ?? ''));
    }
}
