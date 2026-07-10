import { ExerciseAdaptationRevertResult as GeneratedRevertResult } from 'app/openapi/model/exerciseAdaptationRevertResult';
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

// Retained events from an older node may omit timestamps during a rolling deployment.
export type HyperionGenerationEvent = Omit<GeneratedEvent, 'timestamp'> & { timestamp?: string };
export type ExerciseGenerationFileSnapshot = GeneratedFileSnapshot;
export type HyperionGenerationMessage = HyperionGenerationEvent | ExerciseGenerationFileSnapshot;

export function isFileSnapshot(message: HyperionGenerationMessage): message is ExerciseGenerationFileSnapshot {
    return message.type === 'FILE_SNAPSHOT';
}

export type HyperionGenerationStatus = Omit<GeneratedStatus, 'events' | 'fileSnapshots' | 'revertAvailable'> & {
    events: HyperionGenerationEvent[];
    fileSnapshots: ExerciseGenerationFileSnapshot[];
    // Optional while older nodes remain in a rolling deployment.
    revertAvailable?: boolean;
};

export type ExerciseAdaptationRevertResult = GeneratedRevertResult;
export type HyperionGenerationRequest = GeneratedRequest;
export type HyperionGenerationJobStart = GeneratedJobStart;
