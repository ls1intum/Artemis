import { Component, computed, input } from '@angular/core';

interface HighlightSegment {
    text: string;
    match: boolean;
}

/**
 * Highlights every (case-insensitive) occurrence of a search term within a string by wrapping the
 * matched parts in a styled span. Drop-in replacement for ng-bootstrap's `NgbHighlight`.
 *
 * The text is rendered via interpolation (never `innerHTML`), so the result is inherently XSS-safe.
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
    /** The search term whose occurrences should be highlighted. */
    readonly term = input<string | undefined>('');

    /** Splits {@link result} into alternating non-matching / matching segments based on {@link term}. */
    protected readonly segments = computed<HighlightSegment[]>(() => {
        const text = String(this.result() ?? '');
        const term = (this.term() ?? '').trim();
        if (!term) {
            return [{ text, match: false }];
        }

        const segments: HighlightSegment[] = [];
        const lowerText = text.toLowerCase();
        const lowerTerm = term.toLowerCase();
        let index = 0;
        let matchIndex = lowerText.indexOf(lowerTerm, index);
        while (matchIndex !== -1) {
            if (matchIndex > index) {
                segments.push({ text: text.slice(index, matchIndex), match: false });
            }
            segments.push({ text: text.slice(matchIndex, matchIndex + term.length), match: true });
            index = matchIndex + term.length;
            matchIndex = lowerText.indexOf(lowerTerm, index);
        }
        if (index < text.length) {
            segments.push({ text: text.slice(index), match: false });
        }
        return segments;
    });
}
