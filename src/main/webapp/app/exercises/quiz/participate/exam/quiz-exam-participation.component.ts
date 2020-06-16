import { Component, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { MultipleChoiceQuestionComponent } from 'app/exercises/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/exercises/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/exercises/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { DeviceDetectorService } from 'ngx-device-detector';
import * as smoothscroll from 'smoothscroll-polyfill';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { ShortAnswerSubmittedText } from 'app/entities/quiz/short-answer-submitted-text.model';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { MultipleChoiceSubmittedAnswer } from 'app/entities/quiz/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/entities/quiz/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/entities/quiz/short-answer-submitted-answer.model';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-exam-quiz',
    templateUrl: './quiz-exam-participation.component.html',
    providers: [ParticipationService],
    styleUrls: ['../quiz-participation.component.scss'],
})
export class QuizExamParticipationComponent implements OnInit, OnDestroy {
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

    private subscription: Subscription;

    quizExercise: QuizExercise;
    quizId: number;
    courseId: number;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
    submission = new QuizSubmission();

    constructor(private deviceService: DeviceDetectorService, private route: ActivatedRoute, private participationService: ParticipationService) {
        smoothscroll.polyfill();
    }

    ngOnInit(): void {
        this.subscription = this.route.params.subscribe((params) => {
            this.quizId = Number(params['exerciseId']);
            this.courseId = Number(params['courseId']);
        });
        this.participationService.findParticipation(this.quizId).subscribe((response: HttpResponse<StudentParticipation>) => {
            console.log(response.body);
            this.quizExercise = <QuizExercise>response.body!.exercise;
            this.updateParticipationFromServer(response.body!);
        });
    }

    ngOnDestroy(): void {
        this.subscription.unsubscribe();
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

    onSelectionChanged() {
        this.applySelection();
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

        if (this.quizExercise.quizQuestions) {
            // iterate through all questions of this quiz
            this.quizExercise.quizQuestions.forEach((question) => {
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

    /**
     * updates the model according to UI state (reverse of applySubmission):
     *
     * Creates the submission from the user's selection
     * this needs to be done when we want to send the submission
     * either for saving (through websocket)
     * or for submitting (through REST call)
     */
    applySelection() {
        // convert the selection dictionary (key: questionID, value: Array of selected answerOptions / mappings)
        // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
        this.submission.submittedAnswers = [];

        // for multiple-choice questions
        Object.keys(this.selectedAnswerOptions).forEach((questionID) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions.find(function (selectedQuestion) {
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
            this.submission.submittedAnswers.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        Object.keys(this.dragAndDropMappings).forEach((questionID) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions.find(function (localQuestion) {
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
            this.submission.submittedAnswers.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        Object.keys(this.shortAnswerSubmittedTexts).forEach((questionID) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions.find(function (localQuestion) {
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
            this.submission.submittedAnswers.push(shortAnswerSubmittedAnswer);
        }, this);
    }

    /**
     * Apply the data of the participation, replacing all old data
     */
    updateParticipationFromServer(participation: StudentParticipation) {
        // apply submission if it exists
        if (participation && participation.results.length) {
            this.submission = participation.results[0].submission as QuizSubmission;

            // show submission answers in UI
            this.applySubmission();
        } else {
            this.submission = new QuizSubmission();
        }
    }
}
