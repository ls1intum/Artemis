import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, distinctUntilChanged } from 'rxjs';

import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';
import { ScoresPerExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

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

    describe('authentication state changes', () => {
        let authState: BehaviorSubject<User | undefined>;
        let scoped: ScoresStorageService;

        beforeEach(() => {
            authState = new BehaviorSubject<User | undefined>({ id: 99 } as User);
            const customAccountService = new MockAccountService();
            customAccountService.userIdentity.set({ id: 99 } as User);
            customAccountService.getAuthenticationState = () => authState.asObservable().pipe(distinctUntilChanged());

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                providers: [{ provide: AccountService, useValue: customAccountService }],
            });
            scoped = TestBed.inject(ScoresStorageService);

            scoped.setStoredTotalScores(1, {} as CourseScores);
            scoped.setStoredScoresPerExerciseType(1, new Map() as ScoresPerExerciseType);
            scoped.setStoredParticipationResults([{ score: 50, participationId: 7 }]);
        });

        it('should clear stored scores on logout', () => {
            authState.next(undefined);

            expect(scoped.getStoredTotalScores(1)).toBeUndefined();
            expect(scoped.getStoredScoresPerExerciseType(1)).toBeUndefined();
            expect(scoped.getStoredParticipationResult(7)).toBeUndefined();
        });

        it('should clear stored scores when a different user logs in', () => {
            authState.next({ id: 42 } as User);

            expect(scoped.getStoredTotalScores(1)).toBeUndefined();
            expect(scoped.getStoredParticipationResult(7)).toBeUndefined();
        });

        it('should not clear stored scores when the same user re-emits', () => {
            authState.next({ id: 99 } as User);

            expect(scoped.getStoredTotalScores(1)).toBeDefined();
            expect(scoped.getStoredParticipationResult(7)).toBeDefined();
        });
    });
});
