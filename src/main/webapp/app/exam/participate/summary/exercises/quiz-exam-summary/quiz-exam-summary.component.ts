import * as moment from 'moment';
import { Component, Input, OnInit } from '@angular/core';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-quiz-exam-summary',
    templateUrl: './quiz-exam-summary.component.html',
    styles: [],
})
export class QuizExamSummaryComponent implements OnInit {
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
    exerciseWithSolution: QuizExercise;
    showMissingResultsNotice = false;

    @Input()
    exercise: QuizExercise;

    @Input()
    submission: QuizSubmission;

    @Input()
    resultsPublished: boolean;

    @Input()
    exam: Exam;

    constructor(private exerciseService: QuizExerciseService) {}

    ngOnInit(): void {
        this.updateViewFromSubmission();
        // get quiz-exercise with solution after result is published
        if (this.resultsPublished) {
            this.exerciseService.find(this.exercise.id).subscribe((response) => {
                this.exerciseWithSolution = response.body!;
            });
        }
        // we only show the notice when there is a publishResultsDate that has already passed by now and the result is missing
        this.showMissingResultsNotice =
            this.exam != null && this.exam.publishResultsDate != null && this.exam.publishResultsDate.isBefore(moment()) && !this.exercise.studentParticipations[0].results[0];
    }

    get quizQuestions() {
        return this.resultsPublished && this.exerciseWithSolution ? this.exerciseWithSolution.quizQuestions : this.exercise.quizQuestions;
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

        if (this.exercise.quizQuestions && this.submission) {
            // iterate through all questions of this quiz
            this.exercise.quizQuestions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.submission.submittedAnswers
                    ? this.submission.submittedAnswers.find((answer) => {
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

    getScoreForQuizQuestion(quizQuestionId: number) {
        const submittedAnswer = this.submission.submittedAnswers.find((answer) => answer.quizQuestion.id === quizQuestionId);
        return Math.round(submittedAnswer!.scoreInPoints * 100) / 100;
    }
}
