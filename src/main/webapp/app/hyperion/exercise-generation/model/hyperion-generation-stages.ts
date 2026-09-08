import { TumUiStepState } from '@tumaet/ui-angular';
import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

export type HyperionStageKey = 'prepare' | 'design' | 'build' | 'review' | 'save';

/** The pieces of work the agent reports from inside the design stage, in the order it does them. */
export type HyperionSubstepKey = 'concept' | 'spec' | 'artifacts' | 'statement';

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

/** How many rungs the ladder has. A real, fixed denominator is what makes "Step 2 of 5" a fact rather than a guess. */
export const HYPERION_STAGE_COUNT = HYPERION_STAGES.length;

/** The design stage is the only one the agent reports substeps for; every other stage has none. */
export const HYPERION_SUBSTEP_STAGE: HyperionStageKey = 'design';

export const HYPERION_SUBSTEPS: readonly HyperionSubstepKey[] = ['concept', 'spec', 'artifacts', 'statement'];

export interface HyperionSubstep {
    key: HyperionSubstepKey;
    state: TumUiStepState;
}

/**
 * What a stage got done, in the two numbers that can be attributed to it honestly.
 *
 * `turns` counts the agent turns first seen while this stage was running, and `files` the files whose first report
 * arrived then, so a repair loop returning to an earlier stage cannot make a stage claim another stage's work.
 */
export interface HyperionStageSummary {
    turns: number;
    files: number;
}

export interface HyperionStage {
    key: HyperionStageKey;
    state: TumUiStepState;
    /** Present only once the agent has reported at least one substep for this stage. */
    substeps?: HyperionSubstep[];
    /** Present only on a stage that has ended and did report work. */
    summary?: HyperionStageSummary;
    /**
     * Milliseconds already spent inside this stage, summed over every visit.
     *
     * Accumulated rather than measured from the first entry to the last exit, because a repair loop returns the run to
     * an earlier stage: the span between a stage's first entry and its final exit would count the time the run spent
     * in every stage in between as if this one had been working.
     */
    elapsedMs: number;
    /**
     * The moment this stage was last entered, set only while it is the one running.
     *
     * A run takes tens of minutes with minutes of silence inside a single stage, so the only way to tell "working" from
     * "stuck" is a clock that keeps counting; the component adds the time since this moment to {@link elapsedMs}.
     */
    runningSince?: string;
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
    let substepLatest = -1;
    let substepFurthest = -1;
    /** The stage the run was in when the event being read arrived, so activity can be attributed to it. */
    let running = -1;
    const turnsPerStage = HYPERION_STAGES.map(() => 0);
    const filesPerStage = HYPERION_STAGES.map(() => 0);
    const elapsedPerStage = HYPERION_STAGES.map(() => 0);
    const countedTurns = new Set<string>();
    let filesCounted = 0;
    /** The moment the stage in `running` was entered, in epoch millis and as sent, or `undefined` once it was banked. */
    let enteredAt: number | undefined;
    let enteredAtIso: string | undefined;
    let firstTimestamp: string | undefined;

    for (const event of events) {
        const terminal = event.type === 'DONE' || event.type === 'ERROR' || event.type === 'CANCELLED';
        const index = stageIndexOfPhase(event.phase);
        const moment = Date.parse(event.timestamp);
        const timed = Number.isFinite(moment);
        if (timed && firstTimestamp === undefined) {
            firstTimestamp = event.timestamp;
        }
        if (index !== undefined && !terminal) {
            // Leaving a stage banks the time spent in it; re-entering the same one on a repair round does not.
            if (index !== running) {
                if (running >= 0 && enteredAt !== undefined && timed) {
                    elapsedPerStage[running] += Math.max(0, moment - enteredAt);
                }
                enteredAt = timed ? moment : undefined;
                enteredAtIso = timed ? event.timestamp : undefined;
            }
            latest = index;
            running = index;
            furthest = Math.max(furthest, index);
        } else if (terminal && running >= 0 && enteredAt !== undefined && timed) {
            // The run stopped here, so the stage it stopped in is closed at that moment rather than left counting.
            elapsedPerStage[running] += Math.max(0, moment - enteredAt);
            enteredAt = undefined;
            enteredAtIso = undefined;
        }
        const activity = event.activity;
        if (!activity || terminal) {
            continue;
        }
        // The wire type is an open string, so an unknown substep is simply not one this ladder knows how to show.
        const substep = HYPERION_SUBSTEPS.findIndex((key) => key === activity.step);
        if (substep >= 0) {
            substepLatest = substep;
            substepFurthest = Math.max(substepFurthest, substep);
        }
        // Before the first phase arrives the run is preparing, which is the stage the ladder shows as running too.
        const stage = running < 0 ? 0 : running;
        const turn = `${activity.attempt}\0${activity.turn}`;
        if (!countedTurns.has(turn)) {
            countedTurns.add(turn);
            turnsPerStage[stage]++;
        }
        // The counters are cumulative for the whole run, so only what is new here belongs to this stage.
        if (activity.filesWritten > filesCounted) {
            filesPerStage[stage] += activity.filesWritten - filesCounted;
            filesCounted = activity.filesWritten;
        }
    }

    // A run that has started but not yet reported a phase is preparing; anything else would show a ladder with no spinner.
    const implicitPreparing = outcome === undefined && latest === -1 && events.length > 0;
    const current = implicitPreparing ? 0 : latest;
    // Only a run that is still going has a stage the clock may keep counting in.
    const runningSince = outcome !== undefined ? undefined : implicitPreparing ? firstTimestamp : enteredAtIso;
    return HYPERION_STAGES.map((stage, index) => {
        const own = stageState(index, current, furthest, outcome);
        const substeps = stage.key === HYPERION_SUBSTEP_STAGE && substepLatest >= 0 ? substepStates(own, substepLatest, substepFurthest) : undefined;
        // A parent stage is only ever as healthy as its least healthy substep.
        const state = mostCriticalState(own, substeps);
        const turns = turnsPerStage[index];
        return {
            key: stage.key,
            state,
            substeps,
            // A stage that is still running has not finished anything yet, and one that never ran has nothing to report.
            summary: turns > 0 && state !== 'current' && state !== 'pending' ? { turns, files: filesPerStage[index] } : undefined,
            elapsedMs: elapsedPerStage[index],
            runningSince: state === 'current' ? runningSince : undefined,
        };
    });
}

/**
 * Which rung of the ladder the run is on, as a position, never as a completion count.
 *
 * Derived from the furthest stage the run actually reached, so it cannot walk backwards when a repair round returns
 * the run to an earlier stage. A counter that goes from "step 4 of 5" to "step 3 of 5" is worse than no counter,
 * because it reads as the run losing ground.
 *
 * `pending` and `skipped` both mean "the run was never here": a run that failed in stage 2 leaves stages 3 to 5
 * skipped, and reporting it as "step 5 of 5" would claim it got to the end.
 *
 * `undefined` while nothing has started, because "step 0 of 5" is not a position.
 */
export function stagePosition(stages: readonly HyperionStage[]): number | undefined {
    for (let index = stages.length - 1; index >= 0; index--) {
        const state = stages[index].state;
        if (state !== 'pending' && state !== 'skipped') {
            return index + 1;
        }
    }
    return undefined;
}

/**
 * A stage's state, raised to the most critical state any of its substeps is in.
 *
 * Precedence: failed, then running, then complete, then skipped, then pending. A substep reporting a failure while its
 * parent still shows a spinner is a lie the ladder would tell once per repair round, and it is asserted here rather
 * than in a template so every surface that renders the ladder tells the same story.
 *
 * Today {@link substepStates} derives each substep from its parent, so this can only ever confirm the parent. It is
 * the guard for the moment the server starts reporting a substep outcome independently, which is the point at which a
 * template-level fix would be forgotten.
 */
export function mostCriticalState(parent: TumUiStepState, substeps: readonly HyperionSubstep[] | undefined): TumUiStepState {
    if (!substeps?.length) {
        return parent;
    }
    const order: TumUiStepState[] = ['failed', 'current', 'complete', 'skipped', 'pending'];
    const rank = (state: TumUiStepState) => order.indexOf(state);
    return substeps.reduce((worst, substep) => (rank(substep.state) < rank(worst) ? substep.state : worst), parent);
}

/**
 * The state of every substep of a stage, given how that stage itself ended up.
 *
 * The parent decides: a stage that is over can hold no running substep, which is what keeps a terminal outcome from
 * leaving a spinner behind inside a step that has already been struck through.
 */
function substepStates(parent: TumUiStepState, latest: number, furthest: number): HyperionSubstep[] {
    return HYPERION_SUBSTEPS.map((key, index) => {
        const visited = index <= furthest;
        switch (parent) {
            case 'current':
                return { key, state: index === latest ? 'current' : visited ? 'complete' : 'pending' };
            case 'complete':
                return { key, state: visited ? 'complete' : 'skipped' };
            case 'failed':
                return { key, state: index === latest ? 'failed' : visited ? 'complete' : 'skipped' };
            case 'skipped':
                return { key, state: index === latest ? 'skipped' : visited ? 'complete' : 'skipped' };
            case 'pending':
                return { key, state: 'pending' };
        }
    });
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
