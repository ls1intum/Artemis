import { TumUiTagSeverity } from '@tumaet/ui-angular';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { VariantGenerationRequest } from 'app/openapi/model/variant-generation-request';

export type PlacementChoice = 'existing-group' | 'new-group' | 'standalone';

/**
 * Whether the "Create Variant with AI" action is offered for an exercise. Mirrors the server's support rule
 * (`VariantTypeRegistryService.isSupported`), which is the authority and rejects everything else at the REST boundary:
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

/** Longest custom instruction rendered in a chip before it is elided — the tray card is narrow. */
const CUSTOM_CHIP_MAX_LENGTH = 80;

/** Translation key of a difficulty level — reuses the shared exercise translations. */
export function difficultyTranslationKey(d: DifficultyLevel): string {
    return `artemisApp.exercise.${d.toLowerCase()}`;
}

/** Translation key of a narrative style's label, e.g. CREATIVE → …wizard.narrative.CREATIVE. */
export function narrativeStyleTranslationKey(style: string): string {
    return `artemisApp.exerciseVariantGeneration.wizard.narrative.${style}`;
}

/**
 * Resolves a translation key with optional interpolation parameters. Passed in by the calling component rather
 * than resolved here, so these helpers stay pure functions (same pattern as `buildCourseExerciseCards`).
 */
export type TranslateFn = (key: string, params?: Record<string, unknown>) => string;

/** "What is being adapted" chips derived from a generation request — shared by the wizard modal and the tray cards. */
export function adaptationChips(request: VariantGenerationRequest | undefined, translate: TranslateFn): string[] {
    if (!request) {
        return [];
    }
    const chip = (name: string, value: string) => translate(`artemisApp.exerciseVariantGeneration.chip.${name}`, { value });
    const items: string[] = [];
    if (request.targetDifficulty) {
        items.push(chip('difficulty', translate(difficultyTranslationKey(request.targetDifficulty as DifficultyLevel))));
    }
    if (request.domainText) {
        items.push(chip('domain', request.domainText));
    }
    if (request.narrativeStyle) {
        items.push(chip('story', translate(narrativeStyleTranslationKey(request.narrativeStyle))));
    }
    if (request.additionalInstructions) {
        const instructions = request.additionalInstructions;
        items.push(chip('custom', instructions.length > CUSTOM_CHIP_MAX_LENGTH ? `${instructions.slice(0, CUSTOM_CHIP_MAX_LENGTH)}…` : instructions));
    }
    return items;
}

/** Severity of the `tum-ui-tag` showing an exercise's difficulty. */
export function difficultySeverity(d: DifficultyLevel | undefined): TumUiTagSeverity {
    switch (d) {
        case DifficultyLevel.EASY:
            return 'success';
        case DifficultyLevel.MEDIUM:
            return 'warn';
        case DifficultyLevel.HARD:
            return 'danger';
        default:
            return 'secondary';
    }
}

export function durationDays(exercise: Exercise): number {
    if (exercise.releaseDate && exercise.dueDate) {
        return Math.max(1, exercise.dueDate.diff(exercise.releaseDate, 'day'));
    }
    return 7;
}
