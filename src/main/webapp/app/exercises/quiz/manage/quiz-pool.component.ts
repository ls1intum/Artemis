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
import { Exam } from 'app/entities/exam.model';
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

    handleQuestionAdded(quizQuestion: QuizQuestion) {
        this.quizPoolMappingComponent.addQuestion(quizQuestion);
        this.handleUpdate();
    }

    handleQuestionDeleted(quizQuestion: QuizQuestion) {
        this.quizPoolMappingComponent.deleteQuestion(quizQuestion);
        this.handleUpdate();
    }

    save() {
        if (!this.hasPendingChanges || !this.isValid) {
            return;
        }

        this.isSaving = true;
        this.quizQuestionsEditComponent.parseAllQuestions();
        const requestOptions = {} as any;
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

    private initializeQuizPool() {
        this.quizPoolService.find(this.courseId, this.examId).subscribe({
            next: (response: HttpResponse<QuizPool>) => {
                this.quizPool = response.body!;
                this.savedQuizPool = JSON.stringify(this.quizPool);
                this.isValid = true;
                this.computeReasons();
            },
            error: (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    this.quizPool = new QuizPool();
                    this.quizPool.quizGroups = [];
                    this.quizPool.quizQuestions = [];
                    this.hasPendingChanges = false;
                    this.isValid = true;
                    this.changeDetectorRef.detectChanges();
                } else {
                    onError(this.alertService, error);
                }
            },
        });
    }

    handleUpdate() {
        this.hasPendingChanges = JSON.stringify(this.quizPool) !== this.savedQuizPool;
        this.isValid = this.isConfigurationValid();
        this.computeReasons();
    }

    private computeReasons() {
        this.changeDetectorRef.detectChanges();
        this.invalidReasons = this.getInvalidReasons();
        this.warningReasons = this.getWarningReasons();
        this.changeDetectorRef.detectChanges();
    }

    private isConfigurationValid(): boolean {
        const quizQuestionsValid = this.quizPool.quizQuestions.every((question) => isQuizQuestionValid(question, this.dragAndDropQuestionUtil, this.shortAnswerQuestionUtil));
        const totalPoints = this.quizPool.quizQuestions?.map((quizQuestion) => quizQuestion.points ?? 0).reduce((accumulator, points) => accumulator + points, 0);
        return (this.quizPool.quizQuestions.length === 0 || (quizQuestionsValid && totalPoints > 0)) && !this.quizPoolMappingComponent.hasGroupsWithNoQuestion();
    }

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

        return invalidReasons;
    }

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

        if (this.quizPoolMappingComponent.hasGroupsWithDifferentQuestionPoints()) {
            const names = this.quizPoolMappingComponent.getGroupNamesWithDifferentQuestionPoints();
            for (const name of names) {
                warningReasons.push({
                    translateKey: 'artemisApp.quizPool.invalidReasons.groupHasDifferentQuestionPoints',
                    translateValues: {
                        name,
                    },
                });
            }
        }

        return warningReasons;
    }

    private onSaveSuccess(quizPool: QuizPool): void {
        this.isSaving = false;
        this.hasPendingChanges = false;
        this.quizPool = quizPool;
        this.savedQuizPool = JSON.stringify(quizPool);
        this.changeDetectorRef.detectChanges();
    }

    private onSaveError = (): void => {
        this.alertService.error('artemisApp.quizExercise.saveError');
        this.isSaving = false;
        this.changeDetectorRef.detectChanges();
    };
}
