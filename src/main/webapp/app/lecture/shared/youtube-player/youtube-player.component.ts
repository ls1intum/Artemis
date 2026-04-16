import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, signal, viewChild } from '@angular/core';
import { YouTubePlayer } from '@angular/youtube-player';
import interact from 'interactjs';
import { TranscriptViewerComponent } from '../transcript-viewer/transcript-viewer.component';
import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';

const READINESS_TIMEOUT_MS = 10_000;
const POLL_INTERVAL_MS = 250;

// YT.PlayerState values
const YT_STATE_PLAYING = 1;
const YT_STATE_PAUSED = 2;
const YT_STATE_ENDED = 0;
const YT_STATE_BUFFERING = 3;

@Component({
    selector: 'jhi-youtube-player',
    standalone: true,
    imports: [YouTubePlayer, TranscriptViewerComponent],
    templateUrl: './youtube-player.component.html',
    styleUrls: ['./youtube-player.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YouTubePlayerComponent implements AfterViewInit, OnDestroy {
    videoId = input.required<string>();
    transcriptSegments = input.required<TranscriptSegment[]>();
    initialTimestamp = input<number | undefined>(undefined);

    playerFailed = output<void>();

    protected readonly playerVars = { origin: typeof window !== 'undefined' ? window.location.origin : undefined };
    protected readonly currentSegmentIndex = signal<number>(-1);

    // view refs for the interact.js resizer (mirror VideoPlayerComponent)
    videoWrapper = viewChild<ElementRef<HTMLDivElement>>('videoWrapper');
    videoColumn = viewChild<ElementRef<HTMLDivElement>>('videoColumn');
    resizerHandle = viewChild<ElementRef<HTMLDivElement>>('resizerHandle');

    private youtubePlayer: { getCurrentTime: () => number; seekTo: (s: number, allowSeekAhead: boolean) => void } | null = null;
    private pollHandle: ReturnType<typeof setInterval> | null = null;
    private readinessHandle: ReturnType<typeof setTimeout> | null = null;
    private destroyed = false;
    private interactInstance: ReturnType<typeof interact> | undefined;
    private resizeHandler: (() => void) | undefined;
    private resizeObserver: ResizeObserver | undefined;

    constructor() {
        // Resync the active segment when transcript segments arrive asynchronously
        // (e.g. after onPlayerReady already ran against an empty array).
        effect(() => {
            const segments = this.transcriptSegments();
            if (segments.length > 0 && this.youtubePlayer) {
                this.updateCurrentSegment(this.youtubePlayer.getCurrentTime());
            }
        });
    }

    ngAfterViewInit(): void {
        this.readinessHandle = setTimeout(() => {
            if (!this.youtubePlayer && !this.destroyed) {
                this.playerFailed.emit();
            }
        }, READINESS_TIMEOUT_MS);
        this.initResizer();
    }

    ngOnDestroy(): void {
        this.destroyed = true;
        this.clearPolling();
        this.clearReadiness();
        this.interactInstance?.unset();
        if (this.resizeHandler) window.removeEventListener('resize', this.resizeHandler);
        this.resizeObserver?.disconnect();
    }

    private initResizer(): void {
        const wrapperEl = this.videoWrapper()?.nativeElement;
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const resizerEl = this.resizerHandle()?.nativeElement;
        if (!videoColumnEl || !wrapperEl || !resizerEl) {
            return;
        }
        this.interactInstance = interact(resizerEl).draggable({
            listeners: {
                move: (event) => {
                    // Constrain to horizontal axis — only use event.dx, ignore dy
                    const wrapperRect = wrapperEl.getBoundingClientRect();
                    const currentWidth = videoColumnEl.getBoundingClientRect().width;
                    const newWidth = currentWidth + event.dx;
                    const minWidth = 200;
                    const maxWidth = wrapperRect.width - 200;
                    const clamped = Math.max(minWidth, Math.min(newWidth, maxWidth));
                    videoColumnEl.style.flex = `0 0 ${clamped}px`;
                },
            },
            cursorChecker: () => 'col-resize',
        });
        this.resizeHandler = () => {
            videoColumnEl.style.flex = '';
        };
        window.addEventListener('resize', this.resizeHandler);
        this.resizeObserver = new ResizeObserver(() => {
            if (wrapperEl.getBoundingClientRect().width < 992) {
                videoColumnEl.style.flex = '';
            }
        });
        this.resizeObserver.observe(wrapperEl);
    }

    onPlayerReady(event: any): void {
        this.clearReadiness();
        // The @angular/youtube-player exposes the player via the component instance;
        // in tests we inject a stub directly into `youtubePlayer`. Production:
        this.youtubePlayer = this.youtubePlayer ?? event?.target ?? null;
        const initial = this.initialTimestamp();
        if (initial !== undefined && this.youtubePlayer) {
            this.youtubePlayer.seekTo(initial, true);
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
