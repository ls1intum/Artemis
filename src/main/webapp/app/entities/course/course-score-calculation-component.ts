import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

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
    totalScore = 0;
    quizzesTotalScore = 0;
    programmingExerciseTotalScore = 0;
    modelingExerciseTotalScore = 0;
    course: Course;
    private courseExercises: Exercise[];
    private subscription: Subscription;
    private SCORE_NORMALIZATION_VALUE = 0.01;

    constructor(
        private eventManager: JhiEventManager,
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = params['id'];
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        this.courseExercises = this.courseCalculationService.getExercisesByCourseId(this.courseId);
        this.calculateTotalScoreForQuizzes(this.courseId);
        this.calculateTotalScoreForProgrammingExercises(this.courseId);
        this.calculateTotalScoreForModelingExercises(this.courseId);
        this.calculateTotalScoreForTheCourse(this.courseId);
    }

    previousState() {
        window.history.back();
    }

    ngOnDestroy() {}

    calculateTotalScore(courseExercises: Exercise[]): number {
        let score = 0;
        for (let i = 0; i < courseExercises.length; i++) {
            const exercise = courseExercises[i];
            if (exercise.maxScore !== null) {
                const participation: Participation = this.courseCalculationService.getParticipationForExercise(exercise);
                if (participation !== undefined) {
                    const result: Result = this.courseCalculationService.getResultForParticipation(participation, exercise.dueDate);
                    score = score + result.score * this.SCORE_NORMALIZATION_VALUE * exercise.maxScore;
                }
            }
        }
        return score;
    }

    calculateTotalScoreForTheCourse(courseId: number): number {
        this.totalScore = this.calculateTotalScore(this.courseExercises);
        return this.totalScore;
    }

    calculateTotalScoreForQuizzes(courseId: number): number {
        const quizExercises = this.courseExercises.filter(courseExercise => courseExercise.type === 'quiz');
        this.quizzesTotalScore = this.calculateTotalScore(quizExercises);
        return this.quizzesTotalScore;
    }

    calculateTotalScoreForProgrammingExercises(courseId: number): number {
        const programmingExercises = this.courseExercises.filter(programmingExercise => programmingExercise.type === 'programming-exercise');
        this.programmingExerciseTotalScore = this.calculateTotalScore(programmingExercises);
        return this.programmingExerciseTotalScore;
    }

    calculateTotalScoreForModelingExercises(courseId: number): number {
        const modelingExercises = this.courseExercises.filter(modelingExercise => modelingExercise.type === 'modeling-exercise');
        this.modelingExerciseTotalScore = this.calculateTotalScore(modelingExercises);
        return this.modelingExerciseTotalScore;
    }
}
