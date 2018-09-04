import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

import { CourseService } from './course.service';
import { CourseScoreCalculationService } from './courseScoreCalculation.service';
import { Exercise, ExerciseType } from '../../entities/exercise';
import { Course } from '../../entities/course/course.model';

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
        private route: ActivatedRoute) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = parseInt(params['id'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);

        if (this.course === undefined) {
                this.courseService.findAll().subscribe(
                    (res: Course[]) => {
                        this.courseCalculationService.setCourses(res);
                        this.course = this.courseCalculationService.getCourse(this.courseId);
                        this.courseExercises = this.course.exercises;
                        this.calculateAbsoluteScores(this.courseId);
                        this.calculateRelativeScores(this.courseId);
                        this.calculateMaxScores(this.courseId);
                        this.handleEISTCourse2018();
                    }
                );
        } else {
            this.courseExercises = this.course.exercises;
            this.calculateAbsoluteScores(this.courseId);
            this.calculateRelativeScores(this.courseId);
            this.calculateMaxScores(this.courseId);
            this.handleEISTCourse2018();
        }
    }

    handleEISTCourse2018() {
        if (this.courseId === 13) { // EIST
            const homeworkFilter = (courseExercise: Exercise): boolean => {
                return courseExercise.title.match(/Homework.*/g) != null;
            };
            const homeworkScores = this.calculateScores(this.courseId, homeworkFilter);
            this.eistHomeworkTotalScore = homeworkScores.get('absoluteScore');
            this.eistHomeworkRelativeScore = homeworkScores.get('relativeScore');
            this.eistHomeworkMaxScore = homeworkScores.get('maxScore');

            const inClassFilter = (courseExercise: Exercise): boolean => {
                return courseExercise.title.match(/(Lecture.*)/g) != null;
            };

            const inClassScores = this.calculateScores(this.courseId, inClassFilter);
            this.eistInClassTotalScore = inClassScores.get('absoluteScore');
            this.eistInClassRelativeScore = inClassScores.get('relativeScore');
            this.eistInClassMaxScore = inClassScores.get('maxScore');
        }
    }

    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    calculateAbsoluteScores(courseId: number) {
        this.quizzesTotalScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.QUIZ, 'absoluteScore');
        this.programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.PROGRAMMING, 'absoluteScore');
        this.modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.MODELING, 'absoluteScore');

        this.totalScore = this.calculateTotalScoreForTheCourse(courseId, 'absoluteScore');
    }

    calculateRelativeScores(courseId: number) {
        this.quizzesTotalRelativeScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.QUIZ, 'relativeScore');
        this.programmingExerciseTotalRelativeScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.PROGRAMMING, 'relativeScore');
        this.modelingExerciseTotalRelativeScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.MODELING, 'relativeScore');

        this.totalRelativeScore = this.calculateTotalScoreForTheCourse(courseId, 'relativeScore');
    }

    calculateMaxScores(courseId: number) {
        this.quizzesTotalMaxScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.QUIZ, 'maxScore');
        this.programmingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.PROGRAMMING, 'maxScore');
        this.modelingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(courseId, ExerciseType.MODELING, 'maxScore');

        this.totalMaxScore = this.calculateTotalScoreForTheCourse(courseId, 'maxScore');
    }

    calculateScores(courseId: number, filterFunction: (courseExercise: Exercise) => boolean) {
        filterFunction = filterFunction !== undefined ? filterFunction : () => true;
        const filteredExercises = this.courseExercises.filter(filterFunction);
        return this.courseCalculationService.calculateTotalScores(filteredExercises);
    }

    calculateScoreTypeForExerciseType(courseId: number, exerciseType: ExerciseType, scoreType: string): number {
      if (exerciseType !== undefined && scoreType !== undefined ) {
        const filterFunction = (courseExercise: Exercise) => courseExercise.type === exerciseType;
        const scores = this.calculateScores(courseId, filterFunction);
        return scores.get(scoreType);
      } else {
        return NaN;
      }
    }

    calculateTotalScoreForTheCourse(courseId: number, scoreType: string): number {
      const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
      return scores.get(scoreType);
    }
}
