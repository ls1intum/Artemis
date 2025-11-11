import { Injectable } from '@angular/core';
import { LeaderboardDTO, LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({
    providedIn: 'root',
})
export class LeaderboardService extends BaseApiHttpService {
    async getQuizTrainingLeaderboard(courseId: number): Promise<LeaderboardDTO> {
        return this.get<LeaderboardDTO>(`quiz/courses/${courseId}/training/leaderboard`);
    }

    async updateSettings(leaderboardSettings: LeaderboardSettingsDTO): Promise<void> {
        return this.put<void>(`quiz/leaderboard-settings`, leaderboardSettings);
    }

    async getSettings(): Promise<LeaderboardSettingsDTO> {
        return await this.get<LeaderboardSettingsDTO>(`quiz/leaderboard-settings`);
    }
}
