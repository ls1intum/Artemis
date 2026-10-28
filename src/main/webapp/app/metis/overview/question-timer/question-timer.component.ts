import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TumAetUiProgressBarComponent, TumAetUiProgressBarSeverity } from '@tumaet/ui-angular';
import dayjs from 'dayjs/esm';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { formatCountdown } from 'app/metis/shared/rating-scale.util';

/** Milliseconds between two recomputations of the remaining time; four ticks a second keep the display from skipping a second. */
const TICK_INTERVAL_MS = 250;

/** Remaining seconds at and below which the timer is shown as critical. */
const CRITICAL_SECONDS = 30;

/**
 * Countdown for one comprehension-check question.
 *
 * The remaining time is recomputed from the server clock ({@link ArtemisServerDateService}) every 250 ms instead of
 * being counted down locally, so a drifting client clock still shows the limit the server enforces. The panel keeps
 * one timer instance while the current question changes, so the countdown restarts whenever `endDate` changes. The
 * interval is cleared on destroy, and `expired` is emitted exactly once per end date when the countdown reaches zero.
 */
@Component({
    selector: 'jhi-question-timer',
    templateUrl: './question-timer.component.html',
    styleUrl: './question-timer.component.scss',
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiProgressBarComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuestionTimerComponent {
    private readonly serverDateService = inject(ArtemisServerDateService);
    private readonly destroyRef = inject(DestroyRef);

    /** Server time at which the question's limit is reached. */
    readonly endDate = input.required<dayjs.Dayjs>();
    /** The whole limit in seconds; the progress bar shows the remaining share of it. */
    readonly totalSeconds = input.required<number>();
    /** Emitted once per end date when the remaining time reaches zero. */
    readonly expired = output<void>();

    protected readonly remainingSeconds = signal(0);
    protected readonly critical = computed(() => this.remainingSeconds() <= CRITICAL_SECONDS);
    protected readonly display = computed(() => formatCountdown(this.remainingSeconds()));
    protected readonly severity = computed<TumAetUiProgressBarSeverity>(() => (this.critical() ? 'warn' : 'primary'));
    /** Remaining share of the limit in percent; the bar shrinks towards zero. */
    protected readonly progress = computed(() => {
        const total = this.totalSeconds();
        return total > 0 ? Math.min(100, Math.max(0, Math.round((this.remainingSeconds() / total) * 100))) : 0;
    });

    private intervalId?: number;
    private hasExpired = false;

    constructor() {
        // A new end date means a new question: reset the expiry state and tick from the start again.
        effect(() => {
            this.endDate();
            untracked(() => this.restart());
        });
        this.destroyRef.onDestroy(() => this.stop());
    }

    /** Recomputes immediately and starts ticking, unless the limit is already over. */
    private restart(): void {
        this.stop();
        this.hasExpired = false;
        this.tick();
        if (!this.hasExpired) {
            this.intervalId = window.setInterval(() => this.tick(), TICK_INTERVAL_MS);
        }
    }

    private stop(): void {
        if (this.intervalId !== undefined) {
            window.clearInterval(this.intervalId);
            this.intervalId = undefined;
        }
    }

    /** Recomputes the remaining seconds from the server clock and emits `expired` the first time they reach zero. */
    private tick(): void {
        const remainingMs = this.endDate().diff(this.serverDateService.now());
        const remaining = Math.max(0, Math.ceil(remainingMs / 1000));
        this.remainingSeconds.set(remaining);
        if (remaining === 0 && !this.hasExpired) {
            this.hasExpired = true;
            this.stop();
            this.expired.emit();
        }
    }
}
