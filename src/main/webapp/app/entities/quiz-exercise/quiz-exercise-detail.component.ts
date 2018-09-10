import { Component, OnDestroy, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { QuizExerciseService } from './quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Course } from '../course/course.model';
import { CourseService } from '../course/course.service';
import { QuizExercise } from './quiz-exercise.model';
import { DragAndDropQuestionUtil } from '../../components/util/drag-and-drop-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { Question, QuestionType, ScoringType} from '../../entities/question';
import { MultipleChoiceQuestion } from '../../entities/multiple-choice-question';
import { DragAndDropQuestion } from '../../entities/drag-and-drop-question';
import { AnswerOption } from '../../entities/answer-option';
import { Option, Duration } from './quiz-exercise-interfaces';
import * as moment from 'moment';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-detail.component.html',
    providers: [ DragAndDropQuestionUtil ]
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
    savedEntity = {};
    /** Corresponds to question.releaseDate, which is explicit any **/
    dateTime: any;

    showExistingQuestions = false;
    courses: Course[] = [];
    selectedCourse: string;
    quizExercises: QuizExercise[];
    allExistingQuestions: Question[];
    existingQuestions: Question[];
    importFile: Blob;
    searchQueryText: string;
    dndFilterEnabled: boolean;
    mcqFilterEnabled: boolean;

    /** Duration object **/
    duration = new Duration(0, 0);

    /** Status constants **/
    isSaving = false;
    isTrue = true;

    /** Status Options **/
    statusOptionsVisible: Option[] = [
        new Option(false, 'Hidden'),
        new Option(true, 'Visible')
    ];
    statusOptionsPractice: Option[] = [
        new Option(false, 'Closed'),
        new Option(true, 'Open for Practice')
    ];
    statusOptionsActive: Option[] = [
        new Option(true, 'Active')
    ];

    constructor(private route: ActivatedRoute,
        private courseService: CourseService,
        private quizExerciseService: QuizExerciseService,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private router: Router,
        private translateService: TranslateService,
        private fileUploaderService: FileUploaderService) {}

    ngOnInit(): void {
        /** Initialize local constants **/
        this.showExistingQuestions = false;
        this.courses = [];
        this.quizExercises = [];
        this.allExistingQuestions = [];
        this.existingQuestions = [];
        this.importFile = null;
        this.searchQueryText = '';
        this.dndFilterEnabled = true;
        this.mcqFilterEnabled = true;

        this.paramSub = this.route.params.subscribe(params => {
            console.log('params', params);
            /** Query the courseService for the participationId given by the params */
            this.courseService.find(params['courseId']).subscribe((response: HttpResponse<Course>) => {
                this.course = response.body;
                this.init();
            });
            if (params['id']) {
                this.quizExerciseService.find(params['id']).subscribe((response: HttpResponse<QuizExercise>) => {
                    this.quizExercise = response.body;
                });
            }
        });
        this.repository = this.quizExerciseService;
        this.courseRepository = this.courseService;
    }

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
        this.savedEntity = this.entity.id ? Object.assign({}, this.entity) : {};
        if (!this.quizExercise.course) {
            this.quizExercise.course = this.course;
        }
        this.updateDuration();
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.course) {
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
     * @param questionToDelete {Question} the question to remove
     */
    deleteQuestion(questionToDelete: Question) {
        this.quizExercise.questions = this.quizExercise.questions.filter(question => question !== questionToDelete);
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
        ].some(key => this.quizExercise[key] !== this.savedEntity[key]);
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
                return question.title && question.title !== '' && mcQuestion.answerOptions.some(answerOption => answerOption.isCorrect);
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
        const reasons: Object[] = [];
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
        this.quizExercise.questions.forEach(function(question, index) {
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
            translate.get(reason['translateKey'], reason['translateValues']).subscribe((res: string) => {
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

    onSaveSuccess(result: QuizExercise) {
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
    prepareEntity(entity: QuizExercise) {
        entity.releaseDate = entity.releaseDate ? new Date(entity.releaseDate) : new Date();
        entity.duration = Number(entity.duration);
        entity.duration = isNaN(entity.duration) ? 10 : entity.duration;
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
        return !!(
            this.savedEntity &&
            (this.savedEntity as any).isPlannedToStart &&
            moment((this.savedEntity as any).releaseDate).isBefore(moment())
        );
    }

    ngOnDestroy() {
        /** Unsubscribe from route params **/
        this.paramSub.unsubscribe();
    }
}
