import { AfterViewInit, Component, ElementRef, OnDestroy, computed, input, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { escapeString } from 'app/shared/util/text.utils';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

@Component({
    selector: 'jhi-transcript-viewer',
    standalone: true,
    imports: [CommonModule, FormsModule, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    templateUrl: './transcript-viewer.component.html',
    styleUrls: ['./transcript-viewer.component.scss'],
})
export class TranscriptViewerComponent implements AfterViewInit, OnDestroy {
    /** Transcript segments to display */
    transcriptSegments = input<TranscriptSegment[]>([]);

    /** Currently active segment index (from parent) */
    currentSegmentIndex = input<number>(-1);

    /** Event emitted when user clicks on a transcript segment */
    segmentClicked = output<number>();

    /** Reference to the root transcript container */
    private transcriptColumnRef = viewChild<ElementRef<HTMLElement>>('transcriptColumn');

    /** Reference to the scrollable transcript list container */
    private transcriptListRef = viewChild<ElementRef<HTMLElement>>('transcriptList');

    /** Current transcript column width in pixels */
    readonly transcriptColumnWidthPx = signal<number>(0);

    /** Search query for filtering transcript segments */
    readonly searchQuery = signal<string>('');

    /** Current search result index */
    readonly currentSearchIndex = signal<number>(0);

    /** Icons for search UI */
    protected readonly faSearch = faSearch;
    protected readonly faChevronUp = faChevronUp;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faTimes = faTimes;

    /** Filtered transcript segments based on search query */
    readonly filteredSegments = computed(() => this.computeFilteredSegments());

    /** Total number of search results */
    readonly searchResultsCount = computed(() => this.filteredSegments().length);

    /** Check if search is active */
    readonly isSearchActive = computed(() => this.searchQuery().length > 0);

    /** Whether the transcript currently has little horizontal space */
    readonly isNarrowColumn = computed(() => this.transcriptColumnWidthPx() > 0 && this.transcriptColumnWidthPx() < 420);

    private resizeObserver?: ResizeObserver;
    private resizeAnimationFrameId?: number;

    /** Observes transcript container width to support compact controls on narrow columns. */
    ngAfterViewInit(): void {
        const transcriptColumn = this.transcriptColumnRef()?.nativeElement;
        if (!transcriptColumn || typeof ResizeObserver === 'undefined') {
            return;
        }

        this.transcriptColumnWidthPx.set(transcriptColumn.getBoundingClientRect().width);
        this.resizeObserver = new ResizeObserver((entries) => {
            if (this.resizeAnimationFrameId !== undefined) {
                window.cancelAnimationFrame(this.resizeAnimationFrameId);
            }
            this.resizeAnimationFrameId = window.requestAnimationFrame(() => {
                const width = entries[0]?.contentRect.width ?? transcriptColumn.getBoundingClientRect().width;
                this.transcriptColumnWidthPx.set(width);
                this.resizeAnimationFrameId = undefined;
            });
        });
        this.resizeObserver.observe(transcriptColumn);
    }

    /** Cleans up ResizeObserver and pending animation frame callbacks. */
    ngOnDestroy(): void {
        this.resizeObserver?.disconnect();
        if (this.resizeAnimationFrameId !== undefined) {
            window.cancelAnimationFrame(this.resizeAnimationFrameId);
            this.resizeAnimationFrameId = undefined;
        }
    }

    /**
     * Computes the filtered transcript segments based on the current search query.
     * Returns all segments if no search query is active, otherwise returns only matching segments.
     */
    private computeFilteredSegments(): TranscriptSegment[] {
        const segments = this.transcriptSegments();
        const query = this.searchQuery().toLowerCase().trim();
        if (!query) {
            return segments;
        }
        return segments.filter((segment) => segment.text.toLowerCase().includes(query));
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
        this.navigateSearchResults(1);
    }

    /** Navigate to previous search result */
    previousSearchResult(): void {
        this.navigateSearchResults(-1);
    }

    /** Navigates search results with wrap-around behavior in both directions. */
    private navigateSearchResults(direction: 1 | -1): void {
        const total = this.searchResultsCount();
        if (total === 0) {
            return;
        }

        const nextIndex = (this.currentSearchIndex() + direction + total) % total;
        this.currentSearchIndex.set(nextIndex);
        this.scrollToSearchResult(nextIndex);
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
        const escapedText = escapeString(text);
        const query = this.searchQuery().trim();
        if (!query) {
            return escapedText;
        }

        const escapedQuery = escapeString(query);
        const regex = new RegExp(`(${this.escapeRegExp(escapedQuery)})`, 'gi');
        return escapedText.replace(regex, '<mark>$1</mark>');
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
     * Common helper method to scroll to a segment element within the transcript list container.
     * Uses container-scoped scrolling to avoid scrolling the full page.
     */
    private scrollToSegmentElement(segment: TranscriptSegment): void {
        const container = this.transcriptListRef()?.nativeElement;
        if (!container) {
            return;
        }

        const segmentElement = container.querySelector<HTMLElement>(`#segment-${segment.startTime}`);
        if (!segmentElement) {
            return;
        }

        const centeredTop = segmentElement.offsetTop - container.clientHeight / 2 + segmentElement.clientHeight / 2;
        const top = Math.max(0, Math.min(centeredTop, container.scrollHeight - container.clientHeight));
        container.scrollTo({ top, behavior: 'smooth' });
    }
}
