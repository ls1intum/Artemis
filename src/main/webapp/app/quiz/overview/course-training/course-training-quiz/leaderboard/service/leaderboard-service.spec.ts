import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { LeaderboardEntry } from '../leaderboard-types';

@Injectable({
    providedIn: 'root',
})
export class LeaderboardService {
    private http = inject(HttpClient);

    public getQuizTrainingLeaderboard(courseId: number): Observable<LeaderboardEntry[]> {
        return this.http.get<LeaderboardEntry[]>(`api/quiz/courses/${courseId}/training/leaderboard`);
    }
}
