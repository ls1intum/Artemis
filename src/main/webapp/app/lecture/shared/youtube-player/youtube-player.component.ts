import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewEncapsulation, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { YouTubePlayer } from '@angular/youtube-player';
import interact from 'interactjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
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

@Component({
    selector: 'jhi-youtube-player',
    standalone: true,
    imports: [YouTubePlayer, TranscriptViewerComponent, FaIconComponent, ArtemisTranslatePipe],
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

    // view refs for the interact.js resizer (mirror VideoPlayerComponent)
    videoWrapper = viewChild<ElementRef<HTMLDivElement>>('videoWrapper');
    videoColumn = viewChild<ElementRef<HTMLDivElement>>('videoColumn');
    resizerHandle = viewChild<ElementRef<HTMLButtonElement>>('resizerHandle');

    private youtubePlayer: Pick<YouTubePlayer, 'getCurrentTime' | 'seekTo'> | null = null;
    private pollHandle: ReturnType<typeof setInterval> | null = null;
    private readinessHandle: ReturnType<typeof setTimeout> | null = null;
    private destroyed = false;
    private interactInstance: ReturnType<typeof interact> | undefined;
    private resizeHandler: (() => void) | undefined;
    private resizeObserver: ResizeObserver | undefined;
    private lastInitialTimestamp: number | undefined;
    protected readonly isResizing = signal<boolean>(false);

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
        this.interactInstance?.unset();
        if (this.resizeHandler) window.removeEventListener('resize', this.resizeHandler);
        this.resizeObserver?.disconnect();
        this.isResizing.set(false);
    }

    private initializeResizer(): void {
        const wrapperEl = this.videoWrapper()?.nativeElement;
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const resizerEl = this.resizerHandle()?.nativeElement;
        if (!videoColumnEl || !wrapperEl || !resizerEl) {
            return;
        }
        this.interactInstance = interact(resizerEl).draggable({
            listeners: {
                start: () => {
                    this.isResizing.set(true);
                },
                move: (event) => {
                    const wrapperRect = wrapperEl.getBoundingClientRect();
                    const minWidth = 300;
                    const minTranscriptWidth = 250;
                    const wrapperWidth = wrapperRect.width;

                    // Skip resize if wrapper is too narrow
                    if (wrapperWidth <= minWidth + minTranscriptWidth) {
                        return;
                    }

                    const maxWidth = wrapperWidth - minTranscriptWidth;
                    const newWidth = event.clientX - wrapperRect.left;
                    const clampedWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));

                    // Use percentage-based flex-basis for natural scaling
                    const flexBasisPercent = Math.min((clampedWidth / wrapperWidth) * 100, 100);
                    videoColumnEl.style.flex = `0 0 ${flexBasisPercent}%`;
                    videoColumnEl.style.width = '';
                },
                end: () => {
                    this.isResizing.set(false);
                },
            },
            cursorChecker: () => 'col-resize',
        });
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
