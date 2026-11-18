import { MAX_RETAINED_EVENTS, boundEvents, mergeEvents } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { runOutcome, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';

function phaseEvent(phase: NonNullable<HyperionGenerationEvent['phase']>, timestamp: string): HyperionGenerationEvent {
    return { type: 'PROGRESS', phase, message: phase, timestamp } as HyperionGenerationEvent;
}

function progressEvent(message: string, timestamp: string): HyperionGenerationEvent {
    return { type: 'PROGRESS', message, timestamp } as HyperionGenerationEvent;
}

/** The prose a designing agent emits: far more lines than the transcript keeps, which is what made oldest-first eviction lose the run's shape. */
function designChatter(count: number): HyperionGenerationEvent[] {
    return Array.from({ length: count }, (_unused, index) => progressEvent(`writing file ${index}`, `2026-01-01T00:${String(index).padStart(2, '0')}:00Z`));
}

describe('Hyperion generation activity utils', () => {
    describe('boundEvents', () => {
        it('should keep every event while the transcript fits', () => {
            const events = [phaseEvent('PREPARING', 'a'), ...designChatter(3)];
            expect(boundEvents(events)).toEqual(events);
        });

        it('should drop ordinary progress lines rather than the phase events that say where the run is', () => {
            const bounded = boundEvents([phaseEvent('PREPARING', 'a'), phaseEvent('DESIGNING', 'b'), ...designChatter(MAX_RETAINED_EVENTS * 2)]);

            expect(bounded).toHaveLength(MAX_RETAINED_EVENTS);
            expect(bounded.filter((event) => event.phase !== undefined).map((event) => event.phase)).toEqual(['PREPARING', 'DESIGNING']);
            // The lines that survive are the newest ones, so the instructor still reads what just happened.
            expect(bounded.at(-1)?.message).toBe(`writing file ${MAX_RETAINED_EVENTS * 2 - 1}`);
        });

        it('should keep the newest structural events once nothing else is left to drop', () => {
            const phases = Array.from({ length: MAX_RETAINED_EVENTS + 5 }, (_unused, index) => phaseEvent('VERIFYING', `t${index}`));

            const bounded = boundEvents(phases);

            expect(bounded).toHaveLength(MAX_RETAINED_EVENTS);
            expect(bounded[0].timestamp).toBe('t5');
        });

        it('should keep a repair round, which is the only record that a round happened at all', () => {
            const round = { type: 'PROGRESS', message: 'repair', repairRound: { round: 1 }, timestamp: 'r' } as unknown as HyperionGenerationEvent;

            expect(boundEvents([round, ...designChatter(MAX_RETAINED_EVENTS * 2)])).toContain(round);
        });
    });

    describe('the ladder a bounded transcript produces', () => {
        it('should stay on the design stage while the agent floods the transcript with progress lines', () => {
            const events = boundEvents([phaseEvent('PREPARING', 'a'), phaseEvent('DESIGNING', 'b'), ...designChatter(MAX_RETAINED_EVENTS * 2)]);

            const stages = stageStates(events, runOutcome(events));

            // Before the retention fix the DESIGNING event was evicted oldest-first, the transcript held no phase at all, and the ladder walked back to "prepare workspace".
            expect(stages.find((stage) => stage.key === 'prepare')?.state).toBe('complete');
            expect(stages.find((stage) => stage.key === 'design')?.state).toBe('current');
        });

        it('should survive a reconnect that merges the server transcript into the client one', () => {
            const live = boundEvents([phaseEvent('PREPARING', 'a'), phaseEvent('DESIGNING', 'b'), ...designChatter(MAX_RETAINED_EVENTS * 2)]);
            const retained = [phaseEvent('PREPARING', 'a'), phaseEvent('DESIGNING', 'b'), phaseEvent('VERIFYING', 'c')];

            const merged = mergeEvents(live, retained);
            const stages = stageStates(merged, runOutcome(merged));

            expect(stages.find((stage) => stage.key === 'build')?.state).toBe('current');
        });
    });
});
