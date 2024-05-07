import { Component, Signal, WritableSignal, inject, signal } from '@angular/core';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { LearningObjectType, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { Observable, catchError, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { LearningPathStudentNavComponent, LoadedValue } from 'app/course/learning-paths/components/learning-path-student-nav/learning-path-student-nav.component';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { CourseExerciseDetailsModule } from 'app/overview/exercise-details/course-exercise-details.module';
import { LearningPathLectureUnitComponent } from 'app/course/learning-paths/components/learning-path-lecture-unit/learning-path-lecture-unit.component';
import { LearningPathExerciseComponent } from 'app/course/learning-paths/components/learning-path-exercise/learning-path-exercise.component';

@Component({
    selector: 'jhi-learning-path-student-page',
    templateUrl: './learning-path-student-page.component.html',
    styleUrl: './learning-path-student-page.component.scss',
    standalone: true,
    imports: [CommonModule, RouterModule, LearningPathStudentNavComponent, CourseExerciseDetailsModule, LearningPathLectureUnitComponent, LearningPathExerciseComponent],
})
export class LearningPathStudentPageComponent {
    protected readonly LearningObjectType = LearningObjectType;

    private readonly learningPathService: LearningPathService = inject(LearningPathService);
    private readonly alertService: AlertService = inject(AlertService);
    private readonly activatedRoute: ActivatedRoute = inject(ActivatedRoute);

    public readonly courseId: Signal<number> = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => params.courseId)));

    private readonly data$: Observable<LoadedValue<number>> = toObservable(this.courseId).pipe(
        switchMap((courseId) => this.learningPathService.getLearningPathId(courseId)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
        shareReplay(1),
    );

    public readonly learningPathId: Signal<number | undefined> = toSignal(this.data$.pipe(map((loadedValue) => loadedValue.value)));

    public readonly currentLearningObject: WritableSignal<LearningPathNavigationObjectDto | undefined> = signal(undefined);

    public onCurrentLearningObjectChange(learningObject: LearningPathNavigationObjectDto | undefined) {
        this.currentLearningObject.set(learningObject);
    }
}
