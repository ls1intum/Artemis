import { Directive, DoCheck, ElementRef, Inject, Injector, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import { UpgradeComponent } from '@angular/upgrade/static';
import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { CourseService } from '../course/course.service';
import { Course } from '../course/course.model';
import { HttpResponse } from '@angular/common/http';
import { DragAndDropQuestionUtil } from '../../components/util/drag-and-drop-question-util.service';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import 'angular';
import * as moment from 'moment';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { Question, QuestionType, ScoringType } from '../question';
import { MultipleChoiceQuestion } from '../multiple-choice-question';
import { DragAndDropQuestion } from '../drag-and-drop-question';
import { AnswerOption } from '../answer-option';

interface Reason {
    translateKey: string;
    translateValues: {};
}

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
    @Input() courseRepository: CourseService;
    @Input() dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    @Input() router: Router;
    @Input() translateService: TranslateService;
    @Input() fileUploaderService: FileUploaderService;

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

    entity: QuizExercise;
    savedEntity: QuizExercise;
    quizExercise: QuizExercise;
    repository: QuizExerciseService;
    courseRepository: CourseService;
    course: Course;
    router: Router;
    translateService: TranslateService;
    fileUploaderService: FileUploaderService;
    isSaving = false;
    dragAndDropQuestionUtil: DragAndDropQuestionUtil;
    duration = {
        minutes: 0,
        seconds: 0
    };
    dateTime: Date;
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
    courses: Course[] = [];
    selectedCourse: string;
    quizExercises: QuizExercise[] = [];
    allExistingQuestions: Question[] = [];
    existingQuestions: Question[] = [];
    importFile: Blob = null;
    searchQueryText = '';
    dndFilterEnabled = true;
    mcqFilterEnabled = true;

    init() {
        if (this.quizExercise) {
            this.entity = this.quizExercise;
        } else {
            this.entity = new QuizExercise();
            this.entity.title = '';
            this.entity.duration = 600;
            this.entity.isVisibleBeforeStart = false;
            this.entity.isOpenForPractice = false;
            this.entity.isPlannedToStart = false;
            this.entity.releaseDate = new Date((new Date()).toISOString().substring(0, 16));
            this.entity.randomizeQuestionOrder = true;
            this.entity.questions = [];
            this.quizExercise = this.entity;
        }
        this.prepareEntity(this.entity);
        this.prepareDateTime();
        this.savedEntity = this.entity.id ? Object.assign({}, this.entity) : null;
        if (!this.quizExercise.course) {
            this.quizExercise.course = this.course;
        }

        this.updateDuration();
    }

    $onChanges(changes: any) {
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

        const mcQuestion = new MultipleChoiceQuestion();
        mcQuestion.title = '';
        mcQuestion.text = 'Enter your question text here';
        mcQuestion.scoringType = ScoringType.ALL_OR_NOTHING;                    // explicit default value for multiple questions
        mcQuestion.randomizeOrder = true;
        mcQuestion.score = 1;

        const correctSampleAnswerOption = new AnswerOption();
        correctSampleAnswerOption.isCorrect = true;
        correctSampleAnswerOption.text = 'Enter a correct answer option here';

        const incorrectSampleAnswerOption = new AnswerOption();
        incorrectSampleAnswerOption.isCorrect = false;
        incorrectSampleAnswerOption.text = 'Enter an incorrect answer option here';

        mcQuestion.answerOptions = [correctSampleAnswerOption, incorrectSampleAnswerOption];
        this.quizExercise.questions.push(mcQuestion);
    }

    /**
     * Add an empty drag and drop question to the quiz
     */
    addDragAndDropQuestion() {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }

        const dndQuestion = new DragAndDropQuestion();
        dndQuestion.title = '';
        dndQuestion.text = 'Enter your question text here';
        dndQuestion.scoringType = ScoringType.PROPORTIONAL_CORRECT_OPTIONS;     // explicit default value for drag and drop questions
        dndQuestion.randomizeOrder = true;
        dndQuestion.score = 1;
        dndQuestion.dropLocations = [];
        dndQuestion.dragItems = [];
        dndQuestion.correctMappings = [];
        this.quizExercise.questions.push(dndQuestion);
    }

    /**
     * Toggles existing questions view
     */
    showHideExistingQuestions() {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }

        // If courses are not populated, then populate list of courses,
        if (this.courses.length === 0) {
            this.courseRepository.query().subscribe(
                (res: HttpResponse<Course[]>) => {
                    this.courses = res.body;
                }
            );
        }
        this.showExistingQuestions = !this.showExistingQuestions;
    }

    /**
     * Populates list of quiz exercises for the selected course
     */
    onCourseSelect() {
        this.allExistingQuestions = [];
        if (this.selectedCourse === null) {
            return;
        }
        const course = JSON.parse(this.selectedCourse) as Course;
        // For the given course, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise,
        this.repository.findForCourse(course.id)
            .subscribe((quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    const quizExercises = quizExercisesResponse.body;
                    for (const quizExercise of quizExercises) {
                        this.repository.find(quizExercise.id).subscribe((response: HttpResponse<QuizExercise>) => {
                            const quizExerciseResponse = response.body;
                            for (const question of quizExerciseResponse.questions) {
                                question.exercise = quizExercise;
                                this.allExistingQuestions.push(question);
                            }
                            this.applyFilter();
                        });
                    }
                } else {
                    this.onSaveError();
                }
            });
    }

    /**
     * Applies filter on questions shown in add existing questions view.
    */
    applyFilter() {
        this.existingQuestions = [];
        // Depending on the filter selected by user, filter out questions.
        // allExistingQuestions contains list of all questions. We don't change it. We populate existingQuestions list depending on the filter options,
        for (const question of this.allExistingQuestions) {
            if (!this.searchQueryText || this.searchQueryText === ''
                || question.title.toLowerCase().indexOf(this.searchQueryText.toLowerCase()) !== -1) {
                if (this.mcqFilterEnabled === true && question.type === QuestionType.MULTIPLE_CHOICE) {
                    this.existingQuestions.push(question);
                }
                if (this.dndFilterEnabled === true && question.type === QuestionType.DRAG_AND_DROP) {
                    this.existingQuestions.push(question);
                }
            }
        }
    }

    /**
     * Adds selected quizzes to current quiz exercise
     */
    addExistingQuestions() {
        const questions: Question[] = [];
        for (const question of this.existingQuestions) {
            if (question.exportQuiz) {
                questions.push(question);
            }
        }
        this.addQuestions(questions);
        this.showExistingQuestions = !this.showExistingQuestions;
    }

    /**
     * Remove question from the quiz
     * @param question {Question} the question to remove
     */
    deleteQuestion(question: Question) {
        this.quizExercise.questions = this.quizExercise.questions.filter(function(q) {
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
        ].some(function(key) {
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
        const areAllQuestionsValid = this.quizExercise.questions.every(function(question) {
            if (question.type === QuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                return question.title && question.title !== '' && mcQuestion.answerOptions.some(function(answerOption) {
                    return answerOption.isCorrect;
                });
            } else if (question.type === QuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                return question.title && question.title !== '' && dndQuestion.correctMappings &&
                    dndQuestion.correctMappings.length > 0 && this.dragAndDropQuestionUtil.solve(question).length &&
                    this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(question);
            } else {
                console.log('Unknown question type: ' + question);
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
        const reasons = new Array<Reason>();
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
        this.quizExercise.questions.forEach(function(question: Question, index: number) {
            if (!question.title || question.title === '') {
                reasons.push({
                    translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionTitle',
                    translateValues: { index: index + 1 }
                });
            }
            if (question.type === QuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                if (!mcQuestion.answerOptions.some(function(answerOption) {
                    return answerOption.isCorrect;
                })) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionCorrectAnswerOption',
                        translateValues: { index: index + 1 }
                    });
                }
            }
            if (question.type === QuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                if (!dndQuestion.correctMappings || dndQuestion.correctMappings.length === 0) {
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
     * Imports a json quiz file and adds questions to current quiz exercise.
     */
    async importQuiz() {
        if (this.importFile === null || this.importFile === undefined) {
            return;
        }
        const fileReader = new FileReader();
        fileReader.onload = () => {
            try {
                // Read the file and get list of questions from the file,
                const questions = JSON.parse(fileReader.result) as Question[];
                this.addQuestions(questions);
                // Clearing html elements,
                this.importFile = null;
                const control = document.getElementById('importFileInput') as HTMLInputElement;
                control.value = null;
            } catch (e) {
                alert('Import Quiz Failed! Invalid quiz file.');
            }
        };
        fileReader.readAsText(this.importFile);
    }

    /**
     * Adds given questions to current quiz exercise.
     * Ids are removed from new questions so that new id is assigned upon saving the quiz exercise.
     * Images are duplicated for drag and drop questions.
     * @param questions list of questions
     */
    async addQuestions(questions: Question[]) {
        // To make sure all questions are duplicated (new resources are created), we need to remove some fields from the input questions,
        // This contains removing all ids, duplicating images in case of dnd questions,
        for (const question of questions) {
            delete question.questionStatistic;
            delete question.id;
            if (question.type === QuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                for (const answerOption of mcQuestion.answerOptions) {
                    delete answerOption.id;
                }
                this.quizExercise.questions = this.quizExercise.questions.concat([question]);
            } else if (question.type === QuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                // Get image from the old question and duplicate it on the backend and then save new image to the question,
                let fileUploadResponse = await this.fileUploaderService.duplicateFile(dndQuestion.backgroundFilePath);
                dndQuestion.backgroundFilePath = fileUploadResponse.path;

                // For DropLocations, DragItems and CorrectMappings we need to provide tempID,
                // This tempID is used for keep tracking of mappings by backend. Backend removes tempID and generated a new id,
                for (const dropLocation of dndQuestion.dropLocations) {
                    dropLocation.tempID = dropLocation.id;
                    delete dropLocation.id;
                }
                for (const dragItem of dndQuestion.dragItems) {
                    // Duplicating image on backend. This is only valid for image drag items. For text drag items, pictureFilePath is null,
                    if (dragItem.pictureFilePath !== null) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(dragItem.pictureFilePath);
                        dragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    dragItem.tempID = dragItem.id;
                    delete dragItem.id;
                }
                for (const correctMapping of dndQuestion.correctMappings) {
                    // Following fields are not required for dnd question. They will be generated by the backend,
                    delete correctMapping.id;
                    delete correctMapping.dragItemIndex;
                    delete correctMapping.dropLocationIndex;
                    delete correctMapping.invalid;

                    // Duplicating image on backend. This is only valid for image drag items. For text drag items, pictureFilePath is null,
                    if (correctMapping.dragItem.pictureFilePath !== null) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(correctMapping.dragItem.pictureFilePath);
                        correctMapping.dragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    correctMapping.dragItem.tempID = correctMapping.dragItem.id;
                    delete correctMapping.dragItem.id;
                    correctMapping.dropLocation.tempID = correctMapping.dropLocation.id;
                    delete correctMapping.dropLocation.id;
                }
                this.quizExercise.questions = this.quizExercise.questions.concat([question]);
            }
        }
    }

    /**
     * Save the quiz to the server
     */
    save() {
        this.onDateTimeChange();
        if (this.hasSavedQuizStarted() || !this.pendingChanges() || !this.validQuiz()) {
            return;
        }
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

    onSaveSuccess(quizExercise: QuizExercise) {
        this.isSaving = false;
        this.prepareEntity(quizExercise);
        this.savedEntity = Object.assign({}, quizExercise);
        this.quizExercise = quizExercise;
    }

    onSaveError() {
        alert('Saving Quiz Failed! Please try again later.');
        this.isSaving = false;
    }

    /**
     * Makes sure the entity is well formed and its fields are of the correct types
     * @param quizExercise
     */
    prepareEntity(quizExercise: QuizExercise) {
        quizExercise.releaseDate = quizExercise.releaseDate ? new Date(quizExercise.releaseDate) : new Date();
        quizExercise.duration = Number(quizExercise.duration);
        quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
    }

    /**
     * Prepares the date and time model
     */
    prepareDateTime() {
        this.dateTime = this.quizExercise.releaseDate;
    }

    /**
     * Reach to changes of time inputs by updating model and ui
     */
    onDateTimeChange() {
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
        return !!(this.savedEntity &&
            this.savedEntity.isPlannedToStart &&
            moment(this.savedEntity.releaseDate).isBefore(moment())
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
            'courseRepository': '<',
            'courseService': '<',
            'dragAndDropQuestionUtil': '<',
            'router': '<',
            'translateService': '<',
            'fileUploaderService': '<'
        },
        template: require('../../../ng1/entities/quiz-exercise/quiz-exercise-detail.html'),
        controller: QuizExerciseDetailController,
        controllerAs: 'vm'
    });
