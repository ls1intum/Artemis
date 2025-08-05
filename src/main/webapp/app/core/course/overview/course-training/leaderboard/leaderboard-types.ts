export class LeaderboardEntry {
    public rank?: number;
    public league?: number;
    public student?: string;
    public score?: number;
    public answeredCorrectly?: number;
    public answeredWrong?: number;
    public totalQuestions?: number;

    get activity(): { correct: number; wrong: number } {
        return {
            correct: this.answeredCorrectly ?? 0,
            wrong: this.answeredWrong ?? 0,
        };
    }
}
