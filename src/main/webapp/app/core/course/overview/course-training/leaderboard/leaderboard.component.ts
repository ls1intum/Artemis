import { Component, input } from '@angular/core';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { LeaderboardEntry } from 'app/core/course/overview/course-training/leaderboard/leaderboard-types';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';

@Component({
    selector: 'jhi-leaderboard',
    imports: [FontAwesomeModule, ProfilePictureComponent],
    templateUrl: './leaderboard.component.html',
    styleUrl: './leaderboard.component.scss',
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

    get currentAnswerRate(): number {
        const user = this.leaderboard().find((entry) => entry.student === this.currentUser());
        const correct = user?.answeredCorrectly ?? 0;
        const wrong = user?.answeredWrong ?? 1;
        return correct / wrong;
    }

    get currentUserActivity(): { correct: number; wrong: number } {
        const user = this.leaderboard().find((entry) => entry.student === this.currentUser());
        return user
            ? {
                  correct: user.answeredCorrectly ?? 0,
                  wrong: user.answeredWrong ?? 0,
              }
            : { correct: 0, wrong: 0 };
    }
}
