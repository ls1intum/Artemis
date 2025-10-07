import { Component, OnInit, inject, input, output, viewChildren } from '@angular/core';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AbstractQuizSubmission } from 'app/quiz/shared/entities/abstract-quiz-exam-submission.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { QuizConfiguration } from 'app/quiz/shared/entities/quiz-configuration.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { cloneDeep } from 'lodash-es';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ExerciseSaveButtonComponent } from '../exercise-save-button/exercise-save-button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { captureException } from '@sentry/angular';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { addTemporaryHighlightToQuestion } from 'app/quiz/shared/questions/quiz-stepwizard.util';

@Component({
    selector: 'jhi-quiz-submission-exam',
    templateUrl: './quiz-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: QuizExamSubmissionComponent }],
    styleUrls: ['../../../../quiz/overview/participation/quiz-participation.component.scss', './quiz-exam-submission.component.scss'],
    imports: [
        TranslateDirective,
        IncludedInScoreBadgeComponent,
        ExerciseSaveButtonComponent,
        NgbTooltip,
        NgClass,
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        ShortAnswerQuestionComponent,
        ArtemisTranslatePipe,
    ],
})
export class QuizExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    private quizService = inject(ArtemisQuizService);

    exerciseType = ExerciseType.QUIZ;

    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    mcQuestionComponents = viewChildren(MultipleChoiceQuestionComponent);

    dndQuestionComponents = viewChildren(DragAndDropQuestionComponent);

    shortAnswerQuestionComponents = viewChildren(ShortAnswerQuestionComponent);

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    studentSubmission = input.required<AbstractQuizSubmission>();
    exercise = input<QuizExercise>();
    examTimeline = input(false);
    quizConfiguration = input.required<QuizConfiguration>();

    saveCurrentExercise = output<void>();

    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

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
        // prepare selection arrays for each question
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        const quizQuestions = this.quizConfiguration().quizQuestions;

        if (quizQuestions) {
            quizQuestions.forEach((question) => {
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
                    default:
                        captureException('Unknown question type: ' + question);
                        break;
                }
            }, this);
        }
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
        // create dictionaries (key: questionID, value: Array of selected answerOptions / mappings)
        // for the submittedAnswers to hand the selected options / mappings in individual arrays to the question components
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        const quizQuestions = this.quizConfiguration().quizQuestions;
        if (quizQuestions?.length) {
            // iterate through all questions of this quiz
            quizQuestions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.studentSubmission()?.submittedAnswers?.find((answer) => {
                    return answer.quizQuestion?.id === question.id;
                });

                switch (question.type) {
                    case QuizQuestionType.MULTIPLE_CHOICE:
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        if (submittedAnswer) {
                            const selectedOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions;
                            // needs to be cloned, because of two-way binding, otherwise -> instant update in submission
                            this.selectedAnswerOptions.set(question.id!, selectedOptions ? cloneDeep(selectedOptions) : []);
                        } else {
                            // not found, set to empty array
                            this.selectedAnswerOptions.set(question.id!, []);
                        }
                        break;
                    case QuizQuestionType.DRAG_AND_DROP:
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        if (submittedAnswer) {
                            const mappings = (submittedAnswer as DragAndDropSubmittedAnswer).mappings;
                            // needs to be cloned, because of two-way binding, otherwise -> instant update in submission
                            this.dragAndDropMappings.set(question.id!, mappings ? cloneDeep(mappings) : []);
                        } else {
                            // not found, set to empty array
                            this.dragAndDropMappings.set(question.id!, []);
                        }
                        break;
                    case QuizQuestionType.SHORT_ANSWER:
                        // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        if (submittedAnswer) {
                            const submittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts;
                            // needs to be cloned, because of two-way binding, otherwise -> instant update in submission
                            this.shortAnswerSubmittedTexts.set(question.id!, submittedTexts ? cloneDeep(submittedTexts) : []);
                        } else {
                            // not found, set to empty array
                            this.shortAnswerSubmittedTexts.set(question.id!, []);
                        }
                        break;
                    default:
                        captureException('Unknown question type: ' + question);
                        break;
                }
            }, this);
        }
    }

    /**
     * Callback method to be triggered when the user changes any of the answers in the quiz (in sub components based on the question type)
     */
    onSelectionChanged() {
        this.studentSubmission().isSynced = false;
    }

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
        this.selectedAnswerOptions.forEach((answerOptions, questionID) => {
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
        this.dragAndDropMappings.forEach((mappings, questionID) => {
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
        this.shortAnswerSubmittedTexts.forEach((submittedTexts, questionID) => {
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
        this.studentSubmission().submittedAnswers = JSON.parse(this.submissionVersion.content);
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
