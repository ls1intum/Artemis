import { Component, Signal, computed, inject, viewChild } from '@angular/core';
import { LearningPathService } from 'app/course/learning-paths/learning-path.service';
import { LearningObjectType, LearningPathNavigationObjectDto } from 'app/entities/competency/learning-path.model';
import { Observable, catchError, map, of, startWith, switchMap } from 'rxjs';
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

    private readonly navigation: Signal<LearningPathStudentNavComponent | undefined> = viewChild(LearningPathStudentNavComponent);

    readonly courseId: Signal<number> = toSignal(this.activatedRoute.parent!.parent!.params.pipe(map((params) => params.courseId))) as Signal<number>;

    private readonly learningPathIdData$: Observable<LoadedValue<number>> = toObservable(this.courseId).pipe(
        switchMap((courseId) => this.learningPathService.getLearningPathId(courseId!)),
        map((response) => ({ isLoading: false, value: response.body })),
        catchError((error: HttpErrorResponse) => {
            if (error.status === 404) {
                return this.learningPathService.generateLearningPath(this.courseId()).pipe(
                    map((response) => ({ isLoading: false, value: response.body })),
                    catchError((error: HttpErrorResponse) => {
                        onError(this.alertService, error);
                        return of({ isLoading: false, error: error });
                    }),
                );
            }
            onError(this.alertService, error);
            return of({ isLoading: false, error: error });
        }),
        startWith({ isLoading: true }),
    );

    private readonly learningPathIdData: Signal<LoadedValue<number>> = toSignal(this.learningPathIdData$, { requireSync: true });

    readonly learningPathId: Signal<number | null | undefined> = computed(() => this.learningPathIdData().value);

    readonly currentLearningObject: Signal<LearningPathNavigationObjectDto | undefined> = computed(() => this.navigation()?.currentLearningObject());
}
