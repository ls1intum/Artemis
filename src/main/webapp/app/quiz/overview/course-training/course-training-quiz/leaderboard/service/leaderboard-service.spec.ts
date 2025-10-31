import { TestBed, fakeAsync, tick } from '@angular/core/testing';
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
        it('should call the correct URL and return leaderboard data', fakeAsync(() => {
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

            service.getQuizTrainingLeaderboard(courseId).subscribe((data) => {
                expect(data).toEqual(mockLeaderboardData);
            });

            const req = httpMock.expectOne(`api/quiz/courses/${courseId}/training/leaderboard`);
            expect(req.request.method).toBe('GET');
            req.flush(mockLeaderboardData);
            tick();
        }));
    });

    describe('initializeLeaderboardEntry', () => {
        it('should send PUT request with leaderboard settings to the correct URL', fakeAsync(() => {
            const mockSettings: LeaderboardSettingsDTO = {
                showInLeaderboard: true,
            };

            service.initializeLeaderboardEntry(mockSettings).subscribe((response) => {
                expect(response).toBeNull();
            });

            const req = httpMock.expectOne(`api/quiz/leaderboard-settings`);
            expect(req.request.method).toBe('PUT');
            expect(req.request.body).toEqual(mockSettings);
            req.flush(null);
            tick();
        }));
    });
});
