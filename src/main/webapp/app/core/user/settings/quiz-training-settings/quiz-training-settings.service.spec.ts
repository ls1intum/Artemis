import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuizTrainingSettingsService } from './quiz-training-settings.service';
import { LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { provideHttpClient } from '@angular/common/http';

describe('QuizTrainingSettingsService', () => {
    let service: QuizTrainingSettingsService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [QuizTrainingSettingsService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(QuizTrainingSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should retrieve settings from the API', () => {
        const mockSettings: LeaderboardSettingsDTO = { showInLeaderboard: true };

        service.getSettings().subscribe((response) => {
            expect(response.body).toEqual(mockSettings);
        });

        const req = httpMock.expectOne('api/quiz/leaderboard-settings');
        expect(req.request.method).toBe('GET');
        req.flush(mockSettings);
    });

    it('should update settings via the API', () => {
        const settingsToUpdate: LeaderboardSettingsDTO = { showInLeaderboard: false };
        const mockResponse: LeaderboardSettingsDTO = { showInLeaderboard: false };

        service.updateSettings(settingsToUpdate).subscribe((response) => {
            expect(response.body).toEqual(mockResponse);
        });

        const req = httpMock.expectOne('api/quiz/leaderboard-settings');
        expect(req.request.method).toBe('PUT');
        expect(req.request.body).toEqual(settingsToUpdate);
        req.flush(mockResponse);
    });
});
