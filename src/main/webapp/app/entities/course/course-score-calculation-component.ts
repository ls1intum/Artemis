import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

import { CourseService } from './course.service';
import { CourseScoreCalculationService } from './courseScoreCalculation.service';
import {Exercise} from 'app/entities/exercise';
import {Course} from 'app/entities/course/course.model';

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
                    }
                );
        } else {
            this.courseExercises = this.course.exercises;
            this.calculateAbsoluteScores(this.courseId);
            this.calculateRelativeScores(this.courseId);
            this.calculateMaxScores(this.courseId);

            if (this.courseId === 13) { // EIST
                const homeworkFilter = courseExercise => {
                  return courseExercise.title.match(/Homework.*/g);
                };
                const homeworkScores = this.calculateScores(this.courseId, homeworkFilter);
                this.eistHomeworkTotalScore = homeworkScores.get('absoluteScore');
                this.eistHomeworkRelativeScore = homeworkScores.get('relativeScore');
                this.eistHomeworkMaxScore = homeworkScores.get('maxScore');

                const inClassFilter = courseExercise => {
                  return courseExercise.title.match(/(Lecture.*)/g);
                };

                const inClassScores = this.calculateScores(this.courseId, inClassFilter);
                this.eistInClassTotalScore = inClassScores.get('absoluteScore');
                this.eistInClassRelativeScore = inClassScores.get('relativeScore');
                this.eistInClassMaxScore = inClassScores.get('maxScore');
            }

        }
    }

    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    calculateAbsoluteScores(courseId: number) {
        this.quizzesTotalScore = this.calculateScoreTypeForExerciseType(courseId, 'quiz', 'absoluteScore');
        this.programmingExerciseTotalScore = this.calculateScoreTypeForExerciseType(courseId, 'programming-exercise', 'absoluteScore');
        this.modelingExerciseTotalScore = this.calculateScoreTypeForExerciseType(courseId, 'modeling-exercise', 'absoluteScore');

        this.totalScore = this.calculateTotalScoreForTheCourse(courseId, 'absoluteScore');
    }

    calculateRelativeScores(courseId: number) {
        this.quizzesTotalRelativeScore = this.calculateScoreTypeForExerciseType(courseId, 'quiz', 'relativeScore');
        this.programmingExerciseTotalRelativeScore = this.calculateScoreTypeForExerciseType(courseId, 'programming-exercise', 'relativeScore');
        this.modelingExerciseTotalRelativeScore = this.calculateScoreTypeForExerciseType(courseId, 'modeling-exercise', 'relativeScore');

        this.totalRelativeScore = this.calculateTotalScoreForTheCourse(courseId, 'relativeScore');
    }

    calculateMaxScores(courseId: number) {
        this.quizzesTotalMaxScore = this.calculateScoreTypeForExerciseType(courseId, 'quiz', 'maxScore');
        this.programmingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(courseId, 'programming-exercise', 'maxScore');
        this.modelingExerciseTotalMaxScore = this.calculateScoreTypeForExerciseType(courseId, 'modeling-exercise', 'maxScore');

        this.totalMaxScore = this.calculateTotalScoreForTheCourse(courseId, 'maxScore');
    }

    calculateScores(courseId: number, filterFunction: (Object) => boolean) {
        filterFunction = filterFunction !== undefined ? filterFunction : () => true;
        const filteredExercises = this.courseExercises.filter(filterFunction);
        return this.courseCalculationService.calculateTotalScores(filteredExercises);
    }

    calculateScoreTypeForExerciseType(courseId: number, exerciseType: string, scoreType: string): number {
      if (exerciseType !== undefined && scoreType !== undefined ) {
        const filterFunction = courseExercise => courseExercise.type === exerciseType;
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
