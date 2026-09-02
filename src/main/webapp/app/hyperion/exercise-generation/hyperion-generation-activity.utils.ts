import { ExerciseGenerationFileChange, HyperionFileChangeRepo, HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

export const TERMINAL_EVENT_TYPES = new Set<HyperionGenerationEvent['type']>(['DONE', 'CANCELLED', 'ERROR']);

/** The order repositories are listed in, everywhere changed files are shown. */
export const REPO_ORDER: readonly HyperionFileChangeRepo[] = ['solution', 'template', 'tests', 'other'];

/**
 * The file change written most recently, by agent turn and then by timestamp.
 *
 * Used to point at the file a running agent is working on right now; `undefined` when nothing was written yet.
 */
export function newestFileChange(files: readonly ExerciseGenerationFileChange[]): ExerciseGenerationFileChange | undefined {
    return files.reduce<ExerciseGenerationFileChange | undefined>((newest, file) => (newest ? newerFileChange(newest, file) : file), undefined);
}

export const MAX_RETAINED_EVENTS = 50;

/**
 * Whether an event is an ordinary progress line, which the transcript can lose without losing the shape of the run.
 *
 * Mirrors {@code GenerationJobReplayStore.isEvictable}. The two bounds are not the same size — the server keeps ten times as many — but they must agree on what is safe to drop,
 * because both feed the same ladder.
 */
function isEvictable(event: HyperionGenerationEvent): boolean {
    return event.type === 'PROGRESS' && event.phase === undefined && event.repairRound === undefined;
}

/**
 * The newest events within the retention bound, dropping ordinary progress lines before anything that says where the run has been.
 *
 * Oldest-first eviction is wrong here and was a real defect: the agent emits roughly one progress line per turn plus one before every provider call, so the design stage alone
 * overflows this bound with prose within a couple of minutes. Plain FIFO then evicted the `DESIGNING` phase event, which left the transcript with no phase at all — and a run
 * with no phase reads as one that has not left the first stage, so the ladder walked back to "prepare workspace" while the agent was still designing.
 */
export function boundEvents(events: readonly HyperionGenerationEvent[]): HyperionGenerationEvent[] {
    if (events.length <= MAX_RETAINED_EVENTS) {
        return [...events];
    }
    const kept = [...events];
    for (let index = 0; index < kept.length && kept.length > MAX_RETAINED_EVENTS;) {
        if (isEvictable(kept[index])) {
            kept.splice(index, 1);
        } else {
            index++;
        }
    }
    // Only structural events are left and there are still too many. The bound is unconditional, so the oldest of those goes after all.
    return kept.length > MAX_RETAINED_EVENTS ? kept.slice(-MAX_RETAINED_EVENTS) : kept;
}

export function latestTerminalEvent(events: HyperionGenerationEvent[]): HyperionGenerationEvent | undefined {
    for (let index = events.length - 1; index >= 0; index--) {
        const event = events[index];
        if (TERMINAL_EVENT_TYPES.has(event.type)) {
            return event;
        }
    }
    return undefined;
}

export function displayFileChangePath(fileChange: ExerciseGenerationFileChange): string {
    const prefix = `${fileChange.repo}/`;
    return fileChange.path.startsWith(prefix) ? fileChange.path.slice(prefix.length) : fileChange.path;
}

export function fileChangeKey(fileChange: Pick<ExerciseGenerationFileChange, 'repo' | 'path'>): string {
    return `${fileChange.repo}\0${fileChange.path}`;
}

export function mergeEvents(current: HyperionGenerationEvent[], retained: HyperionGenerationEvent[]): HyperionGenerationEvent[] {
    const byKey = new Map<string, HyperionGenerationEvent>();
    for (const event of [...retained, ...current]) {
        byKey.set(`${event.type}|${event.timestamp}|${event.completionStatus ?? ''}|${event.message ?? ''}`, event);
    }
    return boundEvents([...byKey.values()]);
}

export function mergeFileChanges(current: ExerciseGenerationFileChange[], retained: ExerciseGenerationFileChange[]): ExerciseGenerationFileChange[] {
    const byPath = new Map<string, ExerciseGenerationFileChange>();
    for (const fileChange of [...retained, ...current]) {
        const key = fileChangeKey(fileChange);
        const previous = byPath.get(key);
        byPath.set(key, previous ? newerFileChange(previous, fileChange) : fileChange);
    }
    return [...byPath.values()];
}

export function newerFileChange(first: ExerciseGenerationFileChange, second: ExerciseGenerationFileChange): ExerciseGenerationFileChange {
    if (second.turn !== first.turn) {
        return second.turn > first.turn ? second : first;
    }
    const firstTime = first.timestamp ? Date.parse(first.timestamp) : Number.NaN;
    const secondTime = second.timestamp ? Date.parse(second.timestamp) : Number.NaN;
    if (Number.isFinite(firstTime) && Number.isFinite(secondTime) && secondTime !== firstTime) {
        return secondTime > firstTime ? second : first;
    }
    return second;
}
