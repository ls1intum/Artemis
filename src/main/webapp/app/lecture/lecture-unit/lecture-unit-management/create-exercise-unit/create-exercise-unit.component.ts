import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { CourseExerciseService } from 'app/course/manage/course-management.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { forkJoin, Observable } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/alert/alert.service';
import { concatMap, finalize } from 'rxjs/operators';
import { Exercise } from 'app/entities/exercise.model';
import { SortService } from 'app/shared/service/sort.service';

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
    exercises: Exercise[] = [];
    selectedExercises: Exercise[] = [];

    constructor(
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private exerciseUnitService: ExerciseUnitService,
        private courseExerciseService: CourseExerciseService,
        private quizExerciseService: QuizExerciseService,
        private alertService: AlertService,
        private sortService: SortService,
    ) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.activatedRoute.params.subscribe((params: any) => {
            this.lectureId = +params['lectureId'];
            this.courseId = +params['courseId'];

            const programmingObservable = this.courseExerciseService.findAllProgrammingExercisesForCourse(this.courseId);
            const modelingObservable = this.courseExerciseService.findAllModelingExercisesForCourse(this.courseId);
            const textObservable = this.courseExerciseService.findAllTextExercisesForCourse(this.courseId);
            const uploadObservable = this.courseExerciseService.findAllFileUploadExercisesForCourse(this.courseId);
            const quizObservable = this.quizExerciseService.findForCourse(this.courseId);

            forkJoin([programmingObservable, modelingObservable, textObservable, uploadObservable, quizObservable])
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe(
                    (results) => {
                        const programmingExercises = results[0].body!;
                        const modelingExercises = results[1].body!;
                        const textExercises = results[2].body!;
                        const uploadExercises = results[3].body!;
                        const quizExercises = results[4].body!;
                        this.exercises = [].concat.apply([], [programmingExercises, modelingExercises, textExercises, uploadExercises, quizExercises]);
                    },

                    (res: HttpErrorResponse) => onError(this.alertService, res),
                );
        });
    }

    submit(): void {
        const exerciseUnits = this.selectedExercises.map((exercise: Exercise) => {
            const unit = new ExerciseUnit();
            unit.exercise = exercise;
            return unit;
        });

        Observable.from(exerciseUnits)
            .pipe(concatMap((unit) => this.exerciseUnitService.create(unit, this.lectureId)))
            .subscribe(
                () => {
                    this.router.navigate(['../../'], { relativeTo: this.activatedRoute });
                },
                (res: HttpErrorResponse) => onError(this.alertService, res),
            );
    }

    sortRows() {
        this.sortService.sortByProperty(this.exercises, this.predicate, this.reverse);
    }

    handleRowClick(exercise: Exercise) {
        if (this.isSelected(exercise)) {
            this.selectedExercises.forEach((selectedExercise, index) => {
                if (selectedExercise === exercise) {
                    this.selectedExercises.splice(index, 1);
                }
            });
        } else {
            this.selectedExercises.push(exercise);
        }
    }

    isSelected(exercise: Exercise) {
        return this.selectedExercises.includes(exercise);
    }
}
