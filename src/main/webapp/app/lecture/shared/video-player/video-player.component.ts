import { AfterViewInit, Component, ElementRef, Input, OnDestroy, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import videojs from 'video.js';

@Component({
    selector: 'jhi-video-player',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './video-player.component.html',
    styleUrls: ['./video-player.component.scss'],
})
export class VideoPlayerComponent implements AfterViewInit, OnDestroy {
    videoRef = viewChild<ElementRef<HTMLVideoElement>>('videoRef');

    @Input({ required: true }) videoUrl: string = '';
    @Input() transcriptSegments: any[] = [];

    player: videojs.Player | null = null;
    currentSegmentIndex = signal<number>(-1);

    ngAfterViewInit(): void {
        const videoElement = this.videoRef()?.nativeElement;

        if (!videoElement || !this.videoUrl) return;

        this.player = videojs(videoElement, {
            controls: true,
            preload: 'auto',
            sources: [
                {
                    src: this.videoUrl,
                    type: 'application/x-mpegURL',
                },
            ],
        });

        this.player.on('timeupdate', () => {
            const currentTime = this.player?.currentTime?.() ?? 0;
            this.updateCurrentSegment(currentTime);
        });
    }

    seekTo(seconds: number): void {
        this.player?.currentTime(seconds);
        this.player?.play();
    }

    updateCurrentSegment(currentTime: number): void {
        const margin = 0.3;
        const index = this.transcriptSegments.findIndex((s) => currentTime >= s.startTime - margin && currentTime <= s.endTime + margin);

        if (index !== -1 && index !== this.currentSegmentIndex()) {
            this.currentSegmentIndex.set(index);
            const el = document.getElementById(`segment-${this.transcriptSegments[index].startTime}`);
            el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    ngOnDestroy(): void {
        this.player?.dispose();
    }
}
