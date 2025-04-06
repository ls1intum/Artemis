import { Injectable } from '@angular/core';
import { ParticipationResultDTO } from 'app/core/course/shared/entities/course-for-dashboard-dto';
import { ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';

/**
 * This service is used to store course scores and participation results (the relevant result used for the score calculation for each participation) for the currently logged-in user.
 * The methods {@link CourseManagementService#findAllForDashboard} and {@link CourseManagementService#findOneForDashboard} retrieve the scores and participation results in addition to one or multiple {@link Course} objects and save the scores and participation results in this service.
 * This way, multiple components that need the scores and participation results can access them without having to retrieve them again from the server.
 */
@Injectable({ providedIn: 'root' })
export class ScoresStorageService {
    /**
     * This map stores the {@link CourseScores} object for each {@link Course} that the currently logged-in user has access to. The number is the id of the course.
     */
    private storedTotalScores: Map<number, CourseScores> = new Map();

    /**
     * This map stores the {@link ScoresPerExerciseType} object for each {@link Course} that the currently logged-in user has access to. The number is the id of the course.
     * This is a nested map as the {@link ScoresPerExerciseType} object is a map itself. It stores {@link CourseScores}, but not for the total course (like the {@link storedTotalScores} above). Instead, it stores the scores split up per exercise type (programming, text, quiz etc.).
     */
    private storedScoresPerExerciseType: Map<number, ScoresPerExerciseType> = new Map();

    /**
     * This map stores the {@link Result} object for each {@link Participation} of the currently logged-in user. The number is the id of the participation.
     */
    private storedParticipationResults: Map<number, ParticipationResultDTO> = new Map();

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

    getStoredParticipationResult(participationId: number): ParticipationResultDTO | undefined {
        return this.storedParticipationResults.get(participationId);
    }

    setStoredParticipationResults(participationResults?: ParticipationResultDTO[]): void {
        for (const participationResult of participationResults ?? []) {
            this.storedParticipationResults.set(participationResult.participationId, participationResult);
        }
    }
}
