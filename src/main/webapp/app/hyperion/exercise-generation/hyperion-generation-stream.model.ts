/**
 * Client-side types for the live agentic exercise-generation stream. Progress events and whole-file snapshots share one per-user websocket topic and are told apart by their
 * {@link HyperionFileSnapshot.type} discriminator ({@code 'FILE_SNAPSHOT'}), so both flow through a single subscription.
 */

export type HyperionGenerationEventType = 'STARTED' | 'PROGRESS' | 'DONE' | 'CANCELLED' | 'ERROR';

export type HyperionSnapshotRepo = 'solution' | 'template' | 'tests' | 'other';

export type HyperionSnapshotAction = 'create' | 'edit';

/** The structured verification verdict carried on a terminal event, rendered as scannable chips. */
export interface HyperionGenerationVerdict {
    accepted: boolean;
    solutionPassed: boolean;
    templateFailed: boolean;
    testCount: number;
    /** Human-readable explanations of any failed gate; absent/empty when accepted (server omits it via {@code @JsonInclude(NON_EMPTY)}). */
    reasons?: string[];
}

/** A progress or terminal event of a generation run. */
export interface HyperionGenerationEvent {
    type: HyperionGenerationEventType;
    message?: string;
    verdict?: HyperionGenerationVerdict;
    timestamp?: string;
}

/** A whole-file snapshot streamed while the agent writes the repositories, for the live editor preview. */
export interface HyperionFileSnapshot {
    type: 'FILE_SNAPSHOT';
    path: string;
    repo: HyperionSnapshotRepo;
    action: HyperionSnapshotAction;
    content: string;
    sha256: string;
    bytes: number;
    truncated: boolean;
    turn: number;
    timestamp?: string;
}

/** Either kind of message delivered on the shared topic. */
export type HyperionGenerationMessage = HyperionGenerationEvent | HyperionFileSnapshot;

/** Narrows a stream message to a file snapshot. */
export function isFileSnapshot(message: HyperionGenerationMessage): message is HyperionFileSnapshot {
    return message.type === 'FILE_SNAPSHOT';
}

/** The reconnection view returned by the status endpoint so a reloading client can rehydrate and resume the stream. */
export interface HyperionGenerationStatus {
    jobId: string;
    running: boolean;
    /** The explicit run intent, so a reconnecting client restores the correct header label and the revert affordance without inferring it. Absent on runs started before this field existed. */
    mode?: HyperionGenerationMode;
    events: HyperionGenerationEvent[];
    fileSnapshots?: HyperionFileSnapshot[];
}

/** The explicit intent of a run, mirroring the server {@code GenerationMode}. Chosen by the client, never inferred from the exercise's contents. */
export type HyperionGenerationMode = 'GENERATE' | 'ADAPT';

/** Request body for starting an agentic whole-exercise generation/adaptation run (mirrors the server {@code ExerciseGenerationRequestDTO}). */
export interface HyperionGenerationRequest {
    mode?: HyperionGenerationMode;
    /** Optional brief (generate) or free-form instructions (adapt). */
    prompt?: string;
    /** Optional review-comment thread ids an adapt run should address; the server renders them into the brief. */
    selectedFeedbackThreadIds?: number[];
}

/** Response returned when a run is started: the id of the job whose progress the client then follows over the websocket. */
export interface HyperionGenerationJobStart {
    jobId: string;
}
