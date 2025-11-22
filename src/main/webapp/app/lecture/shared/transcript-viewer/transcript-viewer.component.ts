import { Component, computed, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslatePipe } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { escapeString } from 'app/shared/util/text.utils';

import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

@Component({
    selector: 'jhi-transcript-viewer',
    standalone: true,
    imports: [CommonModule, FormsModule, FaIconComponent, TranslatePipe, TranslateDirective],
    templateUrl: './transcript-viewer.component.html',
    styleUrls: ['./transcript-viewer.component.scss'],
})
export class TranscriptViewerComponent {
    /** Transcript segments to display */
    transcriptSegments = input<TranscriptSegment[]>([]);

    /** Currently active segment index (from parent) */
    currentSegmentIndex = input<number>(-1);

    /** Event emitted when user clicks on a transcript segment */
    segmentClicked = output<number>();

    /** Search query for filtering transcript segments */
    searchQuery = signal<string>('');

    /** Current search result index */
    currentSearchIndex = signal<number>(0);

    /** Icons for search UI */
    protected readonly faSearch = faSearch;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faTimes = faTimes;

    /** Filtered transcript segments based on search query */
    filteredSegments = computed(() => this.computeFilteredSegments());

    /** Total number of search results */
    searchResultsCount = computed(() => this.filteredSegments().length);

    /** Check if search is active */
    isSearchActive = computed(() => this.searchQuery().length > 0);

    /**
     * Computes the filtered transcript segments based on the current search query.
     * Returns all segments if no search query is active, otherwise returns only matching segments.
     */
    private computeFilteredSegments(): TranscriptSegment[] {
        const query = this.searchQuery().toLowerCase().trim();
        if (!query) {
            return this.transcriptSegments();
        }
        return this.transcriptSegments().filter((segment) => segment.text.toLowerCase().includes(query));
    }

    /** Update search query and reset to first result */
    onSearchQueryChange(query: string): void {
        this.searchQuery.set(query);
        this.currentSearchIndex.set(0);

        // Auto-scroll to first result if exists
        if (this.filteredSegments().length > 0) {
            this.scrollToSearchResult(0);
        }
    }

    /** Clear search query */
    clearSearch(): void {
        this.searchQuery.set('');
        this.currentSearchIndex.set(0);
    }

    /** Navigate to next search result */
    nextSearchResult(): void {
        const total = this.searchResultsCount();
        if (total === 0) {
            return;
        }

        const nextIndex = (this.currentSearchIndex() + 1) % total;
        this.currentSearchIndex.set(nextIndex);
        this.scrollToSearchResult(nextIndex);
    }

    /** Navigate to previous search result */
    previousSearchResult(): void {
        const total = this.searchResultsCount();
        if (total === 0) {
            return;
        }

        const prevIndex = (this.currentSearchIndex() - 1 + total) % total;
        this.currentSearchIndex.set(prevIndex);
        this.scrollToSearchResult(prevIndex);
    }

    /** Scroll to a specific search result */
    private scrollToSearchResult(index: number): void {
        const filtered = this.filteredSegments();
        if (index < 0 || index >= filtered.length) {
            return;
        }

        const segment = filtered[index];
        this.scrollToSegmentElement(segment);
    }

    /** Highlight search matches in segment text */
    highlightText(text: string): string {
        // Escape HTML entities FIRST to prevent XSS attacks
        const escapedText = escapeString(text);

        const query = this.searchQuery().trim();
        if (!query) {
            return escapedText;
        }

        // Escape the query as well to prevent XSS in search terms
        const escapedQuery = escapeString(query);

        // Use escaped query for regex matching
        const regex = new RegExp(`(${this.escapeRegExp(escapedQuery)})`, 'gi');
        const highlighted = escapedText.replace(regex, '<mark>$1</mark>');

        // Safe to return: all user content is escaped, only <mark> tags are intentional HTML
        // Angular's [innerHTML] will preserve <mark> while keeping escaped HTML entities safe
        return highlighted;
    }

    /** Escape special regex characters */
    private escapeRegExp(text: string): string {
        return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    /** Check if segment matches current search result */
    isCurrentSearchResult(segment: TranscriptSegment): boolean {
        if (!this.isSearchActive()) {
            return false;
        }
        const filtered = this.filteredSegments();
        const currentIndex = this.currentSearchIndex();
        return filtered[currentIndex]?.startTime === segment.startTime;
    }

    /** Called when user clicks on a segment */
    onSegmentClick(startTime: number): void {
        this.segmentClicked.emit(startTime);
    }

    /**
     * Scrolls to the segment at the given index.
     * Called by parent component during video playback.
     */
    scrollToSegment(index: number): void {
        const segments = this.transcriptSegments();
        if (index < 0 || index >= segments.length) {
            return;
        }

        const segment = segments[index];
        this.scrollToSegmentElement(segment);
    }

    /**
     * Common helper method to scroll to a segment element.
     * Encapsulates the DOM element lookup and scrollIntoView logic.
     */
    private scrollToSegmentElement(segment: TranscriptSegment): void {
        const el = document.getElementById(`segment-${segment.startTime}`);
        if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }
}
