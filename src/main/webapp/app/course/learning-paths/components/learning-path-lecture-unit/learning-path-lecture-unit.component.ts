import { Component, InputSignal, WritableSignal, effect, inject, input, signal } from '@angular/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { lastValueFrom } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { VideoUnitComponent } from 'app/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/overview/course-lectures/text-unit/text-unit.component';
import { AttachmentUnitComponent } from 'app/overview/course-lectures/attachment-unit/attachment-unit.component';
import { OnlineUnitComponent } from 'app/overview/course-lectures/online-unit/online-unit.component';

@Component({
    selector: 'jhi-learning-path-lecture-unit',
    standalone: true,
    imports: [ArtemisLectureUnitsModule, ArtemisSharedModule, VideoUnitComponent, TextUnitComponent, AttachmentUnitComponent, OnlineUnitComponent],
    templateUrl: './learning-path-lecture-unit.component.html',
})
export class LearningPathLectureUnitComponent {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService: LectureUnitService = inject(LectureUnitService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService: AlertService = inject(AlertService);

    readonly lectureUnitId: InputSignal<number> = input.required<number>();
    readonly isLoading: WritableSignal<boolean> = signal(false);
    readonly lectureUnit = signal<LectureUnit | undefined>(undefined);

    constructor() {
        effect(() => this.loadLectureUnit(this.lectureUnitId()), { allowSignalWrites: true });
    }

    async loadLectureUnit(lectureUnitId: number): Promise<void> {
        try {
            this.isLoading.set(true);
            const lectureUnit = await lastValueFrom(this.lectureUnitService.getLectureUnitById(lectureUnitId));
            this.lectureUnit.set(lectureUnit);
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLoading.set(false);
        }
    }

    setLearningObjectCompletion(completionEvent: LectureUnitCompletionEvent): void {
        try {
            this.lectureUnitService.completeLectureUnit(this.lectureUnit()!.lecture!, completionEvent);
            this.learningPathNavigationService.setCurrentLearningObjectCompletion(completionEvent.completed);
        } catch (error) {
            this.alertService.error(error);
        }
    }
}
