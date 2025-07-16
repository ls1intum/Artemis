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
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizParticipationService } from 'app/quiz/overview/service/quiz-participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';

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
    private quizParticipationService = inject(QuizParticipationService);
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

    submission = new QuizSubmission();
    isSubmitting = false;
    result = new Result();
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
        this.submitted = false;
        this.currentIndex.set(this.currentIndex() + 1);
        const question = this.currentQuestion();
        if (question) {
            this.initQuestion(question);
        }
    }

    /**
     * initializes a new question with default values
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

    /**
     * applies the current selection to the submission object
     */
    applySelection() {
        this.submission.submittedAnswers = [];
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
                this.submission.submittedAnswers.push(mcSubmittedAnswer);
                break;
            }
            case QuizQuestionType.DRAG_AND_DROP: {
                const mappings = this.dragAndDropMappings;
                const ddSubmittedAnswer = new DragAndDropSubmittedAnswer();
                ddSubmittedAnswer.quizQuestion = question;
                ddSubmittedAnswer.mappings = mappings;
                this.submission.submittedAnswers.push(ddSubmittedAnswer);
                break;
            }
            case QuizQuestionType.SHORT_ANSWER: {
                const submittedTexts = this.shortAnswerSubmittedTexts;
                const saSubmittedAnswer = new ShortAnswerSubmittedAnswer();
                saSubmittedAnswer.quizQuestion = question;
                saSubmittedAnswer.submittedTexts = submittedTexts;
                this.submission.submittedAnswers.push(saSubmittedAnswer);
                break;
            }
        }
    }

    /**
     * Submits the quiz for practice
     */
    onSubmit() {
        const exerciseId = this.currentQuestion()?.exerciseId;
        if (!exerciseId) {
            this.alertService.addAlert({
                type: AlertType.WARNING,
                message: 'error.noExerciseIdForQuestion',
            });
            this.isSubmitting = false;
            return;
        }
        this.applySelection();
        this.isSubmitting = true;
        this.quizParticipationService.submitForPractice(this.submission, exerciseId).subscribe({
            next: (response: HttpResponse<Result>) => {
                if (response.body) {
                    this.onSubmitSuccess(response.body);
                }
            },
            error: (error: HttpErrorResponse) => this.onSubmitError(error),
        });
    }

    onSubmitSuccess(result: Result) {
        this.isSubmitting = false;
        this.submitted = true;
        this.submission = result.submission as QuizSubmission;
        this.applySubmission();
        this.showResult(result);
    }

    /**
     * Callback function for handling error when submitting
     * @param error
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
     * applies the data from the model to the UI (reverse of applySelection):
     *
     * Sets the checkmarks (selected answers) for all questions according to the submission data
     */
    applySubmission() {
        this.selectedAnswerOptions = [];
        this.dragAndDropMappings = [];
        this.shortAnswerSubmittedTexts = [];
        const question = this.currentQuestion();
        if (question) {
            const submittedAnswer = this.submission.submittedAnswers?.find((answer) => answer.quizQuestion?.id === question.id);

            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE:
                    this.selectedAnswerOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer)?.selectedOptions || [];
                    break;
                case QuizQuestionType.DRAG_AND_DROP:
                    this.dragAndDropMappings = (submittedAnswer as DragAndDropSubmittedAnswer)?.mappings || [];
                    break;
                case QuizQuestionType.SHORT_ANSWER:
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
            const submittedAnswer = this.submission.submittedAnswers?.find((answer) => answer.quizQuestion?.id === this.currentQuestion()?.id);
            if (submittedAnswer) {
                this.questionScores = roundValueSpecifiedByCourseSettings(submittedAnswer.scoreInPoints, this.course());
            }
        }
    }

    /**
     * navigates to the course practice page
     */
    navigateToPractice(): void {
        this.router.navigate(['courses', this.courseId(), 'training']);
    }
}
