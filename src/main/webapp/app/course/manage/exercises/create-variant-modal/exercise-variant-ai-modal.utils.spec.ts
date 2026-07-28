import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { adaptationChips, supportsAiVariantGeneration } from './exercise-variant-ai-modal.utils';

describe('supportsAiVariantGeneration', () => {
    const exerciseOfType = (type: ExerciseType) => ({ id: 1, type }) as Exercise;

    const quizWith = (overrides: Partial<QuizExercise>) => ({ id: 1, type: ExerciseType.QUIZ, ...overrides }) as QuizExercise;

    it('should support programming exercises', () => {
        expect(supportsAiVariantGeneration(exerciseOfType(ExerciseType.PROGRAMMING))).toBe(true);
    });

    it.each([ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD])('should not support %s exercises', (type) => {
        expect(supportsAiVariantGeneration(exerciseOfType(type))).toBe(false);
    });

    it('should not support an undefined exercise', () => {
        expect(supportsAiVariantGeneration(undefined)).toBe(false);
    });

    it('should support a quiz without drag-and-drop questions', () => {
        expect(supportsAiVariantGeneration(quizWith({ hasDragAndDropQuestions: false }))).toBe(true);
    });

    it('should not support a quiz flagged as having drag-and-drop questions', () => {
        expect(supportsAiVariantGeneration(quizWith({ hasDragAndDropQuestions: true }))).toBe(false);
    });

    it('should fall back to the loaded questions when the flag is absent', () => {
        const withDragAndDrop = quizWith({ quizQuestions: [{ type: QuizQuestionType.DRAG_AND_DROP }] as QuizExercise['quizQuestions'] });
        const withoutDragAndDrop = quizWith({ quizQuestions: [{ type: QuizQuestionType.MULTIPLE_CHOICE }] as QuizExercise['quizQuestions'] });

        expect(supportsAiVariantGeneration(withDragAndDrop)).toBe(false);
        expect(supportsAiVariantGeneration(withoutDragAndDrop)).toBe(true);
    });

    it('should prefer the explicit flag over the loaded questions', () => {
        // A list view merges the flag in while a stale partial question graph may still be attached.
        const quiz = quizWith({ hasDragAndDropQuestions: true, quizQuestions: [{ type: QuizQuestionType.MULTIPLE_CHOICE }] as QuizExercise['quizQuestions'] });

        expect(supportsAiVariantGeneration(quiz)).toBe(false);
    });

    it('should support a quiz with neither the flag nor loaded questions', () => {
        // Nothing indicates drag-and-drop; the server still rejects it if it turns out to have some.
        expect(supportsAiVariantGeneration(quizWith({}))).toBe(true);
    });
});

describe('adaptationChips', () => {
    // Echoes key(value) so the specs assert the KEY that is looked up, not an English string.
    const translate = (key: string, params?: Record<string, unknown>) => (params?.value === undefined ? key : `${key}(${params.value})`);

    it('should include a storytelling chip when a narrative style is requested', () => {
        expect(adaptationChips({ narrativeStyle: 'IMAGINATIVE' }, translate)).toEqual([
            'artemisApp.exerciseVariantGeneration.chip.story(artemisApp.exerciseVariantGeneration.wizard.narrative.IMAGINATIVE)',
        ]);
    });

    it('should omit the storytelling chip when no narrative style is requested', () => {
        expect(adaptationChips({ domainText: 'banking' }, translate)).toEqual(['artemisApp.exerciseVariantGeneration.chip.domain(banking)']);
    });
});
