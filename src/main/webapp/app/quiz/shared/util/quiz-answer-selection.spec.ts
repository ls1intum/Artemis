import { describe, expect, it } from 'vitest';
import { QuizAnswerSelection } from './quiz-answer-selection';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';

describe('quiz answer selection', () => {
    const questions = [
        { id: 1, type: QuizQuestionType.MULTIPLE_CHOICE },
        { id: 2, type: QuizQuestionType.DRAG_AND_DROP },
        { id: 3, type: QuizQuestionType.SHORT_ANSWER },
    ] as QuizQuestion[];

    it('resets all question types and removes selections belonging to an older quiz', () => {
        const selection = new QuizAnswerSelection();
        selection.onSelectedAnswerOptionsChange(99, [{ id: 1 } as AnswerOption]);
        selection.reset(questions);
        expect([...selection.selectedAnswerOptions()]).toEqual([[1, []]]);
        expect([...selection.dragAndDropMappings()]).toEqual([[2, []]]);
        expect([...selection.shortAnswerSubmittedTexts()]).toEqual([[3, []]]);
        selection.reset(undefined);
        expect(selection.selectedAnswerOptions().size).toBe(0);
    });

    it('detaches exam answers when requested and preserves course answer references otherwise', () => {
        const answer = { quizQuestion: questions[0], selectedOptions: [{ id: 4, text: 'Original' }] } as MultipleChoiceSubmittedAnswer;
        const selection = new QuizAnswerSelection();
        selection.reset(questions, [answer], true);
        selection.selectedAnswerOptions().get(1)![0].text = 'Edited';
        expect(answer.selectedOptions![0].text).toBe('Original');
        selection.reset(questions, [answer]);
        expect(selection.selectedAnswerOptions().get(1)).toBe(answer.selectedOptions);
    });
});
