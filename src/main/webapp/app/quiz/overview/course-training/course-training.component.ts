import { Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { EMPTY } from 'rxjs';
import { LeaderboardService } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/service/leaderboard-service';
import { LeaderboardEntry, LeaderboardSettingsDTO } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard-types';
import { LeagueSilverIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/silver-icon.component';
import { LeagueBronzeIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/bronze-icon.component';
import { LeagueGoldIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/gold-icon.component';
import { LeagueDiamondIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/diamond-icon.component';
import { LeagueMasterIconComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/league/master-icon.component';
import { LeaderboardComponent } from 'app/quiz/overview/course-training/course-training-quiz/leaderboard/leaderboard.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DialogModule } from 'primeng/dialog';
import { FormsModule } from '@angular/forms';
import { faCheck, faClock, faQuestion, faStar, faXmark } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { AccordionModule } from 'primeng/accordion';

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
        LeaderboardComponent,
        TranslateDirective,
        DialogModule,
        FormsModule,
        FontAwesomeModule,
        ToggleSwitchModule,
        AccordionModule,
    ],
    templateUrl: './course-training.component.html',
    styleUrl: './course-training.component.scss',
})
export class CourseTrainingComponent {
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private leaderboardService = inject(LeaderboardService);

    faClock = faClock;
    faStar = faStar;
    faQuestion = faQuestion;
    faXmark = faXmark;
    faCheck = faCheck;

    readonly pointsBronzeLeague = 100;
    readonly pointsSilverLeague = 200;
    readonly pointsGoldLeague = 300;
    readonly pointsDiamondLeague = 400;

    paramsSignal = toSignal(this.route.parent?.params ?? EMPTY);
    courseId = computed(() => this.paramsSignal()?.['courseId']);

    isFirstVisit = signal<boolean>(true);
    showDialog = true;
    showInLeaderboard = true;

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
        return entry?.dueDate ?? 0;
    });

    dueIn = computed(() => {
        const dueDateValue = this.dueDate();
        if (!dueDateValue) {
            return { isValid: false, isPast: false, days: 0, hours: 0, minutes: 0 };
        }

        const now = new Date();
        const dueDate = new Date(dueDateValue);

        if (dueDate <= now) {
            return { isValid: true, isPast: true, days: 0, hours: 0, minutes: 0 };
        }

        const diffMs = dueDate.getTime() - now.getTime();
        const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));
        const diffHours = Math.floor((diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
        const diffMinutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));

        return { isValid: true, isPast: false, days: diffDays, hours: diffHours, minutes: diffMinutes };
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

    leaderboardEntries = signal<LeaderboardEntry[]>([]);
    currentUserEntry = signal<LeaderboardEntry>(undefined!);
    isLoading = signal<boolean>(false);
    isDataLoaded = signal<boolean>(false);

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
                this.leaderboardEntries.set(leaderboard.leaderboardEntryDTO);
                this.currentUserEntry.set(leaderboard.currentUserEntry);
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
        this.router.navigate(['courses', this.courseId(), 'training', 'quiz']);
    }

    onSaveDialog(): void {
        this.isFirstVisit.set(false);
        this.isLoading.set(true);
        const leaderboardSettings = new LeaderboardSettingsDTO();
        leaderboardSettings.showInLeaderboard = this.showInLeaderboard;

        this.leaderboardService.initializeLeaderboardEntry(leaderboardSettings).subscribe({
            next: () => {
                this.loadLeaderboard(this.courseId());
            },
            error: () => {
                this.isLoading.set(false);
            },
        });
    }
}
