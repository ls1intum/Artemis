import { Component, Input, OnInit } from '@angular/core';
import { SafeResourceUrl } from '@angular/platform-browser';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';

@Component({
    selector: 'jhi-video-unit',
    templateUrl: './video-unit.component.html',
    styleUrls: ['../../course-exercises/course-exercise-row.scss'],
})
export class VideoUnitComponent implements OnInit {
    @Input()
    videoUnit: VideoUnit;

    youTubeUrl: SafeResourceUrl;

    isCollapsed = true;

    constructor(private safeResourceUrlPipe: SafeResourceUrlPipe) {}

    ngOnInit() {
        this.youTubeUrl = this.safeResourceUrlPipe.transform(`https://www.youtube.com/embed/${this.videoUnit.source}`);
    }

    handleUnitClick($event: any) {
        $event.stopPropagation();
        this.isCollapsed = !this.isCollapsed;
    }
}
