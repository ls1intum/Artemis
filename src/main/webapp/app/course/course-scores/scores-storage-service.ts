import { Injectable } from '@angular/core';
import { CourseScoresDTO } from 'app/course/course-scores/course-scores-dto';
import { ExerciseType, ExerciseTypeTOTAL } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';

@Injectable({ providedIn: 'root' })
export class ScoresStorageService {
    private storedScoresPerExerciseType: Map<number, Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresDTO>> = new Map();

    private participationResults: Result[] = [];

    getStoredScoresPerExerciseType(courseId: number): Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresDTO> | undefined {
        return this.storedScoresPerExerciseType.get(courseId);
    }

    setStoredScoresPerExerciseType(courseId: number, scoresPerExerciseType: Map<ExerciseType | ExerciseTypeTOTAL, CourseScoresDTO>): void {
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
