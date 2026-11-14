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
        // Reviewed false positive (Sonar S6268): the text is HTML-escaped by escapeHtml before it is trusted.
        if (!query?.trim()) return this.sanitizer.bypassSecurityTrustHtml(escaped); // NOSONAR // nosemgrep

        const safeQuery = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const highlighted = escaped.replace(new RegExp(safeQuery, 'gi'), (match) => `<strong>${match}</strong>`);
        // Reviewed false positive (Sonar S6268): the text is HTML-escaped and the query is regex-escaped, the only markup added is <strong>.
        return this.sanitizer.bypassSecurityTrustHtml(highlighted); // NOSONAR // nosemgrep
    }

    private escapeHtml(text: string): string {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }
}
