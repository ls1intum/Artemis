import { Component, computed } from '@angular/core';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import urlParser from 'js-video-url-parser';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { LectureUnitDirective } from 'app/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { LectureUnitComponent } from 'app/overview/course-lectures/lecture-unit/lecture-unit.component';

@Component({
    selector: 'jhi-video-unit',
    standalone: true,
    imports: [ArtemisSharedPipesModule, LectureUnitComponent],
    templateUrl: './video-unit.component.html',
})
export class VideoUnitComponent extends LectureUnitDirective<VideoUnit> {
    protected readonly faVideo = faVideo;

    // List of regexes that should not be blocked by js-video-url-parser
    private readonly videoUrlAllowList = [
        // TUM-Live. Example: 'https://live.rbg.tum.de/w/test/26?video_only=1'
        RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'),
    ];
    private completionTimeout: NodeJS.Timeout;

    readonly videoUrl = computed(() => {
        if (this.lectureUnit().source) {
            const source = this.lectureUnit().source!;
            if (this.videoUrlAllowList.some((r) => r.test(source)) || !urlParser || urlParser.parse(source)) {
                return source;
            }
        }
        return undefined;
    });

    toggleCollapse(isCollapsed: boolean) {
        super.toggleCollapse(isCollapsed);
        if (!isCollapsed) {
            // log event
            this.logEvent();

            // Mark the unit as completed when the user has it open for at least 5 minutes
            this.completionTimeout = setTimeout(
                () => {
                    this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
                },
                1000 * 60 * 5,
            );
        } else {
            clearTimeout(this.completionTimeout);
        }
    }
}
