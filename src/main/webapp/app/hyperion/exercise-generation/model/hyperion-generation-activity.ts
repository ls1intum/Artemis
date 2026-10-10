import { ExerciseGenerationFileChange, HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { displayFileChangePath, newestFileChange } from 'app/hyperion/exercise-generation/hyperion-generation-activity.utils';
import { HyperionRunOutcome } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';

/** How many past messages the stepper keeps in view. Long enough to see a pattern, short enough not to become a log. */
export const RECENT_ACTIVITY_LIMIT = 5;

/** One labelled number on the aggregate meter. A count is never rendered on its own, so every entry carries its word. */
export interface HyperionActivityCounter {
    key: string;
    labelKey: string;
    count: number;
}

/** One past server message, with the moment it arrived already worded. */
export interface HyperionActivityMessage {
    key: string;
    time: string;
    message: string;
}

/**
 * How long a run may say nothing at all before the surface stops calling it "working" and starts calling it stuck.
 *
 * Ninety seconds is long enough that an ordinary gap between two model calls never trips it, and short enough that a
 * hung provider call is named while the instructor is still watching. Below the threshold the page shows a quiet
 * spinner and a counting clock; at or past it the tone changes, the silence is stated in words, and the escape action
 * is promoted - because otherwise minute 14 of a hung call looks exactly like minute 1.
 */
export const SILENCE_STALLED_MS = 90_000;

/**
 * The same threshold for a run that is explicitly waiting on one model call, which is a normal thing to be doing.
 *
 * Four minutes rather than ninety seconds: a single long completion is expected work, so calling it stuck too early
 * would train the instructor to ignore the warning that matters.
 */
export const MODEL_WAIT_STALLED_MS = 240_000;

/**
 * What the run is doing right now, measured from a server timestamp.
 *
 * The point of this is the silence: model calls in a live run landed minutes apart with nothing in between, so the
 * only honest thing to show is how long that silence has lasted and whether the agent is waiting on the model.
 */
export interface HyperionActivityLiveness {
    waitingOnModel: boolean;
    /** ISO timestamp the duration is counted from. */
    since: string;
    /** How long this particular silence may last before it is reported as a stall. Depends on what is being waited on. */
    stalledAfterMs: number;
}

/**
 * Whether a silence has lasted long enough to be a stall rather than a pause.
 *
 * Takes the clock as an argument so the decision is testable against a wire trace rather than against the wall, and so
 * the component that owns the ticking signal is the only thing that re-renders once a second.
 */
export function isStalled(liveness: HyperionActivityLiveness | undefined, nowMillis: number): boolean {
    if (!liveness) {
        return false;
    }
    const since = Date.parse(liveness.since);
    return Number.isFinite(since) && nowMillis - since >= liveness.stalledAfterMs;
}

export interface HyperionActivityView {
    liveness?: HyperionActivityLiveness;
    counters: HyperionActivityCounter[];
    /**
     * The file the agent touched most recently, while it is still working.
     *
     * Named because "writing code" is the longest and least legible part of a run: a path moving through the test,
     * solution and template trees is the difference between watching progress and watching a spinner.
     */
    latestFile?: string;
    recent: HyperionActivityMessage[];
    /** The run is over, which freezes every ticker rather than letting it count on forever. */
    ended: boolean;
    /** Nothing worth rendering, so the current step stays a plain label. */
    empty: boolean;
}

const COUNTER_LABEL_KEY = 'artemisApp.hyperion.generation.activity';

/** `12s`, `2:14`, or `1:02:14` once a wait has been going for an hour. */
export function formatDuration(totalSeconds: number): string {
    return totalSeconds < 60 ? `${totalSeconds}s` : formatElapsed(totalSeconds);
}

/** `m:ss`, or `h:mm:ss` once a run has been going for an hour. */
export function formatElapsed(totalSeconds: number): string {
    const seconds = totalSeconds % 60;
    const minutes = Math.floor(totalSeconds / 60) % 60;
    const hours = Math.floor(totalSeconds / 3600);
    const paddedSeconds = String(seconds).padStart(2, '0');
    return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${paddedSeconds}` : `${minutes}:${paddedSeconds}`;
}

/** The wall-clock time of a server timestamp as `HH:MM:SS`, or nothing when the server sent no usable one. */
export function formatClockTime(timestamp: string | undefined): string {
    const parsed = timestamp === undefined ? Number.NaN : Date.parse(timestamp);
    if (!Number.isFinite(parsed)) {
        return '';
    }
    const moment = new Date(parsed);
    return [moment.getHours(), moment.getMinutes(), moment.getSeconds()].map((part) => String(part).padStart(2, '0')).join(':');
}

/**
 * Everything the stepper says about the agent's activity, derived once from the event stream.
 *
 * Kept out of the components so both the run page and the code editor's panel report the same thing, and so the
 * rules can be tested against a wire trace rather than against rendered DOM.
 */
export function activityView(
    events: readonly HyperionGenerationEvent[],
    outcome: HyperionRunOutcome | undefined,
    files: readonly ExerciseGenerationFileChange[] = [],
): HyperionActivityView {
    const ended = outcome !== undefined;
    const activity = events.findLast((event) => event.activity !== undefined)?.activity;
    const counters: HyperionActivityCounter[] = activity
        ? (
              [
                  { key: 'attempt', count: activity.attempt },
                  { key: 'turn', count: activity.turn },
                  { key: 'modelCalls', count: activity.modelCalls },
                  { key: 'toolCalls', count: activity.toolCalls },
                  { key: 'files', count: activity.filesWritten },
              ] as const
          )
              // A counter at zero is not news; "0 files" only spends space to say nothing happened yet.
              .filter((counter) => counter.count > 0)
              .map((counter) => ({ key: counter.key, labelKey: `${COUNTER_LABEL_KEY}.${counter.key}`, count: counter.count }))
        : [];
    const recent = events
        .filter((event) => event.message)
        .slice(-RECENT_ACTIVITY_LIMIT)
        .reverse()
        .map<HyperionActivityMessage>((event, index) => ({ key: `${event.timestamp}|${index}`, time: formatClockTime(event.timestamp), message: event.message! }));
    const liveness = ended ? undefined : livenessOf(events);
    // A finished run's files are listed in full elsewhere; singling one out would only claim it is still being written.
    const newestFile = ended ? undefined : newestFileChange(files);
    const latestFile = newestFile ? displayFileChangePath(newestFile) : undefined;
    return {
        liveness,
        counters,
        latestFile,
        recent,
        ended,
        empty: liveness === undefined && counters.length === 0 && recent.length === 0 && latestFile === undefined,
    };
}

/**
 * Whether the agent is waiting on the model, and since when.
 *
 * The waiting flag is read from the newest event that carried activity at all: a later message without activity
 * means something else spoke, not that the provider call came back.
 */
function livenessOf(events: readonly HyperionGenerationEvent[]): HyperionActivityLiveness | undefined {
    const waiting = events.findLast((event) => event.activity !== undefined);
    if (waiting?.activity?.waitingOnModel && hasTimestamp(waiting)) {
        return { waitingOnModel: true, since: waiting.timestamp, stalledAfterMs: MODEL_WAIT_STALLED_MS };
    }
    const newest = events.findLast(hasTimestamp);
    return newest ? { waitingOnModel: false, since: newest.timestamp, stalledAfterMs: SILENCE_STALLED_MS } : undefined;
}

function hasTimestamp(event: HyperionGenerationEvent): boolean {
    return Number.isFinite(Date.parse(event.timestamp));
}
