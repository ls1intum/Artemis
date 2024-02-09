import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import urlParser from 'js-video-url-parser';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { faSquare, faSquareCheck } from '@fortawesome/free-regular-svg-icons';
import { AbstractScienceComponent } from 'app/shared/science/science.component';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';

@Component({
    selector: 'jhi-video-unit',
    templateUrl: './video-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class VideoUnitComponent extends AbstractScienceComponent implements OnInit {
    @Input() videoUnit: VideoUnit;
    @Input() isPresentationMode = false;
    @Output() onCompletion: EventEmitter<LectureUnitCompletionEvent> = new EventEmitter();

    videoUrl: string;
    isCollapsed = true;
    completionTimeout: NodeJS.Timeout;

    // List of regexes that should not be blocked by js-video-url-parser
    videoUrlAllowList = [
        // TUM-Live. Example: 'https://live.rbg.tum.de/w/test/26?video_only=1'
        RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'),
    ];

    // Icons
    faVideo = faVideo;
    faSquare = faSquare;
    faSquareCheck = faSquareCheck;

    constructor(scienceService: ScienceService) {
        super(scienceService, ScienceEventType.LECTURE__OPEN_UNIT);
    }

    ngOnInit() {
        if (this.videoUnit?.id) {
            this.setResourceId(this.videoUnit.id);
        }
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

        if (!this.isCollapsed) {
            // log event
            this.logEvent();

            // Mark the unit as completed when the user has it open for at least 5 minutes
            this.completionTimeout = setTimeout(
                () => {
                    this.onCompletion.emit({ lectureUnit: this.videoUnit, completed: true });
                },
                1000 * 60 * 5,
            );
        } else {
            clearTimeout(this.completionTimeout);
        }
    }

    handleClick(event: Event, completed: boolean) {
        event.stopPropagation();
        this.onCompletion.emit({ lectureUnit: this.videoUnit, completed });
    }
}
