import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { CoursePracticeQuizService } from 'app/quiz/overview/service/course-practice-quiz.service';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizParticipationService } from 'app/quiz/overview/service/quiz-participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-course-practice-quiz',
    imports: [MultipleChoiceQuestionComponent, ShortAnswerQuestionComponent, DragAndDropQuestionComponent, ButtonComponent],
    templateUrl: './course-practice-quiz.component.html',
})
export class CoursePracticeQuizComponent {
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private quizService = inject(CoursePracticeQuizService);

    currentIndex = signal(0);
    private quizParticipationService = inject(QuizParticipationService);
    private alertService = inject(AlertService);

    // Reactive chain for loading quiz questions based on the current route
    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);
    questionsSignal = toSignal(this.quizService.getQuizQuestions(this.courseId()) ?? EMPTY, { initialValue: [] });
    questions = computed(() => this.questionsSignal());

    submission = new QuizSubmission();
    isSubmitting = false;
    result: Result;
    showingResult = false;
    userScore: number;
    submitted = false;
    quizId = 18;
    questionScores: { [id: number]: number } = {};
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
        this.submitted = false;
        if (this.isLastQuestion()) {
            this.navigateToPractice();
        } else {
            this.currentIndex.set(this.currentIndex() + 1);
            this.initQuestion(this.currentQuestion()!);
        }
    }

    /**
     * loads the quiz question
     * @param question
     */
    initQuestion(question: QuizQuestion): void {
        this.showingResult = false;
        this.submission = new QuizSubmission();
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

    applySelection() {
        this.submission.submittedAnswers = [];
        const questionId = this.currentQuestion()!.id!;
        const question = this.questions().find((q) => q.id === questionId);

        if (!question) {
            return;
        }

        switch (question.type) {
            case QuizQuestionType.MULTIPLE_CHOICE: {
                const answerOptions = this.selectedAnswerOptions || [];
                const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
                mcSubmittedAnswer.quizQuestion = question;
                mcSubmittedAnswer.selectedOptions = answerOptions;
                this.submission.submittedAnswers!.push(mcSubmittedAnswer);
                break;
            }
            case QuizQuestionType.DRAG_AND_DROP: {
                const mappings = this.dragAndDropMappings || [];
                const ddSubmittedAnswer = new DragAndDropSubmittedAnswer();
                ddSubmittedAnswer.quizQuestion = question;
                ddSubmittedAnswer.mappings = mappings;
                this.submission.submittedAnswers!.push(ddSubmittedAnswer);
                break;
            }
            case QuizQuestionType.SHORT_ANSWER: {
                const submittedTexts = this.shortAnswerSubmittedTexts || [];
                const saSubmittedAnswer = new ShortAnswerSubmittedAnswer();
                saSubmittedAnswer.quizQuestion = question;
                saSubmittedAnswer.submittedTexts = submittedTexts;
                this.submission.submittedAnswers!.push(saSubmittedAnswer);
                break;
            }
        }
    }

    onSubmit() {
        this.applySelection();
        this.isSubmitting = true;
        this.submitted = true;
        this.quizParticipationService.submitForPractice(this.submission, this.currentQuestion()!.exerciseId!).subscribe({
            next: (response: HttpResponse<Result>) => {
                this.onSubmitSuccess(response.body!);
            },
            error: (error: HttpErrorResponse) => this.onSubmitError(error),
        });
    }

    onSubmitSuccess(result: Result) {
        this.isSubmitting = false;
        this.submission = result.submission as QuizSubmission;
        this.applySubmission();
        this.showResult(result);
    }

    /**
     * Callback function for handling error when submitting
     * @param error
     */
    onSubmitError(error: HttpErrorResponse) {
        const errorMessage = 'Submitting the quiz was not possible. ' + error.headers?.get('X-artemisApp-message') || error.message;
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSubmitting = false;
    }

    /**
     * applies the data from the model to the UI (reverse of applySelection):
     *
     * Sets the checkmarks (selected answers) for all questions according to the submission data
     * this needs to be done when we get new submission data, e.g. through the websocket connection
     */
    applySubmission() {
        this.selectedAnswerOptions = [];
        this.dragAndDropMappings = [];
        this.shortAnswerSubmittedTexts = [];
        const question = this.currentQuestion();
        if (question) {
            const submittedAnswer = this.submission.submittedAnswers?.find((answer) => {
                return answer.quizQuestion!.id === question.id;
            });

            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE:
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer)?.selectedOptions || [];
                    break;
                case QuizQuestionType.DRAG_AND_DROP:
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings = (submittedAnswer as DragAndDropSubmittedAnswer)?.mappings || [];
                    break;
                case QuizQuestionType.SHORT_ANSWER:
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer)?.submittedTexts || [];
                    break;
            }
        }
    }

    /**
     * Display results of the quiz for the user
     * @param result
     */
    showResult(result: Result) {
        this.result = result;
        if (this.result) {
            this.showingResult = true;

            // assign user score (limit decimal places to 2)
            this.userScore = this.submission.scoreInPoints ? round(this.submission.scoreInPoints) : 0;

            // create dictionary with scores for each question
            this.questionScores = {};
            this.submission.submittedAnswers?.forEach((submittedAnswer) => {
                // limit decimal places
                this.questionScores[submittedAnswer.quizQuestion!.id!] = round(submittedAnswer.scoreInPoints!);
            }, this);
        }
    }

    /**
     * navigates to the course practice page
     */
    navigateToPractice(): void {
        this.router.navigate(['courses', this.courseId(), 'practice']);
    }
}
