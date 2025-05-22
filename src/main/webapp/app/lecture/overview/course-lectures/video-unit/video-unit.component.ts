import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { VideoUnit } from 'app/lecture/shared/entities/lecture-unit/videoUnit.model';
import urlParser from 'js-video-url-parser';
import { faVideo } from '@fortawesome/free-solid-svg-icons';
import { LectureUnitDirective } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.directive';
import { VideoPlayerComponent } from 'app/lecture/shared/video-player/video-player.component';
import { LectureUnitComponent } from 'app/lecture/overview/course-lectures/lecture-unit/lecture-unit.component';
import { SafeResourceUrlPipe } from 'app/shared/pipes/safe-resource-url.pipe';
import { ScienceService } from 'app/shared/science/science.service';
import { ScienceEventType } from 'app/shared/science/science.model';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-video-unit',
    standalone: true,
    imports: [CommonModule, VideoPlayerComponent, LectureUnitComponent, SafeResourceUrlPipe],
    templateUrl: './video-unit.component.html',
})
export class VideoUnitComponent extends LectureUnitDirective<VideoUnit> {
    protected readonly faVideo = faVideo;

    private readonly videoUrlAllowList = [
        RegExp('^https://live\\.rbg\\.tum\\.de/w/\\w+/\\d+(/(CAM|COMB|PRES))?\\?video_only=1$'),
        RegExp('^https://edge\\.live\\.rbg\\.tum\\.de/.+\\.m3u8.*$'),
    ];

    readonly videoUrl = computed(() => {
        const source = this.lectureUnit().source;
        if (source && (this.videoUrlAllowList.some((r) => r.test(source)) || (urlParser && urlParser.parse(source)))) {
            return source;
        }
        return undefined;
    });

    readonly transcriptSegments = signal<any[]>([]);
    private completionTimeout: NodeJS.Timeout;

    private readonly scienceService = inject(ScienceService);
    private readonly http = inject(HttpClient);

    toggleCollapse(isCollapsed: boolean) {
        super.toggleCollapse(isCollapsed);
        if (!isCollapsed) {
            this.scienceService.logEvent(ScienceEventType.LECTURE__OPEN_UNIT, this.lectureUnit().id);
            this.fetchTranscript();
            this.completionTimeout = setTimeout(
                () => {
                    this.onCompletion.emit({ lectureUnit: this.lectureUnit(), completed: true });
                },
                5 * 60 * 1000,
            );
        } else {
            clearTimeout(this.completionTimeout);
        }
    }

    private fetchTranscript(): void {
        const lectureUnitId = this.lectureUnit().id;
        this.http.get<any>(`/api/lecture/lecture-unit/${lectureUnitId}/transcript`).subscribe({
            next: (res) => {
                this.transcriptSegments.set(res.segments || []);
            },
            error: () => {
                this.transcriptSegments.set([]);
            },
        });
    }
}
