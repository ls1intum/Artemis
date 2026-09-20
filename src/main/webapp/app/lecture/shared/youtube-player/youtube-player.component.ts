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

/** Minimal shape of the YouTube player instance we interact with (subset of the YT.Player / Angular wrapper API). */
type YoutubePlayerApi = Pick<YouTubePlayer, 'getCurrentTime' | 'getDuration' | 'pauseVideo' | 'seekTo'>;

/** Minimal shape of the event emitted by the YouTube player's `ready` output. */
interface YoutubePlayerReadyEvent {
    target?: YoutubePlayerApi;
}

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
    currentSlideNumberChange = output<number | undefined>();

    protected readonly playerVars = { origin: typeof window !== 'undefined' ? window.location.origin : undefined };
    protected readonly startSeconds = computed(() => {
        const timestamp = this.initialTimestamp();
        return timestamp !== undefined && Number.isFinite(timestamp) && timestamp >= 0 ? Math.floor(timestamp) : undefined;
    });
    protected readonly currentSegmentIndex = signal<number>(-1);
    private readonly currentSlideNumber = signal<number | undefined>(undefined);

    playerComponent = viewChild(YouTubePlayer);

    /** FontAwesome icon for the resizer grip */
    protected readonly faGripLinesVertical = faGripLinesVertical;

    // view refs for the resizer (mirror VideoPlayerComponent)
    videoWrapper = viewChild<ElementRef<HTMLDivElement>>('videoWrapper');
    videoColumn = viewChild<ElementRef<HTMLDivElement>>('videoColumn');
    resizerHandle = viewChild<ElementRef<HTMLButtonElement>>('resizerHandle');

    private youtubePlayer: YoutubePlayerApi | null = null;
    /**
     * Whether the player has been handed over *and* knows how long the video is, which is what makes a requested
     * position judgeable. Angular creates this wrapper component well before the iframe API calls back with
     * {@link onPlayerReady}, and a seek in between silently does nothing; a player that cannot yet state a duration
     * reports 0 and would accept a target beyond the end. Callers that must state whether they really got there (the
     * Iris point-out ack) wait for this signal rather than the wrapper's mere existence.
     */
    private readonly seekableState = signal(false);
    readonly isSeekable = this.seekableState.asReadonly();
    private pollHandle: ReturnType<typeof setInterval> | null = null;
    private readinessHandle: ReturnType<typeof setTimeout> | null = null;
    private destroyed = false;
    private resizeHandler: (() => void) | undefined;
    private resizeObserver: ResizeObserver | undefined;
    private lastInitialTimestamp: number | undefined;
    protected readonly isResizing = signal<boolean>(false);
    private readonly hasEverPlayed = signal<boolean>(false);
    private playerState?: number;

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

        const transcriptColumnEl = wrapperEl.querySelector<HTMLElement>('.transcript-column');
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

    onPlayerReady(event: YoutubePlayerReadyEvent): void {
        this.clearReadiness();
        this.hasEverPlayed.set(false); // Reset for new video
        // Use the Angular wrapper when available so seek calls can be queued reliably.
        this.youtubePlayer = this.playerComponent() ?? this.youtubePlayer ?? event?.target ?? null;
        this.refreshSeekability();
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
        this.playerState = event.data;
        if (!this.youtubePlayer) return;
        // The duration usually arrives with the ready callback, but a player that was still parsing reports it only
        // from its first state change onwards — so re-read it here instead of leaving the video stuck as unseekable.
        this.refreshSeekability();
        if (event.data === YT_STATE_PLAYING) {
            this.hasEverPlayed.set(true);
            this.startPolling();
        } else if (event.data === YT_STATE_PAUSED || event.data === YT_STATE_ENDED || event.data === YT_STATE_BUFFERING) {
            this.clearPolling();
            this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
        }
    }

    onPlayerError(_event: { data: number }): void {
        this.playerFailed.emit();
    }

    /** Re-reads the player's duration, which is 0 for as long as it cannot state one. */
    private refreshSeekability(): void {
        this.seekableState.set((this.youtubePlayer?.getDuration() ?? 0) > 0);
    }

    /** {@link seekTo}'s condition on its own, for a caller that has to know all its targets hold up before moving any. */
    canSeekTo(seconds: number): boolean {
        if (!this.youtubePlayer) {
            return false;
        }
        const duration = this.youtubePlayer.getDuration();
        return seconds >= 0 && (duration <= 0 || seconds <= duration);
    }

    /**
     * Seeks the video to the given position.
     * @param seconds the position to seek to
     * @param resumePlayback whether to start playing from there
     * @return whether the video moved to the requested position: neither a call made before {@link onPlayerReady} nor
     *         a target outside the video counts, the latter because YouTube would clamp it to the end, which is not
     *         what was asked for. While the player cannot state a duration it reports 0, and the target cannot be
     *         judged against that; a caller that must *state* the outcome (the Iris point-out ack) waits for
     *         {@link isSeekable} first, so it never has to trust the answer given in that window.
     */
    seekTo(seconds: number, resumePlayback = true): boolean {
        if (!this.canSeekTo(seconds)) {
            return false;
        }
        // Guaranteed by canSeekTo, which returns false without a player.
        const player = this.youtubePlayer!;
        const wasPlaying = this.isPlaying();
        player.seekTo(seconds, true);
        // YouTube starts playing a video that had not been started yet, where the native player just moves its
        // position. Left as it is, an Iris point-out would set a YouTube lecture playing but not a streamed one.
        if (!resumePlayback && !wasPlaying) {
            player.pauseVideo();
        }
        this.updateCurrentSegment(seconds);
        return true;
    }

    getCurrentSlideNumber(): number | undefined {
        return this.currentSlideNumber();
    }

    isPlaying(): boolean {
        return this.playerState === YT_STATE_PLAYING;
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

    /**
     * Returns the current playback time in seconds, or undefined if player is not ready.
     */
    getCurrentTime(): number | undefined {
        return this.youtubePlayer?.getCurrentTime();
    }

    /**
     * Returns whether the video has been played at least once (not just showing thumbnail).
     */
    hasBeenPlayed(): boolean {
        return this.hasEverPlayed();
    }

    private updateCurrentSegment(currentTime: number): void {
        const segments = this.transcriptSegments();
        const idx = segments.findIndex((s) => currentTime >= s.startTime && currentTime < s.endTime);
        this.currentSegmentIndex.set(idx);
        this.updateActiveSlideNumber(idx >= 0 ? segments[idx].slideNumber : undefined);
    }

    private updateActiveSlideNumber(slideNumber: number | undefined): void {
        if (this.currentSlideNumber() === slideNumber) {
            return;
        }

        this.currentSlideNumber.set(slideNumber);
        this.currentSlideNumberChange.emit(slideNumber);
    }
}
