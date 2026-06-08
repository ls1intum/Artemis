import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Highlights all occurrences of a query string within text by wrapping them in <strong> tags.
 * Output is sanitized SafeHtml — use with [innerHTML] only.
 */
@Pipe({
    name: 'highlightMatch',
    standalone: true,
    pure: true,
})
export class HighlightMatchPipe implements PipeTransform {
    private readonly sanitizer = inject(DomSanitizer);

    transform(text: string | undefined, query: string | undefined): SafeHtml {
        if (!text) return '';
        const escaped = this.escapeHtml(text);
        if (!query?.trim()) return this.sanitizer.bypassSecurityTrustHtml(escaped);

        const safeQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const highlighted = escaped.replace(new RegExp(safeQuery, 'gi'), (match) => `<strong>${match}</strong>`);
        return this.sanitizer.bypassSecurityTrustHtml(highlighted);
    }

    private escapeHtml(text: string): string {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }
}
