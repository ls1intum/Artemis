import { QuizAnswerSelection } from 'app/quiz/shared/util/quiz-answer-selection';
import { Component, OnInit, inject, input, output, viewChildren } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizConfiguration } from 'app/quiz/shared/entities/quiz-configuration.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import type { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { ExamExerciseHeaderComponent } from 'app/exam/overview/exercises/exam-exercise-header/exam-exercise-header.component';
import { ExerciseSaveButtonComponent } from '../exercise-save-button/exercise-save-button.component';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { captureException } from '@sentry/angular';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { addTemporaryHighlightToQuestion } from 'app/quiz/shared/questions/quiz-stepwizard.util';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { parseJson } from 'app/foundation/util/json.util';

@Component({
    selector: 'jhi-quiz-submission-exam',
    templateUrl: './quiz-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: QuizExamSubmissionComponent }],
    styleUrls: ['./quiz-exam-submission.component.scss'],
    imports: [
        ExamExerciseHeaderComponent,
        ExerciseSaveButtonComponent,
        TumAetUiTooltipDirective,
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        ShortAnswerQuestionComponent,
        ArtemisTranslatePipe,
    ],
})
export class QuizExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    private quizService = inject(ArtemisQuizService);
    private examParticipationService = inject(ExamParticipationService);

    exerciseType = ExerciseType.QUIZ;

    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;

    mcQuestionComponents = viewChildren(MultipleChoiceQuestionComponent);

    dndQuestionComponents = viewChildren(DragAndDropQuestionComponent);

    shortAnswerQuestionComponents = viewChildren(ShortAnswerQuestionComponent);

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    studentSubmission = input.required<QuizSubmission>();
    exercise = input<QuizExercise>();
    override examTimeline = input(false);
    quizConfiguration = input.required<QuizConfiguration>();

    saveCurrentExercise = output<void>();

    private readonly answerSelection = new QuizAnswerSelection();
    readonly selectedAnswerOptions = this.answerSelection.selectedAnswerOptions;
    readonly dragAndDropMappings = this.answerSelection.dragAndDropMappings;
    readonly shortAnswerSubmittedTexts = this.answerSelection.shortAnswerSubmittedTexts;
    readonly onSelectedAnswerOptionsChange = this.answerSelection.onSelectedAnswerOptionsChange;
    readonly onDragAndDropMappingsChange = this.answerSelection.onDragAndDropMappingsChange;
    readonly onShortAnswerSubmittedTextsChange = this.answerSelection.onShortAnswerSubmittedTextsChange;

    ngOnInit(): void {
        this.initQuiz();
        this.updateViewFromSubmission();
    }

    getSubmission(): Submission {
        return this.studentSubmission();
    }

    getExerciseId(): number | undefined {
        return this.quizConfiguration().id;
    }

    getExercise(): Exercise {
        return this.quizConfiguration() as Exercise;
    }

    /**
     * Initialize the selections / mappings for each question with an empty array
     */
    initQuiz() {
        // randomize order
        // in the exam timeline, we do not want to randomize the order as this makes it difficult to view the changes between submissions.
        if (!this.examTimeline()) {
            this.quizService.randomizeOrder(this.quizConfiguration().quizQuestions, this.quizConfiguration().randomizeQuestionOrder);
        }
        this.answerSelection.reset(this.quizConfiguration().quizQuestions);
    }

    private highlightQuizQuestion(questionId: number): void {
        const quizQuestions = this.quizConfiguration().quizQuestions;
        if (!quizQuestions) {
            return;
        }

        const questionToBeHighlighted: QuizQuestion | undefined = quizQuestions.find((question) => question.id === questionId);
        if (!questionToBeHighlighted) {
            return;
        }

        addTemporaryHighlightToQuestion(questionToBeHighlighted);
    }

    /**
     * TODO This is duplicated with {@link QuizParticipationComponent#navigateToQuestion}, extract to a shared component
     *
     * By clicking on the bubble of the progress navigation towards the corresponding question of the quiz is triggered
     * @param questionId
     */
    navigateToQuestion(questionId: number): void {
        const element = document.getElementById('question' + questionId);
        if (!element) {
            captureException('navigateToQuestion: element not found for questionId ' + questionId);
            return;
        }
        element.scrollIntoView({
            behavior: 'smooth',
            block: 'nearest',
            inline: 'start',
        });

        this.highlightQuizQuestion(questionId);
    }

    /**
     * applies the data from the model to the UI (reverse of updateSubmissionFromView):
     *
     * Sets the checkmarks (selected answers) for all questions according to the submission data
     * this needs to be done when we get new submission data, e.g. through the websocket connection
     */
    updateViewFromSubmission() {
        this.answerSelection.reset(this.quizConfiguration().quizQuestions, this.studentSubmission()?.submittedAnswers, true);
    }

    /**
     * Callback method to be triggered when the user changes any of the answers in the quiz (in sub components based on the question type)
     */
    onSelectionChanged() {
        this.studentSubmission().isSynced = false;
        // isSynced is mutated in place; notify sync-state-dependent UI (e.g. the save button) to re-evaluate reactively.
        this.examParticipationService.notifySubmissionSyncStateChanged();
    }

    // Bound once and handed to the question components by reference; see the identical callback in
    // QuizParticipationComponent. `onSelectionChanged.bind(this)` in the template minted a new function on every
    // change-detection pass, changing every question component's input on every pass.
    readonly selectionChangedCallback = () => this.onSelectionChanged();

    /**
     * return true if the user changed any answer in the quiz
     */
    hasUnsavedChanges(): boolean {
        return !this.studentSubmission().isSynced!;
    }

    /**
     * updates the model according to UI state (reverse of updateViewFromSubmission):
     *
     * Creates the submission from the user's selection
     * this needs to be done when we want to send the submission
     * either for saving (through websocket)
     * or for submitting (through REST call)
     */
    updateSubmissionFromView(): void {
        // convert the selection dictionary (key: questionID, value: Array of selected answerOptions / mappings)
        // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
        this.studentSubmission().submittedAnswers = [];

        // for multiple-choice questions
        this.selectedAnswerOptions().forEach((answerOptions, questionID) => {
            // find the question object for the given question id
            const question = this.quizConfiguration().quizQuestions?.find(function (selectedQuestion) {
                return selectedQuestion.id === Number(questionID);
            });
            if (!question) {
                captureException('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            mcSubmittedAnswer.quizQuestion = question;
            mcSubmittedAnswer.selectedOptions = answerOptions;
            this.studentSubmission().submittedAnswers!.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        this.dragAndDropMappings().forEach((mappings, questionID) => {
            // find the question object for the given question id
            const question = this.quizConfiguration().quizQuestions?.find(function (localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                captureException('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
            dndSubmittedAnswer.quizQuestion = question;
            dndSubmittedAnswer.mappings = mappings;
            this.studentSubmission().submittedAnswers!.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        this.shortAnswerSubmittedTexts().forEach((submittedTexts, questionID) => {
            // find the question object for the given question id
            const question = this.quizConfiguration().quizQuestions?.find(function (localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                captureException('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
            shortAnswerSubmittedAnswer.quizQuestion = question;
            shortAnswerSubmittedAnswer.submittedTexts = submittedTexts;
            this.studentSubmission().submittedAnswers!.push(shortAnswerSubmittedAnswer);
        }, this);
    }

    updateViewFromSubmissionVersion(): void {
        this.studentSubmission().submittedAnswers = parseJson<SubmittedAnswer[]>(this.submissionVersion.content);
        this.updateViewFromSubmission();
    }

    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        this.submissionVersion = submissionVersion;
        this.updateViewFromSubmissionVersion();
    }

    /**
     * Trigger save action in exam participation component
     */
    notifyTriggerSave() {
        this.saveCurrentExercise.emit();
    }
}
