import { AfterViewInit, Component, ElementRef, OnDestroy, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranscriptViewerComponent } from '../transcript-viewer/transcript-viewer.component';
import Hls from 'hls.js';

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
    imports: [CommonModule, TranscriptViewerComponent],
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

    /** The HLS.js instance */
    private hls: Hls | undefined = undefined;

    /** Track the index of the currently active transcript segment */
    currentSegmentIndex = signal<number>(-1);

    /** Reference to the transcript viewer component */
    transcriptViewer = viewChild(TranscriptViewerComponent);

    /** Store reference to timeupdate handler for cleanup */
    private timeupdateHandler: (() => void) | undefined = undefined;

    ngAfterViewInit(): void {
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : undefined;
        const src = this.videoUrl();

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

    /** Clean up on destroy. */
    ngOnDestroy(): void {
        // Remove event listener to prevent memory leaks
        const elRef = this.videoRef();
        const videoElement = elRef ? elRef.nativeElement : undefined;
        if (videoElement && this.timeupdateHandler) {
            videoElement.removeEventListener('timeupdate', this.timeupdateHandler);
            this.timeupdateHandler = undefined;
        }

        // Destroy HLS instance
        if (this.hls) {
            this.hls.destroy();
            this.hls = undefined;
        }
    }
}
