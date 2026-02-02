import { describe, expect, it } from 'vitest';

import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { toQuizQuestions } from 'app/exercise/synchronization/exercise-metadata-snapshot-quiz.mapper';

import { QuizQuestionSnapshotDTO } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

describe('ExerciseMetadataSnapshotQuizMapper', () => {
    it('maps multiple choice questions with answer options', () => {
        const snapshot: QuizQuestionSnapshotDTO = {
            id: 4,
            title: 'MCQ',
            type: QuizQuestionType.MULTIPLE_CHOICE,
            answerOptions: [
                {
                    id: 5,
                    text: 'A',
                    isCorrect: true,
                },
            ],
        };

        const mapped = toQuizQuestions([snapshot]);

        expect(mapped?.length).toBe(1);
        expect(mapped?.[0]).toBeInstanceOf(MultipleChoiceQuestion);
        const mc = mapped?.[0] as MultipleChoiceQuestion | undefined;
        expect(mc?.answerOptions?.length).toBe(1);
        expect(mc?.answerOptions?.[0].text).toBe('A');
        expect(mc?.answerOptions?.[0].isCorrect).toBe(true);
    });
});
