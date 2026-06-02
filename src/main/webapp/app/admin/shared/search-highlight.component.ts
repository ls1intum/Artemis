import { Component, computed, input } from '@angular/core';

interface HighlightSegment {
    text: string;
    match: boolean;
}

/** Escapes a string so it can be embedded literally inside a RegExp (so the term is matched verbatim, not as a pattern). */
function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Highlights every case-insensitive occurrence of a search term within a string by wrapping the matched
 * parts in a styled span. Modeled on ng-bootstrap's `NgbHighlight` for single-string terms (the only form
 * used in admin user search); array terms and accent-insensitive matching are intentionally not supported.
 *
 * The term is matched literally (regex-special characters are escaped) against the original string, and the
 * text is rendered via interpolation (never `innerHTML`), so the result is inherently XSS-safe.
 */
@Component({
    selector: 'jhi-search-highlight',
    template: `@for (segment of segments(); track $index) {
        <span [class.search-highlight]="segment.match">{{ segment.text }}</span>
    }`,
    styles: ['.search-highlight { font-weight: 700; }'],
})
export class SearchHighlightComponent {
    /** The full text to display. */
    readonly result = input<string | number | undefined>('');
    /** The search term whose occurrences should be highlighted (not trimmed, matching NgbHighlight). */
    readonly term = input<string | undefined>('');

    /** Splits {@link result} into alternating non-matching / matching segments based on {@link term}. */
    protected readonly segments = computed<HighlightSegment[]>(() => {
        const text = String(this.result() ?? '');
        const term = this.term() ?? '';
        if (!term) {
            return [{ text, match: false }];
        }

        // Match against the ORIGINAL text (not a lowercased copy) so slice indices stay valid even for
        // characters whose length changes when lowercased; case-insensitivity comes from the 'i' flag.
        const regex = new RegExp(escapeRegExp(term), 'gi');
        const segments: HighlightSegment[] = [];
        let lastIndex = 0;
        let match: RegExpExecArray | null;
        while ((match = regex.exec(text)) !== null) {
            if (match.index > lastIndex) {
                segments.push({ text: text.slice(lastIndex, match.index), match: false });
            }
            segments.push({ text: match[0], match: true });
            lastIndex = match.index + match[0].length;
            // Defensive guard against a zero-length match to avoid an infinite loop.
            if (match[0].length === 0) {
                regex.lastIndex++;
            }
        }
        if (lastIndex < text.length) {
            segments.push({ text: text.slice(lastIndex), match: false });
        }
        return segments;
    });
}
