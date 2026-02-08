import { LockRepositoryPolicy, SubmissionPenaltyPolicy, SubmissionPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';

import {
    AuxiliaryRepositorySnapshotDTO,
    ParticipationSnapshotDTO,
    ProgrammingExerciseBuildConfigSnapshotDTO,
    SubmissionPolicySnapshotDTO,
} from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

export const toAuxiliaryRepositories = (repositories?: AuxiliaryRepositorySnapshotDTO[]): AuxiliaryRepository[] | undefined => {
    if (!repositories) {
        return undefined;
    }
    return repositories.map((repository) => {
        const aux = new AuxiliaryRepository();
        aux.id = repository.id;
        aux.name = repository.name;
        aux.checkoutDirectory = repository.checkoutDirectory;
        aux.description = repository.description;
        aux.repositoryUri = repository.repositoryUri;
        return aux;
    });
};

export const toSubmissionPolicy = (snapshot?: SubmissionPolicySnapshotDTO): SubmissionPolicy | undefined => {
    if (!snapshot) {
        return undefined;
    }
    let policy: SubmissionPolicy;
    switch (snapshot.type) {
        case 'SubmissionPenaltyPolicy':
            policy = new SubmissionPenaltyPolicy();
            policy.type = SubmissionPolicyType.SUBMISSION_PENALTY;
            policy.exceedingPenalty = snapshot.exceedingPenalty;
            break;
        case 'LockRepositoryPolicy':
            policy = new LockRepositoryPolicy();
            policy.type = SubmissionPolicyType.LOCK_REPOSITORY;
            policy.exceedingPenalty = undefined;
            break;
        default:
            policy = new LockRepositoryPolicy();
            policy.type = SubmissionPolicyType.LOCK_REPOSITORY;
            break;
    }
    policy.id = snapshot.id;
    policy.active = snapshot.active;
    policy.submissionLimit = snapshot.submissionLimit;
    return policy;
};

export const toBuildConfig = (snapshot?: ProgrammingExerciseBuildConfigSnapshotDTO): ProgrammingExerciseBuildConfig | undefined => {
    if (!snapshot) {
        return undefined;
    }
    const buildConfig = new ProgrammingExerciseBuildConfig();
    buildConfig.sequentialTestRuns = snapshot.sequentialTestRuns;
    buildConfig.buildPlanConfiguration = snapshot.buildPlanConfiguration;
    buildConfig.buildScript = snapshot.buildScript;
    buildConfig.checkoutSolutionRepository = snapshot.checkoutSolutionRepository ?? false;
    buildConfig.testCheckoutPath = snapshot.testCheckoutPath;
    buildConfig.assignmentCheckoutPath = snapshot.assignmentCheckoutPath;
    buildConfig.solutionCheckoutPath = snapshot.solutionCheckoutPath;
    buildConfig.timeoutSeconds = snapshot.timeoutSeconds;
    buildConfig.dockerFlags = snapshot.dockerFlags;
    buildConfig.theiaImage = snapshot.theiaImage;
    buildConfig.allowBranching = snapshot.allowBranching ?? false;
    buildConfig.branchRegex = snapshot.branchRegex ?? buildConfig.branchRegex;
    return buildConfig;
};

export const toTemplateParticipation = (snapshot: ParticipationSnapshotDTO | undefined): TemplateProgrammingExerciseParticipation | undefined => {
    if (!snapshot) {
        return undefined;
    }
    const participation = new TemplateProgrammingExerciseParticipation();
    participation.id = snapshot.id;
    participation.repositoryUri = snapshot.repositoryUri;
    participation.buildPlanId = snapshot.buildPlanId;
    return participation;
};

export const toSolutionParticipation = (snapshot: ParticipationSnapshotDTO | undefined): SolutionProgrammingExerciseParticipation | undefined => {
    if (!snapshot) {
        return undefined;
    }
    const participation = new SolutionProgrammingExerciseParticipation();
    participation.id = snapshot.id;
    participation.repositoryUri = snapshot.repositoryUri;
    participation.buildPlanId = snapshot.buildPlanId;
    return participation;
};
