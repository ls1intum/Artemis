import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { Principal } from '../../core';
import { ActivatedRoute } from '@angular/router';
import * as moment from 'moment';
import { Course, CourseService } from '../course';
import { Question } from '../question';

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
    course: Course;
    predicate: string;
    reverse: boolean;
    courseId: number;

    /**
     * Exports given quiz questions into json file
     * @param quizQuestions Quiz questions we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    static exportQuiz(quizQuestions: Question[], exportAll: boolean) {
        // Make list of questions which we need to export,
        const questions: Question[] = [];
        for (const question of quizQuestions) {
            if (exportAll === true || question.exportQuiz === true) {
                delete question.questionStatistic;
                questions.push(question);
            }
        }
        if (questions.length === 0) {
            return;
        }
        // Make blob from the list of questions and download the file,
        const quizJson = JSON.stringify(questions);
        const blob = new Blob([quizJson], { type: 'application/json' });
        this.downloadFile(blob);
    }

    /**
     * Make a file of given blob and allows user to download it from the browser.
     * @param blob data to be written in file.
     */
    static downloadFile(blob: Blob) {
        // Different browsers require different code to download file,
        if (window.navigator.msSaveOrOpenBlob) {
            // IE & Edge
            window.navigator.msSaveBlob(blob, 'quiz.json');
        } else {
            // Chrome & FF
            // Create a url and attach file to it,
            const url = window.URL.createObjectURL(blob);
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = 'quiz.json';
            document.body.appendChild(anchor); // For FF
            // Click the url so that browser shows save file dialog,
            anchor.click();
            document.body.removeChild(anchor);
        }
    }

    constructor(
        private courseService: CourseService,
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
            (res: HttpErrorResponse) => this.onError(res)
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
        this.eventSubscriber = this.eventManager.subscribe('quizExerciseListModification', () => this.load());
    }

    private loadForCourse(courseId: number) {
        this.quizExerciseService.findForCourse(courseId).subscribe(
            (res: HttpResponse<QuizExercise[]>) => {
                this.quizExercises = res.body;
                this.setQuizExercisesStatus();
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
        this.courseService.find(this.courseId).subscribe(res => {
            this.course = res.body;
        });
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    /**
     * Checks if the quiz exercise is over
     * @param quizExercise The quiz exercise we want to know if it's over
     * @returns {boolean} true if the quiz exercise is over, false if not.
     */
    quizIsOver(quizExercise: QuizExercise) {
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
    fullMinutesForSeconds(seconds: number) {
        return Math.floor(seconds / 60);
    }

    /**
     * Set the quiz open for practice
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    openForPractice(quizExerciseId: number) {
        this.quizExerciseService.openForPractice(quizExerciseId).subscribe(
            () => {
                this.loadOne(quizExerciseId);
            },
            (res: HttpErrorResponse) => {
                this.onError(res);
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
        this.quizExercises.forEach(quizExercise => (quizExercise.status = this.statusForQuiz(quizExercise)));
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
    statusForQuiz(quizExercise: QuizExercise) {
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
    startQuiz(quizExerciseId: number) {
        this.quizExerciseService.start(quizExerciseId).subscribe(
            () => {
                this.loadOne(quizExerciseId);
            },
            (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne(quizExerciseId);
            }
        );
    }

    /**
     * Do not load all quizExercise if only one has changed
     *
     * @param quizExerciseId
     */
    private loadOne(quizExerciseId: number) {
        this.quizExerciseService.find(quizExerciseId).subscribe((res: HttpResponse<QuizExercise>) => {
            const index = this.quizExercises.findIndex(quizExercise => quizExercise.id === quizExerciseId);
            const exercise = res.body;
            exercise.status = this.statusForQuiz(exercise);
            if (index === -1) {
                this.quizExercises.push(exercise);
            } else {
                this.quizExercises[index] = exercise;
            }
        });
    }

    /**
     * Exports questions for the given quiz exercise in json file
     * @param quizExerciseId The quiz exercise id we want to export
     * @param exportAll If true exports all questions, else exports only those whose export flag is true
     */
    exportQuizById(quizExerciseId: number, exportAll: boolean) {
        this.quizExerciseService.find(quizExerciseId).subscribe((res: HttpResponse<QuizExercise>) => {
            const exercise = res.body;
            QuizExerciseComponent.exportQuiz(exercise.questions, exportAll);
        });
    }

    /**
     * Make the given quiz-exercise visible to students
     *
     * @param quizExerciseId the quiz exercise id to start
     */
    showQuiz(quizExerciseId: number) {
        this.quizExerciseService.setVisible(quizExerciseId).subscribe(
            () => {
                this.loadOne(quizExerciseId);
            },
            (res: HttpErrorResponse) => {
                this.onError(res);
                this.loadOne(quizExerciseId);
            }
        );
    }

    callback() {}
}
