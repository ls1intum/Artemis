import { Component, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { LeaderboardEntry } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@Component({
    selector: 'jhi-leaderboard',
    imports: [FontAwesomeModule, ProfilePictureComponent],
    templateUrl: './leaderboard.component.html',
    styleUrl: './leaderboard.component.scss',
})
export class Leaderboard {
    currentUser = input<string>('');
    leaderboardName = input<string>('');
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
}
