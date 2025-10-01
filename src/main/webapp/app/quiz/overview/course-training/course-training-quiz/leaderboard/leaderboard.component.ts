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
    currentUserId = input<number>(undefined!);
    leaderboardName = input<string>('');
    leaderboard = input<LeaderboardEntry[]>([]);

    // Computed properties for the highlight box
    get currentUserRank(): number {
        const user = this.leaderboard().find((entry) => entry.userId === this.currentUserId());
        return user?.rank || 0;
    }

    get currentUserScore(): number {
        const user = this.leaderboard().find((entry) => entry.userId === this.currentUserId());
        return user?.score || 0;
    }
}
