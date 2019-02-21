import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';

import { ABSOLUTE_SCORE, Course, CourseScoreCalculationService, CourseService, MAX_SCORE, RELATIVE_SCORE } from '../../entities/course';
import { Exercise, ExerciseType } from '../../entities/exercise';

@Component({
    selector: 'jhi-course-score',
    templateUrl: './course-score-calculation.component.html'
})
export class CourseScoreCalculationComponent implements OnInit, OnDestroy {
    private courseId: number;
    private courseExercises: Exercise[];
    private subscription: Subscription;
    course: Course;

    // absolute score
    totalScore = 0;
    quizzesTotalScore = 0;
    programmingExerciseTotalScore = 0;
    modelingExerciseTotalScore = 0;
    eistHomeworkTotalScore = 0;
    eistInClassTotalScore = 0;

    // relative score
    totalRelativeScore = 0;
    quizzesTotalRelativeScore = 0;
    programmingExerciseTotalRelativeScore = 0;
    modelingExerciseTotalRelativeScore = 0;
    eistHomeworkRelativeScore = 0;
    eistInClassRelativeScore = 0;

    // max score
    totalMaxScore = 0;
    quizzesTotalMaxScore = 0;
    programmingExerciseTotalMaxScore = 0;
    modelingExerciseTotalMaxScore = 0;
    eistHomeworkMaxScore = 0;
    eistInClassMaxScore = 0;

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private route: ActivatedRoute
    ) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = parseInt(params['id'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);

        if (this.course === undefined) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body);
                this.course = this.courseCalculationService.getCourse(this.courseId);
                this.courseExercises = this.course.exercises;
                this.calculateAbsoluteScores();
                this.calculateRelativeScores();
                this.calculateMaxScores();
                this.handleEISTCourse2018();
            });
        } else {
            this.courseExercises = this.course.exercises;
            this.calculateAbsoluteScores();
            this.calculateRelativeScores();
            this.calculateMaxScores();
            this.handleEISTCourse2018();
        }
    }

    handleEISTCourse2018() {
        if (this.courseId === 13) {
            // EIST
            const homeworkFilter = (courseExercise: Exercise): boolean => {
                return courseExercise.title.match(/Homework.*/g) != null;
            };
            const homeworkScores = this.calculateScores(homeworkFilter);
            this.eistHomeworkTotalScore = homeworkScores.get(ABSOLUTE_SCORE);
            this.eistHomeworkRelativeScore = homeworkScores.get(RELATIVE_SCORE);
            this.eistHomeworkMaxScore = homeworkScores.get(MAX_SCORE);

            const inClassFilter = (courseExercise: Exercise): boolean => {
                return courseExercise.title.match(/(Lecture.*)/g) != null;
            };

            const inClassScores = this.calculateScores(inClassFilter);
            this.eistInClassTotalScore = inClassScores.get(ABSOLUTE_SCORE);
            this.eistInClassRelativeScore = inClassScores.get(RELATIVE_SCORE);
            this.eistInClassMaxScore = inClassScores.get(MAX_SCORE);
        }
    }

    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    calculateAbsoluteScores() {
        this.quizzesTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, ABSOLUTE_SCORE);
        this.programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, ABSOLUTE_SCORE);
        this.modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, ABSOLUTE_SCORE);
        this.totalScore = this.calculateTotalScoreForTheCourse(ABSOLUTE_SCORE);
    }

    calculateRelativeScores() {
        this.quizzesTotalRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, RELATIVE_SCORE);
        this.programmingExerciseTotalRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, RELATIVE_SCORE);
        this.modelingExerciseTotalRelativeScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, RELATIVE_SCORE);
        this.totalRelativeScore = this.calculateTotalScoreForTheCourse(RELATIVE_SCORE);
    }

    calculateMaxScores() {
        this.quizzesTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.QUIZ, MAX_SCORE);
        this.programmingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.PROGRAMMING, MAX_SCORE);
        this.modelingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(ExerciseType.MODELING, MAX_SCORE);
        this.totalMaxScore = this.calculateTotalScoreForTheCourse(MAX_SCORE);
    }

    calculateScores(filterFunction: (courseExercise: Exercise) => boolean) {
        filterFunction = filterFunction !== undefined ? filterFunction : () => true;
        const filteredExercises = this.courseExercises.filter(filterFunction);
        return this.courseCalculationService.calculateTotalScores(filteredExercises);
    }

    calculateScoreTypeForExerciseType(exerciseType: ExerciseType, scoreType: string): number {
        if (exerciseType !== undefined && scoreType !== undefined) {
            const filterFunction = (courseExercise: Exercise) => courseExercise.type === exerciseType;
            const scores = this.calculateScores(filterFunction);
            return scores.get(scoreType);
        } else {
            return NaN;
        }
    }

    calculateTotalScoreForTheCourse(scoreType: string): number {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
        return scores.get(scoreType);
    }
}
