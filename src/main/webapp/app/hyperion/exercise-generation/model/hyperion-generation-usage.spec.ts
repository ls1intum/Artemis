import { describe, expect, it } from 'vitest';

import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import {
    BUDGET_NEAR_PERCENT,
    BUDGET_OVER_PERCENT,
    budgetLevel,
    formatEuro,
    formatTokenCount,
    newestLiveUsage,
    spendHero,
    spendView,
} from 'app/hyperion/exercise-generation/model/hyperion-generation-usage';
import { ExerciseGenerationLiveUsage } from 'app/openapi/model/exercise-generation-live-usage';
import { ExerciseGenerationUsage } from 'app/openapi/model/exercise-generation-usage';

function live(partial: Partial<ExerciseGenerationLiveUsage> = {}): ExerciseGenerationLiveUsage {
    return {
        inputTokens: 90_000,
        outputTokens: 10_000,
        cachedInputTokens: 40_000,
        billableTokens: 60_000,
        tokenBudget: 1_000_000,
        modelCalls: 12,
        estimatedCostEur: 0.42,
        estimatedCostComplete: true,
        ...partial,
    };
}

function sealed(partial: Partial<ExerciseGenerationUsage> = {}): ExerciseGenerationUsage {
    return {
        modelCalls: 24,
        toolCalls: 60,
        agentTurns: 18,
        attempts: 2,
        inputTokens: 180_000,
        outputTokens: 20_000,
        cachedInputTokens: 80_000,
        cachedInputTokensComplete: true,
        estimatedCostEur: 0.84,
        estimatedCostEurComplete: true,
        models: ['gpt-5-mini'],
        providerRequestIds: ['req-1'],
        providerRequestIdsComplete: true,
        ...partial,
    };
}

describe('spendView', () => {
    it('reports nothing at all to an instructor who did not start the run', () => {
        // The server withholds the figures from anyone but the owner, so a panel here could only ever be zeroed - and
        // a zeroed spend panel reads as a run that cost nothing.
        expect(spendView({ liveUsage: live(), usage: sealed(), accountingState: 'INCOMPLETE', ownedByCaller: false, terminal: true })).toBeUndefined();
    });

    it('reports nothing before the run has been charged for anything', () => {
        const untouched = live({ inputTokens: 0, outputTokens: 0, cachedInputTokens: 0, billableTokens: 0, modelCalls: 0, estimatedCostEur: 0 });

        expect(spendView({ liveUsage: untouched, ownedByCaller: true, terminal: false })).toBeUndefined();
    });

    it('prefers the streamed snapshot while the run is going, because it is the only source carrying the budget', () => {
        const view = spendView({ liveUsage: live(), usage: sealed(), accountingState: 'PENDING', ownedByCaller: true, terminal: false })!;

        expect(view.budget).toEqual({ billableTokens: 60_000, tokenBudget: 1_000_000, percent: 6 });
        expect(view.inputTokens).toBe(90_000);
        expect(view.accounting).toBe('PENDING');
    });

    it('falls back to the status usage when no snapshot has been streamed yet, so a reload is not blank', () => {
        const view = spendView({ usage: sealed(), accountingState: 'PENDING', ownedByCaller: true, terminal: false })!;

        expect(view.inputTokens).toBe(180_000);
        expect(view.modelCalls).toBe(24);
        // The sealed shape carries no billable figure, so there is no proportion to draw from it.
        expect(view.budget).toBeUndefined();
        expect(view.billableTokens).toBeUndefined();
    });

    it('lets the sealed total win once the run has ended, over any snapshot left in the transcript', () => {
        const view = spendView({ liveUsage: live(), usage: sealed(), accountingState: 'COMPLETE', ownedByCaller: true, terminal: true })!;

        expect(view.inputTokens).toBe(180_000);
        expect(view.agentTurns).toBe(18);
        expect(view.attempts).toBe(2);
        expect(view.models).toEqual(['gpt-5-mini']);
    });

    it('draws no proportion when the deployment configured no token ceiling', () => {
        const view = spendView({ liveUsage: live({ tokenBudget: 0 }), ownedByCaller: true, terminal: false })!;

        expect(view.budget).toBeUndefined();
        // The spend itself is still reported; only the share of a ceiling that does not exist is withheld.
        expect(view.billableTokens).toBe(60_000);
    });

    it('reports an unpriced run as not priced rather than as free', () => {
        const view = spendView({ liveUsage: live({ estimatedCostEur: undefined, estimatedCostComplete: false }), ownedByCaller: true, terminal: false })!;

        expect(view.cost).toEqual({ kind: 'notPriced' });
    });

    it('reports a partially priced total as a floor, and a floor of zero as not priced at all', () => {
        const partial = spendView({ usage: sealed({ estimatedCostEurComplete: false }), accountingState: 'INCOMPLETE', ownedByCaller: true, terminal: true })!;
        expect(partial.cost).toEqual({ kind: 'lowerBound', eur: 0.84 });

        const nothingPriced = spendView({
            usage: sealed({ estimatedCostEur: 0, estimatedCostEurComplete: false }),
            accountingState: 'INCOMPLETE',
            ownedByCaller: true,
            terminal: true,
        })!;
        // "At least nothing" is a fact-shaped way of saying the run was free, so it is not said.
        expect(nothingPriced.cost).toEqual({ kind: 'notPriced' });
    });

    it('marks the cached share as inexact when the provider did not report it for every response', () => {
        const view = spendView({ usage: sealed({ cachedInputTokensComplete: false }), accountingState: 'COMPLETE', ownedByCaller: true, terminal: true })!;

        expect(view.cachedExact).toBe(false);
    });

    it('treats an unknown accounting state as still accumulating rather than as sealed', () => {
        expect(spendView({ liveUsage: live(), ownedByCaller: true, terminal: false })!.accounting).toBe('PENDING');
    });
});

describe('spendHero', () => {
    function hero(input: Parameters<typeof spendView>[0]) {
        return spendHero(spendView(input)!);
    }

    it('leads with money whenever money is known', () => {
        expect(hero({ liveUsage: live(), accountingState: 'PENDING', ownedByCaller: true, terminal: false })).toEqual({
            kind: 'money',
            eur: 0.42,
            lowerBound: false,
        });
    });

    it('leads with the tokens it did measure when nothing was priced, rather than with a word', () => {
        // The hero slot is for a figure. Spending it on the string "Not priced" makes the largest element on the
        // surface an admission that it has nothing to show, while a real, measured number is sitting right there.
        expect(hero({ liveUsage: live({ estimatedCostEur: undefined, estimatedCostComplete: false }), ownedByCaller: true, terminal: false })).toEqual({
            kind: 'tokens',
            unit: 'billable',
            tokens: 60_000,
            lowerBound: false,
        });
    });

    it('falls back to input plus output on a sealed total, which carries no billable figure', () => {
        // Cached input is discounted at a weight that is not part of the terminal account, so the sealed shape has no
        // billable count at all - and the two counts it does have are reported under their own, different word.
        expect(hero({ usage: sealed({ estimatedCostEur: 0, estimatedCostEurComplete: false }), accountingState: 'COMPLETE', ownedByCaller: true, terminal: true })).toEqual({
            kind: 'tokens',
            unit: 'total',
            tokens: 200_000,
            lowerBound: false,
        });
    });

    it('marks a partly priced amount as a floor, and never "at least nothing"', () => {
        expect(hero({ usage: sealed({ estimatedCostEurComplete: false }), accountingState: 'INCOMPLETE', ownedByCaller: true, terminal: true })).toEqual({
            kind: 'money',
            eur: 0.84,
            lowerBound: true,
        });

        // A priced total of zero is not a lower bound of zero; it is a run whose cost is unknown, so tokens lead.
        expect(hero({ usage: sealed({ estimatedCostEur: 0, estimatedCostEurComplete: false }), accountingState: 'INCOMPLETE', ownedByCaller: true, terminal: true })).toEqual({
            kind: 'tokens',
            unit: 'total',
            tokens: 200_000,
            lowerBound: true,
        });
    });

    it('makes every figure of an account that could not be closed a lower bound', () => {
        expect(hero({ liveUsage: live(), accountingState: 'INCOMPLETE', ownedByCaller: true, terminal: false }).lowerBound).toBe(true);
        // A running total is provisional rather than a floor; the accounting tag says so in its own words.
        expect(hero({ liveUsage: live(), accountingState: 'PENDING', ownedByCaller: true, terminal: false }).lowerBound).toBe(false);
    });
});

describe('budgetLevel', () => {
    it('gives every colour the bar can take a word to go with it', () => {
        expect(budgetLevel(0)).toBe('within');
        expect(budgetLevel(BUDGET_NEAR_PERCENT - 1)).toBe('within');
        expect(budgetLevel(BUDGET_NEAR_PERCENT)).toBe('near');
        expect(budgetLevel(BUDGET_OVER_PERCENT - 1)).toBe('near');
        // "Over budget" means past the ceiling. Saying it at 90% would be the word contradicting the number beside it.
        expect(budgetLevel(BUDGET_OVER_PERCENT)).toBe('over');
        expect(budgetLevel(140)).toBe('over');
    });
});

describe('newestLiveUsage', () => {
    it('takes the newest snapshot in the transcript', () => {
        const events: HyperionGenerationEvent[] = [
            { type: 'PROGRESS', timestamp: '2026-01-01T10:00:00Z', liveUsage: live({ modelCalls: 1 }) },
            { type: 'PROGRESS', timestamp: '2026-01-01T10:01:00Z', message: 'no usage here' },
            { type: 'PROGRESS', timestamp: '2026-01-01T10:02:00Z', liveUsage: live({ modelCalls: 9 }) },
        ];

        expect(newestLiveUsage(events)?.modelCalls).toBe(9);
    });

    it('is undefined for a transcript that carries none', () => {
        expect(newestLiveUsage([{ type: 'STARTED', timestamp: '2026-01-01T10:00:00Z' }])).toBeUndefined();
    });
});

describe('formatting', () => {
    it('groups a token count for the reader', () => {
        expect(formatTokenCount(1234567, 'en')).toBe('1,234,567');
        expect(formatTokenCount(1234567, 'de')).toBe('1.234.567');
    });

    it('never rounds a real cost down to nothing', () => {
        // Half a cent is not zero, and rendering it as "0.00" would say the run was free.
        expect(formatEuro(0.004, 'en')).toBe('€0.0040');
        expect(formatEuro(1.5, 'en')).toBe('€1.50');
    });
});
