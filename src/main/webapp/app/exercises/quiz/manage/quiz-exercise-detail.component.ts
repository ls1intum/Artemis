import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnChanges, OnInit, QueryList, SimpleChanges, ViewChildren, ViewEncapsulation } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { Duration, Option } from './quiz-exercise-interfaces';
import { NgbDate, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Location } from '@angular/common';
import { JhiAlertService } from 'ng-jhipster';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { QuizQuestion, QuizQuestionType, ScoringType } from 'app/entities/quiz/quiz-question.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { Course } from 'app/entities/course.model';
import { QuizQuestionEdit } from 'app/exercises/quiz/manage/quiz-question-edit.interface';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/entities/quiz/short-answer-mapping.model';
import { ShortAnswerSpot } from 'app/entities/quiz/short-answer-spot.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { QuizConfirmImportInvalidQuestionsModalComponent } from 'app/exercises/quiz/manage/quiz-confirm-import-invalid-questions-modal.component';
import * as Sentry from '@sentry/browser';
import { cloneDeep } from 'lodash';
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

export interface Reason {
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
    styleUrls: ['./quiz-exercise-detail.component.scss', '../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class QuizExerciseDetailComponent implements OnInit, OnChanges, ComponentCanDeactivate {
    // Make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    @ViewChildren('editMultipleChoice')
    editMultipleChoiceQuestionComponents: QueryList<MultipleChoiceQuestionEditComponent>;

    @ViewChildren('editDragAndDrop')
    editDragAndDropQuestionComponents: QueryList<DragAndDropQuestionEditComponent>;

    @ViewChildren('editShortAnswer')
    editShortAnswerQuestionComponents: QueryList<ShortAnswerQuestionEditComponent>;

    course?: Course;
    quizExercise: QuizExercise;
    exerciseGroup?: ExerciseGroup;
    courseRepository: CourseManagementService;
    notificationText?: string;

    // TODO: why do we have entity, savedEntity and quizExercise?
    entity: QuizExercise;
    savedEntity: QuizExercise;

    isExamMode: boolean;

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
    quizIsValid: boolean;
    warningQuizCache = false;
    pendingChangesCache: boolean;

    /** Status Options **/
    statusOptionsVisible: Option[] = [new Option(false, 'Hidden'), new Option(true, 'Visible')];
    statusOptionsPractice: Option[] = [new Option(false, 'Closed'), new Option(true, 'Open for Practice')];
    statusOptionsActive: Option[] = [new Option(true, 'Active')];

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    /** Route params **/
    examId?: number;
    courseId?: number;
    private invalidFlaggedQuestions: {
        [title: string]: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[] | undefined;
    } = {};

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
        private jhiAlertService: JhiAlertService,
        private location: Location,
        private modalService: NgbModal,
        private changeDetector: ChangeDetectorRef,
        private exerciseGroupService: ExerciseGroupService,
    ) {}

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
                        }
                    });
                }
                // Make sure to call init if we didn't receive an id => new quiz-exercise
                if (!quizId && !this.isExamMode) {
                    this.init();
                }
            });
        }
        if (quizId) {
            this.quizExerciseService.find(quizId).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body!;
                this.init();
                if (this.isExamMode && this.quizExercise.testRunParticipationsExist) {
                    this.jhiAlertService.warning(this.translateService.instant('artemisApp.quizExercise.edit.testRunSubmissionsExist'));
                }
            });
        }
        // TODO: we should try to avoid calling this.init() above more than once
        this.courseRepository = this.courseService;
    }

    /**
     * Initializes local constants and prepares the QuizExercise entity
     */
    init(): void {
        if (this.quizExercise) {
            this.entity = this.quizExercise;
        } else {
            this.entity = new QuizExercise(undefined, undefined);
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
        this.savedEntity = this.entity.id ? cloneDeep(this.entity) : new QuizExercise(undefined, undefined);
        if (!this.quizExercise.course && !this.isExamMode) {
            this.quizExercise.course = this.course;
        }
        if (!this.quizExercise.exerciseGroup && this.isExamMode) {
            this.quizExercise.exerciseGroup = this.exerciseGroup;
        }
        if (!this.isExamMode) {
            this.exerciseCategories = this.quizExercise.categories || [];
            this.courseService.findAllCategoriesOfCourse(this.quizExercise.course!.id!).subscribe(
                (response: HttpResponse<string[]>) => {
                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(response.body!);
                },
                (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
            );
        }
        this.updateDuration();
        this.cacheValidation();
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
        current.month < moment().month() + 1 ||
        moment()
            .year(date.year)
            .month(date.month - 1)
            .date(date.day)
            .isBefore(moment());

    /**
     * Add an empty multiple choice question to the quiz
     */
    addMultipleChoiceQuestion() {
        if (this.quizExercise == undefined) {
            this.quizExercise = this.entity;
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
            this.quizExercise = this.entity;
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
            this.quizExercise = this.entity;
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
        this.quizExerciseService.findForCourse(selectedCourse.id!).subscribe(
            (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    this.applyQuestionsAndFilter(quizExercisesResponse.body!);
                }
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
    }

    onExamSelect(): void {
        this.allExistingQuestions = this.existingQuestions = [];
        if (!this.selectedExamId) {
            return;
        }

        /** Search the selected exam by id in all available exams **/
        const selectedExam = this.exams.find((exam) => exam.id === Number(this.selectedExamId))!;

        // For the given exam, get list of all quiz exercises. And for all quiz exercises, get list of all questions in a quiz exercise
        this.quizExerciseService.findForExam(selectedExam.id!).subscribe(
            (quizExercisesResponse: HttpResponse<QuizExercise[]>) => {
                if (quizExercisesResponse.body) {
                    this.applyQuestionsAndFilter(quizExercisesResponse.body!);
                }
            },
            (error: HttpErrorResponse) => onError(this.jhiAlertService, error),
        );
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
     * 1. Check whether the inputs in the quiz are valid
     * 2. Check if warning are needed for the inputs
     * 3. Display the warnings/invalid reasons in the html file if needed
     */
    cacheValidation(): void {
        this.warningQuizCache = this.computeInvalidWarnings().length > 0;
        this.quizIsValid = this.validQuiz();
        this.pendingChangesCache = this.pendingChanges();
        this.checkForInvalidFlaggedQuestions();
        this.computeInvalidReasons();
        this.computeInvalidWarnings();
        this.changeDetector.detectChanges();
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
     * Determine if there are any changes waiting to be saved
     * @returns {boolean} true if there are any pending changes, false otherwise
     */
    pendingChanges(): boolean {
        if (!this.quizExercise || !this.savedEntity) {
            return false;
        }
        const keysToCompare = [
            'title',
            'difficulty',
            'duration',
            'isPlannedToStart',
            'isVisibleBeforeStart',
            'isOpenForPractice',
            'randomizeQuestionOrder',
            'includedInOverallScore',
        ];

        // Unsaved changes if any of the stated object key values are not equal or the questions/release dates differ
        return (
            keysToCompare.some((key) => this.quizExercise[key] !== this.savedEntity[key]) ||
            !this.areDatesIdentical(this.quizExercise.releaseDate!, this.savedEntity.releaseDate!) ||
            !this.areCategoriesIdentical(this.quizExercise.categories, this.savedEntity.categories) ||
            !this.areQuizExerciseEntityQuestionsIdentical(this.quizExercise.quizQuestions, this.savedEntity.quizQuestions)
        );
    }

    /**
     * Checks whether the used and saved categories are the same.
     * @param categoriesUsed the categories currently used
     * @param categoriesSaved the categories that are saved
     * @returns {boolean} true if the used and saved categories are identical.
     */
    areCategoriesIdentical(categoriesUsed?: ExerciseCategory[], categoriesSaved?: ExerciseCategory[]): boolean {
        return JSON.stringify(categoriesUsed || []).toLowerCase() === JSON.stringify(categoriesSaved || []).toLowerCase();
    }

    /**
     * Compares the provided question array objects
     * @param QA1 {QuizQuestion[]} First question array to compare
     * @param QA2 {QuizQuestion[]} Second question array to compare against
     * @return {boolean} true if the provided Question[] objects are identical, false otherwise
     */
    areQuizExerciseEntityQuestionsIdentical(QA1?: QuizQuestion[], QA2?: QuizQuestion[]): boolean {
        return JSON.stringify(QA1 || []) === JSON.stringify(QA2 || []);
    }

    /**
     * This function compares the provided dates with help of the moment library
     * Since we might be receiving an string instead of a moment object (e.g. when receiving it from the server)
     * we wrap both dates in a moment object. If it's already a moment object, this will just be ignored.
     * @param date1 {string|Moment} First date to compare
     * @param date2 {string|Moment} Second date to compare to
     * @return {boolean} True if the dates are identical, false otherwise
     */
    areDatesIdentical(date1: string | Moment, date2: string | Moment): boolean {
        return moment(date1).isSame(moment(date2));
    }

    /**
     * Check if the current inputs are valid
     * @returns {boolean} true if valid, false otherwise
     */
    private validQuiz(): boolean {
        if (!this.quizExercise) {
            return false;
        }
        // Release date is valid if it's not null/undefined and a valid date; Precondition: isPlannedToStart is set
        // Release date should also not be in the past
        const releaseDateValidAndNotInPastCondition =
            !this.quizExercise.isPlannedToStart ||
            (this.quizExercise.releaseDate !== undefined && moment(this.quizExercise.releaseDate).isValid() && moment(this.quizExercise.releaseDate).isAfter(moment()));

        const isGenerallyValid =
            this.quizExercise.title != undefined &&
            this.quizExercise.title !== '' &&
            this.quizExercise.title.length < 250 &&
            this.quizExercise.duration !== 0 &&
            releaseDateValidAndNotInPastCondition &&
            this.quizExercise.quizQuestions != undefined &&
            !!this.quizExercise.quizQuestions.length;
        const areAllQuestionsValid = this.quizExercise.quizQuestions?.every(function (question) {
            if (question.points == undefined || question.points < 1) {
                return false;
            }
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                if (mcQuestion.answerOptions!.some((answerOption) => answerOption.isCorrect)) {
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
                    this.shortAnswerQuestionUtil.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion) &&
                    this.shortAnswerQuestionUtil.everySpotHasASolution(shortAnswerQuestion.correctMappings, shortAnswerQuestion.spots) &&
                    this.shortAnswerQuestionUtil.everyMappedSolutionHasASpot(shortAnswerQuestion.correctMappings) &&
                    shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim() === '').length === 0 &&
                    !this.shortAnswerQuestionUtil.hasMappingDuplicateValues(shortAnswerQuestion.correctMappings) &&
                    this.shortAnswerQuestionUtil.atLeastAsManySolutionsAsSpots(shortAnswerQuestion)
                );
            } else {
                Sentry.captureException(new Error('Unknown question type: ' + question));
                return question.title && question.title !== '';
            }
        }, this);

        const maxPointsReachableInQuiz = this.quizExercise.quizQuestions?.map((quizQuestion) => quizQuestion.points ?? 0).reduce((a, b) => a + b, 0);

        const noTestRunExists = !this.isExamMode || !this.quizExercise.testRunParticipationsExist;

        return (
            isGenerallyValid &&
            areAllQuestionsValid === true &&
            this.isEmpty(this.invalidFlaggedQuestions) &&
            maxPointsReachableInQuiz !== undefined &&
            maxPointsReachableInQuiz > 0 &&
            noTestRunExists
        );
    }

    /**
     * Iterates through the questions is search for invalid flags. Updates {@link invalidFlaggedQuestions} accordingly.
     * Check the invalid flag of the question as well as all elements which can be set as invalid, for each quiz exercise type.
     * @param questions optional parameter, if it is not set, it iterates over the exercise questions.
     */
    checkForInvalidFlaggedQuestions(questions: QuizQuestion[] = []) {
        if (!this.quizExercise) {
            return;
        }
        if (questions.length === 0) {
            questions = this.quizExercise.quizQuestions!;
        }
        const invalidQuestions: {
            [questionId: number]: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[] | undefined;
        } = {};
        questions.forEach(function (question) {
            const invalidQuestion = question.invalid;
            const invalidElements: (AnswerOption | ShortAnswerSolution | ShortAnswerMapping | ShortAnswerSpot | DropLocation | DragItem | DragAndDropMapping)[] = [];
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE && (<MultipleChoiceQuestion>question).answerOptions !== undefined) {
                (<MultipleChoiceQuestion>question).answerOptions!.forEach(function (option) {
                    if (option.invalid) {
                        invalidElements.push(option);
                    }
                });
            } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                if ((<DragAndDropQuestion>question).dragItems !== undefined) {
                    (<DragAndDropQuestion>question).dragItems!.forEach(function (option) {
                        if (option.invalid) {
                            invalidElements.push(option);
                        }
                    });
                }
                if ((<DragAndDropQuestion>question).correctMappings !== undefined) {
                    (<DragAndDropQuestion>question).correctMappings!.forEach(function (option) {
                        if (option.invalid) {
                            invalidElements.push(option);
                        }
                    });
                }
                if ((<DragAndDropQuestion>question).dropLocations !== undefined) {
                    (<DragAndDropQuestion>question).dropLocations!.forEach(function (option) {
                        if (option.invalid) {
                            invalidElements.push(option);
                        }
                    });
                }
            } else {
                if ((<ShortAnswerQuestion>question).solutions !== undefined) {
                    (<ShortAnswerQuestion>question).solutions!.forEach(function (option) {
                        if (option.invalid) {
                            invalidElements.push(option);
                        }
                    });
                }
                if ((<ShortAnswerQuestion>question).correctMappings !== undefined) {
                    (<ShortAnswerQuestion>question).correctMappings!.forEach(function (option) {
                        if (option.invalid) {
                            invalidElements.push(option);
                        }
                    });
                }
                if ((<ShortAnswerQuestion>question).spots !== undefined) {
                    (<ShortAnswerQuestion>question).spots!.forEach(function (option) {
                        if (option.invalid) {
                            invalidElements.push(option);
                        }
                    });
                }
            }
            if (invalidQuestion || invalidElements.length !== 0) {
                invalidQuestions[question.title!] = invalidElements.length !== 0 ? { invalidElements } : {};
            }
        });
        this.invalidFlaggedQuestions = invalidQuestions;
    }

    /**
     * Get the reasons, why the quiz needs warnings
     * @returns {Array} array of objects with fields 'translateKey' and 'translateValues'
     */
    computeInvalidWarnings(): Warning[] {
        const invalidWarnings = !this.quizExercise
            ? []
            : this.quizExercise.quizQuestions
                  ?.map((question, index) => {
                      if (question.type === QuizQuestionType.MULTIPLE_CHOICE && (<MultipleChoiceQuestion>question).answerOptions!.some((option) => !option.explanation)) {
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
     * Get the reasons, why the quiz is invalid
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
        if (this.quizExercise.title!.length >= 250) {
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
        if (this.isExamMode && this.quizExercise.testRunParticipationsExist) {
            invalidReasons.push({
                translateKey: 'artemisApp.quizExercise.edit.testRunSubmissionsExist',
                translateValues: {},
            });
        }

        /** We only verify the releaseDate if the checkbox is activated **/
        if (this.quizExercise.isPlannedToStart) {
            if (!this.quizExercise.releaseDate || !moment(this.quizExercise.releaseDate).isValid()) {
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
        this.quizExercise.quizQuestions!.forEach(function (question: QuizQuestion, index: number) {
            if (!question.title || question.title === '') {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionTitle',
                    translateValues: { index: index + 1 },
                });
            }
            if (question.points == undefined || question.points < 1) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.questionScore',
                    translateValues: { index: index + 1 },
                });
            }
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                const mcQuestion = question as MultipleChoiceQuestion;
                if (!mcQuestion.answerOptions!.some((answerOption) => answerOption.isCorrect)) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.questionCorrectAnswerOption',
                        translateValues: { index: index + 1 },
                    });
                }
                if (!mcQuestion.answerOptions!.every((answerOption) => answerOption.explanation !== '')) {
                    invalidReasons.push({
                        translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                        translateValues: { index: index + 1 },
                    });
                }
            }
            if (question.title && question.title.length >= 250) {
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
                }
                if (!this.shortAnswerQuestionUtil.validateNoMisleadingShortAnswerMapping(shortAnswerQuestion)) {
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
                if (!(shortAnswerQuestion.solutions?.filter((solution) => solution.text!.trim() === '').length === 0)) {
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
        const invalidFlaggedReasons = !this.quizExercise
            ? []
            : this.quizExercise.quizQuestions
                  ?.map((question, index) => {
                      if (this.invalidFlaggedQuestions[question.title!]) {
                          return {
                              translateKey: 'artemisApp.quizExercise.invalidReasons.questionHasInvalidFlaggedElements',
                              translateValues: { index: index + 1 },
                          };
                      }
                  })
                  .filter(Boolean);

        return invalidReasons.concat(invalidFlaggedReasons as Reason[]);
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
        return reasonString.substr(0, reasonString.length - 5);
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
            });
        } else {
            await this.addQuestions(questions);
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
     * Save the quiz to the server and invoke callback functions depending of result
     */
    save(): void {
        if (this.hasSavedQuizStarted || !this.pendingChangesCache || !this.quizIsValid) {
            return;
        }

        Exercise.sanitize(this.quizExercise);

        this.isSaving = true;
        this.parseAllQuestions();
        if (this.quizExercise.id !== undefined) {
            const requestOptions = {} as any;
            if (this.notificationText) {
                requestOptions.notificationText = this.notificationText;
            }
            this.quizExerciseService.update(this.quizExercise, requestOptions).subscribe(
                (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    this.notificationText = undefined;
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                () => this.onSaveError(),
            );
        } else {
            this.quizExerciseService.create(this.quizExercise).subscribe(
                (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body);
                    } else {
                        this.onSaveError();
                    }
                },
                () => this.onSaveError(),
            );
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
    }

    /**
     * Callback function for when the save fails
     */
    private onSaveError = (): void => {
        console.error('Saving Quiz Failed! Please try again later.');
        this.jhiAlertService.error('artemisApp.quizExercise.saveError');
        this.isSaving = false;
        this.changeDetector.detectChanges();
    };

    /**
     * Makes sure the entity is well formed and its fields are of the correct types
     * @param quizExercise {QuizExercise} exercise which will be prepared
     */
    prepareEntity(quizExercise: QuizExercise): void {
        if (this.isExamMode) {
            quizExercise.releaseDate = moment(quizExercise.releaseDate);
        } else {
            quizExercise.releaseDate = quizExercise.releaseDate ? moment(quizExercise.releaseDate) : moment();
            quizExercise.duration = Number(quizExercise.duration);
            quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
        }
    }

    /**
     * Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange(): void {
        if (!this.isExamMode) {
            const duration = moment.duration(this.duration);
            this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
            this.updateDuration();
            this.cacheValidation();
        } else if (this.quizExercise.releaseDate && this.quizExercise.dueDate) {
            const duration = moment(this.quizExercise.dueDate).diff(this.quizExercise.releaseDate, 's');
            this.quizExercise.duration = round(duration);
            this.updateDuration();
            this.cacheValidation();
        }
    }

    /**
     * Update ui to current value of duration
     */
    updateDuration(): void {
        const duration = moment.duration(this.quizExercise.duration, 'seconds');
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
            this.router.navigate(['/course-management', this.quizExercise.course!.id, 'quiz-exercises']);
        } else {
            this.router.navigate(['/course-management', this.courseId, 'exams', this.examId, 'exercise-groups']);
        }
    }

    /**
     * Check if the saved quiz has started
     * @return {boolean} true if the saved quiz has started, otherwise false
     */
    get hasSavedQuizStarted(): boolean {
        return !!(this.savedEntity && this.savedEntity.isPlannedToStart && moment(this.savedEntity.releaseDate!).isBefore(moment()));
    }

    /**
     * check if Dictionary is empty
     * @param obj the dictionary to be checked
     */
    private isEmpty(obj: {}) {
        return Object.keys(obj).length === 0;
    }

    includedInOverallScoreChange(includedInOverallScore: IncludedInOverallScore) {
        this.quizExercise.includedInOverallScore = includedInOverallScore;
        this.cacheValidation();
    }
}
