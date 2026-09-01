import { describe, expect, it } from 'vitest';

import { RECENT_ACTIVITY_LIMIT, activityView, formatClockTime, formatDuration } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { runOutcome } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { HyperionGenerationActivity, HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

let clock = 0;

function event(partial: Partial<HyperionGenerationEvent> & Pick<HyperionGenerationEvent, 'type'>): HyperionGenerationEvent {
    return { timestamp: new Date(Date.UTC(2026, 0, 1, 12, 0, clock++)).toISOString(), ...partial };
}

function activity(partial: Partial<HyperionGenerationActivity> = {}): HyperionGenerationActivity {
    return { attempt: 1, turn: 1, waitingOnModel: false, modelCalls: 0, toolCalls: 0, filesWritten: 0, ...partial };
}

function view(events: readonly HyperionGenerationEvent[]) {
    return activityView(events, runOutcome(events));
}

describe('hyperion generation activity view', () => {
    describe('liveness', () => {
        it('counts from the event that announced the model call while the agent is waiting on it', () => {
            const waiting = event({ type: 'PROGRESS', message: 'Asking the model', activity: activity({ waitingOnModel: true }) });
            const events = [event({ type: 'STARTED' }), waiting];

            expect(view(events).liveness).toEqual({ waitingOnModel: true, since: waiting.timestamp });
        });

        it('switches to the last-update reading once the model call came back', () => {
            const events = [
                event({ type: 'PROGRESS', message: 'Asking the model', activity: activity({ waitingOnModel: true }) }),
                event({ type: 'PROGRESS', message: 'Wrote Stack.java', activity: activity({ waitingOnModel: false, modelCalls: 1 }) }),
            ];

            expect(view(events).liveness).toEqual({ waitingOnModel: false, since: events[1].timestamp });
        });

        it('keeps reporting the wait when a later event carries no bookkeeping of its own', () => {
            // Something else spoke; that is not the provider call coming back, so the clock must not be reset by it.
            const waiting = event({ type: 'PROGRESS', activity: activity({ waitingOnModel: true }) });
            const events = [waiting];

            expect(view(events).liveness?.waitingOnModel).toBe(true);
        });

        it('stops the reading entirely once the run is over', () => {
            const events = [event({ type: 'PROGRESS', activity: activity({ waitingOnModel: true }) }), event({ type: 'DONE', phase: 'SAVING', completionStatus: 'SUCCESS' })];

            const result = view(events);
            expect(result.liveness).toBeUndefined();
            expect(result.ended).toBe(true);
        });

        it('reports nothing at all before the first event', () => {
            expect(view([])).toEqual({ liveness: undefined, counters: [], recent: [], ended: false, empty: true });
        });
    });

    describe('meter', () => {
        it('labels every counter and omits the ones that are still at zero', () => {
            const events = [event({ type: 'PROGRESS', activity: activity({ attempt: 2, turn: 7, modelCalls: 9, toolCalls: 0, filesWritten: 0 }) })];

            expect(view(events).counters).toEqual([
                { key: 'attempt', labelKey: 'artemisApp.hyperion.generation.activity.attempt', count: 2 },
                { key: 'turn', labelKey: 'artemisApp.hyperion.generation.activity.turn', count: 7 },
                { key: 'modelCalls', labelKey: 'artemisApp.hyperion.generation.activity.modelCalls', count: 9 },
            ]);
        });

        it('reads the newest bookkeeping, not the newest message', () => {
            const events = [event({ type: 'PROGRESS', activity: activity({ turn: 3, filesWritten: 11 }) }), event({ type: 'PROGRESS', message: 'Still going' })];

            expect(view(events).counters.find((counter) => counter.key === 'files')?.count).toBe(11);
        });
    });

    describe('recent messages', () => {
        it('keeps the newest five, newest first, and drops the events that said nothing', () => {
            const events = [...Array.from({ length: 8 }, (_, index) => event({ type: 'PROGRESS' as const, message: `message ${index}` })), event({ type: 'PROGRESS' })];

            const recent = view(events).recent;
            expect(recent).toHaveLength(RECENT_ACTIVITY_LIMIT);
            expect(recent.map((entry) => entry.message)).toEqual(['message 7', 'message 6', 'message 5', 'message 4', 'message 3']);
            expect(recent.every((entry) => /^\d{2}:\d{2}:\d{2}$/.test(entry.time))).toBe(true);
        });
    });

    describe('wording', () => {
        it.each([
            [0, '0s'],
            [12, '12s'],
            [59, '59s'],
            [60, '1:00'],
            [134, '2:14'],
            [3734, '1:02:14'],
        ])('words %i seconds as %s', (seconds, expected) => {
            expect(formatDuration(seconds)).toBe(expected);
        });

        it('says nothing about a timestamp the server did not send', () => {
            expect(formatClockTime(undefined)).toBe('');
            expect(formatClockTime('')).toBe('');
        });
    });
});
