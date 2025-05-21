import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription, combineLatest, of } from 'rxjs';
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
    styleUrl: './course-practice-quiz.component.scss',
})
export class CoursePracticeQuizComponent implements OnInit, OnDestroy {
    @Input() questions: QuizQuestion[] = [];

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private quizService = inject(CoursePracticeQuizService);
    private subscription: Subscription;
    private quizParticipationService = inject(QuizParticipationService);
    private alertService = inject(AlertService);

    courseId: number;
    currentIndex = 0;
    currentQuestion: QuizQuestion;
    submission = new QuizSubmission();
    isSubmitting = false;
    result: Result;
    showingResult = false;
    userScore: number;
    quizId = 18;
    questionScores: { [id: number]: number } = {};
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    ngOnInit(): void {
        this.subscription = combineLatest([this.route.parent?.params ?? of({ courseId: undefined, course: undefined })]).subscribe(([params]) => {
            this.courseId = params['courseId'];
            this.loadQuestions(this.courseId);
        });
    }

    ngOnDestroy(): void {
        if (this.subscription) {
            this.subscription.unsubscribe();
        }
    }

    /**
     * increments the current question index or navigates to the course practice page if the last question is reached
     */
    nextQuestion(): void {
        if (this.isLastQuestion) {
            this.navigateToPractice();
        } else {
            this.currentIndex++;
            this.currentQuestion = this.questions[this.currentIndex];
            this.initQuestion(this.currentQuestion);
        }
    }

    /**
     * checks if the current question is the last question
     */
    get isLastQuestion(): boolean {
        if (this.questions.length === 0) {
            return true;
        }
        return this.currentIndex === this.questions.length - 1;
    }

    /**
     * loads the quiz questions for the given course
     * @param courseId
     */
    loadQuestions(courseId: number): void {
        this.quizService.getQuizQuestions(courseId).subscribe((questions) => {
            this.startQuiz(questions);
        });
    }

    /**
     * Initializes the quiz with the given questions
     * @param questions
     */
    startQuiz(questions: QuizQuestion[]): void {
        this.questions = questions;
        this.currentIndex = 0;
        this.currentQuestion = this.questions[this.currentIndex];
        this.initQuestion(this.currentQuestion);
    }

    /**
     * loads the quiz question
     * @param question
     */
    initQuestion(question: QuizQuestion): void {
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
        this.showingResult = false;
        this.submission = new QuizSubmission();
        if (question) {
            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE:
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions.set(question.id!, []);
                    break;
                case QuizQuestionType.DRAG_AND_DROP:
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings.set(question.id!, []);
                    break;
                case QuizQuestionType.SHORT_ANSWER:
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts.set(question.id!, []);
                    break;
            }
        }
    }

    applySelection() {
        this.submission.submittedAnswers = [];
        const questionId = this.currentQuestion.id!;
        const question = this.questions.find((q) => q.id === questionId);

        if (!question) {
            return;
        }

        switch (question.type) {
            case QuizQuestionType.MULTIPLE_CHOICE: {
                const answerOptions = this.selectedAnswerOptions.get(questionId) || [];
                const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
                mcSubmittedAnswer.quizQuestion = question;
                mcSubmittedAnswer.selectedOptions = answerOptions;
                this.submission.submittedAnswers!.push(mcSubmittedAnswer);
                break;
            }
            case QuizQuestionType.DRAG_AND_DROP: {
                const mappings = this.dragAndDropMappings.get(questionId) || [];
                const ddSubmittedAnswer = new DragAndDropSubmittedAnswer();
                ddSubmittedAnswer.quizQuestion = question;
                ddSubmittedAnswer.mappings = mappings;
                this.submission.submittedAnswers!.push(ddSubmittedAnswer);
                break;
            }
            case QuizQuestionType.SHORT_ANSWER: {
                const submittedTexts = this.shortAnswerSubmittedTexts.get(questionId) || [];
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
        this.quizParticipationService.submitForPractice(this.submission, this.currentQuestion.exerciseId!).subscribe({
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
        // create dictionaries (key: questionID, value: Array of selected answerOptions / mappings)
        // for the submittedAnswers to hand the selected options / mappings in individual arrays to the question components
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.questions) {
            // iterate through all questions of this quiz
            this.questions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.submission.submittedAnswers?.find((answer) => {
                    return answer.quizQuestion!.id === question.id;
                });

                switch (question.type) {
                    case QuizQuestionType.MULTIPLE_CHOICE:
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.selectedAnswerOptions.set(question.id!, (submittedAnswer as MultipleChoiceSubmittedAnswer)?.selectedOptions || []);
                        break;
                    case QuizQuestionType.DRAG_AND_DROP:
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.dragAndDropMappings.set(question.id!, (submittedAnswer as DragAndDropSubmittedAnswer)?.mappings || []);
                        break;
                    case QuizQuestionType.SHORT_ANSWER:
                        // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.shortAnswerSubmittedTexts.set(question.id!, (submittedAnswer as ShortAnswerSubmittedAnswer)?.submittedTexts || []);
                        break;
                }
            }, this);
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
        this.router.navigate(['courses', this.courseId, 'practice']);
    }
}
