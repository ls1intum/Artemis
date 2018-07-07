import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { HttpResponse } from '@angular/common/http';

import { CourseService, CourseScoreCalculationService} from './course.service';
import {Participation} from 'app/entities/participation';
import {Result} from 'app/entities/result';
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
    private SCORE_NORMALIZATION_VALUE = 0.01;
    course: Course;

    // absolute score
    totalScore = 0;
    quizzesTotalScore = 0;
    programmingExerciseTotalScore = 0;
    modelingExerciseTotalScore = 0;

    // relative score
    totalRelativeScore = 0;
    quizzesTotalRelativeScore = 0;
    programmingExerciseTotalRelativeScore = 0;
    modelingExerciseTotalRelativeScore = 0;

    // max score
    totalMaxScore = 0;
    quizzesTotalMaxScore = 0;
    programmingExerciseTotalMaxScore = 0;
    modelingExerciseTotalMaxScore = 0;

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

        if(this.course == undefined) {
            console.log('Blank');
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

    calculateTotalScores(courseExercises: Exercise[]): Map<string, number> {
        let scores = new Map<string, number>();
        let absoluteScore = 0;
        let relativeScore = 0;
        let maxScore = 0;
        for (let i = 0; i < courseExercises.length; i++) {
            const exercise = courseExercises[i];
            if (exercise.maxScore !== null) {
                maxScore = maxScore + exercise.maxScore;
                const participation: Participation = this.courseCalculationService.getParticipationForExercise(exercise);
                if (participation !== undefined) {
                    const result: Result = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate);
                    absoluteScore = absoluteScore + result.score * this.SCORE_NORMALIZATION_VALUE * exercise.maxScore;
                    relativeScore = relativeScore + result.score;
                }
            }
        }
        scores.set('absoluteScore', Math.round(absoluteScore * 100) / 100);
        if (courseExercises.length > 0) {
            scores.set('relativeScore', Math.round(relativeScore/courseExercises.length * 100) / 100);
        } else {
            scores.set('relativeScore', 0);
        }
        scores.set('maxScore', maxScore);
        return scores;
    }

    calculateTotalAbsoluteScoreForTheCourse(courseId: number) {
        let scores = this.calculateTotalScores(this.courseExercises);
        this.totalScore = scores.get('absoluteScore');
    }

    calculateTotalAbsoluteScoreForQuizzes(courseId: number) {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        let scores = this.calculateTotalScores(quizExercises);
        this.quizzesTotalScore = scores.get('absoluteScore');
    }

    calculateTotalAbsoluteScoreForProgrammingExercises(courseId: number) {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        let scores = this.calculateTotalScores(programmingExercises);
        this.programmingExerciseTotalScore = scores.get('absoluteScore');
    }

    calculateTotalAbsoluteScoreForModelingExercises(courseId: number) {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        let scores = this.calculateTotalScores(modelingExercises);
        this.modelingExerciseTotalScore = scores.get('absoluteScore');
    }

    calculateTotalRelativeScoreForTheCourse(courseId: number) {
        let scores = this.calculateTotalScores(this.courseExercises);
        this.totalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalRelativeScoreForQuizzes(courseId: number) {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        let scores = this.calculateTotalScores(quizExercises);
        this.quizzesTotalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalRelativeScoreForProgrammingExercises(courseId: number) {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        let scores = this.calculateTotalScores(programmingExercises);
        this.programmingExerciseTotalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalRelativeScoreForModelingExercises(courseId: number) {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        let scores = this.calculateTotalScores(modelingExercises);
        this.modelingExerciseTotalRelativeScore = scores.get('relativeScore');
    }

    calculateTotalMaxScoreForTheCourse(courseId: number) {
        let scores = this.calculateTotalScores(this.courseExercises);
        this.totalMaxScore = scores.get('maxScore');
    }

    calculateTotalMaxScoreForQuizzes(courseId: number) {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        let scores = this.calculateTotalScores(quizExercises);
        this.quizzesTotalMaxScore = scores.get('maxScore');
    }

    calculateTotalMaxScoreForProgrammingExercises(courseId: number) {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        let scores = this.calculateTotalScores(programmingExercises);
        this.programmingExerciseTotalMaxScore = scores.get('maxScore');
    }

    calculateTotalMaxScoreForModelingExercises(courseId: number) {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        let scores = this.calculateTotalScores(modelingExercises);
        this.modelingExerciseTotalMaxScore = scores.get('maxScore');
    }
}
