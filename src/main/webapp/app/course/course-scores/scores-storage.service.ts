import { Injectable } from '@angular/core';
import { ScoresPerExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { CourseScores } from 'app/course/course-scores/course-scores';

@Injectable({ providedIn: 'root' })
export class ScoresStorageService {
    private storedTotalScores: Map<number, CourseScores> = new Map();

    private storedScoresPerExerciseType: Map<number, ScoresPerExerciseType> = new Map();

    private storedParticipationResults: Map<number, Result> = new Map();

    getStoredTotalScores(courseId: number): CourseScores | undefined {
        return this.storedTotalScores.get(courseId);
    }

    setStoredTotalScores(courseId: number, totalScores: CourseScores): void {
        this.storedTotalScores.set(courseId, totalScores);
    }

    getStoredScoresPerExerciseType(courseId: number): ScoresPerExerciseType | undefined {
        return this.storedScoresPerExerciseType.get(courseId);
    }

    setStoredScoresPerExerciseType(courseId: number, scoresPerExerciseType: ScoresPerExerciseType): void {
        this.storedScoresPerExerciseType.set(courseId, scoresPerExerciseType);
    }

    getStoredParticipationResult(participationId: number): Result | undefined {
        return this.storedParticipationResults.get(participationId);
    }

    setStoredParticipationResults(participationResults: Result[] | undefined): void {
        for (const result of participationResults ?? []) {
            this.storedParticipationResults.set(result.participation!.id!, result);
        }
    }
}
