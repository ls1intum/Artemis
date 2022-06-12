import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { QuizReEvaluateService } from './quiz-re-evaluate.service';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { EventManager } from 'app/core/util/event-manager.service';
import { faBan, faCheck, faCheckCircle, faSpinner, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-quiz-re-evaluate-warning',
    templateUrl: './quiz-re-evaluate-warning.component.html',
    styleUrls: ['../../shared/quiz.scss'],
})
export class QuizReEvaluateWarningComponent implements OnInit {
    isSaving: boolean;

    successful = false;
    failed = false;
    busy = false;

    questionElementDeleted = false;
    questionElementInvalid = false;
    questionCorrectness = false;
    questionDeleted = false;
    questionInvalid = false;
    scoringChanged = false;
    solutionAdded = false;

    quizExercise: QuizExercise;
    backUpQuiz: QuizExercise;

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faTimes = faTimes;
    faCheck = faCheck;
    faCheckCircle = faCheckCircle;

    constructor(
        public activeModal: NgbActiveModal,
        private eventManager: EventManager,
        private quizExerciseService: QuizExerciseService,
        private quizReEvaluateService: QuizReEvaluateService,
    ) {}

    /**
     * Reset saving status, load the quiz by id and back it up.
     */
    ngOnInit(): void {
        this.isSaving = false;
        this.quizExerciseService.find(this.quizExercise.id!).subscribe((res) => {
            this.backUpQuiz = res.body!;
            this.loadQuizSuccess();
        });
    }

    /**
     * Closes the modal
     */
    clear(): void {
        this.activeModal.dismiss('cancel');
    }

    /**
     * check if the changes affect the existing results
     *  1. check if a question is deleted
     *  2. check for each question if:
     *          - it is set invalid
     *          - it has another scoringType
     *          - an answer was deleted
     *  3. check for each question-element if:
     *          - it is set invalid
     *          - the correctness was changed
     */
    loadQuizSuccess(): void {
        // question deleted?
        this.questionDeleted = this.backUpQuiz.quizQuestions!.length !== this.quizExercise.quizQuestions!.length;

        // check each question
        this.quizExercise.quizQuestions!.forEach((question) => {
            // find same question in backUp (necessary if the order has been changed)
            const backUpQuestion = this.backUpQuiz.quizQuestions?.find((questionBackUp) => question.id === questionBackUp.id)!;

            this.checkQuestion(question, backUpQuestion);
        });
    }

    /**
     * 1. compare backUpQuestion and question
     * 2. set flags based on detected changes
     *
     * @param question changed question
     * @param backUpQuestion original not changed question
     */
    checkQuestion(question: QuizQuestion, backUpQuestion: QuizQuestion): void {
        if (backUpQuestion) {
            // question set invalid?
            if (question.invalid !== backUpQuestion.invalid) {
                this.questionInvalid = true;
            }
            // question scoring changed?
            if (question.scoringType !== backUpQuestion.scoringType) {
                this.scoringChanged = true;
            }
            // check MultipleChoiceQuestions
            if (question.type === QuizQuestionType.MULTIPLE_CHOICE) {
                this.checkMultipleChoiceQuestion(question as MultipleChoiceQuestion, backUpQuestion as MultipleChoiceQuestion);
            }
            // check DragAndDropQuestions
            if (question.type === QuizQuestionType.DRAG_AND_DROP) {
                this.checkDragAndDropQuestion(question as DragAndDropQuestion, backUpQuestion as DragAndDropQuestion);
            }
            // check ShortAnswerQuestions
            if (question.type === QuizQuestionType.SHORT_ANSWER) {
                this.checkShortAnswerQuestion(question as ShortAnswerQuestion, backUpQuestion as ShortAnswerQuestion);
            }
        }
    }

    /**
     * 1. check MultipleChoiceQuestion-Elements
     * 2. set flags based on detected changes
     *
     * @param question changed Multiple-Choice-Question
     * @param backUpQuestion original not changed Multiple-Choice-Question
     */
    checkMultipleChoiceQuestion(question: MultipleChoiceQuestion, backUpQuestion: MultipleChoiceQuestion): void {
        // question-Element deleted?
        if (question.answerOptions!.length !== backUpQuestion.answerOptions!.length) {
            this.questionElementDeleted = true;
        }
        // check each answer
        question.answerOptions!.forEach((answer) => {
            // only check if there are no changes on the question-elements yet
            if (!this.questionCorrectness || !this.questionElementInvalid) {
                const backUpAnswer = backUpQuestion.answerOptions!.find((answerBackUp) => answerBackUp.id === answer.id);
                if (backUpAnswer) {
                    // answer set invalid?
                    if (answer.invalid !== backUpAnswer.invalid) {
                        this.questionElementInvalid = true;
                    }
                    // answer correctness changed?
                    if (answer.isCorrect !== backUpAnswer.isCorrect) {
                        this.questionCorrectness = true;
                    }
                }
            }
        });
    }

    /**
     * 1. check DragAndDrop-Question-Elements
     * 2. set flags based on detected changes
     *
     * @param question changed DragAndDrop-Question
     * @param backUpQuestion original not changed DragAndDrop-Question
     */
    checkDragAndDropQuestion(question: DragAndDropQuestion, backUpQuestion: DragAndDropQuestion): void {
        // check if a dropLocation or dragItem was deleted
        if (question.dragItems!.length !== backUpQuestion.dragItems!.length || question.dropLocations!.length !== backUpQuestion.dropLocations!.length) {
            this.questionElementDeleted = true;
        }
        // check if the correct Mappings has changed
        if (JSON.stringify(question.correctMappings).toLowerCase() !== JSON.stringify(backUpQuestion.correctMappings).toLowerCase()) {
            this.questionCorrectness = true;
        }
        // only check if there are no changes on the question-elements yet
        if (!this.questionElementInvalid) {
            // check each dragItem
            question.dragItems!.forEach((dragItem) => {
                const backUpDragItem = backUpQuestion.dragItems?.find((dragItemBackUp) => dragItemBackUp.id === dragItem.id);
                // dragItem set invalid?
                if (backUpDragItem && dragItem.invalid !== backUpDragItem.invalid) {
                    this.questionElementInvalid = true;
                }
            });
            // check each dropLocation
            question.dropLocations!.forEach((dropLocation) => {
                const backUpDropLocation = backUpQuestion.dropLocations?.find((dropLocationBackUp) => dropLocationBackUp.id === dropLocation.id);
                // dropLocation set invalid?
                if (backUpDropLocation && dropLocation.invalid !== backUpDropLocation.invalid) {
                    this.questionElementInvalid = true;
                }
            });
        }
    }

    /**
     * 1. We check all ShortAnswer-Question-Elements in case a spot, solution or mapping has changed/was deleted
     * 2. Set flags based on detected changes to inform the instructor in the UI what his changes have for consequences.
     *
     * @param question
     * @param backUpQuestion
     */
    checkShortAnswerQuestion(question: ShortAnswerQuestion, backUpQuestion: ShortAnswerQuestion): void {
        // check if a spot or solution was deleted
        if (question.solutions!.length < backUpQuestion.solutions!.length || question.spots!.length < backUpQuestion.spots!.length) {
            this.questionElementDeleted = true;
        }
        // check if a spot or solution was added
        if (question.solutions!.length > backUpQuestion.solutions!.length || question.spots!.length > backUpQuestion.spots!.length) {
            this.solutionAdded = true;
        }

        // check if the correct Mappings has changed
        if (JSON.stringify(question.correctMappings).toLowerCase() !== JSON.stringify(backUpQuestion.correctMappings).toLowerCase()) {
            this.questionCorrectness = true;
        }
        // only check if there are no changes on the question-elements yet
        if (!this.questionElementInvalid) {
            // check each solution
            question.solutions!.forEach((solution) => {
                const backUpSolution = backUpQuestion.solutions?.find((solutionBackUp) => {
                    return solutionBackUp.id === solution.id;
                });
                // check if a solution was added
                if (this.solutionAdded && backUpSolution === undefined) {
                    return;
                }
                // solution set invalid?
                if (backUpSolution && solution.invalid !== backUpSolution.invalid) {
                    this.questionElementInvalid = true;
                }
            });
            // check each spot
            question.spots!.forEach((spot) => {
                const backUpSpot = backUpQuestion.spots?.find((spotBackUp) => {
                    return spotBackUp.id === spot.id;
                });
                // spot set invalid?
                if (backUpSpot && spot.invalid !== backUpSpot.invalid) {
                    this.questionElementInvalid = true;
                }
            });
        }
    }

    /**
     * Confirm changes
     *  => send changes to server and wait for result
     *  if saving failed -> show failed message
     */
    confirmChange(): void {
        this.busy = true;

        this.quizReEvaluateService.update(this.quizExercise).subscribe({
            next: () => {
                this.busy = false;
                this.successful = true;
            },
            error: () => {
                this.busy = false;
                this.failed = true;
            },
        });
    }

    /**
     * Close modal
     */
    close(): void {
        this.activeModal.close();
    }
}
