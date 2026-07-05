import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export type PlacementChoice = 'existing-group' | 'new-group' | 'standalone';

export function difficultyLabel(d: DifficultyLevel): string {
    return d.charAt(0) + d.slice(1).toLowerCase();
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
