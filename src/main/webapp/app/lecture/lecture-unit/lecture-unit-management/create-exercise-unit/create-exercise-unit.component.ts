import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { JhiAlertService } from 'ng-jhipster';
import { concatMap, finalize, switchMap, take } from 'rxjs/operators';
import { Exercise } from 'app/entities/exercise.model';
import { SortService } from 'app/shared/service/sort.service';
import { forkJoin, Observable } from 'rxjs';
import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { LectureService } from 'app/lecture/lecture.service';

@Component({
    selector: 'jhi-create-exercise-unit',
    templateUrl: './create-exercise-unit.component.html',
    styleUrls: ['./create-exercise-unit.component.scss'],
})
export class CreateExerciseUnitComponent implements OnInit {
    predicate = 'type';
    reverse = false;
    isLoading = false;

    lectureId: number;
    courseId: number;
    exercisesAvailableForUnitCreation: Exercise[] = [];
    exercisesToCreateUnitFor: Exercise[] = [];

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private courseManagementService: CourseManagementService,
        private lectureService: LectureService,
        private alertService: JhiAlertService,
        private sortService: SortService,
        private exerciseUnitService: ExerciseUnitService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.paramMap
            .pipe(
                take(1),
                switchMap((params) => {
                    this.lectureId = Number(params.get('lectureId'));
                    this.courseId = Number(params.get('courseId'));

                    const courseObservable = this.courseManagementService.findWithExercises(this.courseId);
                    const exerciseUnitObservable = this.exerciseUnitService.findAllByLectureId(this.lectureId);
                    return forkJoin([courseObservable, exerciseUnitObservable]);
                }),
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                ([courseResult, exerciseUnitResult]) => {
                    const allExercisesOfCourse = courseResult?.body?.exercises ? courseResult?.body?.exercises : [];
                    const idsOfExercisesAlreadyConnectedToUnit = exerciseUnitResult?.body
                        ? exerciseUnitResult?.body?.map((exerciseUnit: ExerciseUnit) => exerciseUnit.exercise?.id)
                        : [];
                    this.exercisesAvailableForUnitCreation = allExercisesOfCourse.filter((exercise: Exercise) => !idsOfExercisesAlreadyConnectedToUnit.includes(exercise.id));
                },
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }

    createExerciseUnits() {
        const exerciseUnitsToCreate = this.exercisesToCreateUnitFor.map((exercise: Exercise) => {
            const unit = new ExerciseUnit();
            unit.exercise = exercise;
            return unit;
        });

        Observable.from(exerciseUnitsToCreate)
            .pipe(
                concatMap((unit) => this.exerciseUnitService.create(unit, this.lectureId)),
                finalize(() => {
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                }),
            )
            .subscribe(
                () => {},
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
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
}
