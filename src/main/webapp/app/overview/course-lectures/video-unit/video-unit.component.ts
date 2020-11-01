import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { YouTubePlayer } from '@angular/youtube-player';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { JhiAlertService } from 'ng-jhipster';

@Component({
    selector: 'jhi-video-unit',
    templateUrl: './video-unit.component.html',
    styleUrls: ['../../course-exercises/course-exercise-row.scss'],
})
export class VideoUnitComponent implements OnInit {
    @ViewChild('youtubePlayer')
    youtubePlayer: YouTubePlayer;

    @Input()
    videoUnit: VideoUnit;

    isCollapsed = true;

    playerVars = {
        modestbranding: 1,
        rel: 0,
    };

    constructor(private alertService: JhiAlertService) {}

    ngOnInit() {}

    // error codes are described here: https://developers.google.com/youtube/iframe_api_reference
    handleVideoError($event: any) {
        switch ($event.data) {
            case 2:
                this.alertService.error('artemisApp.videoUnit.youTube.errorCode2');
                break;
            case 5:
                this.alertService.error('artemisApp.videoUnit.youTube.errorCode5');
                break;
            case 100:
                this.alertService.error('artemisApp.videoUnit.youTube.errorCode100');
                break;
            case 101:
            case 150:
                this.alertService.error('artemisApp.videoUnit.youTube.errorCode101Or150');
                break;
            default:
                this.alertService.error('artemisApp.videoUnit.youTube.unknownError');
                break;
        }
    }

    handleUnitClick() {
        this.isCollapsed = !this.isCollapsed;
        this.youtubePlayer.pauseVideo();
    }
}
