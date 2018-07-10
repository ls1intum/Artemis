import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

import { CourseService, CourseScoreCalculationService} from './course.service';
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
            this.courseId = params['id'];
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);

        if (this.course === undefined) {
                this.courseService.findAll().subscribe(
                    (res: Course[]) => {
                        this.courseCalculationService.setCourses(res);
                        this.course = this.courseCalculationService.getCourse(this.courseId);
                        this.courseExercises = this.courseCalculationService.getExercisesByCourse(this.courseId);
                        this.calculateAbsoluteScores(this.courseId);
                        this.calculateRelativeScores(this.courseId);
                        this.calculateMaxScores(this.courseId);
                    }
                );
        } else {
            this.courseExercises = this.courseCalculationService.getExercisesByCourse(this.courseId);
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
                  return courseExercise.title.match(/(Lecture.*)|(Good Morning Quiz.*)|(Quiz.*)/g);
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
        this.calculateTotalAbsoluteScoreForQuizzes(courseId);
        this.calculateTotalAbsoluteScoreForProgrammingExercises(courseId);
        this.calculateTotalAbsoluteScoreForModelingExercises(courseId);
        this.calculateTotalAbsoluteScoreForTheCourse(courseId);
    }

    calculateRelativeScores(courseId: number) {
        this.calculateTotalRelativeScoreForQuizzes(courseId);
        this.calculateTotalRelativeScoreForProgrammingExercises(courseId);
        this.calculateTotalRelativeScoreForModelingExercises(courseId);
        this.calculateTotalRelativeScoreForTheCourse(courseId);
    }

    calculateMaxScores(courseId: number) {
        this.calculateTotalMaxScoreForQuizzes(courseId);
        this.calculateTotalMaxScoreForProgrammingExercises(courseId);
        this.calculateTotalMaxScoreForModelingExercises(courseId);
        this.calculateTotalMaxScoreForTheCourse(courseId);
    }

    calculateScores(courseId: number, filterFunction: (Object) => boolean) {
        filterFunction = filterFunction !== undefined ? filterFunction : () => true;
        const filteredExercises = this.courseExercises.filter(filterFunction);
        return this.courseCalculationService.calculateTotalScores(filteredExercises);
    }

    calculateTotalAbsoluteScoreForTheCourse(courseId: number) {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
        this.totalScore = scores.get('absoluteScore');
    }

    calculateTotalAbsoluteScoreForQuizzes(courseId: number) {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        const scores = this.courseCalculationService.calculateTotalScores(quizExercises);
        this.quizzesTotalScore = scores.get('absoluteScore');
    }

    calculateTotalAbsoluteScoreForProgrammingExercises(courseId: number) {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        const scores = this.courseCalculationService.calculateTotalScores(programmingExercises);
        this.programmingExerciseTotalScore = scores.get('absoluteScore');
    }

    calculateTotalAbsoluteScoreForModelingExercises(courseId: number) {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        const scores = this.courseCalculationService.calculateTotalScores(modelingExercises);
        this.modelingExerciseTotalScore = scores.get('absoluteScore');
    }

    calculateTotalRelativeScoreForTheCourse(courseId: number) {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
        this.totalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalRelativeScoreForQuizzes(courseId: number) {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        const scores = this.courseCalculationService.calculateTotalScores(quizExercises);
        this.quizzesTotalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalRelativeScoreForProgrammingExercises(courseId: number) {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        const scores = this.courseCalculationService.calculateTotalScores(programmingExercises);
        this.programmingExerciseTotalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalRelativeScoreForModelingExercises(courseId: number) {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        const scores = this.courseCalculationService.calculateTotalScores(modelingExercises);
        this.modelingExerciseTotalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalMaxScoreForTheCourse(courseId: number) {
        const scores = this.courseCalculationService.calculateTotalScores(this.courseExercises);
        this.totalMaxScore = scores.get('maxScore');
    }

    calculateTotalMaxScoreForQuizzes(courseId: number) {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        const scores = this.courseCalculationService.calculateTotalScores(quizExercises);
        this.quizzesTotalMaxScore = scores.get('maxScore');
    }

    calculateTotalMaxScoreForProgrammingExercises(courseId: number) {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        const scores = this.courseCalculationService.calculateTotalScores(programmingExercises);
        this.programmingExerciseTotalMaxScore = scores.get('maxScore');
    }

    calculateTotalMaxScoreForModelingExercises(courseId: number) {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        const scores = this.courseCalculationService.calculateTotalScores(modelingExercises);
        this.modelingExerciseTotalMaxScore = scores.get('maxScore');
    }
}
