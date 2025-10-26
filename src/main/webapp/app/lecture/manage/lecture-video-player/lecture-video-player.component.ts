import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

declare const Hls: any;

@Component({
    selector: 'jhi-lecture-video-player',
    templateUrl: './lecture-video-player.component.html',
    styleUrls: ['./lecture-video-player.component.scss'],
    standalone: true,
    imports: [FaIconComponent],
})
export class LectureVideoPlayerComponent implements OnInit, OnDestroy {
    @Input() lectureId: number;
    @ViewChild('videoPlayer', { static: false }) videoPlayerRef?: ElementRef<HTMLVideoElement>;

    loading = signal<boolean>(true);
    streamUrl = signal<string | null>(null);
    faSpinner = faSpinner;

    private hls: any;

    private lectureService = inject(LectureService);
    private alertService = inject(AlertService);

    ngOnInit(): void {
        this.loadVideoStreamUrl();
    }

    ngOnDestroy(): void {
        this.destroyHLS();
    }

    /**
     * Loads the video streaming URL and initializes the player
     */
    private loadVideoStreamUrl(): void {
        this.lectureService.getVideoStreamUrl(this.lectureId).subscribe({
            next: (url: string) => {
                this.streamUrl.set(url);
                this.loading.set(false);
                // Wait for the video element to be available
                setTimeout(() => this.initializeHLSPlayer(), 100);
            },
            error: (error) => {
                this.loading.set(false);
                this.alertService.error(error?.error?.message || 'artemisApp.lecture.video.loadError');
            },
        });
    }

    /**
     * Initializes the HLS video player
     */
    private initializeHLSPlayer(): void {
        if (!this.videoPlayerRef || !this.streamUrl()) {
            return;
        }

        const video = this.videoPlayerRef.nativeElement;
        const url = this.streamUrl()!;

        // Check if HLS.js is supported
        if (typeof Hls !== 'undefined' && Hls.isSupported()) {
            this.hls = new Hls({
                enableWorker: true,
                lowLatencyMode: false,
                backBufferLength: 90,
            });

            this.hls.loadSource(url);
            this.hls.attachMedia(video);

            this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
                // HLS manifest parsed, video ready to play
            });

            this.hls.on(Hls.Events.ERROR, (_event: any, data: any) => {
                // HLS error occurred
                if (data.fatal) {
                    switch (data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            this.alertService.error('artemisApp.lecture.video.networkError');
                            this.hls.startLoad();
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            this.alertService.error('artemisApp.lecture.video.mediaError');
                            this.hls.recoverMediaError();
                            break;
                        default:
                            this.alertService.error('artemisApp.lecture.video.fatalError');
                            this.destroyHLS();
                            break;
                    }
                }
            });
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari)
            video.src = url;
        } else {
            this.alertService.error('artemisApp.lecture.video.notSupported');
        }
    }

    /**
     * Destroys the HLS instance
     */
    private destroyHLS(): void {
        if (this.hls) {
            this.hls.destroy();
            this.hls = null;
        }
    }
}
