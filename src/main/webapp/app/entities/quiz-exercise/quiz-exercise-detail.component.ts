import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnChanges, OnInit, QueryList, SimpleChanges, ViewChildren, ViewEncapsulation } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { QuizExercise } from './quiz-exercise.model';
import { DragAndDropQuestionUtil } from 'app/components/util/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/components/util/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { QuizQuestion, QuizQuestionType, ScoringType } from '../quiz-question';
import { MultipleChoiceQuestion } from 'app/entities/multiple-choice-question';
import { DragAndDropQuestion } from 'app/entities/drag-and-drop-question';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question';
import { AnswerOption } from 'app/entities/answer-option';
import { Duration, Option } from './quiz-exercise-interfaces';
import { NgbDate } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Location } from '@angular/common';
import { ComponentCanDeactivate } from 'app/shared';
import { JhiAlertService } from 'ng-jhipster';
import { Observable } from 'rxjs/Observable';
import { EditDragAndDropQuestionComponent, EditMultipleChoiceQuestionComponent, EditShortAnswerQuestionComponent } from 'app/quiz/edit';
import { EditQuizQuestion } from 'app/quiz/edit/edit-quiz-question.interface';
import { ExerciseCategory, ExerciseService } from 'app/entities/exercise';

interface Reason {
    translateKey: string;
    translateValues: any;
}

interface Warning {
    translateKey: string;
    translateValues: any;
}

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [DragAndDropQuestionUtil, ShortAnswerQuestionUtil],
    styleUrls: ['./quiz-exercise-detail.component.scss', '../../quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class QuizExerciseDetailComponent implements OnInit, OnChanges, ComponentCanDeactivate {
    // Make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    @ViewChildren('editMultipleChoice')
    editMultipleChoiceQuestionComponents: QueryList<EditMultipleChoiceQuestionComponent>;

    @ViewChildren('editDragAndDrop')
    editDragAndDropQuestionComponents: QueryList<EditDragAndDropQuestionComponent>;

    @ViewChildren('editShortAnswer')
    editShortAnswerQuestionComponents: QueryList<EditShortAnswerQuestionComponent>;

    course: Course;
    quizExercise: QuizExercise;
    courseRepository: CourseService;
    notificationText: string | null;

    entity: QuizExercise;
    savedEntity: QuizExercise;

    /** Constants for 'Add existing questions' and 'Import file' features **/
    showExistingQuestions = false;
    courses: Course[] = [];
    selectedCourseId: number | null;
    quizExercises: QuizExercise[];
    allExistingQuestions: QuizQuestion[];
    existingQuestions: QuizQuestion[];
    importFile: Blob | null;
    importFileName: string;
    searchQueryText: string;
    dndFilterEnabled: boolean;
    mcqFilterEnabled: boolean;
    shortAnswerFilterEnabled: boolean;

    /** Duration object **/
    duration = new Duration(0, 0);

    /** Status constants **/
    isSaving = false;
    quizIsValid: boolean;
    warningQuizCache = false;
    pendingChangesCache: boolean;

    /** Status Options **/
    statusOptionsVisible: Option[] = [new Option(false, 'Hidden'), new Option(true, 'Visible')];
    statusOptionsPractice: Option[] = [new Option(false, 'Closed'), new Option(true, 'Open for Practice')];
    statusOptionsActive: Option[] = [new Option(true, 'Active')];

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseService,
        private quizExerciseService: QuizExerciseService,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private router: Router,
        private translateService: TranslateService,
        private fileUploaderService: FileUploaderService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private location: Location,
        private changeDetector: ChangeDetectorRef,
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
        this.shortAnswerFilterEnabled = true;
        this.notificationText = null;

        const courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        const quizId = Number(this.route.snapshot.paramMap.get('id'));
        /** Query the courseService for the participationId given by the params */
        if (courseId) {
            this.courseService.find(courseId).subscribe((response: HttpResponse<Course>) => {
                this.course = response.body!;
                // Make sure to call init if we didn't receive an id => new quiz-exercise
                if (!quizId) {
                    this.init();
                }
            });
        }
        if (quizId) {
            this.quizExerciseService.find(quizId).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body!;
                this.init();
            });
        }
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
            this.entity.quizQuestions = [];
            this.quizExercise = this.entity;
        }
        this.prepareEntity(this.entity);
        // Assign savedEntity to identify local changes
        this.savedEntity = this.entity.id ? JSON.parse(JSON.stringify(this.entity)) : new QuizExercise();
        if (!this.quizExercise.course) {
            this.quizExercise.course = this.course;
        }
        this.exerciseCategories = this.exerciseService.convertExerciseCategoriesFromServer(this.quizExercise);
        this.courseService.findAllCategoriesOfCourse(this.quizExercise.course.id).subscribe(
            (res: HttpResponse<string[]>) => {
                this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(res.body!);
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
        this.updateDuration();
        this.cacheValidation();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.course || changes.quizExercise) {
            this.init();
        }
    }

    updateCategories(categories: ExerciseCategory[]) {
        this.quizExercise.categories = categories.map(el => JSON.stringify(el));
        this.cacheValidation();
    }

    /**
     * @function showDropdown
     * @desc Determine which dropdown to display depending on the relationship between start time, end time, and current time
     * @returns {string} Name of the dropdown to show
     */
    get showDropdown(): string {
        if (this.quizExercise && this.quizExercise.isPlannedToStart) {
            const releaseDate = this.quizExercise.releaseDate!;
            const plannedEndMoment = moment(releaseDate).add(this.quizExercise.duration, 'seconds');
            if (plannedEndMoment.isBefore(moment())) {
                return 'isOpenForPractice';
            } else if (moment(releaseDate).isBefore(moment())) {
                return 'active';
            }
        }
        return 'isVisibleBeforeStart';
    }

    canDeactivate(): Observable<boolean> | boolean {
        return !this.pendingChangesCache;
    }

    // displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (!this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
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
        mcQuestion.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        mcQuestion.scoringType = ScoringType.ALL_OR_NOTHING; // explicit default value for multiple questions
        mcQuestion.randomizeOrder = true;
        mcQuestion.score = 1;

        const correctSampleAnswerOption = new AnswerOption();
        correctSampleAnswerOption.isCorrect = true;
        correctSampleAnswerOption.text = 'Enter a correct answer option here';
        correctSampleAnswerOption.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        correctSampleAnswerOption.explanation = 'Add an explanation here (only visible in feedback after quiz has ended)';

        const incorrectSampleAnswerOption = new AnswerOption();
        incorrectSampleAnswerOption.isCorrect = false;
        incorrectSampleAnswerOption.text = 'Enter a wrong answer option here';

        mcQuestion.answerOptions = [correctSampleAnswerOption, incorrectSampleAnswerOption];
        this.quizExercise.quizQuestions.push(mcQuestion);
        this.cacheValidation();
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
        dndQuestion.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        dndQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY; // explicit default value for drag and drop questions
        dndQuestion.randomizeOrder = true;
        dndQuestion.score = 1;
        dndQuestion.dropLocations = [];
        dndQuestion.dragItems = [];
        dndQuestion.correctMappings = [];
        this.quizExercise.quizQuestions.push(dndQuestion);
        this.cacheValidation();
    }

    /**
     * @function addDShortAnswerQuestion
     * @desc Add an empty short answer question to the quiz
     */
    addShortAnswerQuestion(): void {
        if (typeof this.quizExercise === 'undefined') {
            this.quizExercise = this.entity;
        }

        const shortAnswerQuestion = new ShortAnswerQuestion();
        shortAnswerQuestion.title = '';
        shortAnswerQuestion.text =
            'Enter your long question if needed\n\n' +
            'Select a part of the text and click on Add Spot to automatically create an input field and the corresponding mapping\n\n' +
            'You can define a input field like this: This [-spot 1] an [-spot 2] field.\n\n' +
            'To define the solution for the input fields you need to create a mapping (multiple mapping also possible):\n\n' +
            '[-option 1] is\n' +
            '[-option 2] input\n' +
            '[-option 1,2] correctInBothFields';
        shortAnswerQuestion.scoringType = ScoringType.ALL_OR_NOTHING; // explicit default value for short answer questions
        shortAnswerQuestion.randomizeOrder = true;
        shortAnswerQuestion.score = 1;
        shortAnswerQuestion.spots = [];
        shortAnswerQuestion.solutions = [];
        shortAnswerQuestion.correctMappings = [];
        this.quizExercise.quizQuestions.push(shortAnswerQuestion);
        this.cacheValidation();
    }

    /**
     * @function calculateMaxExerciseScore
     * @desc Iterates over the questions of the quizExercise and calculates the sum of all question scores
     */
    calculateMaxExerciseScore(): number {
        let scoreSum = 0;
        this.quizExercise.quizQuestions.forEach(question => (scoreSum += question.score));
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
                this.courses = res.body!;
            });
        }
        this.showExistingQuestions = !this.showExistingQuestions;
        this.selectedCourseId = null;
        this.allExistingQuestions = this.existingQuestions = [];
        this.changeDetector.detectChanges();
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
        const selectedCourse = this.courses.find(course => course.id === Number(this.selectedCourseId))!;

        // TODO: the following code seems duplicated (see quiz-exercise-export.component.ts in the method loadForCourse). Try to avoid duplication!
        // For the given course, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise,
        this.quizExerciseService.findForCourse(selectedCourse.id).subscribe(
            (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    const quizExercises = quizExercisesResponse.body!;
                    for (const quizExercise of quizExercises) {
                        this.quizExerciseService.find(quizExercise.id).subscribe((response: HttpResponse<QuizExercise>) => {
                            const quizExerciseResponse = response.body!;
                            if (quizExerciseResponse.quizQuestions != null && quizExerciseResponse.quizQuestions.length > 0) {
                                for (const question of quizExerciseResponse.quizQuestions) {
                                    question.exercise = quizExercise;
                                    this.allExistingQuestions.push(question);
                                }
                            } else {
                                console.log('The quiz ' + quizExerciseResponse.title + ' does not contain questions!');
                            }
                            this.applyFilter();
                        });
                    }
                }
            },
            (res: HttpErrorResponse) => this.onError(res),
        );
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
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
            if (!this.searchQueryText || this.searchQueryText === '' || question.title.toLowerCase().indexOf(this.searchQueryText.toLowerCase()) !== -1) {
                if (this.mcqFilterEnabled === true && question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    this.existingQuestions.push(question);
                }
                if (this.dndFilterEnabled === true && question.type === QuizQuestionType.DRAG_AND_DROP) {
                    this.existingQuestions.push(question);
                }
                if (this.shortAnswerFilterEnabled === true && question.type === QuizQuestionType.SHORT_ANSWER) {
                    this.existingQuestions.push(question);
                }
            }
        }
        this.cacheValidation();
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
        this.changeDetector.detectChanges();
    }

    /**
     * @function addExistingQuestions
     * @desc Adds selected quizzes to current quiz exercise
     */
    addExistingQuestions(): void {
        const questions: QuizQuestion[] = [];
        for (const question of this.existingQuestions) {
            if (question.exportQuiz) {
                questions.push(question);
            }
        }
        this.addQuestions(questions);
        this.showExistingQuestions = !this.showExistingQuestions;
        this.selectedCourseId = null;
        this.allExistingQuestions = this.existingQuestions = [];
        this.cacheValidation();
    }

    /**
     * @function cacheValidation
     * @desc 1. Check whether the inputs in the quiz are valid
     *       2. Check if warning are needed for the inputs
     *       3. Display the warnings/invalid reasons in the html file if needed
     */
    cacheValidation(): void {
        this.warningQuizCache = this.computeInvalidWarnings().length > 0;
        this.quizIsValid = this.validQuiz();
        this.pendingChangesCache = this.pendingChanges();
        this.computeInvalidReasons();
        this.computeInvalidWarnings();
        this.changeDetector.detectChanges();
    }

    /**
     * @function deleteQuestion
     * @desc Remove question from the quiz
     * @param questionToDelete {QuizQuestion} the question to remove
     */
    deleteQuestion(questionToDelete: QuizQuestion): void {
        this.quizExercise.quizQuestions = this.quizExercise.quizQuestions.filter(question => question !== questionToDelete);
        this.cacheValidation();
    }

    /**
     * @function onQuestionUpdated
     * @desc Handles the change of a question by replacing the array with a copy (allows for shallow comparison)
     */
    onQuestionUpdated(): void {
        this.cacheValidation();
        this.quizExercise.quizQuestions = Array.from(this.quizExercise.quizQuestions);
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
        const keysToCompare = ['title', 'difficulty', 'duration', 'isPlannedToStart', 'isVisibleBeforeStart', 'isOpenForPractice'];

        // Unsaved changes if any of the stated object key values are not equal or the questions/release dates differ
        return (
            keysToCompare.some(key => this.quizExercise[key] !== this.savedEntity[key]) ||
            !this.areDatesIdentical(this.quizExercise.releaseDate!, this.savedEntity.releaseDate!) ||
            !this.areCategoriesIdentical(this.quizExercise.categories, this.savedEntity.categories) ||
            !this.areQuizExerciseEntityQuestionsIdentical(this.quizExercise.quizQuestions, this.savedEntity.quizQuestions)
        );
    }

    areCategoriesIdentical(categoriesUsed: string[], categoriesSaved: string[]): boolean {
        if (!categoriesUsed) {
            categoriesUsed = [];
        }
        if (!categoriesSaved) {
            categoriesSaved = [];
        }
        return JSON.stringify(categoriesUsed).toLowerCase() === JSON.stringify(categoriesSaved).toLowerCase();
    }

    /**
     * @function areQuizExerciseEntityQuestionsIdentical
     * @desc Compares the provided question array objects
     * @param QA1 {QuizQuestion[]} First question array to compare
     * @param QA2 {QuizQuestion[]} Second question array to compare against
     * @return {boolean} true if the provided Question[] objects are identical, false otherwise
     */
    areQuizExerciseEntityQuestionsIdentical(QA1: QuizQuestion[], QA2: QuizQuestion[]): boolean {
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
    private validQuiz(): boolean {
        if (!this.quizExercise) {
            return false;
        }
        // Release date is valid if it's not null and a valid date; Precondition: isPlannedToStart is set
        // Release date should also not be in the past
        const releaseDateValidAndNotInPastCondition: boolean =
            !this.quizExercise.isPlannedToStart ||
            (this.quizExercise.releaseDate != null && moment(this.quizExercise.releaseDate).isValid() && moment(this.quizExercise.releaseDate).isAfter(moment()));

        const isGenerallyValid: boolean =
            this.quizExercise.title !== '' &&
            this.quizExercise.title.length < 250 &&
            this.quizExercise.duration !== 0 &&
            releaseDateValidAndNotInPastCondition &&
            this.quizExercise.quizQuestions &&
            !!this.quizExercise.quizQuestions.length;
        const areAllQuestionsValid = this.quizExercise.quizQuestions.every(function(question) {
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                if (mcQuestion.answerOptions!.some(answerOption => answerOption.isCorrect)) {
                    return question.title && question.title !== '' && question.title.length < 250;
                }
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                return (
                    question.title &&
                    question.title !== '' &&
                    question.title.length < 250 &&
                    dndQuestion.correctMappings &&
                    dndQuestion.correctMappings.length > 0 &&
                    this.dragAndDropQuestionUtil.solve(dndQuestion).length &&
                    this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)
                );
            } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const shortAnswerQuestion = question as ShortAnswerQuestion;
                return (
                    question.title &&
                    question.title !== '' &&
                    shortAnswerQuestion.correctMappings &&
                    shortAnswerQuestion.correctMappings.length > 0 &&
                    // && this.shortAnswerQuestionUtil.solveShortAnswer(shortAnswerQuestion).length
                    this.shortAnswerQuestionUtil.validateNoMisleadingCorrectShortAnswerMapping(shortAnswerQuestion) &&
                    this.shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings, shortAnswerQuestion.spots) &&
                    this.shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings) &&
                    shortAnswerQuestion.solutions.filter(solution => solution.text.trim() === '').length === 0 &&
                    !this.shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings) &&
                    this.shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion)
                );
            } else {
                console.log('Unknown question type: ' + question);
                return question.title && question.title !== '';
            }
        }, this);

        return isGenerallyValid && areAllQuestionsValid;
    }

    /**
     * @function computeInvalidWarnings
     * @desc Get the reasons, why the quiz needs warnings
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    computeInvalidWarnings(): Warning[] {
        const invalidWarnings = !this.quizExercise
            ? []
            : this.quizExercise.quizQuestions
                  .map((question, index) => {
                      if (question.type === QuizQuestionType.MULTIPLE_CHOICE && (<MultipleChoiceQuestion>question).answerOptions!.some(option => !option.explanation)) {
                          return {
                              translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                              translateValues: { index: index + 1 },
                          };
                      }
                  })
                  .filter(Boolean);

        return invalidWarnings as Warning[];
    }

    /**
     * @function invalidReasons
     * @desc Get the reasons, why the quiz is invalid
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    computeInvalidReasons(): Reason[] {
        const invalidReasons = new Array<Reason>();
        if (!this.quizExercise) {
            return [];
        }

        if (!this.quizExercise.title || this.quizExercise.title === '') {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizTitle',
                translateValues: {},
            });
        }
        if (this.quizExercise.title.length >= 250) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizTitleLength',
                translateValues: {},
            });
        }
        if (!this.quizExercise.duration) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.quizDuration',
                translateValues: {},
            });
        }
        if (!this.quizExercise.quizQuestions || this.quizExercise.quizQuestions.length === 0) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.invalidReasons.noQuestion',
                translateValues: {},
            });
        }
        /** We only verify the releaseDate if the checkbox is activated **/
        if (this.quizExercise.isPlannedToStart) {
            if (this.quizExercise.releaseDate == null || !moment(this.quizExercise.releaseDate).isValid()) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.invalidStartTime',
                    translateValues: {},
                });
            }
            // Release Date valid but lies in the past
            if (this.quizExercise.releaseDate && moment(this.quizExercise.releaseDate).isValid()) {
                if (moment(this.quizExercise.releaseDate).isBefore(moment())) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.startTimeInPast',
                        translateValues: {},
                    });
                }
            }
        }
        this.quizExercise.quizQuestions.forEach(function(question: QuizQuestion, index: number) {
            if (!question.title || question.title === '') {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitle',
                    translateValues: { index: index + 1 },
                });
            }
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                if (!mcQuestion.answerOptions!.some(answeroption => answeroption.isCorrect)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectAnswerOption',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!mcQuestion.answerOptions!.every(answeroption => answeroption.explanation !== '')) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                        translateValues: { index: index + 1 },
                    });
                }
            }
            if (question.title.length >= 250) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitleLength',
                    translateValues: { index: index + 1 },
                });
            }

            if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                if (!dndQuestion.correctMappings || dndQuestion.correctMappings.length === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                } else if (this.dragAndDropQuestionUtil.solve(dndQuestion, []).length === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionUnsolvable',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.dragAndDropQuestionUtil.validateNoMisleadingCorrectMapping(dndQuestion)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                }
            }
            if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const shortAnswerQuestion = question as ShortAnswerQuestion;
                if (!shortAnswerQuestion.correctMappings || shortAnswerQuestion.correctMappings.length === 0) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                } /*else if (this.shortAnswerQuestionUtil.solveShortAnswer(shortAnswerQuestion, []).length === 0) {
                    reasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionUnsolvable',
                        translateValues: { index: index + 1 }
                    });
                } */
                if (!this.shortAnswerQuestionUtil.validateNoMisleadingCorrectShortAnswerMapping(shortAnswerQuestion)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.misleadingCorrectMapping',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings, shortAnswerQuestion.spots)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEverySpotHasASolution',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionEveryMappedSolutionHasASpot',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!(shortAnswerQuestion.solutions.filter(solution => solution.text.trim() === '').length === 0)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionSolutionHasNoValue',
                        translateValues: { index: index + 1 },
                    });
                }
                if (this.shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionDuplicateMapping',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!this.shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.shortAnswerQuestionUnsolvable',
                        translateValues: { index: index + 1 },
                    });
                }
            }
        }, this);
        return invalidReasons;
    }

    /**
     * @function invalidReasonsHTML
     * @desc Get the reasons, why the quiz is invalid as an HTML string
     * @return {string} the reasons in HTML
     */
    invalidReasonsHTML(): string {
        const translate = this.translateService;
        let reasonString = '';
        for (const reason of this.computeInvalidReasons()) {
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
                const questions = JSON.parse(fileReader.result as string) as QuizQuestion[];
                this.addQuestions(questions);
                // Clearing html elements,
                this.importFile = null;
                this.importFileName = '';
                const control = document.getElementById('importFileInput') as HTMLInputElement;
                control.value = '';
            } catch (e) {
                alert('Import Quiz Failed! Invalid quiz file.');
            }
        };
        fileReader.readAsText(this.importFile);
        this.cacheValidation();
    }

    /**
     * @function addQuestions
     * @desc Adds given questions to current quiz exercise.
     * Ids are removed from new questions so that new id is assigned upon saving the quiz exercise.
     * Images are duplicated for drag and drop questions.
     * @param questions list of questions
     */
    async addQuestions(questions: QuizQuestion[]) {
        // To make sure all questions are duplicated (new resources are created), we need to remove some fields from the input questions,
        // This contains removing all ids, duplicating images in case of dnd questions, the question statistic and the exercise
        for (const question of questions) {
            delete question.quizQuestionStatistic;
            delete question.exercise;
            delete question.id;
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                for (const answerOption of mcQuestion.answerOptions!) {
                    delete answerOption.id;
                }
                this.quizExercise.quizQuestions = this.quizExercise.quizQuestions.concat([question]);
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
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
                    const correctMappingDragItem = correctMapping.dragItem!;
                    if (correctMappingDragItem.pictureFilePath !== null) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(correctMappingDragItem.pictureFilePath);
                        correctMappingDragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    correctMappingDragItem.tempID = correctMappingDragItem.id;
                    delete correctMapping.dragItem!.id;
                    correctMapping.dropLocation!.tempID = correctMapping.dropLocation!.id;
                    delete correctMapping.dropLocation!.id;
                }
                this.quizExercise.quizQuestions = this.quizExercise.quizQuestions.concat([question]);
            } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const shortAnswerQuestion = question as ShortAnswerQuestion;

                // For Spots, Solutions and CorrectMappings we need to provide tempID,
                // This tempID is used for keep tracking of mappings by backend. Backend removes tempID and generated a new id,
                for (const spot of shortAnswerQuestion.spots) {
                    spot.tempID = spot.id;
                    delete spot.id;
                }
                for (const solution of shortAnswerQuestion.solutions) {
                    solution.tempID = solution.id;
                    delete solution.id;
                }
                for (const correctMapping of shortAnswerQuestion.correctMappings) {
                    // Following fields are not required for short answer question. They will be generated by the backend,
                    delete correctMapping.id;
                    delete correctMapping.shortAnswerSolutionIndex;
                    delete correctMapping.shortAnswerSpotIndex;
                    delete correctMapping.invalid;

                    correctMapping.solution.tempID = correctMapping.solution.id;
                    delete correctMapping.solution.id;
                    correctMapping.spot.tempID = correctMapping.spot.id;
                    delete correctMapping.spot.id;
                }
                this.quizExercise.quizQuestions = this.quizExercise.quizQuestions.concat([question]);
            }
        }
    }

    /**
     * @function triggers the parsing of the editor content in the designated edit component
     */
    parseAllQuestions(): void {
        const editQuestionComponents: EditQuizQuestion[] = [
            ...this.editMultipleChoiceQuestionComponents.toArray(),
            ...this.editDragAndDropQuestionComponents.toArray(),
            ...this.editShortAnswerQuestionComponents.toArray(),
        ];
        editQuestionComponents.forEach(component => component.prepareForSave());
    }

    /**
     * @function save
     * @desc Save the quiz to the server and invoke callback functions depending of result
     */
    save(): void {
        if (this.hasSavedQuizStarted || !this.pendingChangesCache || !this.quizIsValid) {
            return;
        }
        this.isSaving = true;
        this.parseAllQuestions();
        if (this.quizExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.quizExerciseService.update(this.quizExercise, requestOptions).subscribe(
                (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    this.notificationText = null;
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                (res: HttpErrorResponse) => this.onSaveError(res),
            );
            this.pendingChangesCache = false;
        } else {
            this.quizExerciseService.create(this.quizExercise).subscribe(
                (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                (res: HttpErrorResponse) => this.onSaveError(res),
            );
            this.pendingChangesCache = false;
        }
    }

    /**
     * @function onSaveSuccess
     * @desc Callback function for when the save succeeds
     * Terminates the saving process and assign the returned quizExercise to the local entities
     * @param {QuizExercise} quizExercise: Saved quizExercise entity
     */
    private onSaveSuccess(quizExercise: QuizExercise): void {
        this.isSaving = false;
        this.prepareEntity(quizExercise);
        this.savedEntity = JSON.parse(JSON.stringify(quizExercise));
        this.quizExercise = quizExercise;
        this.changeDetector.detectChanges();
    }

    /**
     * @function onSaveError
     * @desc Callback function for when the save fails
     */
    private onSaveError = (error?: HttpErrorResponse): void => {
        console.error('Saving Quiz Failed! Please try again later.');
        this.jhiAlertService.error('artemisApp.quizExercise.saveError');
        this.isSaving = false;
        this.changeDetector.detectChanges();
    };

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
     * @function onDurationChange
     * @desc Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange(): void {
        const duration = moment.duration(this.duration);
        this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
        this.updateDuration();
        this.cacheValidation();
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
        this.router.navigate(['/course', this.quizExercise.course!.id, 'quiz-exercise']);
    }

    /**
     * @function hasSavedQuizStarted
     * @desc Check if the saved quiz has started
     * @return {boolean} true if the saved quiz has started, otherwise false
     */
    get hasSavedQuizStarted(): boolean {
        return !!(this.savedEntity && this.savedEntity.isPlannedToStart && moment(this.savedEntity.releaseDate!).isBefore(moment()));
    }

    back(): void {
        this.location.back();
    }
}
