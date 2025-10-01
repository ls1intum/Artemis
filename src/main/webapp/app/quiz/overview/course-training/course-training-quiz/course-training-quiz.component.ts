import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { CourseTrainingQuizService } from 'app/quiz/overview/service/course-training-quiz.service';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY, filter, map, switchMap } from 'rxjs';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { SubmittedAnswerAfterEvaluation } from 'app/quiz/overview/course-training/course-training-quiz/SubmittedAnswerAfterEvaluation';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { QuizQuestionTraining } from 'app/quiz/overview/course-training/course-training-quiz/quiz-question-training.model';
import { DialogModule } from 'primeng/dialog';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';

@Component({
    selector: 'jhi-course-training-quiz',
    imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, DragAndDropQuestionComponent, ButtonComponent, TranslateDirective, DialogModule],
    templateUrl: './course-training-quiz.component.html',
})
export class CourseTrainingQuizComponent {
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private quizService = inject(CourseTrainingQuizService);

    currentIndex = signal(0);
    private alertService = inject(AlertService);
    private courseService = inject(CourseManagementService);

    // Pagination options
    page = signal(0);
    size = 20;
    totalItems = signal(0);
    allLoadedQuestions = signal<QuizQuestionTraining[]>([]);
    hasNext = signal(false);

    // Reactive chain for loading quiz questions based on the current route
    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);
    courseSignal = toSignal(
        this.route.parent!.params.pipe(
            map((p) => p['courseId'] as number | undefined),
            filter((id): id is number => id !== undefined),
            switchMap((id) => this.courseService.find(id)),
            map((res) => res.body ?? undefined),
        ),
        { initialValue: undefined },
    );
    course = computed(() => this.courseSignal());
    questionsLoaded = computed(() => this.allLoadedQuestions().length > 0);
    nextPage = computed(() => (this.currentIndex() + 2) % this.size === 0 && this.hasNext());

    submittedAnswer: SubmittedAnswer;
    showingResult = false;
    submitted = false;
    questionScores: number = 0;
    selectedAnswerOptions: AnswerOption[] = [];
    dragAndDropMappings: DragAndDropMapping[] = [];
    shortAnswerSubmittedTexts: ShortAnswerSubmittedText[] = [];
    previousRatedStatus = true;
    showUnratedConfirmation = false;
    questionIds: number[] = [];
    isNewSession = true;

    /**
     * checks if the current question is the last question
     */
    isLastQuestion = computed(() => {
        const questions = this.allLoadedQuestions();
        if (questions.length === 0) {
            return true;
        }
        return this.currentIndex() === this.allLoadedQuestions().length - 1;
    });

    /**
     * gets the current question
     */
    currentQuestion = computed(() => {
        const questions = this.allLoadedQuestions();
        if (questions.length === 0) {
            return undefined;
        }
        return questions[this.currentIndex()].quizQuestionWithSolutionDTO;
    });

    isRated = computed(() => {
        const questions = this.allLoadedQuestions();
        if (questions.length === 0) {
            return false;
        }
        return questions[this.currentIndex()].isRated;
    });

    constructor() {
        effect(() => {
            const id = this.courseId();
            if (id && this.page() === 0) {
                this.loadQuestions();
            }
        });

        effect(() => {
            const questionStatus = this.isRated();
            if (questionStatus !== undefined) {
                this.checkRatingStatusChange();
            }
        });

        effect(() => {
            if (this.nextPage()) {
                this.loadNextPage();
            }
        });
    }

    /**
     * loads questions for the current page
     */
    loadQuestions(): void {
        if (!this.courseId()) {
            return;
        }

        this.quizService.getQuizQuestionsPage(this.courseId(), this.page(), this.size, this.questionIds, this.isNewSession).subscribe({
            next: (res: HttpResponse<QuizQuestionTraining[]>) => {
                this.hasNext.set(res.headers.get('X-Has-Next') === 'true');

                if (this.page() === 0 && res.body) {
                    this.questionIds = res.body[0].questionIds ? res.body[0].questionIds : [];
                    this.isNewSession = res.body[0].isNewSession;
                }

                if (this.page() === 0) {
                    this.allLoadedQuestions.set(res.body || []);
                } else {
                    this.allLoadedQuestions.update((current) => [...current, ...(res.body || [])]);
                }

                if (this.allLoadedQuestions().length > 0 && this.currentIndex() === 0) {
                    this.initQuestion(this.currentQuestion()!);
                }
            },
        });
    }

    /**
     * loads the next page of questions
     */
    loadNextPage(): void {
        if (!this.hasNext()) {
            return;
        }
        this.page.update((page) => page + 1);
        this.loadQuestions();
    }

    /**
     * increments the current question index or navigates to the course practice page if the last question is reached
     */
    nextQuestion(): void {
        const questions = this.allLoadedQuestions();
        if (this.currentIndex() < questions.length - 1) {
            this.currentIndex.update((index) => index + 1);
            const question = this.currentQuestion();
            if (question) {
                this.initQuestion(question);
            }
        }
    }

    checkRatingStatusChange(): void {
        const currentIsRated = this.isRated();

        if (this.previousRatedStatus && currentIsRated === false) {
            this.showUnratedConfirmation = true;
        }

        this.previousRatedStatus = currentIsRated;
    }

    /**
     * initializes a new question with default values
     * @param question
     */
    initQuestion(question: QuizQuestion): void {
        this.showingResult = false;
        this.submitted = false;
        this.checkRatingStatusChange();
        if (question) {
            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE:
                    this.selectedAnswerOptions = [];
                    break;
                case QuizQuestionType.DRAG_AND_DROP:
                    this.dragAndDropMappings = [];
                    break;
                case QuizQuestionType.SHORT_ANSWER:
                    this.shortAnswerSubmittedTexts = [];
                    break;
            }
        }
    }

    /**
     * applies the current selection to the trainingAnswer object
     */
    applySelection() {
        const question = this.currentQuestion();
        if (!question) {
            return;
        }

        switch (question.type) {
            case QuizQuestionType.MULTIPLE_CHOICE: {
                const answerOptions = this.selectedAnswerOptions;
                const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
                mcSubmittedAnswer.quizQuestion = question;
                mcSubmittedAnswer.selectedOptions = answerOptions;
                this.submittedAnswer = mcSubmittedAnswer;
                break;
            }
            case QuizQuestionType.DRAG_AND_DROP: {
                const mappings = this.dragAndDropMappings;
                const ddSubmittedAnswer = new DragAndDropSubmittedAnswer();
                ddSubmittedAnswer.quizQuestion = question;
                ddSubmittedAnswer.mappings = mappings;
                this.submittedAnswer = ddSubmittedAnswer;
                break;
            }
            case QuizQuestionType.SHORT_ANSWER: {
                const submittedTexts = this.shortAnswerSubmittedTexts;
                const saSubmittedAnswer = new ShortAnswerSubmittedAnswer();
                saSubmittedAnswer.quizQuestion = question;
                saSubmittedAnswer.submittedTexts = submittedTexts;
                this.submittedAnswer = saSubmittedAnswer;
                break;
            }
        }
    }

    /**
     * Submits the quiz for practice
     */
    onSubmit() {
        const questionId = this.currentQuestion()?.id;
        if (!questionId) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'No questionId found',
            });
            return;
        }
        this.applySelection();
        this.quizService.submitForTraining(this.submittedAnswer, questionId, this.courseId(), this.isRated()).subscribe({
            next: (response: HttpResponse<SubmittedAnswerAfterEvaluation>) => {
                if (response.body) {
                    this.onSubmitSuccess(response.body);
                }
            },
            error: (error: HttpErrorResponse) => this.onSubmitError(error),
        });
    }

    onSubmitSuccess(evaluatedAnswer: SubmittedAnswerAfterEvaluation) {
        this.submitted = true;
        this.showingResult = true;

        this.questionScores = roundValueSpecifiedByCourseSettings(evaluatedAnswer.scoreInPoints || 0, this.course());

        // update UI with the evaluated answer
        this.applyEvaluatedAnswer(evaluatedAnswer);
    }

    /**
     * Callback function for handling error when submitting
     */
    onSubmitError(error: HttpErrorResponse) {
        const errorMessage = 'Submitting the quiz was not possible. ' + error.message;
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
    }

    /**
     * Applies the evaluated answer to the current question
     */
    applyEvaluatedAnswer(evaluatedAnswer: SubmittedAnswerAfterEvaluation) {
        const question = this.currentQuestion();
        if (!question) return;

        switch (question.type) {
            case QuizQuestionType.MULTIPLE_CHOICE:
                this.selectedAnswerOptions = evaluatedAnswer.selectedOptions || [];
                break;
            case QuizQuestionType.DRAG_AND_DROP:
                this.dragAndDropMappings = evaluatedAnswer.mappings || [];
                break;
            case QuizQuestionType.SHORT_ANSWER:
                this.shortAnswerSubmittedTexts = evaluatedAnswer.submittedTexts || [];
                break;
        }
    }

    /**
     * navigates to the course practice page
     */
    navigateToTraining(): void {
        this.router.navigate(['courses', this.courseId(), 'training']);
    }

    confirmUnratedPractice(): void {
        this.showUnratedConfirmation = false;
    }

    cancelUnratedPractice(): void {
        this.showUnratedConfirmation = false;
        this.navigateToTraining();
    }
}
