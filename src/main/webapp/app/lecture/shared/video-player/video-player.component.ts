import { AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';
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
    @ViewChild('videoRef', { static: false }) videoRef?: ElementRef<HTMLVideoElement>;

    @Input() videoUrl!: string;
    @Input() transcriptSegments: any[] = [];

    player: any;
    currentSegmentIndex = -1;

    ngAfterViewInit(): void {
        if (!this.videoRef || !this.videoUrl) return;

        this.player = videojs(this.videoRef.nativeElement, {
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
            const currentTime = this.player.currentTime();
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

        if (index !== -1 && index !== this.currentSegmentIndex) {
            this.currentSegmentIndex = index;
            const el = document.getElementById(`segment-${this.transcriptSegments[index].startTime}`);
            el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    ngOnDestroy(): void {
        this.player?.dispose();
    }
}
