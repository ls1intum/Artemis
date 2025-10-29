import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { LeaderboardService } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/service/leaderboard-service';
import { LeaderboardEntry, LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { LeaderboardComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { faCheck, faClock, faQuestion, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { LeagueBadgeComponent } from 'app/quiz/overview/course-training/league-badge/league-badge.component';
import dayjs from 'dayjs/esm';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { QuizTrainingDialogComponent } from 'app/quiz/overview/course-training/quiz-training-dialog.component';

@Component({
    selector: 'jhi-course-training',
    imports: [
        ButtonComponent,
        LeaderboardComponent,
        TranslateDirective,
        DialogModule,
        FormsModule,
        FontAwesomeModule,
        ToggleSwitchModule,
        LeagueBadgeComponent,
        ButtonModule,
        TooltipModule,
        QuizTrainingDialogComponent,
    ],
    templateUrl: './course-training.component.html',
    styleUrl: './course-training.component.scss',
})
export class CourseTrainingComponent {
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly leaderboardService = inject(LeaderboardService);

    protected readonly faClock = faClock;
    protected readonly faQuestion = faQuestion;
    protected readonly faXmark = faXmark;
    protected readonly faCheck = faCheck;

    protected readonly pointsBronzeLeague = 50;
    protected readonly pointsSilverLeague = 150;
    protected readonly pointsGoldLeague = 300;
    protected readonly pointsDiamondLeague = 500;

    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);
    isFirstVisit = signal<boolean>(true);
    leaderboardEntries = signal<LeaderboardEntry[]>([]);
    currentUserEntry = signal<LeaderboardEntry | undefined>(undefined);
    isLoading = signal<boolean>(false);
    isDataLoaded = signal<boolean>(false);
    currentTime = signal<string>('');
    initialShowInLeaderboard = signal<boolean>(true);

    showDialog = true;
    showInLeaderboard = true;
    displayInfoDialog = false;

    league = computed(() => {
        const entry = this.currentUserEntry();
        const league = entry?.selectedLeague ?? 0;
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
        const entry = this.currentUserEntry();
        return entry?.userName ?? '';
    });

    points = computed(() => {
        const entry = this.currentUserEntry();
        return entry?.score ?? 0;
    });

    dueDate = computed(() => {
        const entry = this.currentUserEntry();
        return entry?.dueDate ? dayjs(entry.dueDate) : undefined;
    });

    dueIn = computed(() => {
        const dueDateDayjs = this.dueDate();
        if (!dueDateDayjs) {
            return { isValid: false, isPast: false, days: 0, hours: 0, minutes: 0 };
        }

        const now = dayjs(this.currentTime());

        if (now.isAfter(dueDateDayjs)) {
            return { isValid: true, isPast: true, days: 0, hours: 0, minutes: 0 };
        }

        const diffDays = dueDateDayjs.diff(now, 'day');
        const diffHours = dueDateDayjs.diff(now, 'hour') % 24;
        const diffMinutes = dueDateDayjs.diff(now, 'minute') % 60;

        if (diffDays === 0 && diffHours === 0 && diffMinutes === 0) {
            const diffSeconds = dueDateDayjs.diff(now, 'second');
            if (diffSeconds < 60) {
                return { isValid: true, isPast: true, days: 0, hours: 0, minutes: 0 };
            }
        }

        return {
            isValid: true,
            isPast: false,
            days: diffDays,
            hours: diffHours,
            minutes: diffMinutes,
        };
    });

    totalQuestions = computed(() => {
        const entry = this.currentUserEntry();
        return entry?.totalQuestions ?? 0;
    });

    correctQuestions = computed(() => {
        const entry = this.currentUserEntry();
        return entry?.answeredCorrectly ?? 0;
    });

    wrongQuestions = computed(() => {
        const entry = this.currentUserEntry();
        return entry?.answeredWrong ?? 0;
    });

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
            next: (leaderboard) => {
                this.leaderboardEntries.set(leaderboard.leaderboardEntries);
                this.currentUserEntry.set(leaderboard.currentUserEntry);
                this.currentTime.set(leaderboard.currentTime);
                this.isLoading.set(false);
                this.isDataLoaded.set(true);

                if (leaderboard.hasUserSetSettings) {
                    this.showDialog = false;
                    this.isFirstVisit.set(false);
                }
            },
            error: () => this.isLoading.set(false),
        });
    }

    public navigateToTraining(): void {
        const courseId = this.courseId();
        if (courseId !== undefined) {
            this.router.navigate(['courses', courseId, 'training', 'quiz']);
        }
    }

    onSaveDialog(): void {
        this.isLoading.set(true);
        const leaderboardSettings = new LeaderboardSettingsDTO();
        leaderboardSettings.showInLeaderboard = this.showInLeaderboard;

        this.leaderboardService.updateSettings(leaderboardSettings).subscribe({
            next: () => {
                this.isFirstVisit.set(false);
                const courseId = this.courseId();
                if (courseId !== undefined) {
                    this.loadLeaderboard(courseId);
                }
            },
            error: () => {
                this.isLoading.set(false);
            },
        });
    }

    showInfoDialog(): void {
        this.isLoading.set(true);
        this.leaderboardService.getSettings().subscribe({
            next: (response) => {
                const settings = response.body;
                if (settings) {
                    this.showInLeaderboard = settings.showInLeaderboard!;
                    this.initialShowInLeaderboard.set(settings.showInLeaderboard!);
                }
                this.displayInfoDialog = true;
                this.isLoading.set(false);
            },
            error: () => {
                this.isLoading.set(false);
            },
        });
    }

    onSaveInfoDialog(): void {
        this.isLoading.set(true);
        const leaderboardSettings = new LeaderboardSettingsDTO();
        leaderboardSettings.showInLeaderboard = this.showInLeaderboard;

        this.leaderboardService.updateSettings(leaderboardSettings).subscribe({
            next: () => {
                this.initialShowInLeaderboard.set(this.showInLeaderboard);
                this.displayInfoDialog = false;
                const courseId = this.courseId();
                if (courseId !== undefined) {
                    this.loadLeaderboard(courseId);
                }
            },
            error: () => {
                this.isLoading.set(false);
            },
        });
    }
}
