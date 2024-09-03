import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { QuizPool } from 'app/entities/quiz/quiz-pool.model';
import { QuizPoolService } from 'app/exercises/quiz/manage/quiz-pool.service';
import { QuizPoolMappingComponent } from 'app/exercises/quiz/manage/quiz-pool-mapping.component';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import { ValidationReason } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { QuizQuestionListEditComponent } from 'app/exercises/quiz/manage/quiz-question-list-edit.component';
import { onError } from 'app/shared/util/global.utils';
import { computeQuizQuestionInvalidReason, isQuizQuestionValid } from 'app/exercises/quiz/shared/quiz-manage-util.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Exam } from 'app/entities/exam/exam.model';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-quiz-pool',
    templateUrl: './quiz-pool.component.html',
    providers: [DragAndDropQuestionUtil, ShortAnswerQuestionUtil],
    changeDetection: ChangeDetectionStrategy.OnPush,
    styleUrls: ['./quiz-pool.component.scss', '../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class QuizPoolComponent implements OnInit {
    @ViewChild('quizPoolQuestionMapping')
    quizPoolMappingComponent: QuizPoolMappingComponent;
    @ViewChild('quizQuestionsEdit')
    quizQuestionsEditComponent: QuizQuestionListEditComponent;

    faExclamationCircle = faExclamationCircle;

    quizPool: QuizPool;
    savedQuizPool: string;
    isSaving: boolean;
    isValid: boolean;
    hasPendingChanges: boolean;
    invalidReasons: ValidationReason[] = [];
    warningReasons: ValidationReason[] = [];

    courseId: number;
    examId: number;
    isExamStarted: boolean;

    constructor(
        private route: ActivatedRoute,
        private quizPoolService: QuizPoolService,
        private examService: ExamManagementService,
        private changeDetectorRef: ChangeDetectorRef,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.checkIfExamStarted();
        this.initializeQuizPool();
    }

    /**
     * Add question to the quiz pool mapping component
     *
     * @param quizQuestion the quiz question to be added
     */
    handleQuestionAdded(quizQuestion: QuizQuestion) {
        this.quizPoolMappingComponent.addQuestion(quizQuestion);
        this.handleUpdate();
    }

    /**
     * Delete question from the quiz pool mapping component
     *
     * @param quizQuestion the quiz question to be deleted
     */
    handleQuestionDeleted(quizQuestion: QuizQuestion) {
        this.quizPoolMappingComponent.deleteQuestion(quizQuestion);
        this.handleUpdate();
    }

    /**
     * Save the quiz pool if there is pending changes and the configuration is valid
     */
    save() {
        if (!this.hasPendingChanges || !this.isValid) {
            return;
        }

        this.isSaving = true;
        this.quizQuestionsEditComponent.parseAllQuestions();
        const requestOptions = {} as any;
        this.quizPool.maxPoints = this.quizPoolMappingComponent.getMaxPoints();
        this.quizPoolService.update(this.courseId, this.examId, this.quizPool, requestOptions).subscribe({
            next: (quizPoolResponse: HttpResponse<QuizPool>) => {
                if (quizPoolResponse.body) {
                    this.onSaveSuccess(quizPoolResponse.body);
                } else {
                    this.onSaveError();
                }
            },
            error: () => this.onSaveError(),
        });
    }

    /**
     * Set isExamStarted to true if exam has been started or false otherwise
     */
    private checkIfExamStarted() {
        this.examService.find(this.courseId, this.examId).subscribe({
            next: (response: HttpResponse<Exam>) => {
                const exam = response.body!;
                this.isExamStarted = exam.startDate ? exam.startDate.isBefore(dayjs()) : false;
                this.changeDetectorRef.detectChanges();
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Set quizPool if already exists or create a new object otherwise
     */
    private initializeQuizPool() {
        this.quizPoolService.find(this.courseId, this.examId).subscribe({
            next: (response: HttpResponse<QuizPool>) => {
                const quizPool = response.body;
                if (!quizPool) {
                    this.quizPool = new QuizPool();
                    this.quizPool.quizGroups = [];
                    this.quizPool.quizQuestions = [];
                    this.hasPendingChanges = false;
                    this.isValid = true;
                    this.changeDetectorRef.detectChanges();
                } else {
                    this.quizPool = quizPool;
                    this.savedQuizPool = JSON.stringify(this.quizPool);
                    this.isValid = true;
                    this.computeReasons();
                }
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
            },
        });
    }

    /**
     * Set pending changes to true if there is a change from the last saved quiz pool and set is valid to true if the configuration is valid
     */
    handleUpdate() {
        this.hasPendingChanges = JSON.stringify(this.quizPool) !== this.savedQuizPool;
        this.isValid = this.isConfigurationValid();
        this.computeReasons();
    }

    /**
     * Set invalidReasons and warningReasons
     */
    private computeReasons() {
        this.changeDetectorRef.detectChanges();
        this.invalidReasons = this.getInvalidReasons();
        this.warningReasons = this.getWarningReasons();
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Check if the quiz questions and groups are all valid.
     * @return true if the configuration is valid or false otherwise
     */
    private isConfigurationValid(): boolean {
        const quizQuestionsValid = this.quizPool.quizQuestions.every((question) => isQuizQuestionValid(question, this.dragAndDropQuestionUtil, this.shortAnswerQuestionUtil));
        const totalPoints = this.quizPool.quizQuestions?.map((quizQuestion) => quizQuestion.points ?? 0).reduce((accumulator, points) => accumulator + points, 0);
        return (
            (this.quizPool.quizQuestions.length === 0 || (quizQuestionsValid && totalPoints > 0)) &&
            !this.quizPoolMappingComponent.hasGroupsWithNoQuestion() &&
            !this.quizPoolMappingComponent.hasGroupsWithDifferentQuestionPoints()
        );
    }

    /**
     * Compute invalid reasons of the configurations
     * @return an array of ValidationReason.
     */
    private getInvalidReasons(): Array<ValidationReason> {
        const invalidReasons = new Array<ValidationReason>();
        this.quizPool.quizQuestions!.forEach((question: QuizQuestion, index: number) => {
            computeQuizQuestionInvalidReason(invalidReasons, question, index, this.dragAndDropQuestionUtil, this.shortAnswerQuestionUtil);
        });

        if (this.quizPoolMappingComponent.hasGroupsWithNoQuestion()) {
            const names = this.quizPoolMappingComponent.getGroupNamesWithNoQuestion();
            for (const name of names) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizPool.invalidReasons.groupNoQuestion',
                    translateValues: {
                        name,
                    },
                });
            }
        }

        if (this.quizPoolMappingComponent.hasGroupsWithDifferentQuestionPoints()) {
            const names = this.quizPoolMappingComponent.getGroupNamesWithDifferentQuestionPoints();
            for (const name of names) {
                invalidReasons.push({
                    translateKey: 'artemisApp.quizPool.invalidReasons.groupHasDifferentQuestionPoints',
                    translateValues: {
                        name,
                    },
                });
            }
        }

        return invalidReasons;
    }

    /**
     * Compute warning reasons of the configurations
     * @return an array of ValidationReason.
     */
    private getWarningReasons(): Array<ValidationReason> {
        const warningReasons = new Array<ValidationReason>();
        this.quizPool.quizQuestions.forEach((quizQuestion: QuizQuestion, index: number) => {
            if (quizQuestion.type === QuizQuestionType.MULTIPLE_CHOICE && (<MultipleChoiceQuestion>quizQuestion).answerOptions!.some((option) => !option.explanation)) {
                warningReasons.push({
                    translateKey: 'artemisApp.quizExercise.invalidReasons.explanationIsMissing',
                    translateValues: { index: index + 1 },
                });
            }
        });
        return warningReasons;
    }

    /**
     * Callback if the save is successful. Set isSaving & hasPendingchanges to false and update quizPool and savedQuizPool.
     *
     * @param quizPool the saved quiz pool
     */
    private onSaveSuccess(quizPool: QuizPool): void {
        this.isSaving = false;
        this.hasPendingChanges = false;
        this.quizPool = quizPool;
        this.savedQuizPool = JSON.stringify(quizPool);
        this.changeDetectorRef.detectChanges();
    }

    /**
     * Callback if the save is unsuccessful. Set isSaving to false and display alert.
     */
    private onSaveError = (): void => {
        this.alertService.error('artemisApp.quizExercise.saveError');
        this.isSaving = false;
        this.changeDetectorRef.detectChanges();
    };
}
