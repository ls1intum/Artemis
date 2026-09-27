import { describe, expect, it } from 'vitest';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { toQuizQuestion, toSubmittedAnswer, toSubmittedAnswerFromLiveClient, toSubmittedAnswerFromStudent } from 'app/quiz/shared/util/generated-quiz-question.util';
import { DragAndDropQuizQuestionWithSolution } from 'app/openapi/model/drag-and-drop-quiz-question-with-solution';
import { DragAndDropQuizQuestionWithoutSolution } from 'app/openapi/model/drag-and-drop-quiz-question-without-solution';
import { ShortAnswerQuizQuestionWithSolution } from 'app/openapi/model/short-answer-quiz-question-with-solution';
import { MultipleChoiceQuizQuestionWithoutSolution } from 'app/openapi/model/multiple-choice-quiz-question-without-solution';
import { MultipleChoiceSubmittedAnswerAfterEvaluation } from 'app/openapi/model/multiple-choice-submitted-answer-after-evaluation';
import { DragAndDropSubmittedAnswerAfterEvaluation } from 'app/openapi/model/drag-and-drop-submitted-answer-after-evaluation';
import { ShortAnswerSubmittedAnswerBeforeEvaluation } from 'app/openapi/model/short-answer-submitted-answer-before-evaluation';

const dragItem = (id: number): DragItem => Object.assign(new DragItem(), { id });
const dropLocation = (id: number): DropLocation => Object.assign(new DropLocation(), { id });
const spot = (id: number): ShortAnswerSpot => Object.assign(new ShortAnswerSpot(), { id });
const submittedText = (text: string | undefined, spotId?: number): ShortAnswerSubmittedText =>
    Object.assign(new ShortAnswerSubmittedText(), { text, spot: spotId === undefined ? undefined : spot(spotId) });

describe('toQuizQuestion', () => {
    it('should convert a drag-and-drop question with its solution into class instances', () => {
        const question: DragAndDropQuizQuestionWithSolution = {
            type: 'drag-and-drop',
            id: 1,
            title: 'Match',
            dragItems: [{ id: 11, text: 'item' }],
            dropLocations: [{ id: 21, posX: 5 }],
            correctMappings: [
                { id: 31, invalid: true, dragItem: { id: 11 }, dropLocation: { id: 21 } },
                { id: 32, dragItem: { id: 11 } },
            ],
        };

        const converted = toQuizQuestion(question) as DragAndDropQuestion;

        expect(converted).toBeInstanceOf(DragAndDropQuestion);
        expect(converted.title).toBe('Match');
        expect(converted.dragItems![0]).toBeInstanceOf(DragItem);
        expect(converted.dragItems![0].text).toBe('item');
        expect(converted.dropLocations![0]).toBeInstanceOf(DropLocation);
        expect(converted.dropLocations![0].posX).toBe(5);

        const [complete, withoutDropLocation] = converted.correctMappings!;
        expect(complete).toBeInstanceOf(DragAndDropMapping);
        expect(complete.id).toBe(31);
        expect(complete.invalid).toBe(true);
        expect(complete.dragItem).toBeInstanceOf(DragItem);
        expect(complete.dropLocation).toBeInstanceOf(DropLocation);
        expect(complete.dropLocation!.id).toBe(21);
        expect(withoutDropLocation.dragItem!.id).toBe(11);
        expect(withoutDropLocation.dropLocation).toBeUndefined();
    });

    it('should leave the correct mappings absent while the server withholds the solution', () => {
        const question: DragAndDropQuizQuestionWithoutSolution = { type: 'drag-and-drop', id: 1, dragItems: [{ id: 11 }], dropLocations: [{ id: 21 }] };

        const converted = toQuizQuestion(question) as DragAndDropQuestion;

        expect(converted.dragItems).toHaveLength(1);
        expect(converted.correctMappings).toBeUndefined();
    });

    it('should convert a short-answer question with its solution into class instances', () => {
        const question: ShortAnswerQuizQuestionWithSolution = {
            type: 'short-answer',
            id: 2,
            spots: [{ id: 41, spotNr: 1 }],
            solutions: [{ id: 51, text: 'answer' }],
            correctMappings: [
                { id: 61, spot: { id: 41 }, solution: { id: 51, text: 'answer' } },
                { id: 62, invalid: true },
            ],
        };

        const converted = toQuizQuestion(question) as ShortAnswerQuestion;

        expect(converted).toBeInstanceOf(ShortAnswerQuestion);
        expect(converted.spots![0]).toBeInstanceOf(ShortAnswerSpot);
        expect(converted.solutions![0]).toBeInstanceOf(ShortAnswerSolution);
        const [complete, empty] = converted.correctMappings!;
        expect(complete).toBeInstanceOf(ShortAnswerMapping);
        expect(complete.spot).toBeInstanceOf(ShortAnswerSpot);
        expect(complete.solution).toBeInstanceOf(ShortAnswerSolution);
        expect(complete.solution!.text).toBe('answer');
        expect(empty.id).toBe(62);
        expect(empty.invalid).toBe(true);
        expect(empty.spot).toBeUndefined();
        expect(empty.solution).toBeUndefined();
    });

    it('should keep answer options absent when the server omits them', () => {
        const question: MultipleChoiceQuizQuestionWithoutSolution = { type: 'multiple-choice', id: 3, singleChoice: true };

        const converted = toQuizQuestion(question) as MultipleChoiceQuestion;

        expect(converted).toBeInstanceOf(MultipleChoiceQuestion);
        expect(converted.singleChoice).toBe(true);
        expect(converted.answerOptions).toBeUndefined();
    });
});

describe('toSubmittedAnswerFromLiveClient', () => {
    it('should send only the ids of the selected answer options', () => {
        const answer = new MultipleChoiceSubmittedAnswer();
        answer.quizQuestion = Object.assign(new MultipleChoiceQuestion(), { id: 3 });
        answer.selectedOptions = [Object.assign(new AnswerOption(), { id: 71, text: 'not sent' })];

        expect(toSubmittedAnswerFromLiveClient(answer)).toEqual({ type: 'multiple-choice', quizQuestion: { id: 3 }, selectedOptions: [{ id: 71 }] });
    });

    it('should send only the ids of the mapped drag items and drop locations', () => {
        const answer = new DragAndDropSubmittedAnswer();
        answer.quizQuestion = Object.assign(new DragAndDropQuestion(), { id: 1 });
        answer.mappings = [new DragAndDropMapping(dragItem(11), dropLocation(21))];

        expect(toSubmittedAnswerFromLiveClient(answer)).toEqual({
            type: 'drag-and-drop',
            quizQuestion: { id: 1 },
            mappings: [{ dragItem: { id: 11 }, dropLocation: { id: 21 } }],
        });
    });

    it('should send each submitted text with the id of its spot', () => {
        const answer = new ShortAnswerSubmittedAnswer();
        answer.quizQuestion = Object.assign(new ShortAnswerQuestion(), { id: 2 });
        answer.submittedTexts = [submittedText('typed', 41)];

        expect(toSubmittedAnswerFromLiveClient(answer)).toEqual({
            type: 'short-answer',
            quizQuestion: { id: 2 },
            submittedTexts: [{ text: 'typed', spot: { id: 41 } }],
        });
    });

    it('should reject an answer without a known question type', () => {
        const answer = { type: 'essay' } as unknown as SubmittedAnswer;

        expect(() => toSubmittedAnswerFromLiveClient(answer)).toThrow('Unknown submitted answer type: essay');
    });
});

describe('toSubmittedAnswerFromStudent', () => {
    it('should send an empty selection when the student selected nothing', () => {
        const answer = new MultipleChoiceSubmittedAnswer();
        answer.quizQuestion = Object.assign(new MultipleChoiceQuestion(), { id: 3 });

        expect(toSubmittedAnswerFromStudent(answer)).toEqual({ type: 'multiple-choice', questionId: 3, selectedOptions: [] });
    });

    it('should skip drag-and-drop mappings that miss a drag item or a drop location', () => {
        const answer = new DragAndDropSubmittedAnswer();
        answer.quizQuestion = Object.assign(new DragAndDropQuestion(), { id: 1 });
        answer.mappings = [
            new DragAndDropMapping(dragItem(11), dropLocation(21)),
            new DragAndDropMapping(dragItem(12), undefined),
            new DragAndDropMapping(undefined, dropLocation(22)),
        ];

        expect(toSubmittedAnswerFromStudent(answer)).toEqual({ type: 'drag-and-drop', questionId: 1, mappings: [{ dragItemId: 11, dropLocationId: 21 }] });
    });

    it('should omit blank texts and texts without a spot', () => {
        const answer = new ShortAnswerSubmittedAnswer();
        answer.quizQuestion = Object.assign(new ShortAnswerQuestion(), { id: 2 });
        answer.submittedTexts = [submittedText('kept', 41), submittedText('   ', 42), submittedText(undefined, 43), submittedText('no spot')];

        expect(toSubmittedAnswerFromStudent(answer)).toEqual({ type: 'short-answer', questionId: 2, submittedTexts: [{ text: 'kept', spotId: 41 }] });
    });

    it('should send empty lists when the answer has no mappings or texts', () => {
        const dragAndDropAnswer = new DragAndDropSubmittedAnswer();
        dragAndDropAnswer.quizQuestion = Object.assign(new DragAndDropQuestion(), { id: 1 });
        const shortAnswer = new ShortAnswerSubmittedAnswer();
        shortAnswer.quizQuestion = Object.assign(new ShortAnswerQuestion(), { id: 2 });

        expect(toSubmittedAnswerFromStudent(dragAndDropAnswer)).toEqual({ type: 'drag-and-drop', questionId: 1, mappings: [] });
        expect(toSubmittedAnswerFromStudent(shortAnswer)).toEqual({ type: 'short-answer', questionId: 2, submittedTexts: [] });
    });

    it('should reject an answer without a known question type', () => {
        const answer = { type: 'essay', quizQuestion: { id: 1 } } as unknown as SubmittedAnswer;

        expect(() => toSubmittedAnswerFromStudent(answer)).toThrow('Unknown submitted answer type: essay');
    });
});

describe('toSubmittedAnswer', () => {
    it('should keep the score and the question link of an evaluated multiple-choice answer', () => {
        const answer: MultipleChoiceSubmittedAnswerAfterEvaluation = {
            type: 'multiple-choice',
            id: 81,
            scoreInPoints: 2,
            quizQuestion: { type: 'multiple-choice', id: 3 },
            selectedOptions: [{ id: 71, isCorrect: true }],
        };

        const converted = toSubmittedAnswer(answer) as MultipleChoiceSubmittedAnswer;

        expect(converted).toBeInstanceOf(MultipleChoiceSubmittedAnswer);
        expect(converted.type).toBe(QuizQuestionType.MULTIPLE_CHOICE);
        expect(converted.scoreInPoints).toBe(2);
        expect(converted.quizQuestion).toBeInstanceOf(MultipleChoiceQuestion);
        expect(converted.quizQuestion!.id).toBe(3);
        expect(converted.selectedOptions![0]).toBeInstanceOf(AnswerOption);
        expect(converted.selectedOptions![0].isCorrect).toBe(true);
    });

    it('should convert the mappings of an evaluated drag-and-drop answer', () => {
        const answer: DragAndDropSubmittedAnswerAfterEvaluation = {
            type: 'drag-and-drop',
            id: 82,
            scoreInPoints: 0,
            mappings: [{ id: 31, dragItem: { id: 11 }, dropLocation: { id: 21 } }],
        };

        const converted = toSubmittedAnswer(answer) as DragAndDropSubmittedAnswer;

        expect(converted).toBeInstanceOf(DragAndDropSubmittedAnswer);
        expect(converted.scoreInPoints).toBe(0);
        expect(converted.quizQuestion).toBeUndefined();
        expect(converted.mappings![0]).toBeInstanceOf(DragAndDropMapping);
        expect(converted.mappings![0].dragItem).toBeInstanceOf(DragItem);
        expect(converted.mappings![0].dropLocation!.id).toBe(21);
    });

    it('should leave the score of an unevaluated short-answer answer absent and convert its spots', () => {
        const answer: ShortAnswerSubmittedAnswerBeforeEvaluation = {
            type: 'short-answer',
            id: 83,
            quizQuestion: { type: 'short-answer', id: 2 },
            submittedTexts: [{ text: 'typed', spot: { id: 41 } }, { text: 'orphan' }],
        };

        const converted = toSubmittedAnswer(answer) as ShortAnswerSubmittedAnswer;

        expect(converted).toBeInstanceOf(ShortAnswerSubmittedAnswer);
        expect(converted.scoreInPoints).toBeUndefined();
        expect(converted.quizQuestion).toBeInstanceOf(ShortAnswerQuestion);
        const [typed, orphan] = converted.submittedTexts!;
        expect(typed).toBeInstanceOf(ShortAnswerSubmittedText);
        expect(typed.text).toBe('typed');
        expect(typed.spot).toBeInstanceOf(ShortAnswerSpot);
        expect(typed.spot!.id).toBe(41);
        expect(orphan.spot).toBeUndefined();
    });
});
