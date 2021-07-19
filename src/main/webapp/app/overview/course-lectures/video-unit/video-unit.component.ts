import { Component, Input, OnInit } from '@angular/core';
import { SafeResourceUrl } from '@angular/platform-browser';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import urlParser from 'js-video-url-parser';

@Component({
    selector: 'jhi-video-unit',
    templateUrl: './video-unit.component.html',
    styleUrls: ['../lecture-unit.component.scss'],
})
export class VideoUnitComponent implements OnInit {
    @Input()
    videoUnit: VideoUnit;

    videoUrl: SafeResourceUrl;

    isCollapsed = true;

    constructor(private safeResourceUrlPipe: SafeResourceUrlPipe) {}

    ngOnInit() {
        if (this.videoUnit?.source) {
            // Validate the URL before displaying it
            if (urlParser.parse(this.videoUnit.source)) {
                this.videoUrl = this.safeResourceUrlPipe.transform(this.videoUnit.source);
            }
        }
    }

    handleCollapse(event: any) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }
}
