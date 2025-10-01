import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { LeaderboardService } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/service/leaderboard-service.spec';
import { LeaderboardDTO, LeaderboardEntry } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { LeagueSilverIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/silver-icon.component';
import { LeagueBronzeIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/bronze-icon.component';
import { LeagueGoldIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/gold-icon.component';
import { LeagueDiamondIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/diamond-icon.component';
import { LeagueMasterIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/master-icon.component';
import { Leaderboard } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard.component';
@Component({
    selector: 'jhi-course-practice',
    imports: [
        ButtonComponent,
        LeagueSilverIconComponent,
        LeagueBronzeIconComponent,
        LeagueGoldIconComponent,
        LeagueDiamondIconComponent,
        LeagueMasterIconComponent,
        LeagueDiamondIconComponent,
        Leaderboard,
    ],
    templateUrl: './course-training.component.html',
    styleUrl: './course-training.component.scss',
})
export class CourseTrainingComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private leaderboardService = inject(LeaderboardService);

    readonly pointsBronzeLeague = 100;
    readonly pointsSilverLeague = 200;
    readonly pointsGoldLeague = 300;
    readonly pointsDiamondLeague = 400;

    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);

    league = computed(() => {
        const userId = this.mockLeaderboard().currentUserId;
        const entries = this.mockLeaderboard().entries;

        const userEntry = entries.find((entry) => entry.userId === userId);
        const league = userEntry?.studentLeague ?? 0;
        if (league === 1) {
            return 'Master';
        }
        if (league === 2) {
            return 'Diamond';
        }
        if (league === 3) {
            return 'Gold';
        }
        if (league === 4) {
            return 'Silver';
        }
        if (league === 5) {
            return 'Bronze';
        } else {
            return 'No League';
        }
    });

    leaderboardName = computed(() => {
        const userId = this.mockLeaderboard().currentUserId;
        const entries = this.mockLeaderboard().entries;

        const userEntry = entries.find((entry) => entry.userId === userId);
        return userEntry?.student ?? '';
    });

    points = computed(() => {
        const userId = this.mockLeaderboard().currentUserId;
        const entries = this.mockLeaderboard().entries;

        const userEntry = entries.find((entry) => entry.userId === userId);
        return userEntry?.score ?? 0;
    });

    dueDate = computed(() => {
        const userId = this.mockLeaderboard().currentUserId;
        const entries = this.mockLeaderboard().entries;

        const userEntry = entries.find((entry) => entry.userId === userId);
        return userEntry?.dueDate ?? 0;
    });

    dueIn = computed(() => {
        const dueDateValue = this.dueDate();

        if (!dueDateValue) {
            return 0;
        }

        const today = new Date();
        today.setHours(0, 0, 0, 0);

        const dueDate = new Date(dueDateValue);
        dueDate.setHours(0, 0, 0, 0);

        const diffTime = dueDate.getTime() - today.getTime();
        const diffDays = Math.floor(diffTime / (1000 * 60 * 60 * 24));

        return Math.max(0, diffDays);
    });

    streak = computed(() => {
        const entries = this.mockLeaderboard().entries;

        const userEntry = entries.find((entry) => entry.userId === this.mockLeaderboard().currentUserId);
        return userEntry?.streak ?? 0;
    });

    currentUserId = computed(() => this.mockLeaderboard().currentUserId);

    leaderboardEntries = signal<LeaderboardEntry[]>([]);
    isLoading = signal<boolean>(false);

    constructor() {
        effect(() => {
            const id = this.courseId();
            if (id) {
                this.loadLeaderboard(Number(id));
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

    now = new Date();

    mockLeaderboard = signal<LeaderboardDTO>({
        currentUserId: 1,
        entries: [
            {
                rank: 1,
                studentLeague: 2,
                student: 'Maria Musterfrau',
                userId: 1,
                score: 371,
                answeredCorrectly: 32,
                answeredWrong: 5,
                dueDate: new Date(Date.UTC(this.now.getFullYear(), this.now.getMonth(), this.now.getDate() + 2)),
                streak: 10,
            },
            { rank: 2, league: 2, student: 'Bob Sample', score: 112, answeredCorrectly: 28, answeredWrong: 8 },
            { rank: 3, league: 3, student: 'Carol Test', score: 100, answeredCorrectly: 20, answeredWrong: 12 },
            { rank: 4, league: 3, student: 'Moritz Spengler', score: 92, answeredCorrectly: 18, answeredWrong: 15 },
            { rank: 5, league: 1, student: 'Emma Schmidt', score: 105, answeredCorrectly: 27, answeredWrong: 10 },
            { rank: 6, league: 2, student: 'Felix Wagner', score: 98, answeredCorrectly: 25, answeredWrong: 12 },
            { rank: 7, league: 2, student: 'Greta Müller', score: 95, answeredCorrectly: 24, answeredWrong: 13 },
            { rank: 8, league: 2, student: 'Moritz Spengler', score: 92, answeredCorrectly: 18, answeredWrong: 15 },
            { rank: 9, league: 2, student: 'Hannah Becker', score: 89, answeredCorrectly: 23, answeredWrong: 16 },
            { rank: 10, league: 2, student: 'Ingo Fischer', score: 86, answeredCorrectly: 22, answeredWrong: 14 },
            { rank: 11, league: 2, student: 'Julia Richter', score: 84, answeredCorrectly: 20, answeredWrong: 12 },
            { rank: 12, league: 2, student: 'Karl Schneider', score: 80, answeredCorrectly: 19, answeredWrong: 15 },
            { rank: 13, league: 3, student: 'Lea Hoffmann', score: 78, answeredCorrectly: 17, answeredWrong: 13 },
            { rank: 14, league: 4, student: 'Max Schäfer', score: 75, answeredCorrectly: 18, answeredWrong: 19 },
            { rank: 15, league: 4, student: 'Nina Krause', score: 72, answeredCorrectly: 16, answeredWrong: 16 },
            { rank: 16, league: 3, student: 'Oliver Wolf', score: 68, answeredCorrectly: 15, answeredWrong: 18 },
            { rank: 17, league: 5, student: 'Paula Neumann', score: 65, answeredCorrectly: 14, answeredWrong: 17 },
            { rank: 18, league: 3, student: 'Quentin Zimmermann', score: 60, answeredCorrectly: 13, answeredWrong: 19 },
            { rank: 19, league: 5, student: 'Rosa Keller', score: 55, answeredCorrectly: 12, answeredWrong: 20 },
            { rank: 20, league: 3, student: 'Stefan Huber', score: 50, answeredCorrectly: 10, answeredWrong: 18 },
        ],
    });
}
