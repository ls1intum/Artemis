import { beforeEach, describe, expect, it } from 'vitest';
import { computeQuizQuestionInvalidReason, isQuizEditable, isQuizQuestionValid } from 'app/quiz/shared/service/quiz-manage-util.service';
import { QuizBatch, QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('QuizManageUtil', () => {
    let quizExercise: QuizExercise;

    describe('isQuizEditable', () => {
        beforeEach(() => {
            quizExercise = new QuizExercise(undefined, undefined);
            quizExercise.id = 1;
            quizExercise.title = 'test';
            quizExercise.duration = 600;
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.status = QuizStatus.VISIBLE;
            quizExercise.isAtLeastEditor = true;
            quizExercise.quizEnded = false;
        });

        it('should return true if new quiz', () => {
            quizExercise.id = undefined;
            expect(isQuizEditable(quizExercise)).toBe(true);
        });

        it('should return true if existing quiz is synchronized, not active and not over', () => {
            expect(isQuizEditable(quizExercise)).toBe(true);
        });

        it('should return true if existing quiz is batched, no batch exists, not active, at least editor and not over', () => {
            quizExercise.quizMode = QuizMode.BATCHED;
            quizExercise.quizBatches = undefined;
            expect(isQuizEditable(quizExercise)).toBe(true);
        });

        it('should return false if existing quiz is batched, batch exists, not active, at least editor and not over', () => {
            quizExercise.quizMode = QuizMode.BATCHED;
            quizExercise.quizBatches = [new QuizBatch()];
            expect(isQuizEditable(quizExercise)).toBe(false);
        });

        it('should return false if existing quiz is synchronized, active, at least editor, and not over', () => {
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.status = QuizStatus.ACTIVE;
            expect(isQuizEditable(quizExercise)).toBe(false);
        });

        it('should return false if existing quiz is synchronized, not active, at least editor, and over', () => {
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.quizEnded = true;
            expect(isQuizEditable(quizExercise)).toBe(false);
        });

        it('should return false if existing quiz is synchronized, not active, not editor and not over', () => {
            quizExercise.quizMode = QuizMode.SYNCHRONIZED;
            quizExercise.isAtLeastEditor = false;
            expect(isQuizEditable(quizExercise)).toBe(false);
        });
    });

    describe('isQuizQuestionValid', () => {
        const dragAndDropQuestionUtil = new DragAndDropQuestionUtil();
        const shortAnswerQuestionUtil = new ShortAnswerQuestionUtil();
        const multipleChoiceQuestion = new MultipleChoiceQuestion();

        it('should return false if points is undefined', () => {
            multipleChoiceQuestion.points = undefined;
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBe(false);
        });

        it('should return false if points < 1', () => {
            multipleChoiceQuestion.points = -1;
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBe(false);
        });

        it('should return false if points > 9999', () => {
            multipleChoiceQuestion.points = 10000;
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBe(false);
        });

        it('should return true if question is valid', () => {
            multipleChoiceQuestion.points = 100;
            multipleChoiceQuestion.title = 'Title';
            const answerOption0 = new AnswerOption();
            const answerOption1 = new AnswerOption();
            answerOption0.isCorrect = true;
            multipleChoiceQuestion.answerOptions = [answerOption0, answerOption1];
            expect(isQuizQuestionValid(multipleChoiceQuestion, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBe(true);
        });

        it.each([
            { text: 'a'.repeat(254), valid: true },
            { text: 'a'.repeat(255), valid: true },
            { text: 'a'.repeat(256), valid: false },
            { text: 'Lorem ipsum '.repeat(5000), valid: false },
        ])('should validate text drag-item length for $text.length characters', ({ text, valid }) => {
            const question = new DragAndDropQuestion();
            question.title = 'Drag and drop';
            question.points = 1;
            const dragItem = new DragItem();
            dragItem.id = 1;
            dragItem.text = 'Mapped item';
            const dropLocation = new DropLocation();
            dropLocation.id = 1;
            question.dragItems = [dragItem, { id: 2, text }];
            question.dropLocations = [dropLocation];
            question.correctMappings = [new DragAndDropMapping(dragItem, dropLocation)];

            expect(!!isQuizQuestionValid(question, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBe(valid);
            const invalidReasons: ValidationReason[] = [];
            computeQuizQuestionInvalidReason(invalidReasons, question, 0, dragAndDropQuestionUtil, shortAnswerQuestionUtil);
            expect(invalidReasons).toEqual(
                valid
                    ? []
                    : [
                          {
                              translateKey: 'artemisApp.quizExercise.invalidReasons.dragItemTextLength',
                              translateValues: { index: 1, threshold: 255 },
                          },
                      ],
            );
        });

        it('should allow picture drag items without text', () => {
            const question = new DragAndDropQuestion();
            question.title = 'Picture question';
            question.points = 1;
            const dragItem: DragItem = { id: 1, pictureFilePath: 'item.png' };
            const dropLocation: DropLocation = { id: 1 };
            question.dragItems = [dragItem];
            question.dropLocations = [dropLocation];
            question.correctMappings = [new DragAndDropMapping(dragItem, dropLocation)];

            expect(isQuizQuestionValid(question, dragAndDropQuestionUtil, shortAnswerQuestionUtil)).toBe(true);
            const invalidReasons: ValidationReason[] = [];
            computeQuizQuestionInvalidReason(invalidReasons, question, 0, dragAndDropQuestionUtil, shortAnswerQuestionUtil);
            expect(invalidReasons).toEqual([]);
        });
    });
});
