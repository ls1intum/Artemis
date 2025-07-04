import { AfterViewInit, Component, ElementRef, OnDestroy, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import videojs from 'video.js';

type VideoJsPlayer = ReturnType<typeof videojs>;

/**
 * A transcript segment corresponding to a portion of the video.
 */
export interface TranscriptSegment {
    startTime: number;
    endTime: number;
    text: string;
    slideNumber?: number;
}

/**
 * A transcript-synced video player component using Video.js.
 * Allows highlighting transcript lines and seeking the video via the transcript.
 */
@Component({
    selector: 'jhi-video-player',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './video-player.component.html',
    styleUrls: ['./video-player.component.scss'],
})
export class VideoPlayerComponent implements AfterViewInit, OnDestroy {
    /**
     * Reference to the <video> element in the template
     */
    videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoRef');

    /**
     * The URL of the video to play (required input)
     */
    videoUrl = input<string | undefined>();

    /**
     * An array of transcript segments to highlight and sync
     */
    transcriptSegments = input<TranscriptSegment[]>([]);

    /**
     * The Video.js player instance
     */
    player: VideoJsPlayer | null = null;

    /**
     * Signal to track the index of the currently active transcript segment
     */
    currentSegmentIndex = signal<number>(-1);

    /**
     * Initializes the video player after the view is rendered.
     * Also starts syncing transcript highlights with playback time.
     */
    ngAfterViewInit(): void {
        const videoElement = this.videoRef()?.nativeElement;
        if (!videoElement || !this.videoUrl()) return;

        // Initialize Video.js player
        this.player = videojs(videoElement, {
            controls: true,
            preload: 'auto',
            sources: [
                {
                    src: this.videoUrl(),
                    type: 'application/x-mpegURL',
                },
            ],
        });

        // Sync transcript segments on time update
        this.player.on('timeupdate', () => {
            const currentTime = this.player?.currentTime?.() ?? 0;
            this.updateCurrentSegment(currentTime);
        });
    }

    /**
     * Seek the video to the given time and resume playback.
     * Typically used when clicking a transcript segment.
     * @param seconds Time to seek to (in seconds)
     */
    seekTo(seconds: number): void {
        this.player?.currentTime(seconds);
        this.player?.play();
    }

    /**
     * Updates the `currentSegmentIndex` signal based on playback time.
     * Also scrolls the active transcript line into view smoothly.
     * @param currentTime Current playback time of the video
     */
    updateCurrentSegment(currentTime: number): void {
        const margin = 0.3; // Add slight tolerance for matching segments
        const segments = this.transcriptSegments();
        const index = segments.findIndex((s) => currentTime >= s.startTime - margin && currentTime <= s.endTime + margin);

        if (index !== -1 && index !== this.currentSegmentIndex()) {
            this.currentSegmentIndex.set(index);
            const el = document.getElementById(`segment-${segments[index].startTime}`);
            el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    /**
     * Cleans up the video player instance on component destruction.
     */
    ngOnDestroy(): void {
        this.player?.dispose();
    }
}
