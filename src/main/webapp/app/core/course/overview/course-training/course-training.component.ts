import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { Leaderboard } from 'app/core/course/overview/course-training/leaderboard/leaderboard';
import { LeagueBronzeIconComponent } from 'app/core/course/overview/course-training/leaderboard/league/bronze-icon.component';
import { LeagueSilverIconComponent } from 'app/core/course/overview/course-training/leaderboard/league/silver-icon.component';
import { LeagueGoldIconComponent } from 'app/core/course/overview/course-training/leaderboard/league/gold-icon.component';
import { LeaderboardService } from 'app/core/course/overview/course-training/leaderboard/service/leaderboard-service';
import { LeaderboardEntry } from 'app/core/course/overview/course-training/leaderboard/leaderboard-types';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-course-practice',
    imports: [ButtonComponent, Leaderboard, LeagueBronzeIconComponent, LeagueSilverIconComponent, LeagueGoldIconComponent],
    templateUrl: './course-training.component.html',
    styleUrl: './course-training.scss',
})
export class CourseTrainingComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private leaderboardService = inject(LeaderboardService);
    private accountService = inject(AccountService);

    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);
    league = computed(() => {
        const user = this.currentUser();
        const entries = this._leaderboard();

        const userEntry = entries.find((entry) => entry.student === user.name);
        const league = userEntry?.league ?? 0;
        if (league === 1) {
            return 'Gold';
        }
        if (league === 2) {
            return 'Silver';
        }
        if (league === 3) {
            return 'Bronze';
        } else {
            return 'No League';
        }
    });
    points = computed(() => {
        const user = this.currentUser();
        const entries = this._leaderboard();

        const userEntry = entries.find((entry) => entry.student === user.name);
        return userEntry?.score ?? 0;
    });
    currentUser = signal<UserPublicInfoDTO>(undefined!);

    leaderboardEntries = signal<LeaderboardEntry[]>([]);
    isLoading = signal<boolean>(false);

    constructor() {
        effect(() => {
            const id = this.courseId();
            if (id) {
                //this.loadLeaderboardD(Number(id));
                this.accountService.identity().then((account) => {
                    if (account) {
                        this.currentUser.set(account);
                    }
                });
            }
        });
    }

    loadLeaderboard(courseId: number): void {
        this.isLoading.set(true);
        this.leaderboardService.getQuizTrainingLeaderboard(courseId).subscribe({
            next: (entries) => {
                this.leaderboardEntries.set(entries);
                this.isLoading.set(false);
            },
            error: () => this.isLoading.set(false),
        });
    }

    public navigateToTraining(): void {
        this.router.navigate(['courses', this.courseId(), 'training', 'quiz']);
    }

    // Sample leaderboard data
    _leaderboard = signal<LeaderboardEntry[]>([
        { rank: 1, league: 1, student: 'Maria Musterfrau', score: 120, activity: { correct: 32, wrong: 5 } },
        { rank: 2, league: 2, student: 'Bob Sample', score: 112, activity: { correct: 28, wrong: 8 } },
        { rank: 3, league: 3, student: 'Carol Test', score: 100, activity: { correct: 20, wrong: 12 } },
        { rank: 4, league: 3, student: 'Moritz Spengler', score: 92, activity: { correct: 18, wrong: 15 } },
        { rank: 5, league: 1, student: 'Emma Schmidt', score: 105, activity: { correct: 27, wrong: 10 } },
        { rank: 6, league: 2, student: 'Felix Wagner', score: 98, activity: { correct: 25, wrong: 12 } },
        { rank: 7, league: 2, student: 'Greta Müller', score: 95, activity: { correct: 24, wrong: 13 } },
        { rank: 8, league: 2, student: 'Moritz Spengler', score: 92, activity: { correct: 18, wrong: 15 } },
        { rank: 9, league: 2, student: 'Hannah Becker', score: 89, activity: { correct: 23, wrong: 16 } },
        { rank: 10, league: 2, student: 'Ingo Fischer', score: 86, activity: { correct: 22, wrong: 14 } },
        { rank: 11, league: 2, student: 'Julia Richter', score: 84, activity: { correct: 20, wrong: 12 } },
        { rank: 12, league: 2, student: 'Karl Schneider', score: 80, activity: { correct: 19, wrong: 15 } },
        { rank: 13, league: 3, student: 'Lea Hoffmann', score: 78, activity: { correct: 17, wrong: 13 } },
        { rank: 14, league: 3, student: 'Max Schäfer', score: 75, activity: { correct: 18, wrong: 19 } },
        { rank: 15, league: 3, student: 'Nina Krause', score: 72, activity: { correct: 16, wrong: 16 } },
        { rank: 16, league: 3, student: 'Oliver Wolf', score: 68, activity: { correct: 15, wrong: 18 } },
        { rank: 17, league: 3, student: 'Paula Neumann', score: 65, activity: { correct: 14, wrong: 17 } },
        { rank: 18, league: 3, student: 'Quentin Zimmermann', score: 60, activity: { correct: 13, wrong: 19 } },
        { rank: 19, league: 3, student: 'Rosa Keller', score: 55, activity: { correct: 12, wrong: 20 } },
        { rank: 20, league: 3, student: 'Stefan Huber', score: 50, activity: { correct: 10, wrong: 18 } },
    ]);
}
