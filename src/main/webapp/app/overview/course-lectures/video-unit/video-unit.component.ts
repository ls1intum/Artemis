import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faCheck, faVideo } from '@fortawesome/free-solid-svg-icons';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import urlParser from 'js-video-url-parser';
import { LectureUnit } from 'app/entities/lecture-unit/lectureUnit.model';

@Component({
    selector: 'jhi-video-unit',
    templateUrl: './video-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class VideoUnitComponent implements OnInit {
    @Input() videoUnit: VideoUnit;
    @Output() onComplete: EventEmitter<any> = new EventEmitter();

    videoUrl: string;
    isCollapsed = true;

    // List of regexes that should not be blocked by js-video-url-parser
    videoUrlAllowList = [
        // TUM-Live. Example: 'https://live.rbg.tum.de/w/test/26?video_only=1'
        RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'),
    ];

    // Icons
    faVideo = faVideo;
    faCheck = faCheck;

    constructor() {}

    ngOnInit() {
        if (this.videoUnit?.source) {
            // Validate the URL before displaying it
            if (this.videoUrlAllowList.some((r) => r.test(this.videoUnit.source!)) || !urlParser || urlParser.parse(this.videoUnit.source)) {
                this.videoUrl = this.videoUnit.source;
            }
        }
    }

    handleCollapse(event: Event) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;

        this.onComplete.emit(this.videoUnit as LectureUnit);
    }
}
