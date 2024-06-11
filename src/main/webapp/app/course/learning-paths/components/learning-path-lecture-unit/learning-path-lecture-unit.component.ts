import { Component, InputSignal, inject, input, signal } from '@angular/core';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AlertService } from 'app/core/util/alert.service';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { LearningPathNavigationService } from 'app/course/learning-paths/services/learning-path-navigation.service';
import { lastValueFrom, switchMap } from 'rxjs';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';

@Component({
    selector: 'jhi-learning-path-lecture-unit',
    standalone: true,
    imports: [ArtemisLectureUnitsModule],
    templateUrl: './learning-path-lecture-unit.component.html',
})
export class LearningPathLectureUnitComponent {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService: LectureUnitService = inject(LectureUnitService);
    private readonly learningPathNavigationService = inject(LearningPathNavigationService);
    private readonly alertService: AlertService = inject(AlertService);

    readonly lectureUnitId: InputSignal<number> = input.required<number>();
    readonly isLectureUnitLoading = signal(false);
    private readonly lectureUnit$ = toObservable(this.lectureUnitId).pipe(switchMap((lectureUnitId) => this.getLectureUnit(lectureUnitId)));
    readonly lectureUnit = toSignal(this.lectureUnit$);

    async getLectureUnit(lectureUnitId: number): Promise<LectureUnit | undefined> {
        try {
            this.isLectureUnitLoading.set(true);
            return await lastValueFrom(this.lectureUnitService.getLectureUnitById(lectureUnitId));
        } catch (error) {
            this.alertService.error(error);
        } finally {
            this.isLectureUnitLoading.set(false);
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
