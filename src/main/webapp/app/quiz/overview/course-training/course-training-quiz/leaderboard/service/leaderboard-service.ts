import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { LeaderboardDTO, LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LeaderboardService {
    private http = inject(HttpClient);

    getQuizTrainingLeaderboard(courseId: number): Observable<LeaderboardDTO> {
        return this.http.get<LeaderboardDTO>(`api/quiz/courses/${courseId}/training/leaderboard`);
    }

    updateSettings(leaderboardSettings: LeaderboardSettingsDTO): Observable<void> {
        return this.http.put<void>(`api/quiz/leaderboard-settings`, leaderboardSettings);
    }

    getSettings(): Observable<HttpResponse<LeaderboardSettingsDTO>> {
        return this.http.get<LeaderboardSettingsDTO>(`api/quiz/leaderboard-settings`, { observe: 'response' });
    }
}
