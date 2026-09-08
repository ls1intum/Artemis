import { ExerciseGenerationActivity as GeneratedActivity } from 'app/openapi/model/exercise-generation-activity';
import { ExerciseGenerationRevertResult as GeneratedRevertResult } from 'app/openapi/model/exercise-generation-revert-result';
import { ExerciseGenerationEvent as GeneratedEvent } from 'app/openapi/model/exercise-generation-event';
import { ExerciseGenerationFileChange as GeneratedFileChange } from 'app/openapi/model/exercise-generation-file-change';
import { ExerciseGenerationJobStart as GeneratedJobStart } from 'app/openapi/model/exercise-generation-job-start';
import { ExerciseGenerationRequest as GeneratedRequest } from 'app/openapi/model/exercise-generation-request';
import { ExerciseGenerationStatus as GeneratedStatus } from 'app/openapi/model/exercise-generation-status';
import { ExerciseGenerationMetadataSuggestionResponse as GeneratedMetadataSuggestion } from 'app/openapi/model/exercise-generation-metadata-suggestion-response';
import { ExerciseGenerationVerdict as GeneratedVerdict } from 'app/openapi/model/exercise-generation-verdict';

export type HyperionGenerationEventType = GeneratedEvent['type'];
export type HyperionGenerationCompletionStatus = NonNullable<GeneratedEvent['completionStatus']>;
export type HyperionFileChangeRepo = GeneratedFileChange['repo'];
export type HyperionFileChangeAction = GeneratedFileChange['action'];
export type HyperionGenerationMode = NonNullable<GeneratedRequest['mode']>;

export type HyperionGenerationVerdict = GeneratedVerdict;
/** The agent's own bookkeeping for the run so far, carried on the events it emits while it works. */
export type HyperionGenerationActivity = GeneratedActivity;
export type HyperionGenerationEvent = GeneratedEvent;
export type ExerciseGenerationFileChange = GeneratedFileChange;
export type HyperionGenerationMessage = HyperionGenerationEvent | ExerciseGenerationFileChange;

export interface HyperionExerciseGenerationState {
    exerciseId: number;
    jobId: string;
    running: boolean;
}

export function isFileChange(message: HyperionGenerationMessage): message is ExerciseGenerationFileChange {
    return message.type === 'FILE_CHANGE';
}

export type HyperionGenerationStatus = Omit<GeneratedStatus, 'events' | 'fileChanges'> & {
    events: HyperionGenerationEvent[];
    fileChanges: ExerciseGenerationFileChange[];
};

export type ExerciseGenerationRevertResult = GeneratedRevertResult;
export type HyperionGenerationRequest = GeneratedRequest;
export type HyperionGenerationJobStart = GeneratedJobStart;
/**
 * What Hyperion derives from a brief: the title and difficulty the instructor may overwrite, and the identifiers and points Artemis works out for them. Every value is one the
 * setup request will accept at the moment it was derived.
 */
export type HyperionMetadataSuggestion = GeneratedMetadataSuggestion;
