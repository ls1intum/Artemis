import { Component, Input, OnChanges, inject } from '@angular/core';
import dayjs from 'dayjs/esm';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { Exam } from 'app/entities/exam/exam.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Result } from 'app/entities/result.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { QuizParticipation } from 'app/entities/quiz/quiz-participation.model';

@Component({
    selector: 'jhi-quiz-exam-summary',
    templateUrl: './quiz-exam-summary.component.html',
})
export class QuizExamSummaryComponent implements OnChanges {
    private exerciseService = inject(QuizExerciseService);
    private serverDateService = inject(ArtemisServerDateService);

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

    @Input()
    quizParticipation: QuizParticipation;

    @Input()
    submission: QuizSubmission;

    @Input()
    resultsPublished: boolean;

    @Input()
    exam: Exam;

    result?: Result;

    ngOnChanges(): void {
        this.updateViewFromSubmission();
        if (this.quizParticipation.studentParticipations) {
            this.result =
                this.quizParticipation.studentParticipations.length > 0 && this.quizParticipation.studentParticipations[0].results?.length
                    ? this.quizParticipation.studentParticipations[0].results[0]
                    : undefined;
        } else {
            this.result = this.submission.results?.length ? this.submission.results[0] : undefined;
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

        if (this.quizParticipation.quizQuestions && this.submission) {
            // iterate through all questions of this quiz
            this.quizParticipation.quizQuestions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.submission.submittedAnswers
                    ? this.submission.submittedAnswers.find((answer) => {
                          return answer.quizQuestion!.id === question.id;
                      })
                    : undefined;

                if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                    // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const selectedOptions = (submittedAnswer as MultipleChoiceSubmittedAnswer).selectedOptions;
                        this.selectedAnswerOptions.set(question.id!, selectedOptions ? selectedOptions : []);
                    } else {
                        // not found, set to empty array
                        this.selectedAnswerOptions.set(question.id!, []);
                    }
                } else if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                    // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const mappings = (submittedAnswer as DragAndDropSubmittedAnswer).mappings;
                        this.dragAndDropMappings.set(question.id!, mappings ? mappings : []);
                    } else {
                        // not found, set to empty array
                        this.dragAndDropMappings.set(question.id!, []);
                    }
                } else if (question.type === QuizQuestionType.SHORT_ANSWER) {
                    // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                    if (submittedAnswer) {
                        const submittedTexts = (submittedAnswer as ShortAnswerSubmittedAnswer).submittedTexts;
                        this.shortAnswerSubmittedTexts.set(question.id!, submittedTexts ? submittedTexts : []);
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

    getScoreForQuizQuestion(quizQuestionId?: number) {
        if (this.submission && this.submission.submittedAnswers && this.submission.submittedAnswers.length > 0) {
            const submittedAnswer = this.submission.submittedAnswers.find((answer) => {
                return answer && answer.quizQuestion ? answer.quizQuestion.id === quizQuestionId : false;
            });
            if (submittedAnswer && submittedAnswer.scoreInPoints !== undefined) {
                return roundValueSpecifiedByCourseSettings(submittedAnswer.scoreInPoints, this.exam.course);
            }
        }
        return 0;
    }

    /**
     * We only show the notice when there is a publishResultsDate that has already passed by now and the result is missing
     */
    get showMissingResultsNotice(): boolean {
        if (this.exam && this.exam.publishResultsDate && this.quizParticipation.studentParticipations && this.quizParticipation.studentParticipations.length > 0) {
            return dayjs(this.exam.publishResultsDate).isBefore(this.serverDateService.now()) && !this.result;
        }
        return false;
    }
}
