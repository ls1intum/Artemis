import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewEncapsulation, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { YouTubePlayer } from '@angular/youtube-player';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ResizableConstraints, ResizableDirective, ResizableSizeEvent } from 'app/shared-ui/directives/resizable.directive';
import { TranscriptViewerComponent } from '../transcript-viewer/transcript-viewer.component';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

const READINESS_TIMEOUT_MS = 10_000;
const POLL_INTERVAL_MS = 250;

// YT.PlayerState values
const YT_STATE_PLAYING = 1;
const YT_STATE_PAUSED = 2;
const YT_STATE_ENDED = 0;
const YT_STATE_BUFFERING = 3;
const MIN_TRANSCRIPT_HEIGHT = 500;
/** Minimum width of the video column in pixels. */
const MIN_VIDEO_WIDTH = 300;
/** Minimum width reserved for the transcript column in pixels. */
const MIN_TRANSCRIPT_WIDTH = 250;

@Component({
    selector: 'jhi-youtube-player',
    standalone: true,
    imports: [YouTubePlayer, TranscriptViewerComponent, FaIconComponent, ArtemisTranslatePipe, ResizableDirective],
    templateUrl: './youtube-player.component.html',
    styleUrls: ['./youtube-player.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
    host: { class: 'youtube-player-host' },
})
export class YouTubePlayerComponent implements AfterViewInit, OnDestroy {
    videoId = input.required<string>();
    transcriptSegments = input.required<TranscriptSegment[]>();
    initialTimestamp = input<number | undefined>(undefined);

    playerFailed = output<void>();

    protected readonly playerVars = { origin: typeof window !== 'undefined' ? window.location.origin : undefined };
    protected readonly startSeconds = computed(() => {
        const timestamp = this.initialTimestamp();
        return timestamp !== undefined && Number.isFinite(timestamp) && timestamp >= 0 ? Math.floor(timestamp) : undefined;
    });
    protected readonly currentSegmentIndex = signal<number>(-1);

    playerComponent = viewChild(YouTubePlayer);

    /** FontAwesome icon for the resizer grip */
    protected readonly faGripLinesVertical = faGripLinesVertical;

    // view refs for the resizer (mirror VideoPlayerComponent)
    videoWrapper = viewChild<ElementRef<HTMLDivElement>>('videoWrapper');
    videoColumn = viewChild<ElementRef<HTMLDivElement>>('videoColumn');
    resizerHandle = viewChild<ElementRef<HTMLButtonElement>>('resizerHandle');

    private youtubePlayer: Pick<YouTubePlayer, 'getCurrentTime' | 'seekTo'> | null = null;
    private pollHandle: ReturnType<typeof setInterval> | null = null;
    private readinessHandle: ReturnType<typeof setTimeout> | null = null;
    private destroyed = false;
    private resizeHandler: (() => void) | undefined;
    private resizeObserver: ResizeObserver | undefined;
    private lastInitialTimestamp: number | undefined;
    protected readonly isResizing = signal<boolean>(false);

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

    constructor() {
        // Resync the active segment when transcript segments arrive asynchronously
        // (e.g. after onPlayerReady already ran against an empty array).
        effect(() => {
            const segments = this.transcriptSegments();
            if (segments.length > 0 && this.youtubePlayer) {
                this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
            }
        });

        // Keep YouTube deeplinks aligned with late query-param updates as well.
        effect(() => {
            const timestamp = this.startSeconds();
            const playerComponent = this.playerComponent();

            if (timestamp === undefined) {
                this.lastInitialTimestamp = undefined;
                return;
            }

            if (!playerComponent || this.lastInitialTimestamp === timestamp) {
                return;
            }

            this.lastInitialTimestamp = timestamp;
            playerComponent.seekTo(timestamp, true);
            this.updateCurrentSegment(timestamp);
        });
    }

    ngAfterViewInit(): void {
        this.readinessHandle = setTimeout(() => {
            if (!this.youtubePlayer && !this.destroyed) {
                this.playerFailed.emit();
            }
        }, READINESS_TIMEOUT_MS);
        this.initializeResizer();
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.clearPolling();
        this.clearReadiness();
        if (this.resizeHandler) window.removeEventListener('resize', this.resizeHandler);
        this.resizeObserver?.disconnect();
        this.isResizing.set(false);
    }

    /**
     * Wires up the window-resize listener and ResizeObserver that keep the transcript column height in
     * sync with the video column. The drag handling itself is provided by the {@link ResizableDirective}.
     */
    private initializeResizer(): void {
        const wrapperEl = this.videoWrapper()?.nativeElement;
        const videoColumnEl = this.videoColumn()?.nativeElement;
        if (!videoColumnEl || !wrapperEl) {
            return;
        }
        this.resizeHandler = () => {
            this.syncTranscriptHeight();
        };
        window.addEventListener('resize', this.resizeHandler);
        this.resizeObserver = new ResizeObserver(() => {
            this.syncTranscriptHeight();
        });
        this.resizeObserver.observe(videoColumnEl);
        this.syncTranscriptHeight();
    }

    /** Disables pointer events on the iframe while the divider is being dragged. */
    protected onResizeStart(): void {
        this.isResizing.set(true);
    }

    /** Re-enables pointer events on the iframe once the drag finishes. */
    protected onResizeEnd(): void {
        this.isResizing.set(false);
    }

    /**
     * Applies a resize from the {@link ResizableDirective} to the video column as a percentage-based flex-basis.
     * The directive runs with `resizableApplyInlineSize=false`: it would otherwise write an inline `width`, which a
     * `flex: 3` column ignores (flex-basis 0% wins over width), so the divider would not actually move. We translate
     * the clamped px width into `flex: 0 0 <percent>%` so the split changes and still scales when the wrapper resizes.
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
     * Syncs the transcript column's max-height to match the video column's height.
     * Ensures the transcript is at least MIN_TRANSCRIPT_HEIGHT pixels tall.
     */
    private syncTranscriptHeight(): void {
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const wrapperEl = this.videoWrapper()?.nativeElement;

        if (!videoColumnEl || !wrapperEl) {
            return;
        }

        const transcriptColumnEl = wrapperEl.querySelector('.transcript-column') as HTMLElement | null;
        if (!transcriptColumnEl) {
            return;
        }

        const videoHeight = videoColumnEl.offsetHeight;
        const targetHeight = Math.max(videoHeight, MIN_TRANSCRIPT_HEIGHT);
        transcriptColumnEl.style.maxHeight = `${targetHeight}px`;
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

    onPlayerReady(event: any): void {
        this.clearReadiness();
        // Use the Angular wrapper when available so seek calls can be queued reliably.
        this.youtubePlayer = this.playerComponent() ?? this.youtubePlayer ?? event?.target ?? null;
        const initial = this.startSeconds();
        if (initial !== undefined && this.youtubePlayer) {
            if (!this.playerComponent() && this.lastInitialTimestamp !== initial) {
                this.lastInitialTimestamp = initial;
                this.youtubePlayer.seekTo(initial, true);
            }
            this.updateCurrentSegment(initial);
        } else if (this.youtubePlayer) {
            this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
        }
    }

    onStateChange(event: { data: number }): void {
        if (!this.youtubePlayer) return;
        if (event.data === YT_STATE_PLAYING) {
            this.startPolling();
        } else if (event.data === YT_STATE_PAUSED || event.data === YT_STATE_ENDED || event.data === YT_STATE_BUFFERING) {
            this.clearPolling();
            this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
        }
    }

    onPlayerError(_event: { data: number }): void {
        this.playerFailed.emit();
    }

    seekTo(seconds: number): void {
        if (!this.youtubePlayer) return;
        this.youtubePlayer.seekTo(seconds, true);
        this.updateCurrentSegment(seconds);
    }

    private startPolling(): void {
        this.clearPolling();
        this.pollHandle = setInterval(() => {
            if (this.youtubePlayer) {
                this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
            }
        }, POLL_INTERVAL_MS);
    }

    private clearPolling(): void {
        if (this.pollHandle !== null) {
            clearInterval(this.pollHandle);
            this.pollHandle = null;
        }
    }

    private clearReadiness(): void {
        if (this.readinessHandle !== null) {
            clearTimeout(this.readinessHandle);
            this.readinessHandle = null;
        }
    }

    private updateCurrentSegment(currentTime: number): void {
        const segments = this.transcriptSegments();
        const idx = segments.findIndex((s) => currentTime >= s.startTime && currentTime < s.endTime);
        this.currentSegmentIndex.set(idx);
    }
}
