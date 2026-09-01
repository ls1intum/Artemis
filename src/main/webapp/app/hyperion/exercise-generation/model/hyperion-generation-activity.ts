import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
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
 * What the run is doing right now, measured from a server timestamp.
 *
 * The point of this is the silence: model calls in a live run landed minutes apart with nothing in between, so the
 * only honest thing to show is how long that silence has lasted and whether the agent is waiting on the model.
 */
export interface HyperionActivityLiveness {
    waitingOnModel: boolean;
    /** ISO timestamp the duration is counted from. */
    since: string;
}

export interface HyperionActivityView {
    liveness?: HyperionActivityLiveness;
    counters: HyperionActivityCounter[];
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
export function activityView(events: readonly HyperionGenerationEvent[], outcome: HyperionRunOutcome | undefined): HyperionActivityView {
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
    return { liveness, counters, recent, ended, empty: liveness === undefined && counters.length === 0 && recent.length === 0 };
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
        return { waitingOnModel: true, since: waiting.timestamp };
    }
    const newest = events.findLast(hasTimestamp);
    return newest ? { waitingOnModel: false, since: newest.timestamp } : undefined;
}

function hasTimestamp(event: HyperionGenerationEvent): boolean {
    return Number.isFinite(Date.parse(event.timestamp));
}
