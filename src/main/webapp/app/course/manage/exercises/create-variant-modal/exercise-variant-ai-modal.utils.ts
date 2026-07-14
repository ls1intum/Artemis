import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { VariantGenerationRequest } from 'app/openapi/model/variantGenerationRequest';

export type PlacementChoice = 'existing-group' | 'new-group' | 'standalone';

export function difficultyLabel(d: DifficultyLevel): string {
    return d.charAt(0) + d.slice(1).toLowerCase();
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
