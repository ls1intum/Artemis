import { TumUiStepState } from '@tumaet/ui-angular';
import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

export type HyperionStageKey = 'prepare' | 'design' | 'build' | 'review' | 'save';

type ServerPhase = NonNullable<HyperionGenerationEvent['phase']>;

/**
 * The stages an instructor sees, and the server phases that feed each one. This is the single ordering; every
 * surface that renders progress imports it, so the same run cannot read as being at different points in two places.
 */
export const HYPERION_STAGES: readonly { readonly key: HyperionStageKey; readonly phases: readonly ServerPhase[] }[] = [
    { key: 'prepare', phases: ['PREPARING'] },
    { key: 'design', phases: ['DESIGNING'] },
    { key: 'build', phases: ['VERIFYING'] },
    { key: 'review', phases: ['REVIEWING', 'REPAIRING'] },
    { key: 'save', phases: ['SAVING'] },
];

export interface HyperionStage {
    key: HyperionStageKey;
    state: TumUiStepState;
}

/** How a run ended, or `undefined` while it is still going. */
export type HyperionRunOutcome = 'saved' | 'needsReview' | 'partial' | 'failed' | 'cancelled';

const STAGE_INDEX_BY_PHASE = new Map<ServerPhase, number>(HYPERION_STAGES.flatMap((stage, index) => stage.phases.map((phase) => [phase, index] as const)));

export function stageIndexOfPhase(phase: ServerPhase | undefined): number | undefined {
    return phase === undefined ? undefined : STAGE_INDEX_BY_PHASE.get(phase);
}

/**
 * The run's outcome, or `undefined` while it is still going.
 *
 * `DONE` is the only event that carries a completion status, and it carries one for partial saves too, so the
 * distinction between a saved draft and a half-saved one lives here rather than in every caller.
 */
export function runOutcome(events: readonly HyperionGenerationEvent[]): HyperionRunOutcome | undefined {
    for (let index = events.length - 1; index >= 0; index--) {
        const event = events[index];
        switch (event.type) {
            case 'DONE':
                switch (event.completionStatus) {
                    case 'PARTIAL':
                        return 'partial';
                    case 'NEEDS_REVIEW':
                        return 'needsReview';
                    default:
                        return 'saved';
                }
            case 'ERROR':
                return 'failed';
            case 'CANCELLED':
                return 'cancelled';
        }
    }
    return undefined;
}

/**
 * The state of every stage, for a run that is either in flight or finished.
 *
 * Terminal events must not be read through their `phase`: the server stamps `SAVING` on every `DONE`, so deriving
 * the ladder from the phase alone leaves the last stage spinning forever after a successful run. The outcome is
 * therefore an explicit argument, and it wins over any phase seen on the wire.
 *
 * While a run is in flight, completion is monotonic: a repair loop that returns from `review` to `build` moves the
 * spinner back to `build` but leaves `review` marked complete, because that work really was done once.
 */
export function stageStates(events: readonly HyperionGenerationEvent[], outcome: HyperionRunOutcome | undefined): HyperionStage[] {
    let furthest = -1;
    let latest = -1;
    for (const event of events) {
        const index = stageIndexOfPhase(event.phase);
        if (index === undefined || event.type === 'DONE' || event.type === 'ERROR' || event.type === 'CANCELLED') {
            continue;
        }
        latest = index;
        furthest = Math.max(furthest, index);
    }

    // A run that has started but not yet reported a phase is preparing; anything else would show a ladder with no spinner.
    const current = outcome === undefined && latest === -1 && events.length > 0 ? 0 : latest;
    return HYPERION_STAGES.map((stage, index) => ({ key: stage.key, state: stageState(index, current, furthest, outcome) }));
}

function stageState(index: number, latest: number, furthest: number, outcome: HyperionRunOutcome | undefined): TumUiStepState {
    switch (outcome) {
        case 'saved':
        case 'needsReview':
            return 'complete';
        case 'partial':
            // Everything ran; only the write-out came back uncertain, and that is the one thing the instructor must look at.
            return index === HYPERION_STAGES.length - 1 ? 'failed' : 'complete';
        case 'failed':
            return index < furthest ? 'complete' : index === furthest ? 'failed' : 'skipped';
        case 'cancelled':
            return index < furthest ? 'complete' : 'skipped';
    }

    if (index === latest) {
        return 'current';
    }
    // A stage the run has already been through stays complete, including one it has stepped back past for a repair round.
    return index <= furthest ? 'complete' : 'pending';
}
