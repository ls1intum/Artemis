import { ExerciseGenerationEvent } from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';

export type GenerationPhase = 'preparing' | 'authoring' | 'verifying' | 'saving' | 'done';

/** The linear order of the working phases (excluding the terminal {@code done}); drives the run card's stepper index. */
export const GENERATION_PHASE_ORDER: GenerationPhase[] = ['preparing', 'authoring', 'verifying', 'saving'];

export type GenerationRepo = 'solution' | 'template' | 'tests' | 'other';

export interface GenerationFileChange {
    path: string;
    repo: GenerationRepo;
    action: 'create' | 'edit';
}

/** A structured, best-effort view of a run derived from its raw progress lines; the raw transcript remains the authoritative record. */
export interface GenerationProgress {
    phase: GenerationPhase;
    currentStep?: string;
    attempt?: number;
    attemptTotal?: number;
    files: GenerationFileChange[];
}

// Transcript tool-call line "Turn <n>: <tool> <arg>"; for write_file/edit_file the arg is the full path (see AgentLoopRunner#describeToolCall).
// Group 1 = turn number, group 2 = tool name, group 3 = the argument (a path for file tools, the command for bash).
const TOOL_LINE = /^Turn\s+(\d+):\s+(\w+)\s*(.*)$/;
const ATTEMPT = /attempt\s+(\d+)\s+of\s+(\d+)/i;

/**
 * Derives a structured progress view from the streamed event transcript. Purely additive: any line it does not recognise is ignored for the structure but is still shown verbatim
 * in the raw transcript, so the structured view never hides information. Resilient to the coalesced events the server sends (a single event may carry several newline-joined lines).
 *
 * @param events the events received so far (oldest first)
 * @param finished whether a terminal event has arrived (forces the {@code done} phase)
 */
export function parseGenerationProgress(events: ExerciseGenerationEvent[], finished: boolean): GenerationProgress {
    const files = new Map<string, GenerationFileChange>();
    let phase: GenerationPhase = 'preparing';
    let currentStep: string | undefined;
    let attempt: number | undefined;
    let attemptTotal: number | undefined;

    for (const event of events) {
        if (event.type !== 'STARTED' && event.type !== 'PROGRESS') {
            continue;
        }
        for (const rawLine of (event.message ?? '').split('\n')) {
            const line = rawLine.trim();
            if (!line) {
                continue;
            }

            if (/^Verifying the generated exercise/i.test(line)) {
                phase = 'verifying';
                const match = ATTEMPT.exec(line);
                if (match) {
                    attempt = Number(match[1]);
                    attemptTotal = Number(match[2]);
                }
                currentStep = line;
                continue;
            }
            if (/^Verification passed\. Saving/i.test(line)) {
                phase = 'saving';
                currentStep = line;
                continue;
            }

            const tool = TOOL_LINE.exec(line);
            if (tool) {
                phase = 'authoring';
                recordFileChange(files, tool[2], tool[3] ?? '');
                currentStep = line;
                continue;
            }

            if (/^Creating sandbox session/i.test(line) || /^Seeding workspace/i.test(line)) {
                phase = 'preparing';
                currentStep = line;
                continue;
            }

            // Any other line (compaction, retries, rejection notice, verification report) updates the caption without changing the coarse phase.
            currentStep = line;
        }
    }

    return { phase: finished ? 'done' : phase, currentStep, attempt, attemptTotal, files: Array.from(files.values()) };
}

function recordFileChange(files: Map<string, GenerationFileChange>, tool: string, argument: string): void {
    if (tool !== 'write_file' && tool !== 'edit_file') {
        return;
    }
    const path = argument.trim();
    if (!path) {
        return;
    }
    // Keep the first-seen action: a file the agent created this run stays "create" even if it later edits it.
    if (!files.has(path)) {
        files.set(path, { path, repo: repoOf(path), action: tool === 'write_file' ? 'create' : 'edit' });
    }
}

function repoOf(path: string): GenerationRepo {
    const segment = path.replace(/^[./]+/, '').split('/')[0];
    if (segment === 'solution' || segment === 'template' || segment === 'tests') {
        return segment;
    }
    return 'other';
}

/** The visual category of a transcript line, driving its icon and colour. */
export type TranscriptKind = 'tool' | 'verify' | 'milestone' | 'notice' | 'error';

/**
 * One rendered line of the agent transcript. Derived from the raw progress strings so the transcript can be shown as a structured, readable log (turn badge, tool chip, monospace
 * file/command target) instead of undifferentiated text. {@code text} is always the full original line, used for non-tool entries and as a hover title.
 */
export interface TranscriptEntry {
    kind: TranscriptKind;
    turn?: number;
    /** The agent tool that produced the line (e.g. {@code write_file}, {@code bash}, {@code verify}), when the line is a tool call. */
    tool?: string;
    /** The most informative argument of a tool call: a file path for file tools, the command for bash. */
    target?: string;
    /** The full original progress line. */
    text: string;
    /** For file tools, which repository the target file belongs to. */
    repo?: GenerationRepo;
}

/**
 * Parses the streamed event transcript into structured, renderable entries. One entry per non-empty progress line (a coalesced event may still carry several newline-joined lines)
 * plus one per terminal event. Purely presentational — the raw {@code text} is preserved on every entry, so nothing is hidden.
 *
 * @param events the events received so far (oldest first)
 */
export function parseTranscript(events: ExerciseGenerationEvent[]): TranscriptEntry[] {
    const entries: TranscriptEntry[] = [];
    for (const event of events) {
        if (event.type === 'ERROR') {
            entries.push({ kind: 'error', text: event.message ?? '' });
            continue;
        }
        if (event.type === 'CANCELLED') {
            entries.push({ kind: 'notice', text: event.message ?? '' });
            continue;
        }
        if (event.type === 'DONE') {
            entries.push({ kind: 'milestone', text: event.message ?? '' });
            continue;
        }
        for (const rawLine of (event.message ?? '').split('\n')) {
            const line = rawLine.trim();
            if (line) {
                entries.push(classifyTranscriptLine(line));
            }
        }
    }
    return entries;
}

function classifyTranscriptLine(line: string): TranscriptEntry {
    const tool = TOOL_LINE.exec(line);
    if (tool) {
        const name = tool[2];
        const target = (tool[3] ?? '').trim() || undefined;
        const isFileTool = name === 'write_file' || name === 'edit_file' || name === 'read_file';
        return {
            kind: name === 'verify' ? 'verify' : 'tool',
            // tool[1] is captured by the mandatory \d+ group, so it is always a finite integer.
            turn: Number(tool[1]),
            tool: name,
            target,
            text: line,
            repo: isFileTool && target ? repoOf(target) : undefined,
        };
    }
    if (/^Verifying the generated exercise/i.test(line)) {
        return { kind: 'verify', text: line };
    }
    if (/^Verification passed/i.test(line) || /^Creating sandbox session/i.test(line) || /^Seeding workspace/i.test(line) || /^Agent (finished|submitted)/i.test(line)) {
        return { kind: 'milestone', text: line };
    }
    if (/could not be executed|Model call failed|Reached the step budget/i.test(line)) {
        return { kind: 'error', text: line };
    }
    return { kind: 'notice', text: line };
}
