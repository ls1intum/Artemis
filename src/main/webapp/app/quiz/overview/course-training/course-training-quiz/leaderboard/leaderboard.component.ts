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
export class LeaderboardComponent {
    currentUserId = input<number>();
    leaderboardName = input<string>('');
    leaderboard = input<LeaderboardEntry[]>([]);

    isUserInLeaderboard = computed(() => {
        const entries = this.leaderboard();
        if (!entries || entries.length === 0) {
            return false;
        }
        return this.leaderboard().some((entry) => entry.userId === this.currentUserId());
    });

    currentUser = computed(() => {
        const userId = this.currentUserId();
        return this.leaderboard().find((entry) => entry.userId === userId);
    });

    // Computed properties for the highlight box
    currentUserRank = computed(() => {
        const user = this.currentUser();
        return user?.rank || 0;
    });

    currentUserScore = computed(() => {
        const user = this.currentUser();
        return user?.score || 0;
    });

    currentUserPictureUrl = computed(() => {
        const user = this.currentUser();
        return user?.imageURL || '';
    });

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
