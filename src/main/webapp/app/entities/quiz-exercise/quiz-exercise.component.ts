import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { Principal } from '../../shared';
import { ActivatedRoute } from '@angular/router';
import * as moment from 'moment';

@Component({
    selector: 'jhi-quiz-exercise',
    templateUrl: './quiz-exercise.component.html'
})
export class QuizExerciseComponent implements OnInit, OnDestroy {
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    QuizStatus = {
        HIDDEN: 'Hidden',
        VISIBLE: 'Visible',
        ACTIVE: 'Active',
        CLOSED: 'Closed',
        OPEN_FOR_PRACTICE: 'Open for Practice'
    };

    quizExercises: QuizExercise[];
    predicate: string;
    reverse: boolean;
    courseId: number;

    constructor(
        private quizExerciseService: QuizExerciseService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal,
        private route: ActivatedRoute
    ) {
        this.predicate = 'id';
        this.reverse = true;
    }

    private loadAll() {
        this.quizExerciseService.query().subscribe(
            (res: HttpResponse<QuizExercise[]>) => {
                this.quizExercises = res.body;
                this.setQuizExercisesStatus();
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.courseId = params['courseId'];
            this.load();
            this.registerChangeInQuizExercises();
        });
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: QuizExercise) {
        return item.id;
    }
    registerChangeInQuizExercises() {
        this.eventSubscriber = this.eventManager.subscribe('quizExerciseListModification', response => this.load());
    }

    private loadForCourse(courseId) {
        this.quizExerciseService.findForCourse(courseId).subscribe(
            (res: HttpResponse<QuizExercise[]>) => {
                this.quizExercises = res.body;
                this.setQuizExercisesStatus();
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }

    /**
     * Checks if the quiz exercise is over
     * @param quizExercise The quiz exercise we want to know if it's over
     * @returns {boolean} true if the quiz exercise is over, false if not.
     */
    quizIsOver(quizExercise) {
        if (quizExercise.isPlannedToStart) {
            const plannedEndMoment = moment(quizExercise.releaseDate).add(quizExercise.duration, 'seconds');
            return plannedEndMoment.isBefore(moment());
            // the quiz is over
        }
        // the quiz hasn't started yet
        return false;
    }

    /**
     * Convert seconds to full minutes
     * @param seconds {number} the number of seconds
     * @returns {number} the number of full minutes
     */
    fullMinutesForSeconds(seconds) {
        return Math.floor(seconds / 60);
    }

    /**
     * Set the quiz open for practice
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    openForPractice(quizExerciseId) {
        this.quizExerciseService.openForPractice(quizExerciseId).subscribe(
            () => {
                this.loadOne(quizExerciseId);
            },
            (res: HttpErrorResponse) => {
                this.onError(res.message);
                this.loadOne(quizExerciseId);
            }
        );
    }

    private load() {
        if (this.courseId) {
            this.loadForCourse(this.courseId);
        } else {
            this.loadAll();
        }
    }

    setQuizExercisesStatus() {
        this.quizExercises.forEach(quizExercise => quizExercise.status = this.statusForQuiz(quizExercise));
    }

    /**
     * Checks if the User is Admin/Instructor or Teaching Assistant
     * @returns {boolean} true if the User is an Admin/Instructor, false if not.
     */
    userIsInstructor() {
        return this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
    }

    /**
     * Method for determining the current status of a quiz exercise
     * @param quizExercise The quiz exercise we want to determine the status of
     * @returns {string} The status as a string
     */
    statusForQuiz(quizExercise) {
        if (quizExercise.isPlannedToStart && quizExercise.remainingTime != null) {
            if (quizExercise.remainingTime <= 0) {
                // the quiz is over
                return quizExercise.isOpenForPractice ? this.QuizStatus.OPEN_FOR_PRACTICE : this.QuizStatus.CLOSED;
            } else {
                return this.QuizStatus.ACTIVE;
            }
        }
        // the quiz hasn't started yet
        return quizExercise.isVisibleBeforeStart ? this.QuizStatus.VISIBLE : this.QuizStatus.HIDDEN;
    }

    /**
     * Start the given quiz-exercise immediately
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    startQuiz(quizExerciseId) {
        this.quizExerciseService.start(quizExerciseId).subscribe(
            () => {
                this.loadOne(quizExerciseId);
            },
            (res: HttpErrorResponse) => {
                this.onError(res.message);
                this.loadOne(quizExerciseId);
            }
        );
    }

    /**
     * Do not load all quizExercise if only one has changed
     *
     * @param quizExerciseId
     */
    private loadOne(quizExerciseId) {
        this.quizExerciseService.find(quizExerciseId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                const index = this.quizExercises.findIndex(quizExercise => quizExercise.id === quizExerciseId);
                const exercise = res.body;
                exercise.status = this.statusForQuiz(exercise);
                if (index === -1) {
                    this.quizExercises.push(exercise);
                } else {
                    this.quizExercises[index] = exercise;
                }
            }
        );
    }

    /**
     * Exports quiz in json format
     * @param quizExerciseId The quiz exercise id we want to export
     */
    exportQuizById(quizExerciseId) {
        this.quizExerciseService.find(quizExerciseId).subscribe(
            (res: HttpResponse<QuizExercise>) => {
                const exercise = res.body;
                QuizExerciseComponent.exportQuiz(exercise, true);
            }
        );
    }

    /**
     * Exports quiz in json format
     * @param quizExercise The quiz exercise we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    static exportQuiz(quizExercise: any, exportAll: boolean) {
        let questions = [];
        if (exportAll === true) {
            questions = quizExercise.questions;
        } else {
            for (let question of quizExercise.questions) {
                if (question.exportQuiz === true)
                    questions.push(question);
            }
        }
        if (questions.length === 0) return;
        let quizJson = JSON.stringify(questions);
        let blob = new Blob([quizJson], {type: 'application/json'});

        if (window.navigator.msSaveOrOpenBlob) { //IE & Edge
            window.navigator.msSaveBlob(blob, 'quiz.json');
        } else {//Chrome & FF
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = 'quiz.json';
            document.body.appendChild(anchor); //For FF
            anchor.click();
            document.body.removeChild(anchor);
        }
    }

    /**
     * Make the given quiz-exercise visible to students
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    showQuiz(quizExerciseId) {
        this.quizExerciseService.setVisible(quizExerciseId).subscribe(
            () => {
                this.loadOne(quizExerciseId);
            },
            (res: HttpErrorResponse) => {
                this.onError(res.message);
                this.loadOne(quizExerciseId);
            }
        );
    }

    callback() { }
}
