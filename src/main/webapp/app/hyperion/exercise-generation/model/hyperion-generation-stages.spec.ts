import { describe, expect, it } from 'vitest';
import { TumUiStepState } from '@tumaet/ui-angular';

import { HYPERION_STAGES, HyperionStageKey, HyperionSubstepKey, runOutcome, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { HyperionGenerationActivity, HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

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

/** Agent bookkeeping with only the fields a test is about; the rest is the quiet, plausible baseline. */
function activity(partial: Partial<HyperionGenerationActivity> = {}): HyperionGenerationActivity {
    return { attempt: 1, turn: 1, waitingOnModel: false, modelCalls: 0, toolCalls: 0, filesWritten: 0, ...partial };
}

/** The design stage's substeps for a trace, keyed by substep. */
function substeps(events: readonly HyperionGenerationEvent[]): Record<HyperionSubstepKey, TumUiStepState> | undefined {
    const design = stageStates(events, runOutcome(events)).find((stage) => stage.key === 'design');
    return design?.substeps ? (Object.fromEntries(design.substeps.map((substep) => [substep.key, substep.state])) as Record<HyperionSubstepKey, TumUiStepState>) : undefined;
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

describe('hyperion design substeps', () => {
    const designing = (step: HyperionSubstepKey, overrides: Partial<HyperionGenerationActivity> = {}) =>
        event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ step, ...overrides }) });

    it('reports no substeps before the agent has said which one it is on', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'PROGRESS', phase: 'DESIGNING' })];

        expect(substeps(events)).toBeUndefined();
    });

    it('completes what is behind the newest substep and leaves exactly one running', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), designing('concept'), designing('spec'), designing('artifacts')];

        expect(substeps(events)).toEqual({ concept: 'complete', spec: 'complete', artifacts: 'current', statement: 'pending' });
    });

    it('keeps a substep the agent already did complete when it steps back to it', () => {
        const events = [event({ type: 'STARTED' }), designing('concept'), designing('artifacts'), designing('spec')];

        // The spinner follows the work back, but nothing that really happened is un-done.
        expect(substeps(events)).toEqual({ concept: 'complete', spec: 'current', artifacts: 'complete', statement: 'pending' });
    });

    it('only ever attaches substeps to the design stage', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), designing('concept'), event({ type: 'PROGRESS', phase: 'VERIFYING' })];

        expect(stageStates(events, undefined).filter((stage) => stage.substeps !== undefined)).toHaveLength(1);
    });

    it.each([[event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' })], [event({ type: 'ERROR', message: 'agent crashed' })], [event({ type: 'CANCELLED' })]])(
        'leaves no substep running after %o',
        (terminal) => {
            // The bug class this guards: a terminal event that clears the stage ladder but leaves a spinner nested inside it.
            const events = [event({ type: 'STARTED', phase: 'PREPARING' }), designing('concept'), designing('spec'), terminal];

            const states = substeps(events)!;
            expect(Object.values(states)).not.toContain('current');
            expect(states.concept).toBe('complete');
            expect(states.statement).toBe('skipped');
        },
    );

    it('marks the substep the run failed in and never claims the ones after it', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), designing('concept'), designing('spec'), event({ type: 'ERROR', message: 'model unavailable' })];

        expect(substeps(events)).toEqual({ concept: 'complete', spec: 'failed', artifacts: 'skipped', statement: 'skipped' });
    });

    it('completes every substep the design stage got through when a later stage fails', () => {
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING' }),
            designing('concept'),
            designing('statement'),
            event({ type: 'PROGRESS', phase: 'VERIFYING' }),
            event({ type: 'ERROR', message: 'build environment unavailable' }),
        ];

        expect(substeps(events)).toEqual({ concept: 'complete', spec: 'complete', artifacts: 'complete', statement: 'complete' });
    });
});

describe('hyperion stage summaries', () => {
    function summary(events: readonly HyperionGenerationEvent[], key: HyperionStageKey) {
        return stageStates(events, runOutcome(events)).find((stage) => stage.key === key)?.summary;
    }

    it('says what a finished stage got done, and nothing about one that is still running', () => {
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING', activity: activity({ turn: 1 }) }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 2, filesWritten: 3 }) }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 3, filesWritten: 7 }) }),
            event({ type: 'PROGRESS', phase: 'VERIFYING', activity: activity({ turn: 4, filesWritten: 7 }) }),
        ];

        expect(summary(events, 'prepare')).toEqual({ turns: 1, files: 0 });
        expect(summary(events, 'design')).toEqual({ turns: 2, files: 7 });
        // The stage that is running has not finished anything yet, so it claims nothing.
        expect(summary(events, 'build')).toBeUndefined();
    });

    it("does not let a repair loop make one stage claim another stage's files", () => {
        const events = [
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 1, filesWritten: 4 }) }),
            event({ type: 'PROGRESS', phase: 'VERIFYING', activity: activity({ turn: 2, filesWritten: 4 }) }),
            event({ type: 'PROGRESS', phase: 'REVIEWING', activity: activity({ turn: 3, filesWritten: 6 }) }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 4, filesWritten: 6 }) }),
            event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' }),
        ];

        expect(summary(events, 'design')).toEqual({ turns: 2, files: 4 });
        expect(summary(events, 'build')).toEqual({ turns: 1, files: 0 });
        expect(summary(events, 'review')).toEqual({ turns: 1, files: 2 });
    });

    it('counts a turn once, in the stage it first appeared in', () => {
        const events = [
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 5 }) }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 5 }) }),
            event({ type: 'PROGRESS', phase: 'VERIFYING', activity: activity({ turn: 5 }) }),
            event({ type: 'PROGRESS', phase: 'VERIFYING', activity: activity({ attempt: 2, turn: 1 }) }),
            event({ type: 'CANCELLED' }),
        ];

        expect(summary(events, 'design')).toEqual({ turns: 1, files: 0 });
        expect(summary(events, 'build')).toEqual({ turns: 1, files: 0 });
    });

    it('reports nothing for a stage the agent never sent bookkeeping for', () => {
        const events = [event({ type: 'STARTED', phase: 'PREPARING' }), event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' })];

        expect(stageStates(events, runOutcome(events)).every((stage) => stage.summary === undefined)).toBe(true);
    });
});

describe('hyperion generation stage timing', () => {
    /** Every stage's accumulated time in seconds, keyed by stage. */
    function seconds(events: readonly HyperionGenerationEvent[]): Record<HyperionStageKey, number> {
        const stages = stageStates(events, runOutcome(events));
        return Object.fromEntries(stages.map((stage) => [stage.key, stage.elapsedMs / 1000])) as Record<HyperionStageKey, number>;
    }

    function at(secondsIntoTheRun: number): string {
        return new Date(Date.UTC(2026, 0, 1, 10, 0, 0) + secondsIntoTheRun * 1000).toISOString();
    }

    it('measures a stage from the moment it was entered to the moment the run left it', () => {
        const events: HyperionGenerationEvent[] = [
            { type: 'STARTED', phase: 'PREPARING', timestamp: at(0) },
            { type: 'PROGRESS', phase: 'DESIGNING', timestamp: at(30) },
            { type: 'PROGRESS', phase: 'VERIFYING', timestamp: at(330) },
            { type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS', timestamp: at(400) },
        ];

        expect(seconds(events)).toMatchObject({ prepare: 30, design: 300, build: 70 });
        // The terminal event closes the stage the run stopped in rather than leaving it counting for the session.
        expect(stageStates(events, runOutcome(events)).every((stage) => stage.runningSince === undefined)).toBe(true);
    });

    it('adds up the visits to a stage a repair round returns to, rather than spanning the detour', () => {
        const events: HyperionGenerationEvent[] = [
            { type: 'PROGRESS', phase: 'VERIFYING', timestamp: at(0) },
            { type: 'PROGRESS', phase: 'REVIEWING', timestamp: at(60) },
            // Back to building for a repair round. The 120 s spent reviewing must not be credited to the build stage.
            { type: 'PROGRESS', phase: 'VERIFYING', timestamp: at(180) },
            { type: 'PROGRESS', phase: 'REVIEWING', timestamp: at(240) },
            { type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS', timestamp: at(300) },
        ];

        // Build ran twice for a minute each; review ran for two minutes and then for the last minute of the run.
        expect(seconds(events)).toMatchObject({ build: 120, review: 180 });
    });

    it('marks only the running stage as still counting, and only while the run is going', () => {
        const events: HyperionGenerationEvent[] = [
            { type: 'STARTED', phase: 'PREPARING', timestamp: at(0) },
            { type: 'PROGRESS', phase: 'DESIGNING', timestamp: at(45) },
        ];
        const stages = stageStates(events, runOutcome(events));

        expect(stages.find((stage) => stage.key === 'design')!.runningSince).toBe(at(45));
        expect(stages.filter((stage) => stage.runningSince !== undefined)).toHaveLength(1);
    });

    it('counts a run that has started but reported no phase yet against the preparing stage', () => {
        const events: HyperionGenerationEvent[] = [{ type: 'STARTED', timestamp: at(0) }];

        expect(stageStates(events, runOutcome(events)).find((stage) => stage.key === 'prepare')!.runningSince).toBe(at(0));
    });
});
