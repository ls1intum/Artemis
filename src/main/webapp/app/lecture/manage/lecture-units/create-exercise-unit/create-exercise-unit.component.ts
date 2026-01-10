import { Component, effect, inject, input, output, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/shared/service/alert.service';
import { concatMap, finalize } from 'rxjs/operators';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { forkJoin, from } from 'rxjs';
import { ExerciseUnitService } from 'app/lecture/manage/lecture-units/services/exercise-unit.service';
import { faSort, faTimes } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';

@Component({
    selector: 'jhi-create-exercise-unit',
    templateUrl: './create-exercise-unit.component.html',
    styleUrls: ['./create-exercise-unit.component.scss'],
    imports: [TranslateDirective, FaIconComponent, SortDirective, SortByDirective],
})
export class CreateExerciseUnitComponent {
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly alertService = inject(AlertService);
    private readonly sortService = inject(SortService);
    private readonly exerciseUnitService = inject(ExerciseUnitService);

    protected readonly faTimes = faTimes;
    protected readonly faSort = faSort;

    lectureId = input<number | undefined>(undefined);
    courseId = input<number | undefined>(undefined);
    hasCancelButton = input<boolean>();
    shouldNavigateOnSubmit = input<boolean>(true);

    onCancel = output<void>();
    onExerciseUnitCreated = output<void>();

    predicate = 'type';
    reverse = false;
    isLoading = signal(false);

    exercisesAvailableForUnitCreation = signal<Exercise[]>([]);
    exercisesToCreateUnitFor = signal<Exercise[]>([]);

    constructor() {
        effect((onCleanup) => {
            const lectureId = this.lectureId();
            const courseId = this.courseId();
            if (lectureId === undefined || courseId === undefined) {
                return;
            }

            this.isLoading.set(true);
            const courseObservable = this.courseManagementService.findWithExercises(courseId);
            const exerciseUnitObservable = this.exerciseUnitService.findAllByLectureId(lectureId);
            const subscription = forkJoin([courseObservable, exerciseUnitObservable])
                .pipe(
                    finalize(() => {
                        this.isLoading.set(false);
                    }),
                )
                .subscribe({
                    next: ([courseResult, exerciseUnitResult]) => {
                        const allExercisesOfCourse = courseResult?.body?.exercises ? courseResult?.body?.exercises : [];
                        const idsOfExercisesAlreadyConnectedToUnit = exerciseUnitResult?.body
                            ? exerciseUnitResult?.body?.map((exerciseUnit: ExerciseUnit) => exerciseUnit.exercise?.id)
                            : [];
                        this.exercisesAvailableForUnitCreation.set(
                            allExercisesOfCourse.filter((exercise: Exercise) => !idsOfExercisesAlreadyConnectedToUnit.includes(exercise.id)),
                        );
                    },
                    error: (res: HttpErrorResponse) => onError(this.alertService, res),
                });

            onCleanup(() => subscription.unsubscribe());
        });
    }

    createExerciseUnits() {
        const lectureId = this.lectureId();
        if (lectureId === undefined) {
            return;
        }
        const exerciseUnitsToCreate = this.exercisesToCreateUnitFor().map((exercise: Exercise) => {
            const unit = new ExerciseUnit();
            unit.exercise = exercise;
            return unit;
        });

        from(exerciseUnitsToCreate)
            .pipe(
                concatMap((unit) => this.exerciseUnitService.create(unit, lectureId)),
                finalize(() => {
                    if (this.shouldNavigateOnSubmit()) {
                        this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                    } else {
                        this.onExerciseUnitCreated.emit();
                    }
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    sortRows() {
        const sorted = [...this.exercisesAvailableForUnitCreation()];
        this.sortService.sortByProperty(sorted, this.predicate, this.reverse);
        this.exercisesAvailableForUnitCreation.set(sorted);
    }

    selectExerciseForUnitCreation(exercise: Exercise) {
        if (this.isExerciseSelectedForUnitCreation(exercise)) {
            this.exercisesToCreateUnitFor.update((exercises) => exercises.filter((e) => e !== exercise));
        } else {
            this.exercisesToCreateUnitFor.update((exercises) => [...exercises, exercise]);
        }
    }

    isExerciseSelectedForUnitCreation(exercise: Exercise) {
        return this.exercisesToCreateUnitFor().includes(exercise);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
