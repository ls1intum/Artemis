export class LeaderboardDTO {
    public leaderboardEntryDTO: LeaderboardEntry[];
    public hasUserSetSettings: boolean;
    public currentUserEntry: LeaderboardEntry;
}

export class LeaderboardEntry {
    public rank: number;
    public selectedLeague: number;
    public userName: string;
    public imageURL?: string;
    public userId: number;
    public score: number;
    public answeredCorrectly: number;
    public answeredWrong: number;
    public totalQuestions: number;
    public dueDate: string;
    public streak: number;
}

export class LeaderboardSettingsDTO {
    public showInLeaderboard?: boolean;
}
