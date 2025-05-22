import { AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import Hls from 'hls.js';

@Component({
    selector: 'jhi-video-player',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './video-player.component.html',
    styleUrls: ['./video-player.component.scss'],
})
export class VideoPlayerComponent implements AfterViewInit, OnDestroy {
    @ViewChild('videoRef') videoRef?: ElementRef<HTMLVideoElement>;

    @Input() videoUrl!: string;
    @Input() transcriptSegments: any[] = [];

    useIframe = false;
    currentSegmentIndex = -1;
    hls?: Hls;

    ngAfterViewInit(): void {
        if (!this.videoUrl) return;

        if (Hls.isSupported() && this.videoRef) {
            this.hls = new Hls();
            this.hls.loadSource(this.videoUrl);
            this.hls.attachMedia(this.videoRef.nativeElement);
        } else if (this.videoRef) {
            this.videoRef.nativeElement.src = this.videoUrl;
        }

        this.videoRef?.nativeElement.addEventListener('timeupdate', () => {
            const time = this.videoRef?.nativeElement.currentTime || 0;
            this.updateCurrentSegment(time);
        });
    }

    seekTo(seconds: number): void {
        if (this.videoRef) {
            this.videoRef.nativeElement.currentTime = seconds;
            this.videoRef.nativeElement.play();
        }
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
        this.hls?.destroy();
    }
}
