/** Deserialized from server data; the (!) fields are always present in the leaderboard payload. */
export class LeaderboardDTO {
    public leaderboardEntries!: LeaderboardEntry[];
    public hasUserSetSettings!: boolean;
    public currentUserEntry!: LeaderboardEntry;
    public currentTime!: string;
}

/** Deserialized from server data; the (!) fields are always present in the leaderboard payload. */
export class LeaderboardEntry {
    public rank!: number;
    public selectedLeague!: number;
    public userName!: string;
    public imageURL?: string;
    public userId!: number;
    public score!: number;
    public answeredCorrectly!: number;
    public answeredWrong!: number;
    public totalQuestions!: number;
    public dueDate?: string;
    public streak!: number;
}

export class LeaderboardSettingsDTO {
    public showInLeaderboard?: boolean;
}
