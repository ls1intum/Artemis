import { Component, InputSignal, Signal, inject, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { Observable, catchError, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { LoadedValue } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { LectureUnit, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';

@Component({
    selector: 'jhi-learning-path-lecture-unit',
    standalone: true,
    imports: [ArtemisLectureUnitsModule],
    templateUrl: './learning-path-lecture-unit.component.html',
    styleUrl: './learning-path-lecture-unit.component.scss',
})
export class LearningPathLectureUnitComponent {
    private readonly lectureUnitService: LectureUnitService = inject(LectureUnitService);
    private readonly alertService: AlertService = inject(AlertService);

    public readonly lectureUnitId: InputSignal<number> = input.required<number>();

    private readonly data$: Observable<LoadedValue<LectureUnit>> = toObservable(this.lectureUnitId).pipe(
        switchMap((lectureUnitId) => this.lectureUnitService.getLectureUnitById(lectureUnitId)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
        shareReplay(1),
    );

    public readonly loading: Signal<boolean> = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.isLoading)), { initialValue: false });

    public readonly lectureUnit: Signal<LectureUnit | undefined> = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value)));

    protected readonly LectureUnitType = LectureUnitType;
}
