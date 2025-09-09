import { AfterViewInit, Component, ElementRef, OnDestroy, input, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
// Lazy-load video.js at runtime; type-only import doesn't pull code into initial bundle.
import type videojs from 'video.js';

type VideoJsPlayer = ReturnType<typeof videojs>;

// cache the dynamically loaded module
let _videojsFn: any;
function loadVideoJs(): Promise<any /* typeof videojs */> {
    if (_videojsFn) return Promise.resolve(_videojsFn);
    return import('video.js').then((mod) => {
        _videojsFn = (mod as any).default ?? mod;
        return _videojsFn;
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
    imports: [CommonModule],
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
        if (!this.player) return;
        this.player.currentTime(seconds);
        this.player.play();
    }

    /**
     * Updates the `currentSegmentIndex` signal based on playback time.
     * Also scrolls the active transcript line into view smoothly.
     */
    updateCurrentSegment(currentTime: number): void {
        const margin = 0.3; // tolerance
        const segments = this.transcriptSegments();
        const index = segments.findIndex((s) => currentTime >= s.startTime - margin && currentTime <= s.endTime + margin);

        if (index !== -1 && index !== this.currentSegmentIndex()) {
            this.currentSegmentIndex.set(index);
            const el = document.getElementById(`segment-${segments[index].startTime}`);
            if (el) {
                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
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
}
