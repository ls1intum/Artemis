import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import {
    toAuxiliaryRepositories,
    toBuildConfig,
    toSolutionParticipation,
    toSubmissionPolicy,
    toTemplateParticipation,
} from 'app/exercise/synchronization/exercise-metadata-snapshot-programming.mapper';

import {
    AuxiliaryRepositorySnapshotDTO,
    ParticipationSnapshotDTO,
    ProgrammingExerciseBuildConfigSnapshotDTO,
    SubmissionPolicySnapshotDTO,
} from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

describe('ExerciseMetadataSnapshotProgrammingMapper', () => {
    it('returns undefined when auxiliary repositories snapshot is missing', () => {
        expect(toAuxiliaryRepositories(undefined)).toBeUndefined();
    });

    it('maps auxiliary repositories', () => {
        const snapshot: AuxiliaryRepositorySnapshotDTO[] = [
            { id: 7, repositoryUri: 'git@server:repo-a.git' },
            { id: 8, repositoryUri: 'git@server:repo-b.git' },
        ];

        const mapped = toAuxiliaryRepositories(snapshot);

        expect(mapped).toHaveLength(2);
        expect(mapped?.[0].id).toBe(7);
        expect(mapped?.[0].repositoryUri).toBe('git@server:repo-a.git');
        expect(mapped?.[1].id).toBe(8);
        expect(mapped?.[1].repositoryUri).toBe('git@server:repo-b.git');
    });

    it('maps an empty auxiliary repositories list to an empty result', () => {
        const mapped = toAuxiliaryRepositories([]);

        expect(mapped).toEqual([]);
    });

    it('returns undefined when submission policy snapshot is missing', () => {
        expect(toSubmissionPolicy(undefined)).toBeUndefined();
    });

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
        expect(policy?.active).toBeTrue();
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
        expect(policy?.active).toBeFalse();
        expect(policy?.exceedingPenalty).toBeUndefined();
    });

    it('maps unknown submission policy types to lock repository policy', () => {
        const snapshot: SubmissionPolicySnapshotDTO = {
            id: 3,
            submissionLimit: 2,
            active: true,
            type: 'UnknownPolicy',
        };

        const policy = toSubmissionPolicy(snapshot);

        expect(policy?.type).toBe(SubmissionPolicyType.LOCK_REPOSITORY);
        expect(policy?.submissionLimit).toBe(2);
        expect(policy?.active).toBeTrue();
        expect(policy?.exceedingPenalty).toBeUndefined();
    });

    it('returns undefined when build config snapshot is missing', () => {
        expect(toBuildConfig(undefined)).toBeUndefined();
    });

    it('maps build configuration snapshot with defaults', () => {
        const snapshot: ProgrammingExerciseBuildConfigSnapshotDTO = {
            sequentialTestRuns: true,
            buildPlanConfiguration: 'Maven',
            buildScript: 'gradle test',
            checkoutSolutionRepository: true,
            testCheckoutPath: 'tests',
            assignmentCheckoutPath: 'assignment',
            solutionCheckoutPath: 'solution',
            timeoutSeconds: 120,
            dockerFlags: '--platform linux/amd64',
            theiaImage: 'theia:latest',
            allowBranching: true,
        };

        const config = toBuildConfig(snapshot);

        expect(config?.sequentialTestRuns).toBeTrue();
        expect(config?.buildPlanConfiguration).toBe('Maven');
        expect(config?.buildScript).toBe('gradle test');
        expect(config?.checkoutSolutionRepository).toBeTrue();
        expect(config?.testCheckoutPath).toBe('tests');
        expect(config?.assignmentCheckoutPath).toBe('assignment');
        expect(config?.solutionCheckoutPath).toBe('solution');
        expect(config?.timeoutSeconds).toBe(120);
        expect(config?.dockerFlags).toBe('--platform linux/amd64');
        expect(config?.theiaImage).toBe('theia:latest');
        expect(config?.allowBranching).toBeTrue();
        expect(config?.branchRegex).toBe('.*');
    });

    it('maps build configuration branch regex when provided', () => {
        const snapshot: ProgrammingExerciseBuildConfigSnapshotDTO = {
            branchRegex: 'feature/.*',
            checkoutSolutionRepository: false,
            allowBranching: false,
        };

        const config = toBuildConfig(snapshot);

        expect(config?.branchRegex).toBe('feature/.*');
        expect(config?.checkoutSolutionRepository).toBeFalse();
        expect(config?.allowBranching).toBeFalse();
    });

    it('uses defaults for omitted build configuration booleans and branch regex', () => {
        const snapshot: ProgrammingExerciseBuildConfigSnapshotDTO = {};

        const config = toBuildConfig(snapshot);

        expect(config?.checkoutSolutionRepository).toBeFalse();
        expect(config?.allowBranching).toBeFalse();
        expect(config?.branchRegex).toBe('.*');
    });

    it('returns undefined when participations are missing', () => {
        expect(toTemplateParticipation(undefined)).toBeUndefined();
        expect(toSolutionParticipation(undefined)).toBeUndefined();
    });

    it('maps template and solution participations', () => {
        const snapshot: ParticipationSnapshotDTO = {
            id: 22,
            repositoryUri: 'git@server:template.git',
            buildPlanId: 'TPL-PLAN',
        };

        const template = toTemplateParticipation(snapshot);
        const solution = toSolutionParticipation(snapshot);

        expect(template?.id).toBe(22);
        expect(template?.repositoryUri).toBe('git@server:template.git');
        expect(template?.buildPlanId).toBe('TPL-PLAN');
        expect(solution?.id).toBe(22);
        expect(solution?.repositoryUri).toBe('git@server:template.git');
        expect(solution?.buildPlanId).toBe('TPL-PLAN');
    });
});
