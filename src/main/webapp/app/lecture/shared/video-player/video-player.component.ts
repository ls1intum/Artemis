import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranscriptViewerComponent } from '../transcript-viewer/transcript-viewer.component';
import Hls from 'hls.js';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableConstraints, ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';

import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

/** Minimum width of the video column in pixels. */
const MIN_VIDEO_WIDTH = 300;
/** Minimum width reserved for the transcript column in pixels. */
const MIN_TRANSCRIPT_WIDTH = 250;

@Component({
    selector: 'jhi-video-player',
    standalone: true,
    imports: [CommonModule, TranscriptViewerComponent, FaIconComponent, ArtemisTranslatePipe, ResizableDirective],
    templateUrl: './video-player.component.html',
    styleUrls: ['./video-player.component.scss'],
})
export class VideoPlayerComponent implements AfterViewInit, OnDestroy {
    /** Reference to the <video> element in the template */
    videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoRef');

    /** Reference to the video wrapper container */
    videoWrapper = viewChild<ElementRef<HTMLDivElement>>('videoWrapper');

    /** Reference to the video column for resizing */
    videoColumn = viewChild<ElementRef<HTMLDivElement>>('videoColumn');

    /** Reference to the resizer handle */
    resizerHandle = viewChild<ElementRef<HTMLButtonElement>>('resizerHandle');

    /** The URL of the video to play (required input) */
    videoUrl = input<string | undefined>();

    /** Transcript segments to highlight and sync */
    transcriptSegments = input<TranscriptSegment[]>([]);

    /** Optional timestamp to seek to once the player is ready */
    initialTimestamp = input<number | undefined>(undefined);

    /** Active slide/page number inferred from the transcript */
    currentSlideNumberChange = output<number | undefined>();

    /** The HLS.js instance */
    private hls: Hls | undefined = undefined;

    /** Track the index of the currently active transcript segment */
    currentSegmentIndex = signal<number>(-1);
    private readonly currentSlideNumber = signal<number | undefined>(undefined);

    /** Reference to the transcript viewer component */
    transcriptViewer = viewChild(TranscriptViewerComponent);

    /** Store reference to timeupdate handler for cleanup */
    private timeupdateHandler: (() => void) | undefined = undefined;

    /** FontAwesome icon for the resizer grip */
    faGripLinesVertical = faGripLinesVertical;

    /** True while the divider is being dragged; disables the video's pointer events so the drag stays smooth. */
    protected readonly isResizing = signal<boolean>(false);

    /** Store reference to window resize handler for cleanup */
    private resizeHandler: (() => void) | undefined = undefined;

    /** Store reference to loadedmetadata handler for cleanup */
    private loadedmetadataHandler: (() => void) | undefined = undefined;

    /** ResizeObserver for syncing transcript height with video column */
    private resizeObserver: ResizeObserver | undefined = undefined;

    /** Minimum height for the transcript column */
    private readonly MIN_TRANSCRIPT_HEIGHT = 500;

    /**
     * Width constraints for the resizable video column. The maximum width is derived from the live
     * wrapper width so the transcript always keeps at least {@link MIN_TRANSCRIPT_WIDTH} px of space.
     * When the wrapper is too narrow to fit both columns at their minimums, the maximum is pinned to
     * the minimum so dragging cannot squeeze the transcript below its reserved space.
     */
    protected get resizableConstraints(): ResizableConstraints {
        const wrapperWidth = this.videoWrapper()?.nativeElement.getBoundingClientRect().width ?? 0;
        const maxWidth = Math.max(MIN_VIDEO_WIDTH, wrapperWidth - MIN_TRANSCRIPT_WIDTH);
        return { minWidth: MIN_VIDEO_WIDTH, maxWidth };
    }

    private viewReady = signal<boolean>(false);
    private lastInitialTimestamp: number | undefined;
    private pendingInitialSeek: number | undefined;

    constructor() {
        effect(() => {
            if (!this.viewReady()) {
                return;
            }

            const timestamp = this.initialTimestamp();
            if (timestamp === undefined || !Number.isFinite(timestamp) || timestamp < 0) {
                this.lastInitialTimestamp = undefined;
                this.pendingInitialSeek = undefined;
                return;
            }

            if (this.lastInitialTimestamp === timestamp) {
                return;
            }

            const videoElement = this.videoRef()?.nativeElement;
            if (!videoElement) {
                return;
            }

            this.lastInitialTimestamp = timestamp;
            this.queueInitialSeek(videoElement, timestamp);
        });
    }

    ngAfterViewInit(): void {
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : undefined;
        const src = this.videoUrl();

        this.viewReady.set(true);

        if (!videoElement || !src) {
            return;
        }

        // Initialize HLS.js for .m3u8 files
        if (Hls.isSupported()) {
            this.hls = new Hls();
            this.hls.loadSource(src);
            this.hls.attachMedia(videoElement);

            // Handle HLS errors
            this.hls.on(Hls.Events.ERROR, (_event, data) => {
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            // Try to recover from network error
                            this.hls?.startLoad();
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            // Try to recover from media error
                            this.hls?.recoverMediaError();
                            break;
                        default:
                            // Fatal error, cannot recover
                            this.hls?.destroy();
                            this.hls = undefined;
                            break;
                    }
                }
            });
        } else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari)
            videoElement.src = src;
        }

        // Listen to timeupdate events to sync transcript
        this.timeupdateHandler = () => {
            this.updateCurrentSegment(videoElement.currentTime);
        };
        videoElement.addEventListener('timeupdate', this.timeupdateHandler);

        // Initialize transcript height syncing for the resizable panel
        this.initializeResizer();
    }

    /**
     * Wires up the window-resize listener and ResizeObserver that keep the transcript column height in
     * sync with the video column. The drag handling itself is provided by the {@link ResizableDirective}.
     */
    private initializeResizer(): void {
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const wrapperEl = this.videoWrapper()?.nativeElement;

        if (!videoColumnEl || !wrapperEl) {
            return;
        }

        // On window resize, sync transcript height
        this.resizeHandler = () => {
            this.syncTranscriptHeight();
        };
        window.addEventListener('resize', this.resizeHandler);

        // Use ResizeObserver to reliably sync transcript height whenever video column size changes
        this.resizeObserver = new ResizeObserver(() => {
            this.syncTranscriptHeight();
        });
        this.resizeObserver.observe(videoColumnEl);
    }

    /** Disables the video's pointer events while the divider is being dragged so the drag stays smooth. */
    protected onResizeStart(): void {
        this.isResizing.set(true);
    }

    /** Re-enables the video's pointer events once the drag finishes. */
    protected onResizeEnd(): void {
        this.isResizing.set(false);
    }

    /**
     * Applies a resize from the {@link ResizableDirective} to the video column as a percentage-based flex-basis.
     * The directive runs with `resizableApplyInlineSize=false`: it would otherwise write an inline `width`, which a
     * `flex: 3` column ignores (flex-basis 0% wins over width), so the divider would not actually move. We translate
     * the clamped px width into `flex: 0 0 <percent>%` so the split changes and still scales naturally when the
     * wrapper is resized (matching the previous interact.js behaviour).
     */
    protected onVideoColumnResize(event: ResizableSizeEvent): void {
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const wrapperEl = this.videoWrapper()?.nativeElement;
        if (!videoColumnEl || !wrapperEl) {
            return;
        }
        const wrapperWidth = wrapperEl.getBoundingClientRect().width;
        if (wrapperWidth <= 0) {
            return;
        }
        const percent = Math.min(100, Math.max(0, (event.width / wrapperWidth) * 100));
        videoColumnEl.style.flex = `0 0 ${percent}%`;
        videoColumnEl.style.width = '';
    }

    /**
     * Resets the video/transcript split ratio to default layout.
     * Can be triggered by double-clicking the resizer handle.
     */
    resetSplitRatio(): void {
        const videoColumnEl = this.videoColumn()?.nativeElement;
        if (videoColumnEl) {
            videoColumnEl.style.flex = '';
            videoColumnEl.style.width = '';
        }
    }

    /**
     * Syncs the transcript column's max-height to match the video column's height.
     * Ensures the transcript is at least MIN_TRANSCRIPT_HEIGHT pixels tall.
     */
    private syncTranscriptHeight(): void {
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const wrapperEl = this.videoWrapper()?.nativeElement;

        if (!videoColumnEl || !wrapperEl) {
            return;
        }

        const transcriptColumnEl = wrapperEl.querySelector<HTMLElement>('.transcript-column');
        if (!transcriptColumnEl) {
            return;
        }

        const videoHeight = videoColumnEl.offsetHeight;
        const targetHeight = Math.max(videoHeight, this.MIN_TRANSCRIPT_HEIGHT);
        transcriptColumnEl.style.maxHeight = `${targetHeight}px`;
    }

    /** Seek the video to the given time and optionally resume playback. */
    seekTo(seconds: number, resumePlayback = true): void {
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : undefined;

        if (!videoElement) {
            return;
        }

        videoElement.currentTime = seconds;
        if (resumePlayback) {
            void videoElement.play();
        }
        this.updateCurrentSegment(seconds);
    }

    getCurrentSlideNumber(): number | undefined {
        return this.currentSlideNumber();
    }

    isPlaying(): boolean {
        const videoElement = this.videoRef()?.nativeElement;
        return !!videoElement && !videoElement.paused && !videoElement.ended;
    }

    private queueInitialSeek(videoElement: HTMLVideoElement, seconds: number): void {
        const target = Math.max(0, seconds);
        if (videoElement.readyState >= 1) {
            this.applyInitialSeek(videoElement, target);
            return;
        }

        this.pendingInitialSeek = target;

        // Remove any existing listener before adding a new one
        if (this.loadedmetadataHandler) {
            videoElement.removeEventListener('loadedmetadata', this.loadedmetadataHandler);
        }

        // Create a named listener function that can be removed later
        this.loadedmetadataHandler = () => {
            const pending = this.pendingInitialSeek;
            this.pendingInitialSeek = undefined;
            this.loadedmetadataHandler = undefined; // Clear reference after firing
            if (pending !== undefined) {
                this.applyInitialSeek(videoElement, pending);
            }
        };

        videoElement.addEventListener('loadedmetadata', this.loadedmetadataHandler, { once: true });
    }

    private applyInitialSeek(videoElement: HTMLVideoElement, seconds: number): void {
        const duration = videoElement.duration;
        if (Number.isFinite(duration) && seconds > duration) {
            return;
        }
        const clamped = Number.isFinite(duration) ? Math.max(0, seconds) : seconds;
        videoElement.currentTime = clamped;
        this.updateCurrentSegment(clamped);
    }

    /**
     * Returns the current playback time in seconds, or undefined if no video is loaded.
     */
    getCurrentTime(): number | undefined {
        const videoElement = this.videoRef()?.nativeElement;
        return videoElement?.currentTime;
    }

    /**
     * Returns whether the video has been played at least once (not just showing thumbnail).
     * Uses the played TimeRanges property which is only populated when actual playback occurred.
     */
    hasBeenPlayed(): boolean {
        const videoElement = this.videoRef()?.nativeElement;
        return videoElement ? videoElement.played.length > 0 : false;
    }

    /**
     * Updates the `currentSegmentIndex` signal based on playback time.
     * Scrolls the active transcript line into view via the transcript viewer component.
     */
    updateCurrentSegment(currentTime: number): void {
        const margin = 0.3; // tolerance
        const segments = this.transcriptSegments();
        const index = segments.findIndex((s) => currentTime >= s.startTime - margin && currentTime <= s.endTime + margin);

        if (index === -1) {
            if (this.currentSegmentIndex() !== -1) {
                this.currentSegmentIndex.set(-1);
                this.updateActiveSlideNumber(undefined);
            }
            return;
        }

        if (index !== this.currentSegmentIndex()) {
            this.currentSegmentIndex.set(index);
            this.updateActiveSlideNumber(segments[index].slideNumber);

            // Scroll to the active segment in the transcript viewer
            const viewer = this.transcriptViewer();
            if (viewer) {
                viewer.scrollToSegment(index);
            }
        }
    }

    private updateActiveSlideNumber(slideNumber: number | undefined): void {
        if (this.currentSlideNumber() === slideNumber) {
            return;
        }

        this.currentSlideNumber.set(slideNumber);
        this.currentSlideNumberChange.emit(slideNumber);
    }

    /** Clean up on destroy. */
    ngOnDestroy(): void {
        // Remove event listener to prevent memory leaks
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : undefined;
        if (videoElement && this.timeupdateHandler) {
            videoElement.removeEventListener('timeupdate', this.timeupdateHandler);
            this.timeupdateHandler = undefined;
        }

        // Remove loadedmetadata listener to prevent memory leaks
        if (videoElement && this.loadedmetadataHandler) {
            videoElement.removeEventListener('loadedmetadata', this.loadedmetadataHandler);
            this.loadedmetadataHandler = undefined;
            this.pendingInitialSeek = undefined; // Clear pending seek as well
        }

        // Destroy HLS instance
        if (this.hls) {
            this.hls.destroy();
            this.hls = undefined;
        }

        // Clean up window resize listener
        if (this.resizeHandler) {
            window.removeEventListener('resize', this.resizeHandler);
            this.resizeHandler = undefined;
        }

        // Clean up ResizeObserver
        if (this.resizeObserver) {
            this.resizeObserver.disconnect();
            this.resizeObserver = undefined;
        }
    }
}
