import { Component, input } from '@angular/core';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { LeaderboardEntry } from 'app/core/course/overview/course-training/leaderboard/leaderboard-types';

@Component({
    selector: 'jhi-leaderboard',
    imports: [FontAwesomeModule],
    templateUrl: './leaderboard.html',
    styleUrl: './leaderboard.scss',
})
export class Leaderboard {
    faArrowUp = faArrowUp;
    faArrowDown = faArrowDown;

    currentUser = input<string>('');
    leaderboard = input<LeaderboardEntry[]>([]);

    // Computed properties for the highlight box
    get currentUserRank(): number {
        const user = this.leaderboard().find((entry) => entry.student === this.currentUser());
        return user?.rank || 0;
    }

    get currentUserLeague(): number {
        const user = this.leaderboard().find((entry) => entry.student === this.currentUser());
        return user?.league || 0;
    }

    get currentUserStudent(): string {
        return this.currentUser();
    }

    get currentUserScore(): number {
        const user = this.leaderboard().find((entry) => entry.student === this.currentUser());
        return user?.score || 0;
    }

    get currentUserActivity(): { correct: number; wrong: number } {
        const user = this.leaderboard().find((entry) => entry.student === this.currentUser());
        return user?.activity || { correct: 0, wrong: 0 };
    }
}
