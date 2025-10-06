export class LeaderboardDTO {
    public currentUserId: number;
    public leaderboardEntryDTO: LeaderboardEntry[];
    public hasUserSetSettings: boolean;
}

export class LeaderboardEntry {
    public rank?: number;
    public selectedLeague?: number;
    public userName?: string;
    public userId?: number;
    public score?: number;
    public answeredCorrectly?: number;
    public answeredWrong?: number;
    public totalQuestions?: number;
    public dueDate?: string;
    public streak?: number;
}

export class LeaderboardSettingsDTO {
    public showInLeaderboard?: boolean;
}
