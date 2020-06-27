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
import { cloneDeep, isEmpty } from 'lodash';

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
                        // needs to be cloned, because of two way binding, otherwise -> instant update in submission
                        this.selectedAnswerOptions[question.id] = selectedOptions ? cloneDeep(selectedOptions) : [];
                    } else {
                        // not found, set to empty array
                        this.selectedAnswerOptions[question.id] = [];
                    }
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const mappings = (submittedAnswer as DragAndDropSubmittedAnswer).mappings;
                        // needs to be cloned, because of two way binding, otherwise -> instant update in submission
                        this.dragAndDropMappings[question.id] = mappings ? cloneDeep(mappings) : [];
                    } else {
                        // not found, set to empty array
                        this.dragAndDropMappings[question.id] = [];
                    }
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const submittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts;
                        // needs to be cloned, because of two way binding, otherwise -> instant update in submission
                        this.shortAnswerSubmittedTexts[question.id] = submittedTexts ? cloneDeep(submittedTexts) : [];
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
            // need to check the answers, because they are initialized with empty array in updateViewFromSubmission
            return (
                Object.values(this.selectedAnswerOptions).some((mcAnswer) => mcAnswer.length > 0) ||
                Object.values(this.dragAndDropMappings).some((dndAnswer) => dndAnswer.length > 0) ||
                Object.values(this.shortAnswerSubmittedTexts).some((shortAnswer) => shortAnswer.length > 0)
            );
        } else {
            // be aware of !
            return !this.studentSubmission.submittedAnswers.every((lastSubmittedAnswer) => {
                // checks if there are no changes for each question type
                if (lastSubmittedAnswer.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    const lastSubmittedSelectedOptions = (lastSubmittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions
                        ? (lastSubmittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions
                        : [];
                    const changedOptions: AnswerOption[] = this.selectedAnswerOptions[lastSubmittedAnswer.quizQuestion.id];
                    // check if they have the same length and every selectedOption can be found in the lastSubmittedAnswer
                    return (
                        lastSubmittedSelectedOptions.length === changedOptions.length &&
                        changedOptions.every((changedOption) => lastSubmittedSelectedOptions.findIndex((lastSubmittedOptions) => lastSubmittedOptions.id === changedOption.id) >= 0)
                    );
                } else if (lastSubmittedAnswer.type === QuizQuestionType.DRAG_AND_DROP) {
                    const lastSubmittedDnDMapping = (lastSubmittedAnswer as DragAndDropSubmittedAnswer).mappings
                        ? (lastSubmittedAnswer as DragAndDropSubmittedAnswer).mappings
                        : [];
                    const changedMappings: DragAndDropMapping[] = this.dragAndDropMappings[lastSubmittedAnswer.quizQuestion.id];
                    // check if they have the same length and every dragAndDrop can be found in the lastSubmittedAnswer
                    return (
                        lastSubmittedDnDMapping.length === changedMappings.length &&
                        changedMappings.every((changedMapping) => lastSubmittedDnDMapping.findIndex((lastSubmittedMapping) => lastSubmittedMapping.id === changedMapping.id) >= 0)
                    );
                } else if (lastSubmittedAnswer.type === QuizQuestionType.SHORT_ANSWER) {
                    const lastSubmittedSATexts = (lastSubmittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts
                        ? (lastSubmittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts
                        : [];
                    const changedTexts: ShortAnswerSubmittedText[] = this.shortAnswerSubmittedTexts[lastSubmittedAnswer.quizQuestion.id];
                    // check if they have the same length and every submittedText can be found in the lastSubmittedAnswer
                    return (
                        lastSubmittedSATexts.length === changedTexts.length &&
                        changedTexts.every((changedText) => lastSubmittedSATexts.findIndex((lastSubmittedText) => lastSubmittedText.id === changedText.id) >= 0)
                    );
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
            shortAnswerSubmittedAnswer.submittedTexts = this.shortAnswerSubmittedTexts[questionID].filter(
                (shortAnswerText: ShortAnswerSubmittedText) => shortAnswerText && !isEmpty(shortAnswerText.text),
            );
            this.studentSubmission.submittedAnswers.push(shortAnswerSubmittedAnswer);
        }, this);
        this.studentSubmission.isSynced = false;
    }
}
