import { ChangeDetectorRef, Component, Input, OnInit, QueryList, ViewChildren } from '@angular/core';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import * as smoothscroll from 'smoothscroll-polyfill';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { cloneDeep } from 'lodash-es';
import { ArtemisQuizService } from 'app/shared/quiz/quiz.service';
import { Submission } from 'app/entities/submission.model';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-quiz-submission-exam',
    templateUrl: './quiz-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: QuizExamSubmissionComponent }],
    styleUrls: ['./quiz-exam-submission.component.scss'],
})
export class QuizExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @ViewChildren(MultipleChoiceQuestionComponent)
    mcQuestionComponents: QueryList<MultipleChoiceQuestionComponent>;

    @ViewChildren(DragAndDropQuestionComponent)
    dndQuestionComponents: QueryList<DragAndDropQuestionComponent>;

    @ViewChildren(ShortAnswerQuestionComponent)
    shortAnswerQuestionComponents: QueryList<ShortAnswerQuestionComponent>;

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: QuizSubmission;

    @Input() exercise: QuizExercise;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    constructor(private quizService: ArtemisQuizService, changeDetectorReference: ChangeDetectorRef) {
        super(changeDetectorReference);
        smoothscroll.polyfill();
    }

    ngOnInit(): void {
        this.initQuiz();
        this.updateViewFromSubmission();
    }

    getSubmission(): Submission {
        return this.studentSubmission;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    /**
     * Initialize the selections / mappings for each question with an empty array
     */
    initQuiz() {
        // randomize order
        this.quizService.randomizeOrder(this.exercise);
        // prepare selection arrays for each question
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.exercise.quizQuestions) {
            this.exercise.quizQuestions.forEach((question) => {
                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions.set(question.id!, []);
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings.set(question.id!, []);
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts.set(question.id!, []);
                } else {
                    console.error('Unknown question type: ' + question);
                }
            }, this);
        }
    }

    /**
     * By clicking on the bubble of the progress navigation towards the corresponding question of the quiz is triggered
     * @param questionId
     */
    navigateToQuestion(questionId: number): void {
        let yOffset = 0;
        const examNavigationBar = document.getElementById('exam-navigation-bar');
        if (examNavigationBar) {
            yOffset = examNavigationBar.clientHeight;
        }
        // get html element for question
        const element = document.getElementById('question' + questionId);
        if (element) {
            // scroll to correct y
            const y = element.getBoundingClientRect().top + window.scrollY - yOffset;
            window.scrollTo({ top: y, behavior: 'smooth' });
        }
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

        if (this.exercise.quizQuestions) {
            // iterate through all questions of this quiz
            this.exercise.quizQuestions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.studentSubmission?.submittedAnswers?.find((answer) => {
                    return answer.quizQuestion?.id === question.id;
                });

                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const selectedOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions;
                        // needs to be cloned, because of two-way binding, otherwise -> instant update in submission
                        this.selectedAnswerOptions.set(question.id!, selectedOptions ? cloneDeep(selectedOptions) : []);
                    } else {
                        // not found, set to empty array
                        this.selectedAnswerOptions.set(question.id!, []);
                    }
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const mappings = (submittedAnswer as DragAndDropSubmittedAnswer).mappings;
                        // needs to be cloned, because of two-way binding, otherwise -> instant update in submission
                        this.dragAndDropMappings.set(question.id!, mappings ? cloneDeep(mappings) : []);
                    } else {
                        // not found, set to empty array
                        this.dragAndDropMappings.set(question.id!, []);
                    }
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const submittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts;
                        // needs to be cloned, because of two-way binding, otherwise -> instant update in submission
                        this.shortAnswerSubmittedTexts.set(question.id!, submittedTexts ? cloneDeep(submittedTexts) : []);
                    } else {
                        // not found, set to empty array
                        this.shortAnswerSubmittedTexts.set(question.id!, []);
                    }
                } else {
                    console.error('Unknown question type: ' + question);
                }
            }, this);
        }
    }

    /**
     * Callback method to be triggered when the user changes any of the answers in the quiz (in sub components based on the question type)
     */
    onSelectionChanged() {
        this.studentSubmission.isSynced = false;
    }

    /**
     * return true if the user changed any answer in the quiz
     */
    hasUnsavedChanges(): boolean {
        return !this.studentSubmission.isSynced!;
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
        this.studentSubmission.submittedAnswers = [];

        // for multiple-choice questions
        this.selectedAnswerOptions.forEach((answerOptions, questionID) => {
            // find the question object for the given question id
            const question = this.exercise?.quizQuestions?.find(function (selectedQuestion) {
                return selectedQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            mcSubmittedAnswer.quizQuestion = question;
            mcSubmittedAnswer.selectedOptions = answerOptions;
            this.studentSubmission.submittedAnswers!.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        this.dragAndDropMappings.forEach((mappings, questionID) => {
            // find the question object for the given question id
            const question = this.exercise.quizQuestions?.find(function (localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
            dndSubmittedAnswer.quizQuestion = question;
            dndSubmittedAnswer.mappings = mappings;
            this.studentSubmission.submittedAnswers!.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        this.shortAnswerSubmittedTexts.forEach((submittedTexts, questionID) => {
            // find the question object for the given question id
            const question = this.exercise.quizQuestions?.find(function (localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
            shortAnswerSubmittedAnswer.quizQuestion = question;
            shortAnswerSubmittedAnswer.submittedTexts = submittedTexts;
            this.studentSubmission.submittedAnswers!.push(shortAnswerSubmittedAnswer);
        }, this);
    }
}
