import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { DifficultyLevel, ExerciseMode, IncludedInOverallScore, PlagiarismDetectionConfig } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { StaticCodeAnalysisCategoryState } from 'app/programming/shared/entities/static-code-analysis-category.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';

export interface ExerciseSnapshotDTO {
    id: number;
    title?: string;
    shortName?: string;
    channelName?: string;
    competencyLinks?: CompetencyExerciseLinkSnapshotDTO[];
    maxPoints?: number;
    bonusPoints?: number;
    assessmentType?: AssessmentType;
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;
    difficulty?: DifficultyLevel;
    mode?: ExerciseMode;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    includedInOverallScore?: IncludedInOverallScore;
    problemStatement?: string;
    gradingInstructions?: string;
    categories?: string[];
    teamAssignmentConfig?: TeamAssignmentConfigSnapshot;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    feedbackSuggestionModule?: string;
    gradingCriteria?: GradingCriterion[];
    plagiarismDetectionConfig?: PlagiarismDetectionConfig;
    programmingData?: ProgrammingExerciseSnapshotDTO;
}

export interface TeamAssignmentConfigSnapshot {
    id?: number;
    minTeamSize?: number;
    maxTeamSize?: number;
}

export interface CompetencyExerciseLinkSnapshotDTO {
    competencyId?: CompetencyExerciseIdSnapshotDTO;
    weight?: number;
}

export interface CompetencyExerciseIdSnapshotDTO {
    exerciseId?: number;
    competencyId?: number;
}

/**
 * Client-side representation of the server's {@code ProgrammingExerciseSnapshotDTO}.
 *
 * Fields like {@link testCases}, {@link tasks}, {@link staticCodeAnalysisCategories},
 * and {@link testsCommitId} are not used by metadata sync (they are excluded from
 * change detection) but are included here for DTO completeness.
 */
export interface ProgrammingExerciseSnapshotDTO {
    testRepositoryUri?: string;
    auxiliaryRepositories?: AuxiliaryRepositorySnapshotDTO[];
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde?: boolean;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    programmingLanguage?: ProgrammingLanguage;
    packageName?: string;
    showTestNamesToStudents?: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
    projectKey?: string;
    templateParticipation?: ParticipationSnapshotDTO;
    solutionParticipation?: ParticipationSnapshotDTO;
    testCases?: TestCaseSnapshotDTO[];
    tasks?: TaskSnapshotDTO[];
    staticCodeAnalysisCategories?: StaticCodeAnalysisCategorySnapshotDTO[];
    submissionPolicy?: SubmissionPolicySnapshotDTO;
    projectType?: ProjectType;
    releaseTestsWithExampleSolution?: boolean;
    buildConfig?: ProgrammingExerciseBuildConfigSnapshotDTO;
    testsCommitId?: string;
}

export interface AuxiliaryRepositorySnapshotDTO {
    id?: number;
    name?: string;
    checkoutDirectory?: string;
    description?: string;
    repositoryUri?: string;
    commitId?: string;
}

export interface ParticipationSnapshotDTO {
    id?: number;
    repositoryUri?: string;
    buildPlanId?: string;
    commitId?: string;
}

export interface SubmissionPolicySnapshotDTO {
    id?: number;
    submissionLimit?: number;
    active?: boolean;
    exceedingPenalty?: number;
    type?: string;
}

export interface TestCaseSnapshotDTO {
    id?: number;
    weight?: number;
    bonusMultiplier?: number;
    bonusPoints?: number;
    visibility?: Visibility;
}

export interface TaskSnapshotDTO {
    id?: number;
    taskName?: string;
    testCases?: TestCaseSnapshotDTO[];
}

export interface StaticCodeAnalysisCategorySnapshotDTO {
    id?: number;
    name?: string;
    penalty?: number;
    maxPenalty?: number;
    state?: StaticCodeAnalysisCategoryState;
}

export interface ProgrammingExerciseBuildConfigSnapshotDTO {
    sequentialTestRuns?: boolean;
    branch?: string;
    buildPlanConfiguration?: string;
    buildScript?: string;
    checkoutSolutionRepository?: boolean;
    testCheckoutPath?: string;
    assignmentCheckoutPath?: string;
    solutionCheckoutPath?: string;
    timeoutSeconds?: number;
    dockerFlags?: string;
    theiaImage?: string;
    allowBranching?: boolean;
    branchRegex?: string;
}
