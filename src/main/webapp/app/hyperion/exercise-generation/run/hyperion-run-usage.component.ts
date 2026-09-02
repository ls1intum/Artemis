import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { TumUiMessageComponent, TumUiProgressBarComponent, TumUiProgressBarSeverity, TumUiTagComponent, TumUiTagSeverity } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { formatClockTime } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import {
    HyperionAccountingState,
    HyperionBudgetLevel,
    HyperionSpendView,
    budgetLevel,
    formatEuro,
    formatPercent,
    formatTokenCount,
    spendHero,
} from 'app/hyperion/exercise-generation/model/hyperion-generation-usage';

/**
 * The one figure this surface leads with, resolved to the single statement that may honestly be made about it.
 *
 * `unitKey` is set only for a token hero: money carries its unit in the formatted amount, a token count does not, and
 * a bare five-figure number with no word beside it is not a spend figure at all.
 */
interface HeroLine {
    kind: 'money' | 'tokens';
    value: string;
    /** Renders the "at least" prefix. Never set on a zero - a zero reaches this component as `notPriced` instead. */
    lowerBound: boolean;
    unitKey?: string;
    /** The plainly-labelled sentence under the figure, which is where the unavailable unit is demoted to. */
    captionKey: string;
}

/** The meter, with its ceiling, its share and the word for the threshold it has crossed all already resolved. */
interface BudgetLine {
    percent: number;
    severity: TumUiProgressBarSeverity;
    used: string;
    budget: string;
    share: string;
    level: HyperionBudgetLevel;
    levelKey: string;
    /**
     * The whole reading in one sentence, for `aria-valuetext`.
     *
     * Without it the meter reaches a screen reader as a bare "25 percent": the unit, the ceiling and the word for the
     * threshold it has crossed are all in the text beside the bar, which the bar's own reading does not include.
     */
    valueText: string;
}

/** One row of the breakdown, with its number already formatted and its word already chosen. */
interface UsageFigure {
    key: string;
    labelKey: string;
    value: string;
    /** True when the number is a floor rather than an exact count, which the row has to say in words. */
    lowerBound: boolean;
}

const ACCOUNTING_LABEL_KEY: Record<HyperionAccountingState, string> = {
    PENDING: 'artemisApp.hyperion.generation.usage.state.pending',
    COMPLETE: 'artemisApp.hyperion.generation.usage.state.complete',
    INCOMPLETE: 'artemisApp.hyperion.generation.usage.state.incomplete',
};

const ACCOUNTING_SEVERITY: Record<HyperionAccountingState, TumUiTagSeverity> = {
    PENDING: 'info',
    COMPLETE: 'secondary',
    INCOMPLETE: 'warning',
};

const BUDGET_SEVERITY: Record<HyperionBudgetLevel, TumUiProgressBarSeverity> = {
    within: 'primary',
    near: 'warning',
    over: 'danger',
};

const BUDGET_LEVEL_KEY: Record<HyperionBudgetLevel, string> = {
    within: 'artemisApp.hyperion.generation.usage.budgetWithin',
    near: 'artemisApp.hyperion.generation.usage.budgetNear',
    over: 'artemisApp.hyperion.generation.usage.budgetOver',
};

/**
 * What this run has spent, for the instructor who started it.
 *
 * The hero figure is whichever unit is actually measured: money when money is known, billable tokens when it is not.
 * That is the whole design of this surface. Our own deployment configures no price for the model these runs use, so
 * the previous hero slot held the *word* "Not priced" - a surface whose largest element admitted it had nothing to
 * show. The currency amount is not deleted, it is demoted to a plainly labelled sentence.
 *
 * Every statement here is bounded by what the server actually knows. A cost with no configured price is reported in
 * words rather than as zero; an account that could not be closed is a lower bound rather than a total, and "at least
 * nothing" is never said; a deployment without a token ceiling gets no proportion at all instead of a bar drawn
 * against a divisor of zero; and an instructor who does not own the run is shown no panel rather than an empty one.
 * The numbers are locale-formatted and tabular, so a counter that ticks while a run works does not shift the layout.
 *
 * States: **running** (running-total tag, hero moving), **sealed** (final tag plus the moment it was sealed),
 * **incomplete** (warning tag, every figure a floor, explanatory message), **unpriced** (token hero), **no ceiling**
 * (figure, no bar), **withheld** (this component is not rendered at all - see `spendView`).
 */
@Component({
    selector: 'jhi-hyperion-run-usage',
    templateUrl: './hyperion-run-usage.component.html',
    styleUrl: './hyperion-run-usage.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, TumUiMessageComponent, TumUiProgressBarComponent, TumUiTagComponent],
})
export class HyperionRunUsageComponent {
    private readonly translateService = inject(TranslateService);

    readonly spend = input.required<HyperionSpendView>();
    /** `compact` collapses the whole surface to one line so it fits the code editor's bottom panel. */
    readonly density = input<'full' | 'compact'>('full');
    /**
     * ISO timestamp of the moment the account was sealed, so a figure read a week later is not mistaken for a live one.
     *
     * Only meaningful once the accounting state is `COMPLETE`; a running total has nothing to stamp.
     */
    readonly sealedAt = input<string | undefined>();

    /** Re-read on every language change, because a number formatted for one locale is wrong in the other. */
    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });
    private readonly locale = computed(() => {
        this.languageChange();
        return this.translateService.getCurrentLang() ?? 'en';
    });

    protected readonly compact = computed(() => this.density() === 'compact');

    protected readonly accountingLabelKey = computed(() => ACCOUNTING_LABEL_KEY[this.spend().accounting]);
    protected readonly accountingSeverity = computed(() => ACCOUNTING_SEVERITY[this.spend().accounting]);
    /** The one accounting state that has to be explained rather than merely labelled. */
    protected readonly incomplete = computed(() => this.spend().accounting === 'INCOMPLETE');

    /** The moment a sealed total stopped moving, shown beside it so a stale reading cannot pass for a live one. */
    protected readonly sealedTime = computed(() => {
        const sealedAt = this.sealedAt();
        return this.spend().accounting === 'COMPLETE' && sealedAt ? formatClockTime(sealedAt) : undefined;
    });

    protected readonly hero = computed<HeroLine>(() => {
        const spend = this.spend();
        const hero = spendHero(spend);
        const locale = this.locale();
        if (hero.kind === 'money') {
            return {
                kind: 'money',
                value: formatEuro(hero.eur, locale),
                lowerBound: hero.lowerBound,
                captionKey:
                    spend.cost.kind === 'lowerBound'
                        ? 'artemisApp.hyperion.generation.usage.costPartialHint'
                        : spend.accounting === 'PENDING'
                          ? 'artemisApp.hyperion.generation.usage.costSoFar'
                          : 'artemisApp.hyperion.generation.usage.costFinal',
            };
        }
        return {
            kind: 'tokens',
            value: formatTokenCount(hero.tokens, locale),
            lowerBound: hero.lowerBound,
            unitKey: hero.unit === 'billable' ? 'artemisApp.hyperion.generation.usage.billableTokens' : 'artemisApp.hyperion.generation.usage.totalTokens',
            // The currency amount is not hidden, it is demoted: this sentence is where "no price is configured" lives.
            captionKey: 'artemisApp.hyperion.generation.usage.notPricedHint',
        };
    });

    protected readonly budget = computed<BudgetLine | undefined>(() => {
        const budget = this.spend().budget;
        if (!budget) {
            return undefined;
        }
        const locale = this.locale();
        const level = budgetLevel(budget.percent);
        const used = formatTokenCount(budget.billableTokens, locale);
        const ceiling = formatTokenCount(budget.tokenBudget, locale);
        const levelKey = BUDGET_LEVEL_KEY[level];
        return {
            percent: budget.percent,
            severity: BUDGET_SEVERITY[level],
            used,
            budget: ceiling,
            share: formatPercent(budget.percent, locale),
            level,
            // Every threshold the bar's colour crosses also gets a word, so the colour is never the only signal.
            levelKey,
            valueText: this.translateService.instant('artemisApp.hyperion.generation.usage.budgetValueText', {
                used,
                budget: ceiling,
                level: this.translateService.instant(levelKey),
            }),
        };
    });

    /** Set only when tokens were charged but the deployment configured no ceiling to charge them against. */
    protected readonly uncappedTokens = computed(() => {
        const spend = this.spend();
        return spend.budget === undefined && spend.billableTokens !== undefined ? formatTokenCount(spend.billableTokens, this.locale()) : undefined;
    });

    protected readonly figures = computed<UsageFigure[]>(() => {
        const spend = this.spend();
        const locale = this.locale();
        const rows: UsageFigure[] = [
            { key: 'input', labelKey: 'artemisApp.hyperion.generation.usage.inputLabel', value: formatTokenCount(spend.inputTokens, locale), lowerBound: false },
            { key: 'output', labelKey: 'artemisApp.hyperion.generation.usage.outputLabel', value: formatTokenCount(spend.outputTokens, locale), lowerBound: false },
            {
                key: 'cached',
                labelKey: 'artemisApp.hyperion.generation.usage.cachedLabel',
                value: formatTokenCount(spend.cachedInputTokens, locale),
                // The provider did not report a cached split for every response, so this is a floor and must say so.
                lowerBound: !spend.cachedExact,
            },
            { key: 'modelCalls', labelKey: 'artemisApp.hyperion.generation.usage.modelCallsLabel', value: formatTokenCount(spend.modelCalls, locale), lowerBound: false },
        ];
        if (spend.agentTurns !== undefined) {
            rows.push({ key: 'turns', labelKey: 'artemisApp.hyperion.generation.usage.agentTurnsLabel', value: formatTokenCount(spend.agentTurns, locale), lowerBound: false });
        }
        if (spend.attempts !== undefined) {
            rows.push({ key: 'attempts', labelKey: 'artemisApp.hyperion.generation.usage.attemptsLabel', value: formatTokenCount(spend.attempts, locale), lowerBound: false });
        }
        return rows;
    });
}
