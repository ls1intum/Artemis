import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';

@Injectable({ providedIn: 'root' })
export class QuizTrainingSettingsService {
    private http = inject(HttpClient);

    getSettings(): Observable<HttpResponse<LeaderboardSettingsDTO>> {
        return this.http.get(`api/quiz/leaderboard-settings`, { observe: 'response' });
    }

    updateSettings(settings: LeaderboardSettingsDTO): Observable<HttpResponse<LeaderboardSettingsDTO>> {
        return this.http.put(`api/quiz/leaderboard-settings`, settings, { observe: 'response' });
    }
}
