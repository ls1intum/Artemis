import { Component, Directive, DoCheck, ElementRef, Inject, Injector, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { UpgradeComponent } from '@angular/upgrade/static';
import { QuizExercise } from '../../entities/quiz-exercise/quiz-exercise.model';
import 'angular';
import * as moment from 'moment';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { QuizReEvaluateWarningComponent } from './quiz-re-evaluate-warning.component';
import { QuizExercisePopupService } from '../../entities/quiz-exercise';
import { Router } from '@angular/router';

/** This Angular directive will act as an interface to the 'upgraded' AngularJS component
 *  The upgrade is realized as given Angular tutorial:
 *  https://angular.io/guide/upgrade#using-angularjs-component-directives-from-angular-code */
/* tslint:disable-next-line:directive-selector */
@Directive({selector: 'quiz-re-evaluate'})
/* tslint:disable-next-line:directive-class-suffix */
export class QuizReEvaluateWrapper extends UpgradeComponent implements OnInit, OnChanges, DoCheck, OnDestroy {
    /** The names of the input and output properties here must match the names of the
     *  `<` and `&` bindings in the AngularJS component that is being wrapped */

    @Input() quizExercise: QuizExercise;
    @Input() modalService: NgbModal;
    @Input() popupService: QuizExercisePopupService;
    @Input() router: Router;

    constructor(@Inject(ElementRef) elementRef: ElementRef, @Inject(Injector) injector: Injector) {
        /** We must pass the name of the directive as used by AngularJS (!) to the super */
        super('quizReEvaluate', elementRef, injector);
    }

    /** For this class to work when compiled with AoT, we must implement these lifecycle hooks
     *  because the AoT compiler will not realise that the super class implements them */
    ngOnInit() { super.ngOnInit(); }

    ngOnChanges(changes: SimpleChanges) { super.ngOnChanges(changes); }

    ngDoCheck() { super.ngDoCheck(); }

    ngOnDestroy() { super.ngOnDestroy(); }
}

declare const angular: any;

class QuizReEvaluateController {
    quizExercise: QuizExercise;
    modalService: NgbModal;
    popupService: QuizExercisePopupService;
    router: Router;

    datePickerOpenStatus = {
        releaseDate: false
    };
    isSaving = false;
    true = true;
    duration = {
        minutes: 0,
        seconds: 0
    };
    // create BackUp for resets
    backUpQuiz: QuizExercise;

    // status options depending on relationship between start time, end time, and current time
    statusOptionsVisible = [
        {
            key: false,
            label: 'Hidden'
        },
        {
            key: true,
            label: 'Visible'
        }
    ];
    statusOptionsPractice = [
        {
            key: false,
            label: 'Closed'
        },
        {
            key: true,
            label: 'Open for Practice'
        }
    ];
    statusOptionsActive = [
        {
            key: true,
            label: 'Active'
        }
    ];

    init() {
        this.prepareEntity(this.quizExercise);
        this.backUpQuiz = angular.copy(this.quizExercise);
    }

    $onChanges(changes) {
        if (changes.quizExercise && typeof changes.quizExercise.currentValue !== 'undefined') {
            this.init();
        }
    }

    /**
     * Remove question from the quiz
     * @param question {Question} the question to remove
     */
    deleteQuestion(question) {
        this.quizExercise.questions = this.quizExercise.questions.filter(function(q) {
            return q !== question;
        });
    }

    /**
     * Handles the change of a question by replacing the array with a copy
     *                                      (allows for shallow comparison)
     */
    onQuestionUpdated() {
        this.quizExercise.questions = Array.from(this.quizExercise.questions);
    }

    /**
     * Determine if there are any changes waiting to be saved
     * @returns {boolean} true if there are any pending changes, false otherwise
     */
    pendingChanges() {
        return !angular.equals(this.quizExercise, this.backUpQuiz);
    }

    /**
     * Check if the current inputs are valid
     * @returns {boolean} true if valid, false otherwise
     */
    validQuiz() {
        return this.quizExercise
            && this.quizExercise.title
            && this.quizExercise.title !== ''
            && this.quizExercise.duration;
    }

    /**
     * Open Warning-Modal
     *  -> if confirmed: send changed quiz to server (in Modal-controller)
     *                              and go back to parent-template
     *  -> if canceled: close Modal
     */
    save() {
        this.popupService.open(QuizReEvaluateWarningComponent as Component, this.quizExercise);
    }

    back() {
        this.router.navigate(['/course', this.quizExercise.course.id, 'quiz-exercise']);
    }

    /**
     * Makes sure the entity is well formed and its fields are of the correct types
     * @param entity
     */
    prepareEntity(entity) {
        entity.releaseDate = entity.releaseDate ? new Date(entity.releaseDate) : new Date();
        entity.duration = Number(entity.duration);
        entity.duration = isNaN(entity.duration) ? 10 : entity.duration;
    }

    /**
     * Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange() {
        const duration = moment.duration(this.duration);
        this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
        this.updateDuration();
    }

    /**
     * update ui to current value of duration
     */
    updateDuration() {
        const duration = moment.duration(this.quizExercise.duration, 'seconds');
        this.duration.minutes = 60 * duration.hours() + duration.minutes();
        this.duration.seconds = duration.seconds();
    }

    /**
     * Gives the duration time in a String with this format: <minutes>:<seconds>
     *@returns {String} the duration as String
     */
    durationString() {
        if (this.duration.seconds <= 0) {
            return this.duration.minutes + ':00';
        }
        if (this.duration.seconds < 10) {
            return this.duration.minutes + ':0' + this.duration.seconds;
        }
        return this.duration.minutes + ':' + this.duration.seconds;
    }

    /**
     * Resets the whole Quiz
     */
    resetAll() {
        this.quizExercise = angular.copy(this.backUpQuiz);
    }

    /**
     * Resets the quiz title
     */
    resetQuizTitle() {
        this.quizExercise.title = angular.copy(this.backUpQuiz.title);
    }

    /**
     * move the question one position up
     * @param question {Question} the question to move
     */
    moveUp(question) {
        const index = this.quizExercise.questions.indexOf(question);
        if (index === 0) {
            return;
        }
        const tempQuestions = angular.copy(this.quizExercise.questions);
        const temp = tempQuestions[index];
        tempQuestions[index] = tempQuestions[index - 1];
        tempQuestions[index - 1] = temp;
        this.quizExercise.questions = tempQuestions;
    }

    /**
     * move the question one position down
     * @param question {Question} the question to move
     */
    moveDown(question) {
        const index = this.quizExercise.questions.indexOf(question);
        if (index === (this.quizExercise.questions.length - 1)) {
            return;
        }
        const tempQuestions = angular.copy(this.quizExercise.questions);
        const temp = tempQuestions[index];
        tempQuestions[index] = tempQuestions[index + 1];
        tempQuestions[index + 1] = temp;
        this.quizExercise.questions = tempQuestions;
    }
}

/** Defining the angularJS module here to circumvent separation of scopes
 *  The definition is identical to the one in the AngularJS application */
angular
    .module('artemisApp')
    .component('quizReEvaluate', {
        bindings: {
            'quizExercise': '<',
            'modalService': '<',
            'popupService': '<',
            'router': '<'
        },
        template: require('../../../ng1/quiz/re-evaluate/quiz-re-evaluate.html'),
        controller: QuizReEvaluateController,
        controllerAs: 'vm'
    });
