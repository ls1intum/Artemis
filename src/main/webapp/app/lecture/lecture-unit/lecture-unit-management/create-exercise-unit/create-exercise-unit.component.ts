import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { concatMap, finalize, switchMap, take } from 'rxjs/operators';
import { Exercise } from 'app/entities/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { forkJoin, combineLatest, from } from 'rxjs';
import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { faSort, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-create-exercise-unit',
    templateUrl: './create-exercise-unit.component.html',
    styleUrls: ['./create-exercise-unit.component.scss'],
})
export class CreateExerciseUnitComponent implements OnInit {
    @Input()
    hasCancelButton: boolean;
    @Output()
    onCancel: EventEmitter<any> = new EventEmitter<any>();

    faTimes = faTimes;

    predicate = 'type';
    reverse = false;
    isLoading = false;

    lectureId: number;
    courseId: number;
    exercisesAvailableForUnitCreation: Exercise[] = [];
    exercisesToCreateUnitFor: Exercise[] = [];

    // Icons
    faSort = faSort;

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private courseManagementService: CourseManagementService,
        private alertService: AlertService,
        private sortService: SortService,
        private exerciseUnitService: ExerciseUnitService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        const lectureRoute = this.activatedRoute.parent!.parent!;
        combineLatest([lectureRoute.paramMap, lectureRoute.parent!.paramMap])
            .pipe(
                take(1),
                switchMap(([params, parentParams]) => {
                    this.lectureId = Number(params.get('lectureId'));
                    this.courseId = Number(parentParams.get('courseId'));

                    const courseObservable = this.courseManagementService.findWithExercises(this.courseId);
                    const exerciseUnitObservable = this.exerciseUnitService.findAllByLectureId(this.lectureId);
                    return forkJoin([courseObservable, exerciseUnitObservable]);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: ([courseResult, exerciseUnitResult]) => {
                    const allExercisesOfCourse = courseResult?.body?.exercises ? courseResult?.body?.exercises : [];
                    const idsOfExercisesAlreadyConnectedToUnit = exerciseUnitResult?.body
                        ? exerciseUnitResult?.body?.map((exerciseUnit: ExerciseUnit) => exerciseUnit.exercise?.id)
                        : [];
                    this.exercisesAvailableForUnitCreation = allExercisesOfCourse.filter((exercise: Exercise) => !idsOfExercisesAlreadyConnectedToUnit.includes(exercise.id));
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    createExerciseUnits() {
        const exerciseUnitsToCreate = this.exercisesToCreateUnitFor.map((exercise: Exercise) => {
            const unit = new ExerciseUnit();
            unit.exercise = exercise;
            return unit;
        });

        from(exerciseUnitsToCreate)
            .pipe(
                concatMap((unit) => this.exerciseUnitService.create(unit, this.lectureId)),
                finalize(() => {
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe({
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
    }

    sortRows() {
        this.sortService.sortByProperty(this.exercisesAvailableForUnitCreation, this.predicate, this.reverse);
    }

    selectExerciseForUnitCreation(exercise: Exercise) {
        if (this.isExerciseSelectedForUnitCreation(exercise)) {
            this.exercisesToCreateUnitFor.forEach((selectedExercise, index) => {
                if (selectedExercise === exercise) {
                    this.exercisesToCreateUnitFor.splice(index, 1);
                }
            });
        } else {
            this.exercisesToCreateUnitFor.push(exercise);
        }
    }

    isExerciseSelectedForUnitCreation(exercise: Exercise) {
        return this.exercisesToCreateUnitFor.includes(exercise);
    }

    cancelForm() {
        this.onCancel.emit();
    }
}
