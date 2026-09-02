import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiCardComponent,
    TumUiConfirmDialogComponent,
    TumUiConfirmationService,
    TumUiStatusDotComponent,
    TumUiStatusDotState,
} from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { elapsedSecondsSince, serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';
import { formatElapsed } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { HyperionSpendView, formatEuro, formatTokenCount, spendHero } from 'app/hyperion/exercise-generation/model/hyperion-generation-usage';

const CANCEL_CONFIRMATION_KEY = 'hyperionRunCancelConfirmation';

/**
 * The band a run is expected to finish inside, in minutes.
 *
 * Measured from real runs, and used to derive a statement rather than to print a constant: "Runs usually take 10 to 25
 * minutes" reads identically at 40 seconds and at 40 minutes, and past the band it is simply wrong.
 */
const TYPICAL_DURATION_MIN_MINUTES = 10;
const TYPICAL_DURATION_MAX_MINUTES = 25;

/** How far through the band the run is, which is what decides whether the estimate still applies. */
type DurationBand = 'inside' | 'over';

/** One column of the facts rail: a label, a figure, and the qualifier that keeps the figure honest. */
interface RunFact {
    key: string;
    labelKey: string;
    value: string;
    /** Translated on the way in where the figure is a number, so the template binds strings only. */
    unitKey?: string;
    qualifierKey?: string;
    /** Set on the qualifier that reports a run running past its expected band, which the stylesheet colours. */
    band?: DurationBand;
}

/**
 * Which run this is, how it is doing, what it has consumed, and what can be done about it.
 *
 * The facts rail is how cost becomes more prominent without out-ranking the answer: a one-line spend summary sits
 * above the fold, in every state, including while the run is going - rather than a larger number lower down. The rail
 * carries no Spend column at all for an instructor who does not own the run, because the server withholds the figures
 * from them and a zeroed column would read as a run that cost nothing.
 *
 * Deliberately reports no percentage: the agent's remaining work is not knowable, and a bar that creeps to 90% and
 * stops is a worse answer than a stage name and a clock. What it does report is a *derived* time statement, because an
 * indeterminate wait over ten seconds needs an estimate and a constant sentence is not one.
 */
@Component({
    selector: 'jhi-hyperion-run-header',
    templateUrl: './hyperion-run-header.component.html',
    styleUrl: './hyperion-run-header.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [TumUiConfirmationService],
    imports: [
        RouterLink,
        ArtemisTranslatePipe,
        TranslateDirective,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiCardComponent,
        TumUiConfirmDialogComponent,
        TumUiStatusDotComponent,
    ],
})
export class HyperionRunHeaderComponent {
    private readonly confirmationService = inject(TumUiConfirmationService);
    private readonly translateService = inject(TranslateService);

    readonly exerciseTitle = input<string | undefined>();
    /** Translation keys for the meta line, e.g. Java · Maven · Medium. Never raw enum values. */
    readonly metaLabelKeys = input<readonly string[]>([]);
    readonly statusState = input.required<TumUiStatusDotState>();
    readonly statusLabelKey = input.required<string>();
    /** ISO timestamp of the run's STARTED event; without one there is nothing to count from. */
    readonly startedAt = input<string | undefined>();
    /**
     * ISO timestamp of the terminal event.
     *
     * A run opened long after it finished must report how long it took, not how long ago it began, so a finished run
     * is measured against its own end rather than against the clock.
     */
    readonly endedAt = input<string | undefined>();
    /** Whether the run has ended, however it ended. Freezes the clock and swaps Cancel for Run again. */
    readonly terminal = input(false);
    readonly ownedByCaller = input(true);
    readonly cancelAvailable = input(false);
    readonly cancelPending = input(false);
    readonly runAgainAvailable = input(false);
    /** Offered on an exercise that has never generated anything, so the empty state carries an action rather than directions. */
    readonly startAvailable = input(false);
    readonly startPending = input(false);
    readonly editorLink = input<readonly (string | number)[] | undefined>();
    readonly exerciseLink = input<readonly (string | number)[] | undefined>();
    /** 1-based position in the five fixed stages, derived from position rather than completion so it cannot walk back. */
    readonly stepPosition = input<number | undefined>();
    readonly stepTotal = input<number | undefined>();
    /** `undefined` withholds the Spend column entirely, which is what a non-owner and an uncharged run both get. */
    readonly spend = input<HyperionSpendView | undefined>();
    readonly fileCount = input(0);

    readonly cancelRequested = output<void>();
    readonly runAgainRequested = output<void>();
    readonly startRequested = output<void>();

    protected readonly cancelConfirmationKey = CANCEL_CONFIRMATION_KEY;
    /** Read by the code editor to open its AI activity panel instead of the build output it defaults to. */
    protected readonly openGenerationActivityState = { openGenerationActivity: true };

    /** Emits once the run is over, which is what stops the clock from ticking for the rest of the session. */
    private readonly runEnded = new Subject<void>();
    private readonly now = serverTimeSignal(this.runEnded);

    /** Re-read on every language change, because a number formatted for one locale is wrong in the other. */
    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });
    private readonly locale = computed(() => {
        this.languageChange();
        return this.translateService.getCurrentLang() ?? 'en';
    });

    private readonly elapsedSeconds = computed(() => {
        const startedAt = this.startedAt();
        if (!startedAt) {
            return undefined;
        }
        const endedAt = this.endedAt();
        const until = endedAt ? Date.parse(endedAt) : this.now();
        return Number.isFinite(until) ? elapsedSecondsSince(startedAt, until) : undefined;
    });

    protected readonly elapsed = computed(() => {
        const seconds = this.elapsedSeconds();
        return seconds === undefined ? undefined : formatElapsed(seconds);
    });

    /**
     * Every column of the facts rail, in one place so the rail's shape is a single decision.
     *
     * A column with nothing to say is absent rather than blank: an elapsed clock before the run started, a step counter
     * before the first stage, a file count of zero, and - always - the spend of a run the viewer does not own.
     */
    protected readonly facts = computed<RunFact[]>(() => {
        const facts: RunFact[] = [];
        const elapsed = this.elapsed();
        if (elapsed !== undefined) {
            const qualifier = this.durationQualifier();
            facts.push({
                key: 'elapsed',
                labelKey: 'artemisApp.hyperion.generation.run.elapsed',
                value: elapsed,
                qualifierKey: qualifier.qualifierKey,
                band: qualifier.band,
            });
        }
        const position = this.stepPosition();
        const total = this.stepTotal();
        if (position !== undefined && total !== undefined) {
            facts.push({
                key: 'step',
                labelKey: 'artemisApp.hyperion.generation.run.stepLabel',
                value: this.translateService.instant('artemisApp.hyperion.generation.run.stepValue', { position, total }),
            });
        }
        const spend = this.spendFact();
        if (spend) {
            facts.push(spend);
        }
        const files = this.fileCount();
        if (files > 0) {
            facts.push({ key: 'files', labelKey: 'artemisApp.hyperion.generation.run.filesLabel', value: formatTokenCount(files, this.locale()) });
        }
        return facts;
    });

    /**
     * The spend column: the same hero decision the spend region makes, at rail size.
     *
     * Money leads when money is known; billable tokens lead when it is not. The two surfaces share `spendHero` rather
     * than each deciding for itself, because a header and a detail region that disagree about what a run cost is worse
     * than either of them being absent.
     */
    private spendFact(): RunFact | undefined {
        const spend = this.spend();
        if (!spend) {
            return undefined;
        }
        const hero = spendHero(spend);
        const locale = this.locale();
        const lowerBound = hero.lowerBound ? 'artemisApp.hyperion.generation.run.spendAtLeast' : undefined;
        if (hero.kind === 'money') {
            return {
                key: 'spend',
                labelKey: 'artemisApp.hyperion.generation.run.spendLabel',
                value: formatEuro(hero.eur, locale),
                qualifierKey: lowerBound ?? (spend.accounting === 'PENDING' ? 'artemisApp.hyperion.generation.usage.costSoFar' : undefined),
            };
        }
        return {
            key: 'spend',
            labelKey: 'artemisApp.hyperion.generation.run.spendLabel',
            value: formatTokenCount(hero.tokens, locale),
            unitKey: hero.unit === 'billable' ? 'artemisApp.hyperion.generation.usage.billableTokens' : 'artemisApp.hyperion.generation.usage.totalTokens',
            // The unavailable unit is stated in words rather than shown as a zero, at rail size as well as in detail.
            qualifierKey: 'artemisApp.hyperion.generation.run.costNotPriced',
        };
    }

    /**
     * The duration estimate, derived rather than printed.
     *
     * Inside the band it is an estimate. Past the band it stops being one, and saying so - "longer than usual, still
     * working" - is the difference between a page that is reporting and a page that is repeating itself. A finished
     * run gets neither: its duration is a record, not a forecast.
     */
    private durationQualifier(): Pick<RunFact, 'qualifierKey' | 'band'> {
        if (this.terminal()) {
            return {};
        }
        const seconds = this.elapsedSeconds();
        if (seconds !== undefined && seconds > TYPICAL_DURATION_MAX_MINUTES * 60) {
            return { qualifierKey: 'artemisApp.hyperion.generation.run.longerThanUsual', band: 'over' };
        }
        return { qualifierKey: 'artemisApp.hyperion.generation.run.typicalBand', band: 'inside' };
    }

    protected readonly typicalBandParams = { min: TYPICAL_DURATION_MIN_MINUTES, max: TYPICAL_DURATION_MAX_MINUTES };

    constructor() {
        effect(() => {
            if (this.terminal()) {
                this.runEnded.next();
            }
        });
    }

    protected confirmCancel(): void {
        this.confirmationService.confirm({
            key: CANCEL_CONFIRMATION_KEY,
            header: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmHeader'),
            message: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmMessage'),
            acceptLabel: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmAccept'),
            rejectLabel: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmReject'),
            acceptSeverity: 'danger',
            accept: () => this.cancelRequested.emit(),
        });
    }
}
