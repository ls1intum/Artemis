import { Injectable } from '@angular/core';
import { ScoresPerExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { CourseScores } from 'app/course/course-scores/course-scores';

@Injectable({ providedIn: 'root' })
export class ScoresStorageService {
    private storedTotalScores: Map<number, CourseScores> = new Map();

    private storedScoresPerExerciseType: Map<number, ScoresPerExerciseType> = new Map();

    private participationResults: Result[] = [];

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
        return this.participationResults.find((result: Result) => result.participation?.id === participationId);
    }

    setStoredParticipationResults(participationResults: Result[] | undefined): void {
        if (participationResults !== undefined) {
            this.participationResults = participationResults;
        }
    }
}
