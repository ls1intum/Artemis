import { AfterViewInit, Component, ElementRef, OnDestroy, computed, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faChevronUp, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
// Lazy-load video.js at runtime; type-only import doesn't pull code into initial bundle.
import type videojs from 'video.js';

type VideoJsPlayer = ReturnType<typeof videojs>;

// cache the dynamically loaded module
let videoJsPlayerFactory: any;
function loadVideoJs(): Promise<any /* typeof videojs */> {
    if (videoJsPlayerFactory) {
        return Promise.resolve(videoJsPlayerFactory);
    }
    return import('video.js').then((mod) => {
        videoJsPlayerFactory = (mod as any).default ?? mod;
        return videoJsPlayerFactory;
    });
}

/**
 * A transcript segment corresponding to a portion of the video.
 */
export interface TranscriptSegment {
    startTime: number;
    endTime: number;
    text: string;
    slideNumber?: number;
}

@Component({
    selector: 'jhi-video-player',
    standalone: true,
    imports: [CommonModule, FormsModule, FaIconComponent],
    templateUrl: './video-player.component.html',
    styleUrls: ['./video-player.component.scss'],
})
export class VideoPlayerComponent implements AfterViewInit, OnDestroy {
    /** Reference to the <video> element in the template */
    videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoRef');

    /** The URL of the video to play (required input) */
    videoUrl = input<string | undefined>();

    /** Transcript segments to highlight and sync */
    transcriptSegments = input<TranscriptSegment[]>([]);

    /** The Video.js player instance (set once created) */
    private player: VideoJsPlayer | null = null;

    /** Track the index of the currently active transcript segment */
    currentSegmentIndex = signal<number>(-1);

    /** Search query for filtering transcript segments */
    searchQuery = signal<string>('');

    /** Current search result index */
    currentSearchIndex = signal<number>(0);

    /** Timestamp of last auto-scroll to prevent excessive scrolling */
    private lastAutoScrollTime = 0;

    /** Minimum delay (ms) between auto-scrolls during video playback */
    private readonly AUTO_SCROLL_THROTTLE_MS = 1000;

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

    ngAfterViewInit(): void {
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : null;
        const src = this.videoUrl();

        if (!videoElement || !src) {
            return;
        }

        // Initialize Video.js lazily
        loadVideoJs().then((videojsFn) => {
            const player: VideoJsPlayer = videojsFn(videoElement, {
                controls: true,
                preload: 'auto',
                sources: [{ src, type: 'application/x-mpegURL' }],
            });

            // store instance after creation
            this.player = player;

            // Safe: 'player' is definitely defined in this scope
            player.on('timeupdate', () => {
                const v = typeof player.currentTime === 'function' ? player.currentTime() : 0;
                const currentTime: number = typeof v === 'number' && !Number.isNaN(v) ? v : 0;
                this.updateCurrentSegment(currentTime);
            });
        });
    }

    /** Seek the video to the given time and resume playback. */
    seekTo(seconds: number): void {
        if (!this.player) {
            return;
        }
        this.player.currentTime(seconds);
        this.player.play();
    }

    /**
     * Updates the `currentSegmentIndex` signal based on playback time.
     * Scrolls the active transcript line into view, but throttled to prevent performance issues.
     */
    updateCurrentSegment(currentTime: number): void {
        const margin = 0.3; // tolerance
        const segments = this.transcriptSegments();
        const index = segments.findIndex((s) => currentTime >= s.startTime - margin && currentTime <= s.endTime + margin);

        if (index !== -1 && index !== this.currentSegmentIndex()) {
            this.currentSegmentIndex.set(index);

            // Throttle auto-scrolling to prevent excessive calls (performance optimization)
            const now = Date.now();
            if (now - this.lastAutoScrollTime >= this.AUTO_SCROLL_THROTTLE_MS) {
                this.lastAutoScrollTime = now;
                const el = document.getElementById(`segment-${segments[index].startTime}`);
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            }
        }
    }

    /** Clean up on destroy. */
    ngOnDestroy(): void {
        if (this.player) {
            this.player.dispose();
            this.player = null;
        }
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
        const el = document.getElementById(`segment-${segment.startTime}`);
        if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    /** Highlight search matches in segment text */
    highlightText(text: string): string {
        // Escape HTML entities FIRST to prevent XSS attacks
        const escapedText = this.escapeHtml(text);

        const query = this.searchQuery().trim();
        if (!query) {
            return escapedText;
        }

        // Escape the query as well to prevent XSS in search terms
        const escapedQuery = this.escapeHtml(query);

        // Use escaped query for regex matching
        const regex = new RegExp(`(${this.escapeRegExp(escapedQuery)})`, 'gi');
        const highlighted = escapedText.replace(regex, '<mark>$1</mark>');

        // Safe to return: all user content is escaped, only <mark> tags are intentional HTML
        // Angular's [innerHTML] will preserve <mark> while keeping escaped HTML entities safe
        return highlighted;
    }

    /** Escape HTML special characters to prevent XSS */
    private escapeHtml(text: string): string {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
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
}
