import { Component, signal } from '@angular/core';

@Component({
    selector: 'jhi-leaderboard',
    imports: [],
    templateUrl: './leaderboard.html',
    styleUrl: './leaderboard.scss',
})
export class Leaderboard {
    // Simulate loading state (optional, remove if not needed)
    isLoading = signal(false);

    // Sample leaderboard data (replace with API call as needed)
    /*leaderboard = signal<_LeaderboardEntry[]>([
        { rank: 1, league: 'Gold', student: 'Alice Example', score: 120},
        { rank: 2, league: 'Silver', student: 'Bob Sample', score: 112},
        { rank: 3, league: 'Bronze', student: 'Carol Test', score: 100},
        { rank: 4, league: 'Bronze', student: 'Moritz Spengler', score: 92}
    ]);*/
}
