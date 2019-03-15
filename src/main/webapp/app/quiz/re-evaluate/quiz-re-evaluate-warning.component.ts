import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { QuizReEvaluateService } from './quiz-re-evaluate.service';
import { QuizQuestion, QuizQuestionType } from '../../entities/quiz-question';
import { MultipleChoiceQuestion } from '../../entities/multiple-choice-question';
import { DragAndDropQuestion } from '../../entities/drag-and-drop-question';

@Component({
    selector: 'jhi-quiz-re-evaluate-warning',
    templateUrl: './quiz-re-evaluate-warning.component.html'
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

    quizExercise: QuizExercise;
    backUpQuiz: QuizExercise;

    constructor(
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager,
        private quizExerciseService: QuizExerciseService,
        private quizReEvaluateService: QuizReEvaluateService
    ) {}

    ngOnInit(): void {
        this.isSaving = false;
        this.quizExerciseService.find(this.quizExercise.id).subscribe(res => {
            this.backUpQuiz = res.body;
            this.loadQuizSuccess(this.quizExercise);
        });
    }

    /**
     * @function clear
     * @desc Closes the modal
     */
    clear(): void {
        this.activeModal.dismiss('cancel');
    }

    /**
     * @function loadQuizSuccess
     * @desc check if the changes affect the existing results
     *  1. check if a question is deleted
     *  2. check for each question if:
     *          - it is set invalid
     *          - it has another scoringType
     *          - an answer was deleted
     *  3. check for each question-element if:
     *          - it is set invalid
     *          - the correctness was changed
     *
     * @param quiz {quizExercise} the reference Quiz from Server
     */
    loadQuizSuccess(quiz: QuizExercise): void {
        // question deleted?
        this.questionDeleted = this.backUpQuiz.questions.length !== this.quizExercise.questions.length;

        // check each question
        this.quizExercise.questions.forEach(question => {
            // find same question in backUp (necessary if the order has been changed)
            const backUpQuestion = this.backUpQuiz.questions.find(questionBackUp => {
                return question.id === questionBackUp.id;
            });

            this.checkQuestion(question, backUpQuestion);
        });
    }

    /**
     * @function checkQuestion
     * @desc
     * 1. compare backUpQuestion and question
     * 2. set flags based on detected changes
     *
     * @param question changed question
     * @param backUpQuestion original not changed question
     */
    checkQuestion(question: QuizQuestion, backUpQuestion: QuizQuestion): void {
        if (backUpQuestion !== null) {
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
        }
    }

    /**
     * @function checkMultipleChoiceQuestion
     * @desc
     * 1. check MultipleChoiceQuestion-Elements
     * 2. set flags based on detected changes
     *
     * @param question changed Multiple-Choice-Question
     * @param backUpQuestion original not changed Multiple-Choice-Question
     */
    checkMultipleChoiceQuestion(question: MultipleChoiceQuestion, backUpQuestion: MultipleChoiceQuestion): void {
        // question-Element deleted?
        if (question.answerOptions.length !== backUpQuestion.answerOptions.length) {
            this.questionElementDeleted = true;
        }
        // check each answer
        question.answerOptions.forEach(answer => {
            // only check if there are no changes on the question-elements yet
            if (!this.questionCorrectness || !this.questionElementInvalid) {
                const backUpAnswer = backUpQuestion.answerOptions.find(answerBackUp => {
                    return answerBackUp.id === answer.id;
                });
                if (backUpAnswer !== null) {
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
     * @function checkDragAndDropQuestion
     * @desc
     * 1. check DragAndDrop-Question-Elements
     * 2. set flags based on detected changes
     *
     * @param question changed DragAndDrop-Question
     * @param backUpQuestion original not changed DragAndDrop-Question
     */
    checkDragAndDropQuestion(question: DragAndDropQuestion, backUpQuestion: DragAndDropQuestion): void {
        // check if a dropLocation or dragItem was deleted
        if (
            question.dragItems.length !== backUpQuestion.dragItems.length ||
            question.dropLocations.length !== backUpQuestion.dropLocations.length
        ) {
            this.questionElementDeleted = true;
        }
        // check if the correct Mappings has changed
        if (JSON.stringify(question.correctMappings).toLowerCase() !== JSON.stringify(backUpQuestion.correctMappings).toLowerCase()) {
            this.questionCorrectness = true;
        }
        // only check if there are no changes on the question-elements yet
        if (!this.questionElementInvalid) {
            // check each dragItem
            question.dragItems.forEach(dragItem => {
                const backUpDragItem = backUpQuestion.dragItems.find(dragItemBackUp => {
                    return dragItemBackUp.id === dragItem.id;
                });
                // dragItem set invalid?
                if (backUpDragItem !== null && dragItem.invalid !== backUpDragItem.invalid) {
                    this.questionElementInvalid = true;
                }
            });
            // check each dropLocation
            question.dropLocations.forEach(dropLocation => {
                const backUpDropLocation = backUpQuestion.dropLocations.find(dropLocationBackUp => {
                    return dropLocationBackUp.id === dropLocation.id;
                });
                // dropLocation set invalid?
                if (backUpDropLocation !== null && dropLocation.invalid !== backUpDropLocation.invalid) {
                    this.questionElementInvalid = true;
                }
            });
        }
    }

    /**
     * @function confirmChange
     * @desc Confirm changes
     *  => send changes to server and wait for result
     *  if saving failed -> show failed massage
     */
    confirmChange(): void {
        this.busy = true;

        this.quizReEvaluateService.update(this.quizExercise).subscribe(
            res => {
                this.busy = false;
                this.successful = true;
            },
            () => {
                this.busy = false;
                this.failed = true;
            }
        );
    }

    /**
     * @function close
     * @desc Close modal
     */
    close(): void {
        this.activeModal.close();
    }
}
