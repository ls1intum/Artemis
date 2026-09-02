import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { TumUiMessageComponent, TumUiProgressBarComponent, TumUiProgressBarSeverity, TumUiTagComponent, TumUiTagSeverity } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionAccountingState, HyperionSpendView, formatEuro, formatPercent, formatTokenCount } from 'app/hyperion/exercise-generation/model/hyperion-generation-usage';

/** The headline cost, resolved to the one statement that may be made about it. */
interface CostLine {
    /** The formatted amount, or `undefined` when nothing may be shown in its place but a word. */
    amount?: string;
    /** `notPriced` renders a word instead of a figure; `lowerBound` renders the amount as a floor. */
    kind: 'amount' | 'lowerBound' | 'notPriced';
    captionKey: string;
}

interface BudgetLine {
    percent: number;
    severity: TumUiProgressBarSeverity;
    used: string;
    budget: string;
    share: string;
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
    INCOMPLETE: 'warn',
};

/** Where a run's share of its token ceiling stops being background information and starts being a warning. */
const BUDGET_WARN_PERCENT = 75;
const BUDGET_DANGER_PERCENT = 90;

/**
 * What this run has spent, for the instructor who started it.
 *
 * Every statement here is bounded by what the server actually knows. A cost with no configured price is reported as
 * "not priced" in words rather than as zero; an account that could not be closed says so rather than presenting a
 * lower bound as a total; and a deployment without a token ceiling gets no proportion at all instead of a bar drawn
 * against a divisor of zero. The numbers are locale-formatted and tabular, so a counter that ticks while a run works
 * does not shift the layout under the reader.
 */
@Component({
    selector: 'jhi-hyperion-run-usage',
    templateUrl: './hyperion-run-usage.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, TumUiMessageComponent, TumUiProgressBarComponent, TumUiTagComponent],
})
export class HyperionRunUsageComponent {
    private readonly translateService = inject(TranslateService);

    readonly spend = input.required<HyperionSpendView>();
    /** `compact` drops the model list and tightens the type so the block fits the code editor's bottom panel. */
    readonly density = input<'full' | 'compact'>('full');

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
    /** The sentence spelling out the running-total tag, which the panel-sized variant has no room to repeat. */
    protected readonly pendingHint = computed(() => this.spend().accounting === 'PENDING' && !this.compact());

    protected readonly cost = computed<CostLine>(() => {
        const { cost, accounting } = this.spend();
        if (cost.kind === 'notPriced') {
            return { kind: 'notPriced', captionKey: 'artemisApp.hyperion.generation.usage.notPricedHint' };
        }
        const amount = formatEuro(cost.eur, this.locale());
        if (cost.kind === 'lowerBound') {
            return { kind: 'lowerBound', amount, captionKey: 'artemisApp.hyperion.generation.usage.costPartialHint' };
        }
        return {
            kind: 'amount',
            amount,
            captionKey: accounting === 'PENDING' ? 'artemisApp.hyperion.generation.usage.costSoFar' : 'artemisApp.hyperion.generation.usage.costFinal',
        };
    });

    protected readonly budget = computed<BudgetLine | undefined>(() => {
        const budget = this.spend().budget;
        if (!budget) {
            return undefined;
        }
        const locale = this.locale();
        return {
            percent: budget.percent,
            severity: budget.percent >= BUDGET_DANGER_PERCENT ? 'danger' : budget.percent >= BUDGET_WARN_PERCENT ? 'warn' : 'primary',
            used: formatTokenCount(budget.billableTokens, locale),
            budget: formatTokenCount(budget.tokenBudget, locale),
            share: formatPercent(budget.percent, locale),
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

    /** The models the run used, listed only where there is room for them. */
    protected readonly models = computed(() => {
        const models = this.spend().models;
        return !this.compact() && models.length > 0 ? models.join(', ') : undefined;
    });
}
