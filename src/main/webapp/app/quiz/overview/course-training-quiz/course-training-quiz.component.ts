import { Component, computed, inject, signal } from '@angular/core';
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
import { QuizTrainingAnswer } from 'app/quiz/overview/course-training-quiz/QuizTrainingAnswer';
import { SubmittedAnswerAfterEvaluationDTO } from 'app/quiz/overview/course-training-quiz/SubmittedAnswerAfterEvaluationDTO';

@Component({
    selector: 'jhi-course-practice-quiz',
    imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, DragAndDropQuestionComponent, ButtonComponent],
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

    // Reactive chain for loading quiz questions based on the current route
    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);
    questionsSignal = toSignal(
        this.route.parent!.params.pipe(
            map((p) => p['courseId'] as number | undefined),
            filter((id): id is number => id !== undefined),
            switchMap((id) => this.quizService.getQuizQuestions(id)),
        ),
        { initialValue: [] },
    );
    questions = computed(() => this.questionsSignal());
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

    trainingAnswer = new QuizTrainingAnswer();
    isSubmitting = false;
    showingResult = false;
    submitted = false;
    questionScores: number = 0;
    selectedAnswerOptions: AnswerOption[] = [];
    dragAndDropMappings: DragAndDropMapping[] = [];
    shortAnswerSubmittedTexts: ShortAnswerSubmittedText[] = [];

    /**
     * checks if the current question is the last question
     */
    isLastQuestion = computed(() => {
        if (this.questions().length === 0) {
            return true;
        }
        return this.currentIndex() === this.questions().length - 1;
    });

    /**
     * gets the current question
     */
    currentQuestion = computed(() => {
        if (this.questions().length === 0) {
            return undefined;
        }
        return this.questions()[this.currentIndex()];
    });

    /**
     * increments the current question index or navigates to the course practice page if the last question is reached
     */
    nextQuestion(): void {
        if (this.currentIndex() < this.questions().length - 1) {
            this.currentIndex.set(this.currentIndex() + 1);
            const question = this.currentQuestion();
            if (question) {
                this.initQuestion(question);
            }
        }
    }

    /**
     * initializes a new question with default values
     * @param question
     */
    initQuestion(question: QuizQuestion): void {
        this.showingResult = false;
        this.submitted = false;
        this.trainingAnswer = new QuizTrainingAnswer();
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
     * applies the current selection to the submission object
     */
    applySelection() {
        this.trainingAnswer.submittedAnswer = undefined;
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
                this.trainingAnswer.submittedAnswer = mcSubmittedAnswer;
                break;
            }
            case QuizQuestionType.DRAG_AND_DROP: {
                const mappings = this.dragAndDropMappings;
                const ddSubmittedAnswer = new DragAndDropSubmittedAnswer();
                ddSubmittedAnswer.quizQuestion = question;
                ddSubmittedAnswer.mappings = mappings;
                this.trainingAnswer.submittedAnswer = ddSubmittedAnswer;
                break;
            }
            case QuizQuestionType.SHORT_ANSWER: {
                const submittedTexts = this.shortAnswerSubmittedTexts;
                const saSubmittedAnswer = new ShortAnswerSubmittedAnswer();
                saSubmittedAnswer.quizQuestion = question;
                saSubmittedAnswer.submittedTexts = submittedTexts;
                this.trainingAnswer.submittedAnswer = saSubmittedAnswer;
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
            this.isSubmitting = false;
            return;
        }
        this.applySelection();
        this.isSubmitting = true;
        this.quizService.submitForTraining(this.trainingAnswer, questionId, this.courseId()).subscribe({
            next: (response: HttpResponse<SubmittedAnswerAfterEvaluationDTO>) => {
                if (response.body) {
                    this.onSubmitSuccess(response.body);
                }
            },
            error: (error: HttpErrorResponse) => this.onSubmitError(error),
        });
    }

    onSubmitSuccess(evaluatedAnswer: SubmittedAnswerAfterEvaluationDTO) {
        this.isSubmitting = false;
        this.submitted = true;
        this.showingResult = true;

        this.questionScores = roundValueSpecifiedByCourseSettings(evaluatedAnswer.scoreInPoints || 0, this.course());

        // UI mit den evaluierten Daten aktualisieren
        this.applyEvaluatedAnswer(evaluatedAnswer);
    }

    /**
     * Callback function for handling error when submitting
     */
    onSubmitError(error: HttpErrorResponse) {
        const errorMessage = 'Submitting the quiz was not possible. ' + (error.headers?.get('X-artemisApp-message') || error.message);
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSubmitting = false;
    }

    /**
     * Wendet die evaluierte Antwort auf die UI an
     */
    applyEvaluatedAnswer(evaluatedAnswer: SubmittedAnswerAfterEvaluationDTO) {
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
}
