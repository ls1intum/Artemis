import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { VariantGenerationRequest } from 'app/openapi/model/variant-generation-request';

export type PlacementChoice = 'existing-group' | 'new-group' | 'standalone';

/**
 * Whether the "Create Variant with AI" action is offered for an exercise. Mirrors the server's support rule
 * (`VariantTypeRegistry.isSupported`), which is the authority and rejects everything else at the REST boundary:
 * only programming and quiz exercises have variant adapters, and a quiz with drag-and-drop questions is out of
 * scope because its content lives in the background image and drag-item geometry, which the agent cannot re-theme.
 */
export function supportsAiVariantGeneration(exercise: Exercise | undefined): boolean {
    if (!exercise) {
        return false;
    }
    switch (exercise.type) {
        case ExerciseType.PROGRAMMING:
            return true;
        case ExerciseType.QUIZ:
            return !quizHasDragAndDropQuestions(exercise);
        default:
            return false;
    }
}

/**
 * List endpoints ship `hasDragAndDropQuestions` without the question graph; views that load full quizzes (the exam
 * exercise groups) only have `quizQuestions`. Prefer the explicit flag and fall back to the loaded questions.
 */
function quizHasDragAndDropQuestions(quiz: QuizExercise): boolean {
    if (quiz.hasDragAndDropQuestions !== undefined) {
        return quiz.hasDragAndDropQuestions;
    }
    return (quiz.quizQuestions ?? []).some((question) => question.type === QuizQuestionType.DRAG_AND_DROP);
}

export function difficultyLabel(d: DifficultyLevel): string {
    return d.charAt(0) + d.slice(1).toLowerCase();
}

/** Human-readable label for a narrative style, e.g. WORKPLACE → "Workplace". */
export function narrativeStyleLabel(style: string): string {
    return style.charAt(0) + style.slice(1).toLowerCase();
}

/** "What is being adapted" chips derived from a generation request — shared by the wizard modal and the tray cards. */
export function adaptationChips(request: VariantGenerationRequest | undefined): string[] {
    if (!request) {
        return [];
    }
    const items: string[] = [];
    if (request.targetDifficulty) {
        items.push(`Difficulty → ${difficultyLabel(request.targetDifficulty as DifficultyLevel)}`);
    }
    if (request.domainText) {
        items.push(`Domain: ${request.domainText}`);
    }
    if (request.narrativeStyle) {
        items.push(`Story: ${narrativeStyleLabel(request.narrativeStyle)}`);
    }
    if (request.additionalInstructions) {
        items.push(request.additionalInstructions.length > 80 ? `Custom: ${request.additionalInstructions.slice(0, 80)}…` : `Custom: ${request.additionalInstructions}`);
    }
    return items;
}

export function difficultyBadgeClass(d: DifficultyLevel | undefined): string {
    switch (d) {
        case DifficultyLevel.EASY:
            return 'bg-success';
        case DifficultyLevel.MEDIUM:
            return 'bg-warning';
        case DifficultyLevel.HARD:
            return 'bg-danger';
        default:
            return 'bg-secondary';
    }
}

export function durationDays(exercise: Exercise): number {
    if (exercise.releaseDate && exercise.dueDate) {
        return Math.max(1, exercise.dueDate.diff(exercise.releaseDate, 'day'));
    }
    return 7;
}
