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

// Transcript tool-call line "Turn <n>: <tool> <arg>" (see AgentLoopRunner#describeToolCall): for tools whose arguments carry a "path" key (write_file/edit_file/read_file) the arg is
// that path; for every other tool (e.g. bash, whose key is "command") it is the raw, whitespace-collapsed, truncated JSON arguments — NOT the extracted command value.
// Group 1 = turn number, group 2 = tool name, group 3 = the argument.
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

            if (/^Checking the exercise builds and grades/i.test(line)) {
                phase = 'verifying';
                const match = ATTEMPT.exec(line);
                if (match) {
                    attempt = Number(match[1]);
                    attemptTotal = Number(match[2]);
                }
                currentStep = line;
                continue;
            }
            if (/^Checks passed\. Saving/i.test(line)) {
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

            if (/^Setting up the build environment/i.test(line) || /^Loading the example exercise/i.test(line)) {
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
