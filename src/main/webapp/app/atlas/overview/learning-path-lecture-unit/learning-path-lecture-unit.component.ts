import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/lectureUnit.service';
import { AlertService } from 'app/shared/service/alert.service';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/course-lecture-details.component';
import { LearningPathNavigationService } from 'app/atlas/overview/learning-path-navigation.service';
import { lastValueFrom } from 'rxjs';
import { VideoUnitComponent } from 'app/lecture/overview/course-lectures/video-unit/video-unit.component';
import { TextUnitComponent } from 'app/lecture/overview/course-lectures/text-unit/text-unit.component';
import { AttachmentUnitComponent } from 'app/lecture/overview/course-lectures/attachment-unit/attachment-unit.component';
import { OnlineUnitComponent } from 'app/lecture/overview/course-lectures/online-unit/online-unit.component';
import { isCommunicationEnabled } from 'app/core/course/shared/entities/course.model';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ExerciseUnitComponent } from 'app/lecture/overview/course-lectures/exercise-unit/exercise-unit.component';

@Component({
    selector: 'jhi-learning-path-lecture-unit',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [VideoUnitComponent, TextUnitComponent, AttachmentUnitComponent, OnlineUnitComponent, DiscussionSectionComponent, TranslateDirective, ExerciseUnitComponent],
    templateUrl: './learning-path-lecture-unit.component.html',
})
export class LearningPathLectureUnitComponent {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService = inject(AlertService);

    readonly lectureUnitId = input.required<number>();
    readonly isLoading = signal<boolean>(false);
    readonly lectureUnit = signal<LectureUnit | undefined>(undefined);

    readonly lecture = computed(() => this.lectureUnit()?.lecture);

    readonly isCommunicationEnabled = computed(() => isCommunicationEnabled(this.lecture()?.course));

    constructor() {
        effect(() => {
            const lectureUnitId = this.lectureUnitId();
            untracked(() => this.loadLectureUnit(lectureUnitId));
        });
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
        this.lectureUnitService.completeLectureUnit(this.lectureUnit()!.lecture!, completionEvent);
        if (this.lectureUnit()?.completed === completionEvent.completed) {
            this.learningPathNavigationService.setCurrentLearningObjectCompletion(completionEvent.completed);
        }
    }
}
