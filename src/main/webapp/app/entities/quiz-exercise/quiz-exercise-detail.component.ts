import { Component, OnDestroy, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Course } from '../course/course.model';
import { CourseService } from '../course/course.service';
import { QuizExercise } from './quiz-exercise.model';
import { DragAndDropQuestionUtil } from '../../components/util/drag-and-drop-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { Question, QuestionType, ScoringType } from '../../entities/question';
import { MultipleChoiceQuestion } from '../../entities/multiple-choice-question';
import { DragAndDropQuestion } from '../../entities/drag-and-drop-question';
import { AnswerOption } from '../../entities/answer-option';
import { Option, Duration } from './quiz-exercise-interfaces';
import { NgbDateStruct, NgbDate, NgbTimeStruct } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { Moment } from 'moment';
import { JhiAlertService } from 'ng-jhipster';

interface Reason {
    translateKey: string;
    translateValues: {};
}

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
    providers: [DragAndDropQuestionUtil]
})
export class QuizExerciseDetailComponent implements OnInit, OnChanges, OnDestroy {
    // Make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    course: Course;
    quizExercise: QuizExercise;
    paramSub: Subscription;
    repository: QuizExerciseService;
    courseRepository: CourseService;

    entity: QuizExercise;
    savedEntity: QuizExercise;

    /** Date and time for Quiz Exercise Start Time **/
    startDate: Moment;
    startTime: NgbTimeStruct;
    dateTime: Moment;
    minDate: NgbDateStruct;

    /** Constants for 'Add existing questions' and 'Import file' features **/
    showExistingQuestions = false;
    courses: Course[] = [];
    selectedCourseId: number;
    quizExercises: QuizExercise[];
    allExistingQuestions: Question[];
    existingQuestions: Question[];
    importFile: Blob;
    importFileName: string;
    searchQueryText: string;
    dndFilterEnabled: boolean;
    mcqFilterEnabled: boolean;

    /** Duration object **/
    duration = new Duration(0, 0);

    /** Status constants **/
    isSaving = false;
    isTrue = true;

    /** Status Options **/
    statusOptionsVisible: Option[] = [new Option(false, 'Hidden'), new Option(true, 'Visible')];
    statusOptionsPractice: Option[] = [new Option(false, 'Closed'), new Option(true, 'Open for Practice')];
    statusOptionsActive: Option[] = [new Option(true, 'Active')];

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseService,
        private quizExerciseService: QuizExerciseService,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private router: Router,
        private translateService: TranslateService,
        private fileUploaderService: FileUploaderService,
        private jhiAlertService: JhiAlertService
    ) {}

    ngOnInit(): void {
        /** Initialize local constants **/
        this.showExistingQuestions = false;
        this.courses = [];
        this.quizExercises = [];
        this.allExistingQuestions = [];
        this.existingQuestions = [];
        this.importFile = null;
        this.importFileName = '';
        this.searchQueryText = '';
        this.dndFilterEnabled = true;
        this.mcqFilterEnabled = true;

        /** Set minDate for DatePicker to today **/
        const today = moment();
        this.minDate = { year: today.year(), month: today.month() + 1, day: today.date() };

        this.paramSub = this.route.params.subscribe(params => {
            /** Query the courseService for the participationId given by the params */
            if (params['courseId']) {
                this.courseService.find(params['courseId']).subscribe((response: HttpResponse<Course>) => {
                    this.course = response.body;
                    // Make sure to call init if we didn't receive an id => new quiz-exercise
                    if (!params['id']) {
                        this.init();
                    }
                });
            }
            if (params['id']) {
                this.quizExerciseService.find(params['id']).subscribe((response: HttpResponse<QuizExercise>) => {
                    this.quizExercise = response.body;
                    this.init();
                });
            }
        });
        this.repository = this.quizExerciseService;
        this.courseRepository = this.courseService;
    }

    /**
     * @function init
     * @desc Initializes local constants and prepares the QuizExercise entity
     */
    init(): void {
        if (this.quizExercise) {
            this.entity = this.quizExercise;
        } else {
            this.entity = new QuizExercise();
            this.entity.title = '';
            this.entity.duration = 600;
            this.entity.isVisibleBeforeStart = false;
            this.entity.isOpenForPractice = false;
            this.entity.isPlannedToStart = false;
            this.entity.releaseDate = moment();
            this.entity.randomizeQuestionOrder = true;
            this.entity.questions = [];
            this.quizExercise = this.entity;
        }
        this.prepareEntity(this.entity);
        this.prepareDateTime();
        // Assign savedEntity to identify local changes
        this.savedEntity = this.entity.id ? JSON.parse(JSON.stringify(this.entity)) : new QuizExercise();
        if (!this.quizExercise.course) {
            this.quizExercise.course = this.course;
        }
        this.updateDuration();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.course || changes.quizExercise) {
            this.init();
        }
    }

    /**
     * @function showDropdown
     * @desc Determine which dropdown to display depending on the relationship between start time, end time, and current time
     * @returns {string} Name of the dropdown to show
     */
    showDropdown(): string {
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
     * @desc Callback for datepicker to decide whether given date should be disabled
     * All dates which are in the past (< today) are disabled
     */
    isDateInPast = (date: NgbDate, current: { month: number }) =>
        current.month < moment().month() + 1 ||
        moment()
            .year(date.year)
            .month(date.month - 1)
            .date(date.day)
            .isBefore(moment());

    /**
     * @function addMultipleChoiceQuestion
     * @desc Add an empty multiple choice question to the quiz
     */
    addMultipleChoiceQuestion() {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }

        const mcQuestion = new MultipleChoiceQuestion();
        mcQuestion.title = '';
        mcQuestion.text = 'Enter your long question if needed';
        mcQuestion.scoringType = ScoringType.ALL_OR_NOTHING; // explicit default value for multiple questions
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
     * @function addDragAndDropQuestion
     * @desc Add an empty drag and drop question to the quiz
     */
    addDragAndDropQuestion(): void {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }

        const dndQuestion = new DragAndDropQuestion();
        dndQuestion.title = '';
        dndQuestion.text = 'Enter your long question if needed';
        dndQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY; // explicit default value for drag and drop questions
        dndQuestion.randomizeOrder = true;
        dndQuestion.score = 1;
        dndQuestion.dropLocations = [];
        dndQuestion.dragItems = [];
        dndQuestion.correctMappings = [];
        this.quizExercise.questions.push(dndQuestion);
    }

    /**
     * @function calculateMaxExerciseScore
     * @desc Iterates over the questions of the quizExercise and calculates the sum of all question scores
     */
    calculateMaxExerciseScore(): number {
        let scoreSum = 0;
        this.quizExercise.questions.forEach(question => (scoreSum += question.score));
        return scoreSum;
    }

    /**
     * @function showHideExistingQuestions
     * @desc Toggles existing questions view
     */
    showHideExistingQuestions(): void {
        if (this.quizExercise == null) {
            this.quizExercise = this.entity;
        }

        // If courses are not populated, then populate list of courses,
        if (this.courses.length === 0) {
            this.courseRepository.query().subscribe((res: HttpResponse<Course[]>) => {
                this.courses = res.body;
            });
        }
        this.showExistingQuestions = !this.showExistingQuestions;
        this.selectedCourseId = null;
        this.allExistingQuestions = this.existingQuestions = [];
    }

    /**
     * @function onCourseSelect
     * @desc Callback function for when a user selected a Course from the Dropdown list from 'Add existing questions'
     *       Populates list of quiz exercises for the selected course
     */
    onCourseSelect(): void {
        this.allExistingQuestions = this.existingQuestions = [];
        if (this.selectedCourseId == null) {
            return;
        }

        /** Search the selected course by id in all available courses **/
        const selectedCourse = this.courses.find(course => course.id === Number(this.selectedCourseId));

        // For the given course, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise,
        this.repository.findForCourse(selectedCourse.id).subscribe(
            (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
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
                }
            },
            (res: HttpErrorResponse) => this.onError(res)
        );
    }

    /**
     * @function applyFilter
     * @desc Applies filter on questions shown in add existing questions view.
     */
    applyFilter(): void {
        this.existingQuestions = [];
        /**
         * Depending on the filter selected by user, filter out questions.
         * allExistingQuestions contains list of all questions.
         * We don't change it. We populate existingQuestions list depending on the filter options.
         */
        for (const question of this.allExistingQuestions) {
            if (
                !this.searchQueryText ||
                this.searchQueryText === '' ||
                question.title.toLowerCase().indexOf(this.searchQueryText.toLowerCase()) !== -1
            ) {
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
     * @function setImportFile
     * @desc Assigns the uploaded import file
     * @param $event object containing the uploaded file
     */
    setImportFile($event: any): void {
        if ($event.target.files.length) {
            const fileList: FileList = $event.target.files;
            this.importFile = fileList[0];
            this.importFileName = this.importFile['name'];
        }
    }

    /**
     * @function addExistingQuestions
     * @desc Adds selected quizzes to current quiz exercise
     */
    addExistingQuestions(): void {
        const questions: Question[] = [];
        for (const question of this.existingQuestions) {
            if (question.exportQuiz) {
                questions.push(question);
            }
        }
        this.addQuestions(questions);
        this.showExistingQuestions = !this.showExistingQuestions;
        this.selectedCourseId = null;
        this.allExistingQuestions = this.existingQuestions = [];
    }

    /**
     * @function deleteQuestion
     * @desc Remove question from the quiz
     * @param questionToDelete {Question} the question to remove
     */
    deleteQuestion(questionToDelete: Question): void {
        this.quizExercise.questions = this.quizExercise.questions.filter(question => question !== questionToDelete);
    }

    /**
     * @function onQuestionUpdated
     * @desc Handles the change of a question by replacing the array with a copy (allows for shallow comparison)
     */
    onQuestionUpdated(): void {
        this.quizExercise.questions = Array.from(this.quizExercise.questions);
    }

    /**
     * @function pendingChanges
     * @desc Determine if there are any changes waiting to be saved
     * @returns {boolean} true if there are any pending changes, false otherwise
     */
    pendingChanges(): boolean {
        if (!this.quizExercise || !this.savedEntity) {
            return false;
        }
        const keysToCompare = ['title', 'duration', 'isPlannedToStart', 'isVisibleBeforeStart', 'isOpenForPractice'];

        // Unsaved changes if any of the stated object key values are not equal or the questions/release dates differ
        return (
            keysToCompare.some(key => this.quizExercise[key] !== this.savedEntity[key]) ||
            !this.areDatesIdentical(this.quizExercise.releaseDate, this.savedEntity.releaseDate) ||
            !this.areQuizExerciseEntityQuestionsIdentical(this.quizExercise.questions, this.savedEntity.questions)
        );
    }

    /**
     * @function areQuizExerciseEntityQuestionsIdentical
     * @desc Compares the provided question array objects
     * @param QA1 {Question[]} First question array to compare
     * @param QA2 {Question[]} Second question array to compare against
     * @return {boolean} true if the provided Question[] objects are identical, false otherwise
     */
    areQuizExerciseEntityQuestionsIdentical(QA1: Question[], QA2: Question[]): boolean {
        return JSON.stringify(QA1).toLowerCase() === JSON.stringify(QA2).toLowerCase();
    }

    /**
     * @function areDatesIdentical
     * @desc This function compares the provided dates with help of the moment library
     * Since we might be receiving an string instead of a moment object (e.g. when receiving it from the backend)
     * we wrap both dates in a moment object. If it's already a moment object, this will just be ignored.
     * @param date1 {string|Moment} First date to compare
     * @param date2 {string|Moment} Second date to compare to
     * @return {boolean} True if the dates are identical, false otherwise
     */
    areDatesIdentical(date1: string | Moment, date2: string | Moment): boolean {
        return moment(date1).isSame(moment(date2));
    }

    /**
     * @function validQuiz
     * @desc Check if the current inputs are valid
     * @returns {boolean} true if valid, false otherwise
     */
    validQuiz(): boolean {
        if (!this.quizExercise) {
            return false;
        }
        // Release date is valid if it's not null and a valid date; Precondition: isPlannedToStart is set
        // Release date should also not be in the past
        const releaseDateValidAndNotInPastCondition: boolean =
            !this.quizExercise.isPlannedToStart ||
            (this.quizExercise.releaseDate != null &&
                moment(this.quizExercise.releaseDate).isValid() &&
                moment(this.quizExercise.releaseDate).isAfter(moment()));

        const isGenerallyValid: boolean =
            this.quizExercise.title &&
            this.quizExercise.title !== '' &&
            this.quizExercise.duration &&
            releaseDateValidAndNotInPastCondition &&
            this.quizExercise.questions &&
            !!this.quizExercise.questions.length;
        const areAllQuestionsValid = this.quizExercise.questions.every(function(question) {
            if (question.type === QuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                return question.title && question.title !== '' && mcQuestion.answerOptions.some(answerOption => answerOption.isCorrect);
            } else if (question.type === QuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                return (
                    question.title &&
                    question.title !== '' &&
                    dndQuestion.correctMappings &&
                    dndQuestion.correctMappings.length > 0 &&
                    this.dragAndDropQuestionUtil.solve(dndQuestion).length &&
                    this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)
                );
            } else {
                console.log('Unknown question type: ' + question);
                return question.title && question.title !== '';
            }
        }, this);

        return isGenerallyValid && areAllQuestionsValid;
    }

    /**
     * @function invalidReasons
     * @desc Get the reasons, why the quiz is invalid
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    invalidReasons(): Reason[] {
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
        /** We only verify the releaseDate if the checkbox is activated **/
        if (this.quizExercise.isPlannedToStart) {
            if (this.quizExercise.releaseDate == null || !moment(this.quizExercise.releaseDate).isValid()) {
                reasons.push({
                    translateKey: 'arTeMiSApp.quizExercise.invalidReasons.invalidStartTime',
                    translateValues: {}
                });
            }
            // Release Date valid but lies in the past
            if (this.quizExercise.releaseDate && moment(this.quizExercise.releaseDate).isValid()) {
                if (moment(this.quizExercise.releaseDate).isBefore(moment())) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.startTimeInPast',
                        translateValues: {}
                    });
                }
            }
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
                if (!mcQuestion.answerOptions.some(answerOption => answerOption.isCorrect)) {
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
                } else if (this.dragAndDropQuestionUtil.solve(dndQuestion, []).length === 0) {
                    reasons.push({
                        translateKey: 'arTeMiSApp.quizExercise.invalidReasons.questionUnsolvable',
                        translateValues: { index: index + 1 }
                    });
                }
                if (!this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)) {
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
     * @function invalidReasonsHTML
     * @desc Get the reasons, why the quiz is invalid as an HTML string
     * @return {string} the reasons in HTML
     */
    invalidReasonsHTML(): string {
        const translate = this.translateService;
        let reasonString = '';
        for (const reason of this.invalidReasons()) {
            translate.get(reason['translateKey'], reason['translateValues']).subscribe((res: string) => {
                reasonString += res + '   -   ';
            });
        }
        return reasonString.substr(0, reasonString.length - 5);
    }

    /**
     * @function importQuiz
     * @desc Imports a json quiz file and adds questions to current quiz exercise.
     */
    async importQuiz() {
        if (this.importFile === null || this.importFile === undefined) {
            return;
        }
        const fileReader = new FileReader();
        fileReader.onload = () => {
            try {
                // Read the file and get list of questions from the file
                const questions = JSON.parse(fileReader.result as string) as Question[];
                this.addQuestions(questions);
                // Clearing html elements,
                this.importFile = null;
                this.importFileName = '';
                const control = document.getElementById('importFileInput') as HTMLInputElement;
                control.value = null;
            } catch (e) {
                alert('Import Quiz Failed! Invalid quiz file.');
            }
        };
        fileReader.readAsText(this.importFile);
    }

    /**
     * @function addQuestions
     * @desc Adds given questions to current quiz exercise.
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
     * @function save
     * @desc Save the quiz to the server and invoke callback functions depending of result
     */
    save(): void {
        this.onDateTimeChange();
        if (this.hasSavedQuizStarted() || !this.pendingChanges() || !this.validQuiz()) {
            return;
        }
        this.isSaving = true;
        if (this.quizExercise.id !== undefined) {
            this.repository.update(this.quizExercise).subscribe(
                (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                (res: HttpErrorResponse) => this.onSaveError(res)
            );
        } else {
            this.repository.create(this.quizExercise).subscribe(
                (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                (res: HttpErrorResponse) => this.onSaveError(res)
            );
        }
    }

    /**
     * @function onSaveSuccess
     * @desc Callback function for when the save succeeds
     * Terminates the saving process and assign the returned quizExercise to the local entities
     * @param {QuizExercise} quizExercise: Saved quizExercise entity
     */
    onSaveSuccess(quizExercise: QuizExercise): void {
        this.isSaving = false;
        this.prepareEntity(quizExercise);
        this.savedEntity = JSON.parse(JSON.stringify(quizExercise));
        this.quizExercise = quizExercise;
    }

    /**
     * @function onSaveError
     * @desc Callback function for when the save fails
     */
    onSaveError(error?: HttpErrorResponse): void {
        console.error('Saving Quiz Failed! Please try again later.');
        this.jhiAlertService.error('arTeMiSApp.quizExercise.saveError');
        this.isSaving = false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }

    /**
     * @function prepareEntity
     * @desc Makes sure the entity is well formed and its fields are of the correct types
     * @param quizExercise {QuizExercise} exercise which will be prepared
     */
    prepareEntity(quizExercise: QuizExercise): void {
        quizExercise.releaseDate = quizExercise.releaseDate ? moment(quizExercise.releaseDate) : moment();
        quizExercise.duration = Number(quizExercise.duration);
        quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
    }

    /**
     * @function prepareDateTime
     * @desc Prepares the date and time model
     */
    prepareDateTime(): void {
        this.dateTime = moment(this.quizExercise.releaseDate);

        // Assign start date by also simply wrapping into moment object
        this.startDate = moment(this.quizExercise.releaseDate);
        // For the time object (time picker), we have to extract the hour and minute values manually
        this.startTime = {
            hour: moment(this.quizExercise.releaseDate).hours(),
            minute: moment(this.quizExercise.releaseDate).minutes(),
            second: 0
        };
    }

    /**
     * @function onDateTimeChange
     * @desc Reach to changes of time inputs by updating model and ui
     */
    onDateTimeChange(newTimeValue?: NgbTimeStruct): void {
        // We have to explicitly assign the new start time here since it hasn't been updated yet
        if (newTimeValue != null) {
            this.startTime = newTimeValue;
        }
        // If the Start Date is valid, we process it, otherwise we set Release Date null
        if (this.startDate && this.startDate.isValid()) {
            // We then set the hours and minutes of our dateTime to the respective time values from our time picker
            this.dateTime = this.startDate.hours(this.startTime.hour).minutes(this.startTime.minute);
            this.quizExercise.releaseDate = this.dateTime;
        } else {
            this.quizExercise.releaseDate = null;
        }
    }

    /**
     * @function onDurationChange
     * @desc Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange(): void {
        const duration = moment.duration(this.duration);
        this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
        this.updateDuration();
    }

    /**
     * @function updateDuration
     * @desc Update ui to current value of duration
     */
    updateDuration(): void {
        const duration = moment.duration(this.quizExercise.duration, 'seconds');
        this.duration.minutes = 60 * duration.hours() + duration.minutes();
        this.duration.seconds = duration.seconds();
    }

    /**
     * @function cancel
     * @desc Navigate back
     */
    cancel(): void {
        this.router.navigate(['/course', this.quizExercise.course.id, 'quiz-exercise']);
    }

    /**
     * @function hasSavedQuizStarted
     * @desc Check if the saved quiz has started
     * @return {boolean} true if the saved quiz has started, otherwise false
     */
    hasSavedQuizStarted(): boolean {
        return !!(this.savedEntity && this.savedEntity.isPlannedToStart && moment(this.savedEntity.releaseDate).isBefore(moment()));
    }

    ngOnDestroy(): void {
        /** Unsubscribe from route params **/
        this.paramSub.unsubscribe();
    }
}
