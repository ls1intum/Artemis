import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { faStopwatch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-quiz-timer-bar',
    templateUrl: './quiz-timer-bar.component.html',
    styleUrl: './quiz-timer-bar.component.scss',
    imports: [FaIconComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizTimerBarComponent {
    timeLimit = input(0);
    timerExpiresAt = input<dayjs.Dayjs>();
    open = input(false);
    showProgress = input(true);
    readonly timerExpired = output<void>();

    protected readonly faStopwatch = faStopwatch;

    protected readonly JSON = JSON;

    private readonly remainingSeconds = signal<number | undefined>(undefined);

    protected readonly displayedRemainingSeconds = computed(() => this.remainingSeconds() ?? this.timeLimit());

    protected readonly displayedRemainingTime = computed(() => {
        const remainingSeconds = this.displayedRemainingSeconds();

        if (remainingSeconds < 60) {
            return remainingSeconds.toString();
        }

        const minutes = Math.floor(remainingSeconds / 60);
        const seconds = remainingSeconds % 60;

        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    });

    protected readonly remainingTimeTranslationKey = computed(() =>
        this.displayedRemainingSeconds() >= 60 ? 'artemisApp.exerciseChatbot.timeLeft' : 'artemisApp.exerciseChatbot.secondsLeft',
    );

    protected readonly progress = computed(() => {
        const timeLimit = this.timeLimit();

        if (timeLimit <= 0) {
            return 100;
        }

        return Math.max(0, Math.min(100, (this.displayedRemainingSeconds() / timeLimit) * 100));
    });

    constructor() {
        // effect is needed here because timer API is non-reactive
        effect((onCleanup) => {
            const timerExpiresAt = this.timerExpiresAt();

            if (timerExpiresAt === undefined) {
                this.remainingSeconds.set(undefined);
                return;
            }

            let intervalId: ReturnType<typeof setInterval> | undefined;

            const updateRemainingSeconds = (): boolean => {
                const remainingSeconds = Math.max(timerExpiresAt.diff(dayjs(), 'second'), 0);

                this.remainingSeconds.set(remainingSeconds);

                if (remainingSeconds === 0) {
                    if (intervalId !== undefined) {
                        clearInterval(intervalId);
                    }

                    this.timerExpired.emit();
                    return false;
                }

                return true;
            };

            if (updateRemainingSeconds()) {
                intervalId = setInterval(updateRemainingSeconds, 1000);
            }

            onCleanup(() => {
                if (intervalId !== undefined) {
                    clearInterval(intervalId);
                }
            });
        });
    }
}
