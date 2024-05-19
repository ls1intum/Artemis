import { Component, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { LoadedValue } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';

@Component({
    selector: 'jhi-learning-path-lecture-unit',
    standalone: true,
    imports: [ArtemisLectureUnitsModule],
    templateUrl: './learning-path-lecture-unit.component.html',
})
export class LearningPathLectureUnitComponent {
    protected readonly LectureUnitType = LectureUnitType;

    private readonly lectureUnitService = inject(LectureUnitService);
    private readonly alertService = inject(AlertService);

    readonly lectureUnitId = input.required<number>();

    private readonly lectureUnitData$ = toObservable(this.lectureUnitId).pipe(
        switchMap((lectureUnitId) => this.lectureUnitService.getLectureUnitById(lectureUnitId)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    ) as Observable<LoadedValue<LectureUnit>>;

    private readonly lectureUnitData = toSignal(this.lectureUnitData$, { requireSync: true });

    readonly isLoading = computed(() => this.lectureUnitData().isLoading);

    readonly lectureUnit = computed(() => this.lectureUnitData().value);

    readonly onLearningObjectCompleted = output<boolean>();

    setLearningObjectCompletion(completionEvent: LectureUnitCompletionEvent) {
        this.lectureUnitService.completeLectureUnit(this.lectureUnit()!.lecture!, completionEvent);
        this.onLearningObjectCompleted.emit(completionEvent.completed);
    }
}
