export class LeaderboardDTO {
    public currentUserId: number;
    public entries: LeaderboardEntry[];
}

export class LeaderboardEntry {
    public rank?: number;
    public league?: number;
    public studentLeague?: number;
    //public student?: User;
    //public leaderboardName?: string;
    public student?: string; // Only for mock data purposes
    public userId?: number; // Only for mock data purposes
    public score?: number;
    public answeredCorrectly?: number;
    public answeredWrong?: number;
    public totalQuestions?: number;
    public dueDate?: Date;
    public streak?: number;
}
