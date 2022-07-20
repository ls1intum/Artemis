import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnChanges, OnInit, QueryList, SimpleChanges, ViewChildren, ViewEncapsulation } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizBatch, QuizExercise, QuizMode } from 'app/entities/quiz/quiz-exercise.model';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { Duration, Option } from './quiz-exercise-interfaces';
import { NgbDate, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { Location } from '@angular/common';
import { AlertService } from 'app/core/util/alert.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/entities/quiz/quiz-question.model';
import { Exercise, IncludedInOverallScore, resetDates, ValidationReason } from 'app/entities/exercise.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { Course } from 'app/entities/course.model';
import { QuizQuestionEdit } from 'app/exercises/quiz/manage/quiz-question-edit.interface';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { QuizConfirmImportInvalidQuestionsModalComponent } from 'app/exercises/quiz/manage/quiz-confirm-import-invalid-questions-modal.component';
import { cloneDeep } from 'lodash-es';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';

// False-positives:
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { DragAndDropQuestionEditComponent } from 'app/exercises/quiz/manage/drag-and-drop-question/drag-and-drop-question-edit.component';
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { MultipleChoiceQuestionEditComponent } from 'app/exercises/quiz/manage/multiple-choice-question/multiple-choice-question-edit.component';
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { ShortAnswerQuestionEditComponent } from 'app/exercises/quiz/manage/short-answer-question/short-answer-question-edit.component';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { round } from 'app/shared/util/utils';
import { onError } from 'app/shared/util/global.utils';
import { QuizExerciseValidationDirective } from 'app/exercises/quiz/manage/quiz-exercise-validation.directive';
import { faExclamationCircle, faPlus, faXmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [DragAndDropQuestionUtil, ShortAnswerQuestionUtil],
    styleUrls: ['./quiz-exercise-detail.component.scss', '../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class QuizExerciseDetailComponent extends QuizExerciseValidationDirective implements OnInit, OnChanges, ComponentCanDeactivate {
    @ViewChildren('editMultipleChoice')
    editMultipleChoiceQuestionComponents: QueryList<MultipleChoiceQuestionEditComponent>;

    @ViewChildren('editDragAndDrop')
    editDragAndDropQuestionComponents: QueryList<DragAndDropQuestionEditComponent>;

    @ViewChildren('editShortAnswer')
    editShortAnswerQuestionComponents: QueryList<ShortAnswerQuestionEditComponent>;

    course?: Course;
    exerciseGroup?: ExerciseGroup;
    courseRepository: CourseManagementService;
    notificationText?: string;

    isImport = false;

    /** Constants for 'Add existing questions' and 'Import file' features **/
    showExistingQuestions = false;
    showExistingQuestionsFromCourse = true;
    showExistingQuestionsFromExam = false;
    showExistingQuestionsFromFile = false;

    exams: Exam[] = [];
    selectedExamId?: number;

    courses: Course[] = [];
    selectedCourseId?: number;
    quizExercises: QuizExercise[];
    allExistingQuestions: QuizQuestion[];
    existingQuestions: QuizQuestion[];
    importFile?: Blob;
    importFileName: string;
    searchQueryText: string;
    dndFilterEnabled: boolean;
    mcqFilterEnabled: boolean;
    shortAnswerFilterEnabled: boolean;

    /** Duration object **/
    duration = new Duration(0, 0);

    /** Status constants **/
    isSaving = false;
    scheduleQuizStart = false;

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    /** Route params **/
    examId?: number;
    courseId?: number;

    // Icons
    faPlus = faPlus;
    faXmark = faXmark;
    faExclamationCircle = faExclamationCircle;

    readonly QuizMode = QuizMode;

    private initCompleted: boolean;

    constructor(
        private route: ActivatedRoute,
        private courseService: CourseManagementService,
        private examRepository: ExamManagementService,
        private quizExerciseService: QuizExerciseService,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private router: Router,
        private translateService: TranslateService,
        private fileUploaderService: FileUploaderService,
        private exerciseService: ExerciseService,
        private alertService: AlertService,
        private location: Location,
        private modalService: NgbModal,
        public changeDetector: ChangeDetectorRef,
        private exerciseGroupService: ExerciseGroupService,
    ) {
        super();
    }

    /**
     * Initialize variables and load course and quiz from server.
     */
    ngOnInit(): void {
        /** Initialize local constants **/
        this.showExistingQuestions = false;
        this.quizExercises = [];
        this.allExistingQuestions = [];
        this.existingQuestions = [];
        this.importFile = undefined;
        this.importFileName = '';
        this.searchQueryText = '';
        this.dndFilterEnabled = true;
        this.mcqFilterEnabled = true;
        this.shortAnswerFilterEnabled = true;
        this.notificationText = undefined;

        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        const quizId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const groupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        if (this.examId && groupId) {
            this.isExamMode = true;
        }

        if (this.router.url.includes('/import')) {
            this.isImport = true;
        }

        /** Query the courseService for the participationId given by the params */
        if (this.courseId) {
            this.courseService.find(this.courseId).subscribe((response: HttpResponse<Course>) => {
                this.course = response.body!;
                // Load exerciseGroup and set exam mode
                if (this.isExamMode) {
                    this.exerciseGroupService.find(this.courseId!, this.examId!, groupId).subscribe((groupResponse: HttpResponse<ExerciseGroup>) => {
                        // Make sure to call init if we didn't receive an id => new quiz-exercise
                        this.exerciseGroup = groupResponse.body || undefined;
                        if (!quizId) {
                            this.init();
                        } else if (this.quizExercise) {
                            this.quizExercise.exerciseGroup = this.exerciseGroup;
                        }
                    });
                } else {
                    // Make sure to call init if we didn't receive an id => new quiz-exercise
                    if (!quizId) {
                        this.init();
                    } else if (this.quizExercise) {
                        this.quizExercise.course = this.course;
                    }
                }
            });
        }
        if (quizId) {
            this.quizExerciseService.find(quizId).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body!;
                this.init();
                if (this.isExamMode && this.quizExercise.testRunParticipationsExist) {
                    this.alertService.warning(this.translateService.instant('artemisApp.quizExercise.edit.testRunSubmissionsExist'));
                }
            });
        }

        // TODO: we should try to avoid calling this.init() above more than once
        this.courseRepository = this.courseService;
    }

    /**
     * Initializes and returns a new quiz exercise
     */
    initializeNewQuizExercise(): QuizExercise {
        const newQuiz = new QuizExercise(undefined, undefined);
        newQuiz.title = '';
        newQuiz.duration = 600;
        newQuiz.isOpenForPractice = false;
        newQuiz.releaseDate = dayjs();
        newQuiz.randomizeQuestionOrder = true;
        newQuiz.quizQuestions = [];
        newQuiz.quizMode = QuizMode.SYNCHRONIZED;
        newQuiz.allowedNumberOfAttempts = 1;
        this.prepareEntity(newQuiz);
        return newQuiz;
    }

    /**
     * Initializes local constants and prepares the QuizExercise entity
     */
    init(): void {
        if (!this.quizExercise) {
            this.quizExercise = this.initializeNewQuizExercise();
        }

        if (this.isImport || this.isExamMode) {
            this.quizExercise.quizBatches = [];
            resetDates(this.quizExercise);
        }

        // Assign savedEntity to identify local changes
        this.savedEntity = this.entity.id && !this.isImport ? cloneDeep(this.entity) : new QuizExercise(undefined, undefined);

        if (this.isExamMode) {
            this.quizExercise.course = undefined;
            if (!this.quizExercise.exerciseGroup || this.isImport) {
                this.quizExercise.exerciseGroup = this.exerciseGroup;
            }
        } else {
            this.quizExercise.exerciseGroup = undefined;
            if (!this.quizExercise.course || this.isImport) {
                this.quizExercise.course = this.course;
            }
        }

        if (!this.isExamMode) {
            this.exerciseCategories = this.quizExercise.categories || [];
            this.courseService.findAllCategoriesOfCourse(this.courseId!).subscribe({
                next: (response: HttpResponse<string[]>) => {
                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(response.body!);
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
        // Exam exercises cannot be not included into the total score
        if (this.isExamMode && this.quizExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            this.quizExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        }
        this.scheduleQuizStart = (this.quizExercise.quizBatches?.length ?? 0) > 0;
        this.updateDuration();
        this.cacheValidation();
        this.initCompleted = true;
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        if (this.initCompleted) {
            // TODO: quiz cleanup: this makes the exercise dirty and attempts to prevent leaving.
            // Currently initCompleted field is used to prevent marking the exercise dirty on initialization.
            // However making a change and undoing it still has the issue.
            // Additionally, quiz exercises are for some reason the only exercise type the has the unsaved changes warning.
            this.exerciseService.validateDate(this.quizExercise);
        }
        const dueDate = this.quizExercise.quizMode === QuizMode.SYNCHRONIZED ? null : this.quizExercise.dueDate;
        this.quizExercise?.quizBatches?.forEach((batch) => {
            const startTime = dayjs(batch.startTime);
            batch.startTimeError = startTime.isBefore(this.quizExercise.releaseDate) || startTime.add(dayjs.duration(this.duration)).isAfter(dueDate ?? null);
        });
    }

    cacheValidation() {
        this.validateDate();

        if (this.quizExercise.quizMode === QuizMode.SYNCHRONIZED) {
            if (this.scheduleQuizStart) {
                if ((this.quizExercise.quizBatches?.length ?? 0) !== 1) {
                    this.quizExercise.quizBatches = [this.quizExercise.quizBatches?.[0] ?? new QuizBatch()];
                }
            } else {
                if ((this.quizExercise.quizBatches?.length ?? 0) !== 0) {
                    this.quizExercise.quizBatches = [];
                }
            }
        }

        return super.cacheValidation(this.changeDetector);
    }

    addQuizBatch() {
        if (!this.quizExercise.quizBatches) {
            this.quizExercise.quizBatches = [];
        }
        this.quizExercise.quizBatches.push(new QuizBatch());
    }

    removeQuizBatch(quizBatch: QuizBatch) {
        if (this.quizExercise.quizBatches) {
            const idx = this.quizExercise.quizBatches.indexOf(quizBatch);
            if (idx >= 0) {
                this.quizExercise.quizBatches.splice(idx, 1);
            }
        }
    }

    /**
     * Apply updates for changed course and quizExercise
     * @param changes the changes to apply
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.course || changes.quizExercise) {
            this.init();
        }
    }

    /**
     * Update the categories and overwrite the cache, overwrites existing categories
     * @param categories the new categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.quizExercise.categories = categories;
        this.cacheValidation();
    }

    /**
     * Determine which dropdown to display depending on the relationship between start time, end time, and current time
     * @returns {string} Name of the dropdown to show
     */
    get showDropdown(): string {
        if (!this.quizExercise || !this.quizExercise.quizStarted || this.isImport) {
            return 'isVisibleBeforeStart';
        } else if (this.quizExercise.quizEnded) {
            return 'isOpenForPractice';
        } else {
            return 'active';
        }
    }

    /**
     * Returns whether pending changes are present, preventing a deactivation.
     */
    canDeactivate(): boolean {
        return !this.pendingChangesCache;
    }

    /**
     * displays the alert for confirming refreshing or closing the page if there are unsaved changes
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any) {
        if (!this.canDeactivate()) {
            event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    /**
     * @desc Callback for datepicker to decide whether given date should be disabled
     * All dates which are in the past (< today) are disabled
     */
    isDateInPast = (date: NgbDate, current: { month: number }) =>
        current.month < dayjs().month() + 1 ||
        dayjs()
            .year(date.year)
            .month(date.month - 1)
            .date(date.day)
            .isBefore(dayjs());

    /**
     * Add an empty multiple choice question to the quiz
     */
    addMultipleChoiceQuestion() {
        if (this.quizExercise == undefined) {
            this.quizExercise = this.initializeNewQuizExercise();
        }

        const mcQuestion = new MultipleChoiceQuestion();
        mcQuestion.title = '';
        mcQuestion.text = 'Enter your long question if needed';
        mcQuestion.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        mcQuestion.scoringType = ScoringType.ALL_OR_NOTHING; // explicit default value for multiple questions
        mcQuestion.randomizeOrder = true;
        mcQuestion.points = 1;

        const correctSampleAnswerOption = new AnswerOption();
        correctSampleAnswerOption.isCorrect = true;
        correctSampleAnswerOption.text = 'Enter a correct answer option here';
        correctSampleAnswerOption.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        correctSampleAnswerOption.explanation = 'Add an explanation here (only visible in feedback after quiz has ended)';

        const incorrectSampleAnswerOption = new AnswerOption();
        incorrectSampleAnswerOption.isCorrect = false;
        incorrectSampleAnswerOption.text = 'Enter a wrong answer option here';

        mcQuestion.answerOptions = [correctSampleAnswerOption, incorrectSampleAnswerOption];
        this.quizExercise.quizQuestions!.push(mcQuestion);
        this.cacheValidation();
    }

    /**
     * Add an empty drag and drop question to the quiz
     */
    addDragAndDropQuestion(): void {
        if (this.quizExercise == undefined) {
            this.quizExercise = this.initializeNewQuizExercise();
        }

        const dndQuestion = new DragAndDropQuestion();
        dndQuestion.title = '';
        dndQuestion.text = 'Enter your long question if needed';
        dndQuestion.hint = 'Add a hint here (visible during the quiz via ?-Button)';
        dndQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY; // explicit default value for drag and drop questions
        dndQuestion.randomizeOrder = true;
        dndQuestion.points = 1;
        dndQuestion.dropLocations = [];
        dndQuestion.dragItems = [];
        dndQuestion.correctMappings = [];
        this.quizExercise.quizQuestions!.push(dndQuestion);
        this.cacheValidation();
    }

    /**
     * Add an empty short answer question to the quiz
     */
    addShortAnswerQuestion(): void {
        if (this.quizExercise == undefined) {
            this.quizExercise = this.initializeNewQuizExercise();
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
        shortAnswerQuestion.scoringType = ScoringType.PROPORTIONAL_WITHOUT_PENALTY; // explicit default value for short answer questions
        shortAnswerQuestion.randomizeOrder = true;
        shortAnswerQuestion.points = 1;
        shortAnswerQuestion.spots = [];
        shortAnswerQuestion.solutions = [];
        shortAnswerQuestion.correctMappings = [];
        this.quizExercise.quizQuestions!.push(shortAnswerQuestion);
        this.cacheValidation();
    }

    /**
     * Iterates over the questions of the quizExercise and calculates the sum of all question scores
     */
    calculateMaxExerciseScore(): number {
        let scoreSum = 0;
        this.quizExercise.quizQuestions!.forEach((question) => (scoreSum += question.points!));
        return scoreSum;
    }

    /**
     * Toggles existing questions view
     */
    showHideExistingQuestions(): void {
        if (!this.quizExercise) {
            this.quizExercise = this.initializeNewQuizExercise();
        }

        // If courses are not populated, then populate list of courses
        if (this.courses.length === 0) {
            this.courseRepository.getAllCoursesWithQuizExercises().subscribe((res: HttpResponse<Course[]>) => {
                this.courses = res.body!;
            });
        }
        // If exams are not populated, then populate list of exams
        if (this.exams.length === 0) {
            this.examRepository.findAllExamsAccessibleToUser(this.courseId!).subscribe((res: HttpResponse<Exam[]>) => {
                this.exams = res.body!;
            });
        }
        this.showExistingQuestions = !this.showExistingQuestions;
        this.setExistingQuestionSourceToCourse();
    }

    /**
     * Callback function for when a user selected a Course from the Dropdown list from 'Add existing questions'
     * Populates list of quiz exercises for the selected course
     */
    onCourseSelect(): void {
        this.allExistingQuestions = this.existingQuestions = [];
        if (!this.selectedCourseId) {
            return;
        }

        /** Search the selected course by id in all available courses **/
        const selectedCourse = this.courses.find((course) => course.id === Number(this.selectedCourseId))!;

        // TODO: the following code seems duplicated (see quiz-exercise-export.component.ts in the method loadForCourse). Try to avoid duplication!
        // For the given course, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise,
        this.quizExerciseService.findForCourse(selectedCourse.id!).subscribe({
            next: (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    this.applyQuestionsAndFilter(quizExercisesResponse.body!);
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    onExamSelect(): void {
        this.allExistingQuestions = this.existingQuestions = [];
        if (!this.selectedExamId) {
            return;
        }

        /** Search the selected exam by id in all available exams **/
        const selectedExam = this.exams.find((exam) => exam.id === Number(this.selectedExamId))!;

        // For the given exam, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise
        this.quizExerciseService.findForExam(selectedExam.id!).subscribe({
            next: (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    this.applyQuestionsAndFilter(quizExercisesResponse.body!);
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private applyQuestionsAndFilter(quizExercises: QuizExercise[]) {
        for (const quizExercise of quizExercises) {
            this.quizExerciseService.find(quizExercise.id!).subscribe((response: HttpResponse<QuizExercise>) => {
                const quizExerciseResponse = response.body!;
                if (quizExerciseResponse.quizQuestions && quizExerciseResponse.quizQuestions.length > 0) {
                    for (const question of quizExerciseResponse.quizQuestions) {
                        question.exercise = quizExercise;
                        this.allExistingQuestions.push(question);
                    }
                }
                this.applyFilter();
            });
        }
    }

    /**
     * Applies filter on questions shown in add existing questions view.
     */
    applyFilter(): void {
        this.existingQuestions = [];
        /**
         * Depending on the filter selected by user, filter out questions.
         * allExistingQuestions contains list of all questions.
         * We don't change it. We populate existingQuestions list depending on the filter options.
         */
        for (const question of this.allExistingQuestions) {
            if (!this.searchQueryText || this.searchQueryText === '' || question.title!.toLowerCase().indexOf(this.searchQueryText.toLowerCase()) !== -1) {
                if (this.mcqFilterEnabled && question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    this.existingQuestions.push(question);
                }
                if (this.dndFilterEnabled && question.type === QuizQuestionType.DRAG_AND_DROP) {
                    this.existingQuestions.push(question);
                }
                if (this.shortAnswerFilterEnabled && question.type === QuizQuestionType.SHORT_ANSWER) {
                    this.existingQuestions.push(question);
                }
            }
        }
        this.cacheValidation();
    }

    /**
     * Assigns the uploaded import file
     * @param event object containing the uploaded file
     */
    setImportFile(event: any): void {
        if (event.target.files.length) {
            const fileList: FileList = event.target.files;
            this.importFile = fileList[0];
            this.importFileName = this.importFile['name'];
        }
        this.changeDetector.detectChanges();
    }

    /**
     * Adds selected quizzes to current quiz exercise
     */
    addExistingQuestions(): void {
        const questions: QuizQuestion[] = [];
        for (const question of this.existingQuestions) {
            if (question.exportQuiz) {
                questions.push(question);
            }
        }
        this.verifyAndImportQuestions(questions);
        this.showExistingQuestions = !this.showExistingQuestions;
        this.showExistingQuestionsFromCourse = true;
        this.showExistingQuestionsFromExam = false;
        this.showExistingQuestionsFromFile = false;
        this.selectedCourseId = undefined;
        this.selectedExamId = undefined;
        this.allExistingQuestions = this.existingQuestions = [];
        this.cacheValidation();
    }

    /**
     * Remove question from the quiz
     * @param questionToDelete {QuizQuestion} the question to remove
     */
    deleteQuestion(questionToDelete: QuizQuestion): void {
        this.quizExercise.quizQuestions = this.quizExercise.quizQuestions?.filter((question) => question !== questionToDelete);
        this.cacheValidation();
    }

    /**
     * Handles the change of a question by replacing the array with a copy (allows for shallow comparison)
     */
    onQuestionUpdated(): void {
        this.cacheValidation();
        this.quizExercise.quizQuestions = Array.from(this.quizExercise.quizQuestions!);
    }

    /**
     * Get the reasons, why the quiz is invalid as an HTML string
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
        return reasonString.slice(0, reasonString.length - 5);
    }

    /**
     * Move file reader creation to separate function to be able to mock
     * https://fromanegg.com/post/2015/04/22/easy-testing-of-code-involving-native-methods-in-javascript/
     */
    generateFileReader() {
        return new FileReader();
    }

    onFileLoadImport(fileReader: FileReader) {
        try {
            // Read the file and get list of questions from the file
            const questions = JSON.parse(fileReader.result as string) as QuizQuestion[];
            this.verifyAndImportQuestions(questions);
            // Clearing html elements,
            this.importFile = undefined;
            this.importFileName = '';
            const control = document.getElementById('importFileInput') as HTMLInputElement;
            if (control) {
                control.value = '';
            }
        } catch (e) {
            alert('Import Quiz Failed! Invalid quiz file.');
        }
    }

    /**
     * Imports a json quiz file and adds questions to current quiz exercise.
     */
    async importQuiz() {
        if (!this.importFile) {
            return;
        }
        const fileReader = this.generateFileReader();
        fileReader.onload = () => this.onFileLoadImport(fileReader);
        fileReader.readAsText(this.importFile);
        this.cacheValidation();
    }

    /**
     * Calls {@link checkForInvalidFlaggedQuestions} to verify whether any question or its elements have the invalid flag set.
     * If this is the case, the user is notified using the {@link QuizConfirmImportInvalidQuestionsModalComponent} modal.
     * If the user accepts importing the questions with invalid flags, all these flags are reset. See {@link addQuestions}.
     * @param questions the question which are being imported.
     */
    async verifyAndImportQuestions(questions: QuizQuestion[]) {
        this.checkForInvalidFlaggedQuestions(questions);
        if (!this.isEmpty(this.invalidFlaggedQuestions)) {
            const modal = this.modalService.open(QuizConfirmImportInvalidQuestionsModalComponent, { keyboard: true, size: 'lg' });
            modal.componentInstance.invalidFlaggedQuestions = questions
                .map((question, index) => {
                    if (this.invalidFlaggedQuestions[question.title!]) {
                        return {
                            translateKey: 'artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements',
                            translateValues: { index: index + 1 },
                        };
                    }
                })
                .filter(Boolean);

            modal.componentInstance.shouldImport.subscribe(async () => {
                await this.addQuestions(questions);
                // Reset the invalid flagged questions
                this.invalidFlaggedQuestions = {};
                this.cacheValidation();
            });
        } else {
            await this.addQuestions(questions);
            this.cacheValidation();
        }
    }

    /**
     * Adds given questions to current quiz exercise.
     * Ids are removed from new questions so that new id is assigned upon saving the quiz exercise.
     * Caution: All "invalid" flags are also removed.
     * Images are duplicated for drag and drop questions.
     * @param questions list of questions
     */
    async addQuestions(questions: QuizQuestion[]) {
        // To make sure all questions are duplicated (new resources are created), we need to remove some fields from the input questions,
        // This contains removing all ids, duplicating images in case of dnd questions, the question statistic and the exercise
        for (const question of questions) {
            // do not set question.exercise = this.quizExercise, because it will cause a cycle when converting to json
            question.exercise = undefined;
            question.quizQuestionStatistic = undefined;
            question.invalid = false;
            question.id = undefined;
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                mcQuestion.answerOptions!.forEach((answerOption) => {
                    answerOption.id = undefined;
                    answerOption.invalid = false;
                });
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                const dndQuestion = question as DragAndDropQuestion;
                // Get image from the old question and duplicate it on the server and then save new image to the question,
                let fileUploadResponse = await this.fileUploaderService.duplicateFile(dndQuestion.backgroundFilePath!);
                dndQuestion.backgroundFilePath = fileUploadResponse.path;

                // For DropLocations, DragItems and CorrectMappings we need to provide tempID,
                // This tempID is used for keep tracking of mappings by server. The server removes tempID and generated a new id,
                dndQuestion.dropLocations!.forEach((dropLocation) => {
                    dropLocation.tempID = dropLocation.id;
                    dropLocation.id = undefined;
                    dropLocation.invalid = false;
                });
                for (const dragItem of dndQuestion.dragItems || []) {
                    // Duplicating image on server. This is only valid for image drag items. For text drag items, pictureFilePath is undefined,
                    if (dragItem.pictureFilePath) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(dragItem.pictureFilePath);
                        dragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    dragItem.tempID = dragItem.id;
                    dragItem.id = undefined;
                    dragItem.invalid = false;
                }
                for (const correctMapping of dndQuestion.correctMappings || []) {
                    // Following fields are not required for dnd question. They will be generated by the server,
                    correctMapping.id = undefined;
                    correctMapping.dragItemIndex = undefined;
                    correctMapping.dropLocationIndex = undefined;
                    correctMapping.invalid = false;

                    // Duplicating image on server. This is only valid for image drag items. For text drag items, pictureFilePath is undefined,
                    const correctMappingDragItem = correctMapping.dragItem!;
                    if (correctMappingDragItem.pictureFilePath) {
                        fileUploadResponse = await this.fileUploaderService.duplicateFile(correctMappingDragItem.pictureFilePath);
                        correctMappingDragItem.pictureFilePath = fileUploadResponse.path;
                    }
                    correctMappingDragItem.tempID = correctMappingDragItem?.id;
                    correctMapping.dragItem!.id = undefined;
                    correctMapping.dropLocation!.tempID = correctMapping.dropLocation!.id;
                    correctMapping.dropLocation!.id = undefined;
                }
            } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                const shortAnswerQuestion = question as ShortAnswerQuestion;

                // For Spots, Solutions and CorrectMappings we need to provide tempID,
                // This tempID is used for keep tracking of mappings by server. The server removes tempID and generated a new id,
                shortAnswerQuestion.spots!.forEach((spot) => {
                    spot.tempID = spot.id;
                    spot.id = undefined;
                    spot.invalid = false;
                });
                shortAnswerQuestion.solutions!.forEach((solution) => {
                    solution.tempID = solution.id;
                    solution.id = undefined;
                    solution.invalid = false;
                });
                shortAnswerQuestion.correctMappings!.forEach((correctMapping) => {
                    // Following fields are not required for short answer question. They will be generated by the server,
                    correctMapping.id = undefined;
                    correctMapping.shortAnswerSolutionIndex = undefined;
                    correctMapping.shortAnswerSpotIndex = undefined;
                    correctMapping.invalid = false;

                    correctMapping.solution!.tempID = correctMapping.solution!.id;
                    correctMapping.solution!.id = undefined;
                    correctMapping.spot!.tempID = correctMapping.spot!.id;
                    correctMapping.spot!.id = undefined;
                });
            }
            this.quizExercise.quizQuestions = this.quizExercise.quizQuestions!.concat([question]);
        }
    }

    /**
     * triggers the parsing of the editor content in the designated edit component
     */
    parseAllQuestions(): void {
        const editQuestionComponents: QuizQuestionEdit[] = [
            ...this.editMultipleChoiceQuestionComponents.toArray(),
            ...this.editDragAndDropQuestionComponents.toArray(),
            ...this.editShortAnswerQuestionComponents.toArray(),
        ];
        editQuestionComponents.forEach((component) => component.prepareForSave());
    }

    /**
     * Save the quiz to the server and invoke callback functions depending on result
     */
    save(): void {
        if (this.hasSavedQuizStarted || !this.pendingChangesCache || !this.quizIsValid) {
            return;
        }

        Exercise.sanitize(this.quizExercise);

        this.isSaving = true;
        this.parseAllQuestions();
        if (this.quizExercise.id !== undefined) {
            if (this.isImport) {
                this.quizExerciseService.import(this.quizExercise).subscribe({
                    next: (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                        if (quizExerciseResponse.body) {
                            this.onSaveSuccess(quizExerciseResponse.body);
                        } else {
                            this.onSaveError();
                        }
                    },
                    error: () => this.onSaveError(),
                });
            } else {
                const requestOptions = {} as any;
                if (this.notificationText) {
                    requestOptions.notificationText = this.notificationText;
                }
                this.quizExerciseService.update(this.quizExercise, requestOptions).subscribe({
                    next: (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                        this.notificationText = undefined;
                        if (quizExerciseResponse.body) {
                            this.onSaveSuccess(quizExerciseResponse.body);
                        } else {
                            this.onSaveError();
                        }
                    },
                    error: () => this.onSaveError(),
                });
            }
        } else {
            this.quizExerciseService.create(this.quizExercise).subscribe({
                next: (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                error: () => this.onSaveError(),
            });
        }
    }

    /**
     * Callback function for when the save succeeds
     * Terminates the saving process and assign the returned quizExercise to the local entities
     * @param {QuizExercise} quizExercise: Saved quizExercise entity
     */
    private onSaveSuccess(quizExercise: QuizExercise): void {
        this.isSaving = false;
        this.pendingChangesCache = false;
        this.prepareEntity(quizExercise);
        this.savedEntity = cloneDeep(quizExercise);
        this.quizExercise = quizExercise;
        this.changeDetector.detectChanges();

        // Navigate back
        if (this.isImport) {
            this.cancel();
        }
    }

    /**
     * Callback function for when the save fails
     */
    private onSaveError = (): void => {
        console.error('Saving Quiz Failed! Please try again later.');
        this.alertService.error('artemisApp.quizExercise.saveError');
        this.isSaving = false;
        this.changeDetector.detectChanges();
    };

    /**
     * Makes sure the entity is well-formed and its fields are of the correct types
     * @param quizExercise {QuizExercise} exercise which will be prepared
     */
    prepareEntity(quizExercise: QuizExercise): void {
        if (!this.isExamMode) {
            quizExercise.releaseDate = quizExercise.releaseDate ? dayjs(quizExercise.releaseDate) : dayjs();
            quizExercise.duration = Number(quizExercise.duration);
            quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
        }
    }

    /**
     * Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange(): void {
        if (!this.isExamMode) {
            const duration = dayjs.duration(this.duration);
            this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
            this.updateDuration();
            this.cacheValidation();
        } else if (this.quizExercise.releaseDate && this.quizExercise.dueDate) {
            const duration = dayjs(this.quizExercise.dueDate).diff(this.quizExercise.releaseDate, 's');
            this.quizExercise.duration = round(duration);
            this.updateDuration();
            this.cacheValidation();
        }
    }

    /**
     * Update ui to current value of duration
     */
    updateDuration(): void {
        const duration = dayjs.duration(this.quizExercise.duration!, 'seconds');
        this.changeDetector.detectChanges();
        // when input fields are empty do not update their values
        if (this.duration.minutes !== undefined) {
            this.duration.minutes = 60 * duration.hours() + duration.minutes();
        }
        if (this.duration.seconds !== undefined) {
            this.duration.seconds = duration.seconds();
        }
    }

    /**
     * Update adding existing questions from a file or course or exam
     */
    setExistingQuestionSourceToCourse(): void {
        this.showExistingQuestionsFromCourse = true;
        this.showExistingQuestionsFromExam = false;
        this.showExistingQuestionsFromFile = false;
        this.updateSelectionAndView();
    }

    /**
     * Update adding existing questions from an exam
     */
    setExistingQuestionSourceToExam(): void {
        this.showExistingQuestionsFromCourse = false;
        this.showExistingQuestionsFromExam = true;
        this.showExistingQuestionsFromFile = false;
        this.updateSelectionAndView();
    }

    /**
     * Update adding existing questions from a file
     */
    setExistingQuestionSourceToFile(): void {
        this.showExistingQuestionsFromCourse = false;
        this.showExistingQuestionsFromExam = false;
        this.showExistingQuestionsFromFile = true;
        this.updateSelectionAndView();
    }

    private updateSelectionAndView() {
        this.selectedCourseId = undefined;
        this.selectedExamId = undefined;
        this.allExistingQuestions = this.existingQuestions = [];
        this.importFile = undefined;
        this.importFileName = '';
        const control = document.getElementById('importFileInput') as HTMLInputElement;
        if (control) {
            control.value = '';
        }
        this.changeDetector.detectChanges();
    }

    /**
     * Navigate back
     */
    cancel(): void {
        if (!this.isExamMode) {
            this.router.navigate(['/course-management', this.courseId, 'quiz-exercises']);
        } else {
            this.router.navigate(['/course-management', this.courseId, 'exams', this.examId, 'exercise-groups']);
        }
    }

    /**
     * Check if the saved quiz has started
     * @return {boolean} true if the saved quiz has started, otherwise false
     */
    get hasSavedQuizStarted(): boolean {
        return !!(this.savedEntity && this.savedEntity.quizBatches && this.savedEntity.quizBatches.some((batch) => dayjs(batch.startTime).isBefore(dayjs())));
    }

    includedInOverallScoreChange(includedInOverallScore: IncludedInOverallScore) {
        this.quizExercise.includedInOverallScore = includedInOverallScore;
        this.cacheValidation();
    }

    computeInvalidReasons(): ValidationReason[] {
        const invalidReasons = new Array<ValidationReason>();
        if (!this.quizExercise) {
            return [];
        }
        // Release Date valid but lies in the past
        if (false /*this.quizExercise.isPlannedToStart*/) {
            // TODO: quiz cleanup: properly validate dates and deduplicate the checks (see isValidQuiz)
            if (!this.quizExercise.releaseDate || !dayjs(this.quizExercise.releaseDate).isValid()) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.invalidStartTime',
                    translateValues: {},
                });
            }
            // Release Date valid but lies in the past
            if (this.quizExercise.releaseDate && dayjs(this.quizExercise.releaseDate).isValid()) {
                if (dayjs(this.quizExercise.releaseDate).isBefore(dayjs())) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.startTimeInPast',
                        translateValues: {},
                    });
                }
            }
        }
        return super.computeInvalidReasons().concat(invalidReasons);
    }

    hasErrorInQuizBatches() {
        return this.quizExercise?.quizBatches?.some((batch) => batch.startTimeError);
    }
}
