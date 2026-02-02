import { describe, expect, it } from 'vitest';

import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { toSubmissionPolicy } from 'app/exercise/synchronization/exercise-metadata-snapshot-programming.mapper';

import { SubmissionPolicySnapshotDTO } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

describe('ExerciseMetadataSnapshotProgrammingMapper', () => {
    it('maps submission penalty policies', () => {
        const snapshot: SubmissionPolicySnapshotDTO = {
            id: 1,
            submissionLimit: 3,
            active: true,
            exceedingPenalty: 10,
            type: 'SubmissionPenaltyPolicy',
        };

        const policy = toSubmissionPolicy(snapshot);

        expect(policy?.type).toBe(SubmissionPolicyType.SUBMISSION_PENALTY);
        expect(policy?.exceedingPenalty).toBe(10);
        expect(policy?.submissionLimit).toBe(3);
    });

    it('maps lock repository policies by default', () => {
        const snapshot: SubmissionPolicySnapshotDTO = {
            id: 2,
            submissionLimit: 5,
            active: false,
            type: 'LockRepositoryPolicy',
        };

        const policy = toSubmissionPolicy(snapshot);

        expect(policy?.type).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(policy?.submissionLimit).toBe(5);
        expect(policy?.active).toBe(false);
    });
});
