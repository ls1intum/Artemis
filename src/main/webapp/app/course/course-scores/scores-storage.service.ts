import { Injectable } from '@angular/core';
import { ScoresPerExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

@Injectable({ providedIn: 'root' })
export class ScoresStorageService {
    private storedScoresPerExerciseType: Map<number, ScoresPerExerciseType> = new Map();

    private participationResults: Result[] = [];

    getStoredScoresPerExerciseType(courseId: number): ScoresPerExerciseType | undefined {
        return this.storedScoresPerExerciseType.get(courseId);
    }

    setStoredScoresPerExerciseType(courseId: number, scoresPerExerciseType: ScoresPerExerciseType): void {
        this.storedScoresPerExerciseType.set(courseId, scoresPerExerciseType);
    }

    getStoredParticipationResult(participationId: number | undefined): Result | undefined {
        if (participationId === undefined) {
            return undefined;
        }
        return this.participationResults.filter((result) => result.participation?.id === participationId)[0];
    }

    setStoredParticipationResults(participationResults: Result[] | undefined): void {
        if (participationResults !== undefined) {
            this.participationResults.push(...participationResults);
        }
    }
}
