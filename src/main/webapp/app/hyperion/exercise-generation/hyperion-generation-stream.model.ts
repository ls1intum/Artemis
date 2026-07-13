import { ExerciseGenerationRevertResult as GeneratedRevertResult } from 'app/openapi/model/exerciseGenerationRevertResult';
import { ExerciseGenerationEvent as GeneratedEvent } from 'app/openapi/model/exerciseGenerationEvent';
import { ExerciseGenerationFileSnapshot as GeneratedFileSnapshot } from 'app/openapi/model/exerciseGenerationFileSnapshot';
import { ExerciseGenerationJobStart as GeneratedJobStart } from 'app/openapi/model/exerciseGenerationJobStart';
import { ExerciseGenerationRequest as GeneratedRequest } from 'app/openapi/model/exerciseGenerationRequest';
import { ExerciseGenerationStatus as GeneratedStatus } from 'app/openapi/model/exerciseGenerationStatus';
import { ExerciseGenerationVerdict as GeneratedVerdict } from 'app/openapi/model/exerciseGenerationVerdict';

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
