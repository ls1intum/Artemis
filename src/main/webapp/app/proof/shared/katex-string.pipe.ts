import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import katex from 'katex';

@Pipe({ name: 'katexString', standalone: true })
export class KatexStringPipe implements PipeTransform {
    private sanitizer = inject(DomSanitizer);

    transform(latex: string | undefined): SafeHtml {
        if (!latex) return '';
        try {
            const html = katex.renderToString(latex, { throwOnError: false, displayMode: false });
            return this.sanitizer.bypassSecurityTrustHtml(html);
        } catch {
            return latex;
        }
    }
}
