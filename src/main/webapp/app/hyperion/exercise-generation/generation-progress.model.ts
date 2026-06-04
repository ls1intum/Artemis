import { ExerciseGenerationEvent } from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';

/** The coarse stage a generation run is in, derived from its progress lines, for a scannable status header. */
export type GenerationPhase = 'preparing' | 'authoring' | 'verifying' | 'saving' | 'done';

/** The repository a generated/edited file belongs to (its top-level path segment), so changes can be grouped. */
export type GenerationRepo = 'solution' | 'template' | 'tests' | 'other';

/** A single file the agent created or edited during the run. */
export interface GenerationFileChange {
    path: string;
    repo: GenerationRepo;
    action: 'create' | 'edit';
}

/** A structured, best-effort view of a run derived from its raw progress lines for a human-friendly UI; the raw transcript remains the authoritative record. */
export interface GenerationProgress {
    phase: GenerationPhase;
    /** The most recent meaningful progress line, used as a live "current step" caption. */
    currentStep?: string;
    /** The verification attempt currently in progress (1-based), when known. */
    attempt?: number;
    /** The maximum number of verification attempts, when known. */
    attemptTotal?: number;
    /** The files created or edited so far, in first-seen order, deduplicated by path. */
    files: GenerationFileChange[];
}

// The agent loop emits one transcript line per tool call as "Turn <n>: <tool> <arg>". For the file tools (write_file/edit_file) the server renders the full file path as the
// argument (see AgentLoopRunner#describeToolCall), so the path is everything after the tool name; for other tools the argument is a command or JSON and is ignored here.
const TOOL_LINE = /^Turn\s+\d+:\s+(\w+)\s*(.*)$/;
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
                recordFileChange(files, tool[1], tool[2] ?? '');
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
