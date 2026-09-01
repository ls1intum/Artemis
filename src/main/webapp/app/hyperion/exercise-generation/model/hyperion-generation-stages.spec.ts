import { describe, expect, it } from 'vitest';
import { TumUiStepState } from '@tumaet/ui-angular';

import { HYPERION_STAGES, HyperionStageKey, runOutcome, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

let clock = 0;

/** A server event with only the fields the ladder reads, so a test reads as the wire trace it stands for. */
function event(partial: Partial<HyperionGenerationEvent> & Pick<HyperionGenerationEvent, 'type'>): HyperionGenerationEvent {
    return { timestamp: new Date(Date.UTC(2026, 0, 1, 0, 0, clock++)).toISOString(), ...partial };
}

/** The ladder for a trace, keyed by stage so an assertion names the stage it is about. */
function ladder(events: readonly HyperionGenerationEvent[]): Record<HyperionStageKey, TumUiStepState> {
    const stages = stageStates(events, runOutcome(events));
    return Object.fromEntries(stages.map((stage) => [stage.key, stage.state])) as Record<HyperionStageKey, TumUiStepState>;
}

function count(events: readonly HyperionGenerationEvent[], state: TumUiStepState): number {
    return stageStates(events, runOutcome(events)).filter((stage) => stage.state === state).length;
}

describe('hyperion generation stages', () => {
    it('completes every stage on a successful run, even though the server stamps SAVING on the DONE event', () => {
        // The regression this guards: reading the ladder from the phase alone left "Save the draft" spinning forever
        // after a run had already succeeded.
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING' }),
            event({ type: 'PROGRESS', phase: 'VERIFYING' }),
            event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' }),
        ];

        expect(count(events, 'complete')).toBe(HYPERION_STAGES.length);
        expect(count(events, 'current')).toBe(0);
    });

    it('completes every stage when the run was saved but needs review', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'DONE', phase: 'SAVING', completionStatus: 'NEEDS_REVIEW' })];

        expect(runOutcome(events)).toBe('needsReview');
        expect(count(events, 'complete')).toBe(HYPERION_STAGES.length);
        expect(count(events, 'current')).toBe(0);
    });

    it('marks exactly the stage that failed and skips the rest on an error', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'ERROR', message: 'agent crashed' })];

        expect(runOutcome(events)).toBe('failed');
        expect(ladder(events)).toEqual({ prepare: 'failed', design: 'skipped', build: 'skipped', review: 'skipped', save: 'skipped' });
        expect(count(events, 'failed')).toBe(1);
        expect(count(events, 'current')).toBe(0);
    });

    it('keeps the work already done and fails only the stage that was running when the error hit', () => {
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING' }),
            event({ type: 'PROGRESS', phase: 'VERIFYING' }),
            event({ type: 'ERROR', message: 'build environment unavailable' }),
        ];

        expect(ladder(events)).toEqual({ prepare: 'complete', design: 'complete', build: 'failed', review: 'skipped', save: 'skipped' });
        expect(count(events, 'failed')).toBe(1);
    });

    it('leaves nothing running after a cancellation', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING' }), event({ type: 'CANCELLED' })];

        expect(runOutcome(events)).toBe('cancelled');
        expect(count(events, 'current')).toBe(0);
        expect(count(events, 'failed')).toBe(0);
        expect(ladder(events)).toEqual({ prepare: 'complete', design: 'skipped', build: 'skipped', review: 'skipped', save: 'skipped' });
    });

    it('does not regress a completed stage when a repair loop sends the run back to building', () => {
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING' }),
            event({ type: 'PROGRESS', phase: 'VERIFYING' }),
            event({ type: 'PROGRESS', phase: 'REVIEWING' }),
            event({ type: 'PROGRESS', phase: 'VERIFYING' }),
        ];

        const states = ladder(events);
        expect(states.prepare).toBe('complete');
        expect(states.design).toBe('complete');
        // The spinner follows the work back to the stage that is actually running, and only one stage ever runs.
        expect(states.build).toBe('current');
        expect(count(events, 'current')).toBe(1);
    });

    it('fails only the save stage on a partial save, because everything before it really did run', () => {
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'VERIFYING' }),
            event({ type: 'DONE', phase: 'SAVING', completionStatus: 'PARTIAL' }),
        ];

        expect(runOutcome(events)).toBe('partial');
        expect(ladder(events)).toEqual({ prepare: 'complete', design: 'complete', build: 'complete', review: 'complete', save: 'failed' });
    });

    it('shows the first stage as running for a job that has started but not reported a phase yet', () => {
        const events = [event({ type: 'STARTED' })];

        expect(runOutcome(events)).toBeUndefined();
        expect(ladder(events).prepare).toBe('current');
        expect(count(events, 'current')).toBe(1);
    });

    it('shows nothing as running before any event arrived', () => {
        expect(count([], 'current')).toBe(0);
        expect(count([], 'pending')).toBe(HYPERION_STAGES.length);
    });
});
