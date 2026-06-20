import { AfterViewInit, Component, DestroyRef, ElementRef, OnDestroy, effect, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { TranscriptViewerComponent } from '../transcript-viewer/transcript-viewer.component';
import Hls from 'hls.js';
import interact from 'interactjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faGripLinesVertical } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MessageModule } from 'primeng/message';

import { TranscriptSegment } from 'app/lecture/shared/models/transcript-segment.model';
import { GocastService } from 'app/videosource/gocast/gocast.service';
import { TumLiveAttributionComponent } from 'app/videosource/gocast/tum-live-attribution.component';

/**
 * Identity of a non-public bound TUM Live stream, used to fetch and refresh playback tokens.
 * courseId: the Artemis course id (used to scope the EP2 call)
 * streamId: the gocast stream id
 * slug: the TUM Live course slug (used to build the watch-page URL for the attribution label)
 */
export interface GocastStreamIdentity {
    courseId: number;
    streamId: number;
    slug: string;
}

/** How many seconds before token expiry to schedule a refresh (safety buffer). */
const TOKEN_REFRESH_BUFFER_SECONDS = 30;

@Component({
    selector: 'jhi-video-player',
    standalone: true,
    imports: [CommonModule, TranscriptViewerComponent, FaIconComponent, ArtemisTranslatePipe, TumLiveAttributionComponent, MessageModule],
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

    /**
     * Identity of a non-public bound TUM Live stream that requires a signed playback token.
     * When provided, the player fetches a token via GocastService.getPlaybackToken and refreshes
     * it before expiry. When absent, the player uses videoUrl directly (existing public path).
     *
     * TODO(gocast): wire this from AttachmentVideoUnitComponent / OnlineUnitComponent once
     * the server-side stream identity is threaded through the lectureUnit DTO. For now, callers
     * that know their unit is a non-public bound TUM Live stream can pass this explicitly.
     */
    gocastIdentity = input<GocastStreamIdentity | undefined>(undefined);

    /** The HLS.js instance */
    private hls: Hls | undefined = undefined;

    /** Track the index of the currently active transcript segment */
    currentSegmentIndex = signal<number>(-1);

    /** Reference to the transcript viewer component */
    transcriptViewer = viewChild(TranscriptViewerComponent);

    /** Store reference to timeupdate handler for cleanup */
    private timeupdateHandler: (() => void) | undefined = undefined;

    /** FontAwesome icon for the resizer grip */
    faGripLinesVertical = faGripLinesVertical;

    /** Interact.js instance for cleanup */
    private interactInstance: ReturnType<typeof interact> | undefined = undefined;

    /** Store reference to window resize handler for cleanup */
    private resizeHandler: (() => void) | undefined = undefined;

    /** Store reference to loadedmetadata handler for cleanup */
    private loadedmetadataHandler: (() => void) | undefined = undefined;

    /** ResizeObserver for syncing transcript height with video column */
    private resizeObserver: ResizeObserver | undefined = undefined;

    /** Minimum height for the transcript column */
    private readonly MIN_TRANSCRIPT_HEIGHT = 500;

    private viewReady = signal<boolean>(false);
    private lastInitialTimestamp: number | undefined;
    private pendingInitialSeek: number | undefined;

    /** Token refresh timer id (returned by setTimeout) */
    private tokenRefreshTimer: ReturnType<typeof setTimeout> | undefined = undefined;

    /** The TUM Live watch-page URL for the attribution label, e.g. https://tum.live/w/{slug}/{streamId} */
    readonly tumLiveWatchUrl = signal<string | undefined>(undefined);

    /** Whether the token fetch failed (shown as an error state) */
    readonly tokenError = signal<boolean>(false);

    private readonly gocastService = inject(GocastService);

    private readonly destroyRef = inject(DestroyRef);

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

        this.viewReady.set(true);

        const identity = this.gocastIdentity();

        if (identity) {
            // Non-public bound TUM Live stream: derive the watch-page link and fetch a token.
            this.tumLiveWatchUrl.set(`https://tum.live/w/${identity.slug}/${identity.streamId}`);
            if (videoElement) {
                this.fetchAndLoadToken(identity, videoElement);
            }
        } else {
            // Existing public path: use videoUrl directly.
            const src = this.videoUrl();
            if (videoElement && src) {
                this.initHls(src, videoElement);
            }
        }

        if (videoElement) {
            this.timeupdateHandler = () => {
                this.updateCurrentSegment(videoElement.currentTime);
            };
            videoElement.addEventListener('timeupdate', this.timeupdateHandler);

            this.initializeResizer();
        }
    }

    /**
     * Fetches a playback token from the Artemis backend (EP2) and loads the signed HLS URL.
     * Schedules a timer to refresh the token before it expires, preserving playback position
     * and play/pause state across the reload.
     */
    private fetchAndLoadToken(identity: GocastStreamIdentity, videoElement: HTMLVideoElement): void {
        this.gocastService
            .getPlaybackToken(identity.courseId, identity.streamId)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (token) => {
                    const url = token.playlistUrl ?? token.playlistUrlPres ?? token.playlistUrlCam;
                    if (!url) {
                        this.tokenError.set(true);
                        return;
                    }

                    this.tokenError.set(false);

                    if (this.hls) {
                        // Refresh: swap the source while preserving position and play state.
                        const currentTime = videoElement.currentTime;
                        const wasPlaying = !videoElement.paused;

                        this.hls.loadSource(url);

                        // Restore position after the new manifest loads
                        const restoreAfterManifest = () => {
                            videoElement.currentTime = currentTime;
                            if (wasPlaying) {
                                videoElement.play().catch(() => {
                                    // Autoplay may be blocked; ignore and let user resume manually
                                });
                            }
                            this.hls?.off(Hls.Events.MANIFEST_PARSED, restoreAfterManifest);
                        };
                        this.hls.on(Hls.Events.MANIFEST_PARSED, restoreAfterManifest);
                    } else {
                        // First load
                        this.initHls(url, videoElement);
                    }

                    // Schedule a refresh slightly before the token expires.
                    // Use a minimum floor of 5 000 ms to avoid tight polling loops when
                    // expiresIn is very small (e.g. ≤ TOKEN_REFRESH_BUFFER_SECONDS).
                    const expiresIn = token.expiresIn; // seconds
                    const refreshAfterMs = Math.max(5000, (expiresIn - TOKEN_REFRESH_BUFFER_SECONDS) * 1000);
                    this.clearTokenRefreshTimer();
                    this.tokenRefreshTimer = setTimeout(() => {
                        this.fetchAndLoadToken(identity, videoElement);
                    }, refreshAfterMs);
                },
                error: () => {
                    // EP2 failed (e.g. 409 no ACTIVE binding, 404, 503). Fall back to the public videoUrl path
                    // so students still see the stream via the unauthenticated HLS URL when available.
                    const fallbackUrl = this.videoUrl();
                    if (fallbackUrl) {
                        this.tokenError.set(false);
                        if (!this.hls) {
                            this.initHls(fallbackUrl, videoElement);
                        }
                    } else {
                        this.tokenError.set(true);
                    }
                },
            });
    }

    /**
     * Initializes HLS.js with the given source URL and attaches it to the video element.
     * This is used both for the initial public path and for the first load of a token-gated stream.
     */
    private initHls(src: string, videoElement: HTMLVideoElement): void {
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
    }

    /**
     * Initializes the interact.js resizer for dragging the divider between video and transcript.
     */
    private initializeResizer(): void {
        const videoColumnEl = this.videoColumn()?.nativeElement;
        const wrapperEl = this.videoWrapper()?.nativeElement;
        const resizerEl = this.resizerHandle()?.nativeElement;

        if (!videoColumnEl || !wrapperEl || !resizerEl) {
            return;
        }

        this.interactInstance = interact(resizerEl).draggable({
            listeners: {
                move: (event) => {
                    const wrapperRect = wrapperEl.getBoundingClientRect();
                    const minWidth = 300;
                    const minTranscriptWidth = 250;
                    const wrapperWidth = wrapperRect.width;

                    // Skip resize if wrapper is too narrow to accommodate both columns
                    if (wrapperWidth <= minWidth + minTranscriptWidth) {
                        return;
                    }

                    const maxWidth = wrapperWidth - minTranscriptWidth; // Leave space for transcript

                    // Calculate new width based on drag position
                    const newWidth = event.clientX - wrapperRect.left;
                    const clampedWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));

                    // Calculate percentage for flex-basis (allows natural scaling on resize)
                    const flexBasisPercent = Math.min((clampedWidth / wrapperWidth) * 100, 100);

                    // Use percentage-based flex-basis so layout naturally follows container resizes
                    videoColumnEl.style.flex = `0 0 ${flexBasisPercent}%`;
                    videoColumnEl.style.width = '';
                    // ResizeObserver will automatically sync transcript height
                },
            },
            cursorChecker: () => 'col-resize',
        });

        // On window resize, sync transcript height (percentage-based flex scales automatically)
        this.resizeHandler = () => {
            if (wrapperEl && videoColumnEl) {
                this.syncTranscriptHeight();
            }
        };
        window.addEventListener('resize', this.resizeHandler);

        // Use ResizeObserver to reliably sync transcript height whenever video column size changes
        this.resizeObserver = new ResizeObserver(() => {
            this.syncTranscriptHeight();
        });
        this.resizeObserver.observe(videoColumnEl);
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

        const transcriptColumnEl = wrapperEl.querySelector('.transcript-column') as HTMLElement | null;
        if (!transcriptColumnEl) {
            return;
        }

        const videoHeight = videoColumnEl.offsetHeight;
        const targetHeight = Math.max(videoHeight, this.MIN_TRANSCRIPT_HEIGHT);
        transcriptColumnEl.style.maxHeight = `${targetHeight}px`;
    }

    /** Seek the video to the given time and resume playback. */
    seekTo(seconds: number): void {
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : undefined;

        if (!videoElement) {
            return;
        }

        videoElement.currentTime = seconds;
        videoElement.play();
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
     * Updates the `currentSegmentIndex` signal based on playback time.
     * Scrolls the active transcript line into view via the transcript viewer component.
     */
    updateCurrentSegment(currentTime: number): void {
        const margin = 0.3; // tolerance
        const segments = this.transcriptSegments();
        const index = segments.findIndex((s) => currentTime >= s.startTime - margin && currentTime <= s.endTime + margin);

        if (index !== -1 && index !== this.currentSegmentIndex()) {
            this.currentSegmentIndex.set(index);

            // Scroll to the active segment in the transcript viewer
            const viewer = this.transcriptViewer();
            if (viewer) {
                viewer.scrollToSegment(index);
            }
        }
    }

    private clearTokenRefreshTimer(): void {
        if (this.tokenRefreshTimer !== undefined) {
            clearTimeout(this.tokenRefreshTimer);
            this.tokenRefreshTimer = undefined;
        }
    }

    /** Clean up on destroy. */
    ngOnDestroy(): void {
        // Clear token refresh timer
        this.clearTokenRefreshTimer();

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

        // Clean up interact instance
        if (this.interactInstance) {
            this.interactInstance.unset();
            this.interactInstance = undefined;
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
