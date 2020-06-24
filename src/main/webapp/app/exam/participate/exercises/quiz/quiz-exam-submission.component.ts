import { Component, QueryList, ViewChildren, Input, OnInit } from '@angular/core';
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

    constructor() {
        super();
        smoothscroll.polyfill();
    }

    ngOnInit(): void {
        this.initQuiz();
        // show submission answers in UI
        this.updateViewFromSubmission();
    }

    /**
     * Initialize the selections / mappings for each question with an empty array
     */
    initQuiz() {
        // prepare selection arrays for each question
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.exercise.quizQuestions) {
            this.exercise.quizQuestions.forEach((question) => {
                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.selectedAnswerOptions[question.id] = [];
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.dragAndDropMappings[question.id] = [];
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    this.shortAnswerSubmittedTexts[question.id] = [];
                } else {
                    console.error('Unknown question type: ' + question);
                }
            }, this);
        }
    }

    /**
     * By clicking on the bubble of the progress navigation towards the corresponding question of the quiz is triggered
     * @param questionIndex
     */
    navigateToQuestion(questionIndex: number): void {
        document.getElementById('question' + questionIndex)!.scrollIntoView({
            behavior: 'smooth',
        });
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
                const submittedAnswer = this.studentSubmission.submittedAnswers
                    ? this.studentSubmission.submittedAnswers.find((answer) => {
                          return answer.quizQuestion.id === question.id;
                      })
                    : null;

                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const selectedOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions;
                        this.selectedAnswerOptions[question.id] = selectedOptions ? selectedOptions : [];
                    } else {
                        // not found, set to empty array
                        this.selectedAnswerOptions[question.id] = [];
                    }
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const mappings = (submittedAnswer as DragAndDropSubmittedAnswer).mappings;
                        this.dragAndDropMappings[question.id] = mappings ? mappings : [];
                    } else {
                        // not found, set to empty array
                        this.dragAndDropMappings[question.id] = [];
                    }
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const submittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts;
                        this.shortAnswerSubmittedTexts[question.id] = submittedTexts ? submittedTexts : [];
                    } else {
                        // not found, set to empty array
                        this.shortAnswerSubmittedTexts[question.id] = [];
                    }
                } else {
                    console.error('Unknown question type: ' + question);
                }
            }, this);
        }
    }

    /**
     * return true if we have no submission yet and we have new answers
     * returns false if all submittedAnswers in studentSubmission are similar to the ones we have in the respective maps in this components
     */
    hasUnsavedChanges(): boolean {
        if (!this.studentSubmission.submittedAnswers) {
            // subcomponents are not using a real map, thats why we need this workaround with Object.keys TODO: fix that at some point
            return Object.keys(this.selectedAnswerOptions).length > 0 || Object.keys(this.dragAndDropMappings).length > 0 || Object.keys(this.shortAnswerSubmittedTexts).length > 0;
        } else {
            return !this.studentSubmission.submittedAnswers.every((answer) => {
                if (answer instanceof MultipleChoiceSubmittedAnswer) {
                    return this.selectedAnswerOptions[answer.quizQuestion.id] === answer;
                } else if (answer instanceof DragAndDropSubmittedAnswer) {
                    return this.dragAndDropMappings[answer.quizQuestion.id] === answer;
                } else if (answer instanceof ShortAnswerSubmittedAnswer) {
                    return this.shortAnswerSubmittedTexts[answer.quizQuestion.id] === answer;
                }
            });
        }
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
        Object.keys(this.selectedAnswerOptions).forEach((questionID) => {
            // find the question object for the given question id
            const question = this.exercise.quizQuestions.find(function (selectedQuestion) {
                return selectedQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            mcSubmittedAnswer.quizQuestion = question;
            mcSubmittedAnswer.selectedOptions = this.selectedAnswerOptions[questionID];
            this.studentSubmission.submittedAnswers.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        Object.keys(this.dragAndDropMappings).forEach((questionID) => {
            // find the question object for the given question id
            const question = this.exercise.quizQuestions.find(function (localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
            dndSubmittedAnswer.quizQuestion = question;
            dndSubmittedAnswer.mappings = this.dragAndDropMappings[questionID];
            this.studentSubmission.submittedAnswers.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        Object.keys(this.shortAnswerSubmittedTexts).forEach((questionID) => {
            // find the question object for the given question id
            const question = this.exercise.quizQuestions.find(function (localQuestion) {
                return localQuestion.id === Number(questionID);
            });
            if (!question) {
                console.error('question not found for ID: ' + questionID);
                return;
            }
            // generate the submittedAnswer object
            const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
            shortAnswerSubmittedAnswer.quizQuestion = question;
            shortAnswerSubmittedAnswer.submittedTexts = this.shortAnswerSubmittedTexts[questionID];
            this.studentSubmission.submittedAnswers.push(shortAnswerSubmittedAnswer);
        }, this);
        this.studentSubmission.isSynced = false;
    }
}
