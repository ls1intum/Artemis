import { describe, expect, it } from 'vitest';

import {
    toAuxiliaryRepositories,
    toBuildConfig,
    toSolutionParticipation,
    toTemplateParticipation,
} from 'app/exercise/synchronization/exercise-metadata-snapshot-programming.mapper';

import { AuxiliaryRepositorySnapshotDTO, ParticipationSnapshotDTO, ProgrammingExerciseBuildConfigSnapshotDTO } from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

describe('ExerciseMetadataSnapshotProgrammingMapper', () => {
    it('returns undefined when auxiliary repositories snapshot is missing', () => {
        expect(toAuxiliaryRepositories(undefined)).toBeUndefined();
    });

    it('maps auxiliary repositories', () => {
        const snapshot: AuxiliaryRepositorySnapshotDTO[] = [
            { id: 7, name: 'repo-a', checkoutDirectory: 'dir-a', description: 'desc-a', repositoryUri: 'git@server:repo-a.git' },
            { id: 8, name: 'repo-b', checkoutDirectory: 'dir-b', description: 'desc-b', repositoryUri: 'git@server:repo-b.git' },
        ];

        const mapped = toAuxiliaryRepositories(snapshot);

        expect(mapped).toHaveLength(2);
        expect(mapped?.[0].id).toBe(7);
        expect(mapped?.[0].name).toBe('repo-a');
        expect(mapped?.[0].checkoutDirectory).toBe('dir-a');
        expect(mapped?.[0].description).toBe('desc-a');
        expect(mapped?.[0].repositoryUri).toBe('git@server:repo-a.git');
        expect(mapped?.[1].id).toBe(8);
        expect(mapped?.[1].name).toBe('repo-b');
        expect(mapped?.[1].checkoutDirectory).toBe('dir-b');
        expect(mapped?.[1].description).toBe('desc-b');
        expect(mapped?.[1].repositoryUri).toBe('git@server:repo-b.git');
    });

    it('maps an empty auxiliary repositories list to an empty result', () => {
        const mapped = toAuxiliaryRepositories([]);

        expect(mapped).toEqual([]);
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

        expect(config?.sequentialTestRuns).toBe(true);
        expect(config?.buildPlanConfiguration).toBe('Maven');
        expect(config?.buildScript).toBe('gradle test');
        expect(config?.checkoutSolutionRepository).toBe(true);
        expect(config?.testCheckoutPath).toBe('tests');
        expect(config?.assignmentCheckoutPath).toBe('assignment');
        expect(config?.solutionCheckoutPath).toBe('solution');
        expect(config?.timeoutSeconds).toBe(120);
        expect(config?.dockerFlags).toBe('--platform linux/amd64');
        expect(config?.theiaImage).toBe('theia:latest');
        expect(config?.allowBranching).toBe(true);
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
        expect(config?.checkoutSolutionRepository).toBe(false);
        expect(config?.allowBranching).toBe(false);
    });

    it('uses defaults for omitted build configuration booleans and branch regex', () => {
        const snapshot: ProgrammingExerciseBuildConfigSnapshotDTO = {};

        const config = toBuildConfig(snapshot);

        expect(config?.checkoutSolutionRepository).toBe(false);
        expect(config?.allowBranching).toBe(false);
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
