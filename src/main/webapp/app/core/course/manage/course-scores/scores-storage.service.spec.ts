import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';

import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('ScoresStorageService', () => {
    setupTestBed({ zoneless: true });

    let scoresStorageService: ScoresStorageService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        });
        scoresStorageService = TestBed.inject(ScoresStorageService);
    });
    it('should filter the correct result from the stored participation results', () => {
        const participation1: Participation = new ProgrammingExerciseStudentParticipation();
        participation1.id = 1;
        const participation2: Participation = new ProgrammingExerciseStudentParticipation();
        participation2.id = 2;

        scoresStorageService.setStoredParticipationResults([
            { score: 100, participationId: participation1.id },
            { score: 0, participationId: participation2.id },
        ]);
        expect(scoresStorageService.getStoredParticipationResult(1)).toEqual({ score: 100, participationId: participation1.id });
        // Should return undefined for an unknown participation id.
        expect(scoresStorageService.getStoredParticipationResult(3)).toBeUndefined();
    });

    it('should return an undefined participation result if the participation does not exist', () => {
        const participation = new StudentParticipation();
        participation.id = 234;
        scoresStorageService.setStoredParticipationResults([{ score: 100, participationId: participation.id }]);
        expect(scoresStorageService.getStoredParticipationResult(1)).toBeUndefined();
    });
});
