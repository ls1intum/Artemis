import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';

@Injectable({ providedIn: 'root' })
export class QuizTrainingSettingsService {
    private http = inject(HttpClient);

    /**
     * Retrieves the current quiz training leaderboard settings for the authenticated user.
     * @returns Observable that emits an HttpResponse containing the user's leaderboard settings
     */
    getSettings(): Observable<HttpResponse<LeaderboardSettingsDTO>> {
        return this.http.get(`api/quiz/leaderboard-settings`, { observe: 'response' });
    }

    /**
     * Updates the quiz training leaderboard settings for the authenticated user.
     * @param settings The new leaderboard settings to apply
     * @returns Observable that emits an HttpResponse containing the updated settings
     */
    updateSettings(settings: LeaderboardSettingsDTO): Observable<HttpResponse<LeaderboardSettingsDTO>> {
        return this.http.put(`api/quiz/leaderboard-settings`, settings, { observe: 'response' });
    }
}
