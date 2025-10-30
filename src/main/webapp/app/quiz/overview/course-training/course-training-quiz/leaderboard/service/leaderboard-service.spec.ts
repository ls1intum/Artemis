import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { LeaderboardService } from './leaderboard-service';
import { LeaderboardDTO, LeaderboardEntry, LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';

describe('LeaderboardService', () => {
    let service: LeaderboardService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), LeaderboardService],
        });

        service = TestBed.inject(LeaderboardService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('getQuizTrainingLeaderboard', () => {
        it('should call the correct URL and return leaderboard data', async () => {
            const courseId = 123;
            const mockEntries: LeaderboardEntry[] = [
                {
                    rank: 1,
                    userName: 'user1',
                    score: 100,
                    userId: 1,
                    selectedLeague: 1,
                    answeredCorrectly: 10,
                    answeredWrong: 2,
                    totalQuestions: 12,
                    dueDate: '2023-06-15',
                    streak: 3,
                },
                {
                    rank: 2,
                    userName: 'user2',
                    score: 90,
                    userId: 2,
                    selectedLeague: 1,
                    answeredCorrectly: 9,
                    answeredWrong: 3,
                    totalQuestions: 12,
                    dueDate: '2023-06-15',
                    streak: 2,
                },
            ];

            const mockLeaderboardData: LeaderboardDTO = {
                leaderboardEntries: mockEntries,
                hasUserSetSettings: true,
                currentUserEntry: mockEntries[0],
                currentTime: '2023-06-15 12:00:00',
            };

            const promise = service.getQuizTrainingLeaderboard(courseId);

            const req = httpMock.expectOne(`api/quiz/courses/${courseId}/training/leaderboard`);
            expect(req.request.method).toBe('GET');
            req.flush(mockLeaderboardData);

            const data = await promise;
            expect(data).toEqual(mockLeaderboardData);
        });
    });

    describe('initializeLeaderboardEntry', () => {
        it('should send PUT request with leaderboard settings to the correct URL', async () => {
            const mockSettings: LeaderboardSettingsDTO = {
                showInLeaderboard: true,
            };

            const promise = service.updateSettings(mockSettings);

            const req = httpMock.expectOne(`api/quiz/leaderboard-settings`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual(mockSettings);
            req.flush(null);

            const response = await promise;
            expect(response).toBeNull();
        });
    });

    it('should call the correct URL and return leaderboard settings', async () => {
        const mockSettings: LeaderboardSettingsDTO = { showInLeaderboard: true };

        const promise = service.getSettings();

        const req = httpMock.expectOne('api/quiz/leaderboard-settings');
        expect(req.request.method).toBe('GET');
        req.flush(mockSettings);

        const settings = await promise;
        expect(settings).toEqual(mockSettings);
    });
});
