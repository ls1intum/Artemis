import { Directive, DoCheck, ElementRef, Inject, Injector, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { UpgradeComponent } from '@angular/upgrade/static';
import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExerciseComponent } from './quiz-exercise.component';
import { Course } from '../course/course.model';
import { HttpResponse } from '@angular/common/http';
import { DragAndDropQuestionUtil } from '../../components/util/drag-and-drop-question-util.service';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import 'angular';
import * as moment from 'moment';

/** This Angular directive will act as an interface to the 'upgraded' AngularJS component
 *  The upgrade is realized as given Angular tutorial:
 *  https://angular.io/guide/upgrade#using-angularjs-component-directives-from-angular-code */
/* tslint:disable-next-line:directive-selector */
@Directive({ selector: 'quiz-exercise-detail' })
/* tslint:disable-next-line:directive-class-suffix */
export class QuizExerciseDetailWrapper extends UpgradeComponent implements OnInit, OnChanges, DoCheck, OnDestroy {
    /** The names of the input and output properties here must match the names of the
     *  `<` and `&` bindings in the AngularJS component that is being wrapped */

    @Input() course: Course;
    @Input() quizExercise: QuizExercise;
    @Input() repository: QuizExerciseService;
    @Input() dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    @Input() router: Router;
    @Input() translateService: TranslateService;

    constructor(@Inject(ElementRef) elementRef: ElementRef, @Inject(Injector) injector: Injector) {
        /** We must pass the name of the directive as used by AngularJS (!) to the super */
        super('quizExerciseDetail', elementRef, injector);
    }

    /** For this class to work when compiled with AoT, we must implement these lifecycle hooks
     *  because the AoT compiler will not realise that the super class implements them */
    ngOnInit() { super.ngOnInit(); }

    ngOnChanges(changes: SimpleChanges) { super.ngOnChanges(changes); }

    ngDoCheck() { super.ngDoCheck(); }

    ngOnDestroy() { super.ngOnDestroy(); }
}

declare const angular: any;

class QuizExerciseDetailController {
    static $inject = ['Course', 'QuizExercise'];

    entity;
    savedEntity;
    quizExercise;
    repository;
    course;
    router;
    translateService;
    isSaving = false;
    isTrue = true;
    dragAndDropQuestionUtil;
    duration = {
        minutes: 0,
        seconds: 0
    };
    dateTime;
    statusOptionsVisible = [{
        key: false,
        label: 'Hidden'
    }, {
        key: true,
        label: 'Visible'
    }];
    statusOptionsPractice = [{
        key: false,
        label: 'Closed'
    }, {
        key: true,
        label: 'Open for Practice'
    }];
    statusOptionsActive = [{
        key: true,
        label: 'Active'
    }];
    showExistingQuestions = false;
    courses = [];
    selectedCourse;
    quizExercises = [];
    selectedQuizExercise;
    importFile = null;

    init() {
        if (this.quizExercise) {
            this.entity = this.quizExercise;
        } else {
            this.entity = {
                title: '',
                duration: 600,
                isVisibleBeforeStart: false,
                isOpenForPractice: false,
                isPlannedToStart: false,
                releaseDate: new Date((new Date()).toISOString().substring(0, 16)),
                randomizeQuestionOrder: true,
                questions: []
            };
            this.quizExercise = this.entity;
        }
        this.prepareEntity(this.entity);
        this.prepareDateTime();
        this.savedEntity = this.entity.id ? Object.assign({}, this.entity) : {};
        if (!this.quizExercise.course) {
            this.quizExercise.course = this.course;
        }
        this.updateDuration();
    }

    $onChanges(changes) {
        if (changes.course || changes.quizExercise) {
            this.init();
        }
    }

    /**
     * Determine which dropdown to display depending on the relationship between start time, end time, and current time
     * @returns {string} the name of the dropdown to show
     */
    showDropdown() {
        if (this.quizExercise && this.quizExercise.isPlannedToStart) {
            const plannedEndMoment = moment(this.quizExercise.releaseDate).add(this.quizExercise.duration, 'seconds');
            if (plannedEndMoment.isBefore(moment())) {
                return 'isOpenForPractice';
            } else if (moment(this.quizExercise.releaseDate).isBefore(moment())) {
                return 'active';
            }
        }
        return 'isVisibleBeforeStart';
    }

    /**
     * Add an empty multiple choice question to the quiz
     */
    addMultipleChoiceQuestion() {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }
        this.quizExercise.questions = this.quizExercise.questions.concat([{
            title: '',
            text: 'Enter your question text here',
            scoringType: 'ALL_OR_NOTHING',
            randomizeOrder: true,
            score: 1,
            type: 'multiple-choice',
            answerOptions: [
                {
                    isCorrect: true,
                    text: 'Enter a correct answer option here'
                },
                {
                    isCorrect: false,
                    text: 'Enter an incorrect answer option here'
                }
            ]
        }]);
    }

    /**
     * Add an empty drag and drop question to the quiz
     */
    addDragAndDropQuestion() {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }
        this.quizExercise.questions = this.quizExercise.questions.concat([{
            title: '',
            text: 'Enter your question text here',
            scoringType: 'ALL_OR_NOTHING',
            randomizeOrder: true,
            score: 1,
            type: 'drag-and-drop',
            dropLocations: [],
            dragItems: [],
            correctMappings: []
        }]);
    }

    /**
     * Adds existing questions to the quiz
     */
    addExistingQuestions() {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }
        this.courses = new Array(this.course);
        this.showExistingQuestions = !this.showExistingQuestions;
    }

    onCourseSelect() {
        if (this.selectedCourse !== null) {
            const course = JSON.parse(this.selectedCourse);
            this.repository.findForCourse(course.id)
                .subscribe((quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                    if (quizExercisesResponse.body) {
                        this.quizExercises = quizExercisesResponse.body;
                    } else {
                        this.onSaveError();
                    }
                });
        }
    }

    onQuizExerciseSelect() {
        if (this.selectedQuizExercise !== null) {
            const quizExercise = JSON.parse(this.selectedQuizExercise);
            alert(quizExercise.title);
        }
    }

    /**
     * Remove question from the quiz
     * @param question {Question} the question to remove
     */
    deleteQuestion(question) {
        this.quizExercise.questions = this.quizExercise.questions.filter(function (q) {
            return q !== question;
        });
    }

    /**
     * Handles the change of a question by replacing the array with a copy (allows for shallow comparison)
     */
    onQuestionUpdated() {
        this.quizExercise.questions = Array.from(this.quizExercise.questions);
    }

    /**
     * Determine if there are any changes waiting to be saved
     * @returns {boolean} true if there are any pending changes, false otherwise
     */
    pendingChanges() {
        if (!this.quizExercise || !this.savedEntity) {
            return false;
        }
        return [
            'title',
            'duration',
            'isPlannedToStart',
            'releaseDate',
            'isVisibleBeforeStart',
            'isOpenForPractice',
            'questions'
        ].some(function (key) {
            return this.quizExercise[key] !== this.savedEntity[key];
        }, this);
    }

    /**
     * Check if the current inputs are valid
     * @returns {boolean} true if valid, false otherwise
     */
    validQuiz() {
        if (!this.quizExercise) {
            return false;
        }
        const isGenerallyValid = this.quizExercise.title && this.quizExercise.title !== '' &&
            this.quizExercise.duration && this.quizExercise.questions && this.quizExercise.questions.length;
        const areAllQuestionsValid = this.quizExercise.questions.every(function (question) {
            switch (question.type) {
                case 'multiple-choice':
                    return question.title && question.title !== '' && question.answerOptions.some(function (answerOption) {
                        return answerOption.isCorrect;
                    });
                case 'drag-and-drop':
                    return question.title && question.title !== '' && question.correctMappings &&
                        question.correctMappings.length > 0 && this.dragAndDropQuestionUtil.solve(question).length &&
                        this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(question);
                default:
                    return question.title && question.title !== '';
            }
        }, this);

        return isGenerallyValid && areAllQuestionsValid;
    }

    /**
     * Get the reasons, why the quiz is invalid
     *
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    invalidReasons() {
        const reasons = [];
        if (!this.quizExercise) {
            return;
        }
        if (!this.quizExercise.title || this.quizExercise.title === '') {
            reasons.push({
                translateKey: 'arTeMiSApp.quizExercise.invalidReasons.quizTitle',
                translateValues: {}
            });
        }
        if (!this.quizExercise.duration) {
            reasons.push({
                translateKey: 'arTeMiSApp.quizExercise.invalidReasons.quizDuration',
                translateValues: {}
            });
        }
        if (!this.quizExercise.questions || this.quizExercise.questions.length === 0) {
            reasons.push({
                translateKey: 'arTeMiSApp.quizExercise.invalidReasons.noQuestion',
                translateValues: {}
            });
        }
        this.quizExercise.questions.forEach(function (question, index) {
            if (!question.title || question.title === '') {
                reasons.push({
                    translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionTitle',
                    translateValues: { index: index + 1 }
                });
            }
            if (question.type === 'multiple-choice') {
                if (!question.answerOptions.some(function (answerOption) {
                    return answerOption.isCorrect;
                })) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionCorrectAnswerOption',
                        translateValues: { index: index + 1 }
                    });
                }
            }
            if (question.type === 'drag-and-drop') {
                if (!question.correctMappings || question.correctMappings.length === 0) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionCorrectMapping',
                        translateValues: { index: index + 1 }
                    });
                } else if (this.dragAndDropQuestionUtil.solve(question, []).length === 0) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionUnsolvable',
                        translateValues: { index: index + 1 }
                    });
                }
                if (!this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(question)) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                        translateValues: { index: index + 1 }
                    });
                }
            }
        }, this);
        return reasons;
    }

    /**
     * Get the reasons, why the quiz is invalid as an HTML string
     *
     * @return {string} the reasons in HTML
     */
    invalidReasonsHTML() {
        const translate = this.translateService;
        let reasonString = '';
        for (const reason of this.invalidReasons()) {
            translate.get(reason.translateKey, reason.translateValues).subscribe((res: string) => {
                reasonString += res + '   -   ';
            });
        }
        return reasonString.substr(0, reasonString.length - 5);
    }

    /**
     * Import the quiz from a json file
     */
    importQuiz() {
        if (this.hasSavedQuizStarted() || this.pendingChanges() || !this.validQuiz()) {
            return;
        }
        let fileReader = new FileReader();
        fileReader.onload = () => {
            let questions = JSON.parse(fileReader.result);
            for (let question of questions) {
                delete question.questionStatistic;
                delete question.id;
                if (question.type === 'multiple-choice') {
                    for (let answerOption of question.answerOptions) {
                        delete answerOption.id;
                    }
                    this.quizExercise.questions = this.quizExercise.questions.concat([question]);
                } else if (question.type === 'drag-and-drop') {
                    // Renaming id property with tempID property,
                    for (let dropLocation of question.dropLocations) {
                        dropLocation.tempID = dropLocation.id;
                        delete dropLocation.id;
                    }
                    for (let dragItem of question.dragItems) {
                        dragItem.tempID = dragItem.id;
                        delete dragItem.id;
                    }
                    for (let correctMapping of question.correctMappings) {
                        delete correctMapping.id;
                        delete correctMapping.dragItemIndex;
                        delete correctMapping.dropLocationIndex;
                        delete correctMapping.invalid;
                        correctMapping.dragItem.tempID = correctMapping.dragItem.id;
                        delete correctMapping.dragItem.id;
                        correctMapping.dropLocation.tempID = correctMapping.dropLocation.id;
                        delete correctMapping.dropLocation.id;
                    }
                    this.quizExercise.questions = this.quizExercise.questions.concat([question]);
                }
            }
        };
        fileReader.readAsText(this.importFile);
    }

    /**
     * Export the quiz to json file
     */
    exportQuiz() {
        if (this.hasSavedQuizStarted() || !this.validQuiz()) {
            return;
        }
        QuizExerciseComponent.exportQuiz(this.quizExercise.questions, false);
    }

    /**
     * Save the quiz to the server
     */
    save() {
        this.onDateTimeChange();
        if (this.hasSavedQuizStarted() || !this.pendingChanges() || !this.validQuiz()) {
            return;
        }
        this.quizExercise.type = 'quiz-exercise';
        this.isSaving = true;
        if (this.quizExercise.id !== undefined) {
            this.repository.update(this.quizExercise)
                .subscribe((quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                });
        } else {
            this.repository.create(this.quizExercise)
                .subscribe((quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                });
        }
    }

    onSaveSuccess(result) {
        this.isSaving = false;
        this.prepareEntity(result);
        this.savedEntity = Object.assign({}, result);
        this.quizExercise = result;
    }

    onSaveError() {
        alert('Saving Quiz Failed! Please try again later.');
        this.isSaving = false;
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
     * Prepares the date and time model
     */
    prepareDateTime() {
        // TODO
        this.dateTime = this.quizExercise.releaseDate;
    }

    /**
     * Reach to changes of time inputs by updating model and ui
     */
    onDateTimeChange() {
        // TODO
        this.quizExercise.releaseDate = this.dateTime;
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
     * Navigate back
     */
    cancel() {
        this.router.navigate(['/course', this.quizExercise.course.id, 'quiz-exercise']);
    }

    /**
     * Check if the saved quiz has started
     *
     * @return {boolean} true if the saved quiz has started, otherwise false
     */
    hasSavedQuizStarted() {
        return !!(
            this.savedEntity &&
            (this.savedEntity as any).isPlannedToStart &&
            moment((this.savedEntity as any).releaseDate).isBefore(moment())
        );
    }
}

/** Defining the angularJS module here to circumvent separation of scopes
 *  The definition is identical to the one in the AngularJS application */
angular
    .module('artemisApp')
    .component('quizExerciseDetail', {
        bindings: {
            'course': '<',
            'quizExercise': '<',
            'repository': '<',
            'dragAndDropQuestionUtil': '<',
            'router': '<',
            'translateService': '<'
        },
        template: require('../../../ng1/entities/quiz-exercise/quiz-exercise-detail.html'),
        controller: QuizExerciseDetailController,
        controllerAs: 'vm'
    });
