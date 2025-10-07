import { Component, computed, input } from '@angular/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { LeaderboardEntry } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-leaderboard',
    imports: [FontAwesomeModule, ProfilePictureComponent, TranslateDirective],
    templateUrl: './leaderboard.component.html',
    styleUrl: './leaderboard.component.scss',
})
export class Leaderboard {
    currentUserId = input<number>(undefined!);
    leaderboardName = input<string>('');
    leaderboard = input<LeaderboardEntry[]>([]);

    isUserInLeaderboard = computed(() => {
        const entries = this.leaderboard();
        if (!entries || entries.length === 0) {
            return false;
        }
        return this.leaderboard().some((entry) => entry.userId === this.currentUserId());
    });

    // Computed properties for the highlight box
    get currentUserRank(): number {
        const user = this.leaderboard().find((entry) => entry.userId === this.currentUserId());
        return user?.rank || 0;
    }

    get currentUserScore(): number {
        const user = this.leaderboard().find((entry) => entry.userId === this.currentUserId());
        return user?.score || 0;
    }

    get currentUserPictureUrl(): string {
        const user = this.leaderboard().find((entry) => entry.userId === this.currentUserId());
        return user?.imageURL || '';
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
