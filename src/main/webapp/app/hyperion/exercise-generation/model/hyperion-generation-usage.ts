import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { ExerciseGenerationLiveUsage } from 'app/openapi/model/exercise-generation-live-usage';
import { ExerciseGenerationStatusAccountingStateEnum } from 'app/openapi/model/exercise-generation-status';
import { ExerciseGenerationUsage } from 'app/openapi/model/exercise-generation-usage';

/** How complete the reported spend is: still accumulating, sealed, or a permanent lower bound. */
export type HyperionAccountingState = ExerciseGenerationStatusAccountingStateEnum;

/**
 * What the run cost, in the only three shapes that can be said honestly.
 *
 * `notPriced` is not zero and not free: it means no price is configured for a model the run used, so no amount may be
 * rendered at all. `lowerBound` means some responses were priced and others were not, so the figure is a floor.
 */
export type HyperionSpendCost = { kind: 'amount'; eur: number } | { kind: 'lowerBound'; eur: number } | { kind: 'notPriced' };

/** The run's share of its token ceiling. Only ever built when the deployment actually configured one. */
export interface HyperionSpendBudget {
    /** Tokens charged against the ceiling, with cached input already discounted at the configured weight. */
    billableTokens: number;
    /** The ceiling itself, always greater than zero. */
    tokenBudget: number;
    /** `billableTokens` as a percentage of `tokenBudget`, rounded, and not capped at 100 - a run may overshoot. */
    percent: number;
}

/**
 * Everything the spend surface renders, derived once from the wire and free of any formatting.
 *
 * Deliberately not a passthrough of either DTO: a live snapshot and a sealed total carry different fields with
 * different guarantees, and every surface that shows spend has to make the same statements about them.
 */
export interface HyperionSpendView {
    accounting: HyperionAccountingState;
    /**
     * Tokens charged against the run's budget. Present only from a live snapshot - the sealed total does not report
     * one, because cached input is discounted at a weight that is not part of the terminal account.
     */
    billableTokens?: number;
    /** Present only when a ceiling was configured and a live snapshot supplied the figure charged against it. */
    budget?: HyperionSpendBudget;
    inputTokens: number;
    outputTokens: number;
    cachedInputTokens: number;
    /** False when the provider did not report a cached split for every response, which makes the cached share a floor. */
    cachedExact: boolean;
    modelCalls: number;
    /** Present only on a sealed total; a live snapshot does not carry the agent's own loop bookkeeping. */
    agentTurns?: number;
    attempts?: number;
    models: readonly string[];
    cost: HyperionSpendCost;
}

/**
 * The one figure a spend surface leads with, in the only two units that can ever be real.
 *
 * Money is the hero when money is known. When it is not - our own deployment configures no price for the model these
 * runs use - the hero is the token figure that *was* measured, plainly labelled, and the missing currency amount is
 * demoted to a sentence. It is never a hero-sized word, and never a zero: a surface whose largest element is the word
 * "Not priced" spends its most prominent position admitting it has nothing to show.
 *
 * `lowerBound` is the "at least" prefix. It is set from the accounting state, because an account that could not be
 * closed makes every figure derived from it a floor - and "at least nothing" is still a claim of free, which is why a
 * zero can never carry it (a zero amount is reported as `notPriced` before it ever reaches here).
 */
export type HyperionSpendHero =
    | { kind: 'money'; eur: number; lowerBound: boolean }
    /** `billable` is charged against the run's ceiling with cached input already discounted; `total` is input + output. */
    | { kind: 'tokens'; unit: 'billable' | 'total'; tokens: number; lowerBound: boolean };

/** Where a run's share of its token ceiling stops being background information and starts being a warning. */
export const BUDGET_NEAR_PERCENT = 75;
/** A run is only "over budget" once it is past the ceiling. Calling 90% "over" would be the colour lying in words. */
export const BUDGET_OVER_PERCENT = 100;

/** How a share of the ceiling reads in words, so the meter's colour is never the only thing that crossed a threshold. */
export type HyperionBudgetLevel = 'within' | 'near' | 'over';

export function budgetLevel(percent: number): HyperionBudgetLevel {
    return percent >= BUDGET_OVER_PERCENT ? 'over' : percent >= BUDGET_NEAR_PERCENT ? 'near' : 'within';
}

/**
 * The hero figure for a spend view: whichever unit is actually measured.
 *
 * A partly-priced total of zero is already `notPriced` by the time it gets here, so the tokens branch covers both "no
 * price is configured at all" and "nothing that ran had one".
 */
export function spendHero(view: HyperionSpendView): HyperionSpendHero {
    // Only a permanently unclosable account makes a figure a floor; a running total is provisional, which the
    // accounting tag says in its own words rather than by prefixing every number with "at least".
    const lowerBound = view.accounting === 'INCOMPLETE';
    if (view.cost.kind === 'amount') {
        return { kind: 'money', eur: view.cost.eur, lowerBound };
    }
    if (view.cost.kind === 'lowerBound') {
        return { kind: 'money', eur: view.cost.eur, lowerBound: true };
    }
    // The sealed total carries no billable figure - cached input is discounted at a weight that is not part of the
    // terminal account - so a finished unpriced run leads with the two counts it does have, under a different word.
    return view.billableTokens !== undefined
        ? { kind: 'tokens', unit: 'billable', tokens: view.billableTokens, lowerBound }
        : { kind: 'tokens', unit: 'total', tokens: view.inputTokens + view.outputTokens, lowerBound };
}

export interface HyperionSpendInput {
    /** The newest live snapshot the run streamed, or `undefined` when none has arrived or survived the transcript bound. */
    liveUsage?: ExerciseGenerationLiveUsage;
    /** The run's usage as the status endpoint reports it: a snapshot while running, the sealed total once it ended. */
    usage?: ExerciseGenerationUsage;
    accountingState?: HyperionAccountingState;
    /** Whether the caller started this run. A non-owner is never shown spend, whatever else arrived. */
    ownedByCaller: boolean;
    /** Whether the run has ended, which is what makes the status usage authoritative over any streamed snapshot. */
    terminal: boolean;
}

/** The newest live spend snapshot in a transcript. Phase boundaries carry one too, so this survives progress-line eviction. */
export function newestLiveUsage(events: readonly HyperionGenerationEvent[]): ExerciseGenerationLiveUsage | undefined {
    return events.findLast((event) => event.liveUsage !== undefined)?.liveUsage;
}

/**
 * What this run has spent, or `undefined` when there is nothing that may honestly be shown.
 *
 * Returns nothing rather than a zeroed panel in the two cases where a panel would be a lie by omission: a caller who
 * is not the owner (the server withholds the figures from them, so an empty meter would only suggest a free run), and
 * a run that has not been charged for anything yet.
 *
 * While the run is going the streamed snapshot wins, because it is the only source that carries the budget. Once it
 * has ended the sealed total wins, because a snapshot from the last phase boundary is not the run's final account.
 */
export function spendView({ liveUsage, usage, accountingState, ownedByCaller, terminal }: HyperionSpendInput): HyperionSpendView | undefined {
    if (!ownedByCaller) {
        return undefined;
    }
    const accounting = accountingState ?? 'PENDING';
    if (terminal && usage) {
        return fromSealedUsage(usage, accounting);
    }
    if (liveUsage) {
        return fromLiveUsage(liveUsage, accounting);
    }
    return usage ? fromSealedUsage(usage, accounting) : undefined;
}

function fromLiveUsage(usage: ExerciseGenerationLiveUsage, accounting: HyperionAccountingState): HyperionSpendView | undefined {
    if (usage.modelCalls <= 0 && usage.inputTokens <= 0 && usage.outputTokens <= 0 && usage.billableTokens <= 0) {
        return undefined;
    }
    const billableTokens = Math.max(0, usage.billableTokens);
    return {
        accounting,
        billableTokens,
        // A ceiling of zero means the deployment configured none, so there is no proportion to draw and none is invented.
        budget: usage.tokenBudget > 0 ? { billableTokens, tokenBudget: usage.tokenBudget, percent: Math.round((billableTokens / usage.tokenBudget) * 100) } : undefined,
        inputTokens: Math.max(0, usage.inputTokens),
        outputTokens: Math.max(0, usage.outputTokens),
        cachedInputTokens: Math.max(0, usage.cachedInputTokens),
        // The live contract carries no completeness flag for the cached split, so it is reported as given rather than
        // hedged; the accounting state above already says the whole snapshot is provisional.
        cachedExact: true,
        modelCalls: Math.max(0, usage.modelCalls),
        models: [],
        cost: liveCost(usage),
    };
}

function fromSealedUsage(usage: ExerciseGenerationUsage, accounting: HyperionAccountingState): HyperionSpendView | undefined {
    if (usage.modelCalls <= 0 && usage.inputTokens <= 0 && usage.outputTokens <= 0) {
        return undefined;
    }
    return {
        accounting,
        inputTokens: Math.max(0, usage.inputTokens),
        outputTokens: Math.max(0, usage.outputTokens),
        cachedInputTokens: Math.max(0, usage.cachedInputTokens),
        cachedExact: usage.cachedInputTokensComplete,
        modelCalls: Math.max(0, usage.modelCalls),
        agentTurns: usage.agentTurns > 0 ? usage.agentTurns : undefined,
        attempts: usage.attempts > 0 ? usage.attempts : undefined,
        models: usage.models ?? [],
        cost: sealedCost(usage),
    };
}

/** An absent amount is the server saying no price was configured, which is never the same as a cost of nothing. */
function liveCost(usage: ExerciseGenerationLiveUsage): HyperionSpendCost {
    if (usage.estimatedCostEur === undefined) {
        return { kind: 'notPriced' };
    }
    if (usage.estimatedCostComplete) {
        return { kind: 'amount', eur: usage.estimatedCostEur };
    }
    return usage.estimatedCostEur > 0 ? { kind: 'lowerBound', eur: usage.estimatedCostEur } : { kind: 'notPriced' };
}

/**
 * The sealed total always carries a number, so the flag is what decides whether it may be read as the cost.
 *
 * An incomplete estimate of zero priced nothing at all, and reporting "at least 0" would be a fact-shaped way of
 * saying the run was free.
 */
function sealedCost(usage: ExerciseGenerationUsage): HyperionSpendCost {
    if (usage.estimatedCostEurComplete) {
        return { kind: 'amount', eur: usage.estimatedCostEur };
    }
    return usage.estimatedCostEur > 0 ? { kind: 'lowerBound', eur: usage.estimatedCostEur } : { kind: 'notPriced' };
}

/** A whole token count in the reader's locale, so a five-figure counter is grouped rather than a run of digits. */
export function formatTokenCount(value: number, locale: string): string {
    return new Intl.NumberFormat(locale).format(Math.max(0, Math.round(value)));
}

/**
 * An amount in EUR in the reader's locale.
 *
 * A real cost below one cent is rendered with four decimals rather than rounded: "0.00 EUR" for a run that did cost
 * something is exactly the claim this surface exists to avoid.
 */
export function formatEuro(value: number, locale: string): string {
    const fractionDigits = value > 0 && value < 0.01 ? 4 : 2;
    return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: 'EUR',
        minimumFractionDigits: fractionDigits,
        maximumFractionDigits: fractionDigits,
    }).format(value);
}

/** A whole-number percentage in the reader's locale, from a value already expressed in percent. */
export function formatPercent(percent: number, locale: string): string {
    return new Intl.NumberFormat(locale, { style: 'percent', maximumFractionDigits: 0 }).format(percent / 100);
}
