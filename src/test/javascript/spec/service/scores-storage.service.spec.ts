import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

describe('ScoresStorageService', () => {
    it('should filter the correct result from the stored participation results', () => {
        const scoresStorageService = new ScoresStorageService();
        const participation1: Participation = new ProgrammingExerciseStudentParticipation();
        participation1.id = 1;
        const participation2: Participation = new ProgrammingExerciseStudentParticipation();
        participation2.id = 2;

        scoresStorageService.setStoredParticipationResults([
            { successful: true, participation: participation1 },
            { successful: false, participation: participation2 },
        ]);
        expect(scoresStorageService.getStoredParticipationResult(1)).toEqual({ successful: true, participation: participation1 });
        // Should return undefined for an unknown participation id.
        expect(scoresStorageService.getStoredParticipationResult(3)).toBeUndefined();
    });

    it('should return an undefined participation result if the participation does not exist', () => {
        const scoresStorageService = new ScoresStorageService();
        const participation = new StudentParticipation();
        participation.id = 234;
        scoresStorageService.setStoredParticipationResults([{ successful: true, participation }]);
        expect(scoresStorageService.getStoredParticipationResult(1)).toBeUndefined();
    });
});
