import { ExerciseGenerationRevertResult as GeneratedRevertResult } from 'app/openapi/model/exercise-generation-revert-result';
import { ExerciseGenerationEvent as GeneratedEvent } from 'app/openapi/model/exercise-generation-event';
import { ExerciseGenerationFileSnapshot as GeneratedFileSnapshot } from 'app/openapi/model/exercise-generation-file-snapshot';
import { ExerciseGenerationJobStart as GeneratedJobStart } from 'app/openapi/model/exercise-generation-job-start';
import { ExerciseGenerationRequest as GeneratedRequest } from 'app/openapi/model/exercise-generation-request';
import { ExerciseGenerationStatus as GeneratedStatus } from 'app/openapi/model/exercise-generation-status';
import { ExerciseGenerationVerdict as GeneratedVerdict } from 'app/openapi/model/exercise-generation-verdict';

export type HyperionGenerationEventType = GeneratedEvent['type'];
export type HyperionGenerationCompletionStatus = NonNullable<GeneratedEvent['completionStatus']>;
export type HyperionSnapshotRepo = GeneratedFileSnapshot['repo'];
export type HyperionSnapshotAction = GeneratedFileSnapshot['action'];
export type HyperionGenerationMode = NonNullable<GeneratedRequest['mode']>;

export type HyperionGenerationVerdict = GeneratedVerdict;
export type HyperionGenerationEvent = GeneratedEvent;
export type ExerciseGenerationFileSnapshot = GeneratedFileSnapshot;
export type HyperionGenerationMessage = HyperionGenerationEvent | ExerciseGenerationFileSnapshot;

export function isFileSnapshot(message: HyperionGenerationMessage): message is ExerciseGenerationFileSnapshot {
    return message.type === 'FILE_SNAPSHOT';
}

export type HyperionGenerationStatus = Omit<GeneratedStatus, 'events' | 'fileSnapshots'> & {
    events: HyperionGenerationEvent[];
    fileSnapshots: ExerciseGenerationFileSnapshot[];
};

export type ExerciseGenerationRevertResult = GeneratedRevertResult;
export type HyperionGenerationRequest = GeneratedRequest;
export type HyperionGenerationJobStart = GeneratedJobStart;
