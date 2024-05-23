import { Component, InputSignal, OutputEmitterRef, Signal, computed, inject, input, output } from '@angular/core';
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

    private readonly lectureUnitService: LectureUnitService = inject(LectureUnitService);
    private readonly alertService: AlertService = inject(AlertService);

    readonly lectureUnitId: InputSignal<number> = input.required<number>();

    private readonly lectureUnitData$: Observable<LoadedValue<LectureUnit>> = toObservable(this.lectureUnitId).pipe(
        switchMap((lectureUnitId) => this.lectureUnitService.getLectureUnitById(lectureUnitId)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    );

    private readonly lectureUnitData: Signal<LoadedValue<LectureUnit>> = toSignal(this.lectureUnitData$, { requireSync: true });

    readonly isLoading: Signal<boolean> = computed(() => this.lectureUnitData().isLoading);

    readonly lectureUnit: Signal<LectureUnit | null | undefined> = computed(() => this.lectureUnitData().value);

    readonly onLearningObjectCompleted: OutputEmitterRef<boolean> = output<boolean>();

    setLearningObjectCompletion(completionEvent: LectureUnitCompletionEvent): void {
        this.lectureUnitService.completeLectureUnit(this.lectureUnit()!.lecture!, completionEvent);
        this.onLearningObjectCompleted.emit(completionEvent.completed);
    }
}
