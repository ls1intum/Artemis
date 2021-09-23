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

    // List of regexes that should not be blocked by js-video-url-parser
    videoProvidersAllowList = [
        // TUM-Live. Example: 'https://live.rbg.tum.de/w/test/26?video_only=1'
        RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'),
    ];

    constructor(private safeResourceUrlPipe: SafeResourceUrlPipe) {}

    ngOnInit() {
        if (this.videoUnit?.source) {
            // check if url is validated by allowlist
            for (let regExp of this.videoProvidersAllowList) {
                if (regExp.test(this.videoUnit.source)) {
                    this.videoUrl = this.safeResourceUrlPipe.transform(this.videoUnit.source);
                    return;
                }
            }
            // Validate the URL before displaying it
            if (!urlParser || urlParser.parse(this.videoUnit.source)) {
                this.videoUrl = this.safeResourceUrlPipe.transform(this.videoUnit.source);
            }
        }
    }

    handleCollapse(event: any) {
        event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }
}
