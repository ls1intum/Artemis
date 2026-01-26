import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/shared/util/date.utils';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';

/**
 * DTO for competency reference (just the ID)
 */
export interface CompetencyDTO {
    id: number;
}

/**
 * DTO for competency links with weight
 */
export interface CompetencyLinkDTO {
    competency: CompetencyDTO;
    weight: number;
}

/**
 * DTO for grading criterion
 */
export interface GradingCriterionDTO {
    id?: number;
    title?: string;
    structuredGradingInstructions?: GradingInstructionDTO[];
}

/**
 * DTO for grading instruction
 */
export interface GradingInstructionDTO {
    id?: number;
    credits?: number;
    gradingScale?: string;
    instructionDescription?: string;
    feedback?: string;
    usageCount?: number;
}

/**
 * DTO for auxiliary repository
 */
export interface AuxiliaryRepositoryDTO {
    id?: number;
    name?: string;
    checkoutDirectory?: string;
    repositoryUri?: string;
    description?: string;
}

/**
 * DTO for build config
 */
export interface UpdateProgrammingExerciseBuildConfigDTO {
    sequentialTestRuns?: boolean;
    buildPlanConfiguration?: string;
    buildScript?: string;
    checkoutSolutionRepository: boolean;
    testCheckoutPath?: string;
    assignmentCheckoutPath?: string;
    solutionCheckoutPath?: string;
    timeoutSeconds: number;
    dockerFlags?: string;
    theiaImage?: string;
    allowBranching: boolean;
    branchRegex?: string;
}

/**
 * DTO for updating programming exercises.
 * Matches the server-side UpdateProgrammingExerciseDTO record structure.
 */
export interface UpdateProgrammingExerciseDTO {
    // Core identification
    id: number;

    // Exercise base fields
    title?: string;
    channelName?: string;
    shortName?: string;
    problemStatement?: string;
    categories?: string[];
    difficulty?: DifficultyLevel;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    feedbackSuggestionModule?: string;
    gradingInstructions?: string;

    // Timeline fields
    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;

    // Course/ExerciseGroup references (by ID)
    courseId?: number;
    exerciseGroupId?: number;

    // Grading and competencies
    gradingCriteria?: GradingCriterionDTO[];
    competencyLinks?: CompetencyLinkDTO[];

    // Programming exercise specific fields
    testRepositoryUri?: string;
    auxiliaryRepositories?: AuxiliaryRepositoryDTO[];
    allowOnlineEditor?: boolean;
    allowOfflineIde?: boolean;
    allowOnlineIde: boolean;
    staticCodeAnalysisEnabled?: boolean;
    maxStaticCodeAnalysisPenalty?: number;
    programmingLanguage?: ProgrammingLanguage;
    packageName?: string;
    showTestNamesToStudents: boolean;
    buildAndTestStudentSubmissionsAfterDueDate?: string;
    testCasesChanged?: boolean;
    projectKey?: string;
    submissionPolicy?: SubmissionPolicy;
    projectType?: ProjectType;
    releaseTestsWithExampleSolution: boolean;

    // Build config
    buildConfig?: UpdateProgrammingExerciseBuildConfigDTO;
}

/**
 * Converts a ProgrammingExercise entity to an UpdateProgrammingExerciseDTO.
 * This ensures the correct data structure is sent to the server.
 *
 * @param exercise the programming exercise to convert
 * @returns the corresponding DTO
 */
export function toUpdateProgrammingExerciseDTO(exercise: ProgrammingExercise): UpdateProgrammingExerciseDTO {
    // Apply bonus points constraint
    ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(exercise);

    // Convert competency links to DTOs
    const competencyLinkDTOs: CompetencyLinkDTO[] | undefined = exercise.competencyLinks?.map((link) => ({
        competency: { id: link.competency!.id! },
        weight: link.weight,
    }));

    // Convert grading criteria to DTOs
    const gradingCriteriaDTOs: GradingCriterionDTO[] | undefined = exercise.gradingCriteria?.map((criterion) => ({
        id: criterion.id,
        title: criterion.title,
        structuredGradingInstructions: criterion.structuredGradingInstructions?.map((instruction) => ({
            id: instruction.id,
            credits: instruction.credits,
            gradingScale: instruction.gradingScale,
            instructionDescription: instruction.instructionDescription,
            feedback: instruction.feedback,
            usageCount: instruction.usageCount,
        })),
    }));

    // Convert auxiliary repositories to DTOs
    const auxiliaryRepositoryDTOs: AuxiliaryRepositoryDTO[] | undefined = exercise.auxiliaryRepositories?.map((repo) => ({
        id: repo.id,
        name: repo.name,
        checkoutDirectory: repo.checkoutDirectory,
        repositoryUri: repo.repositoryUri,
        description: repo.description,
    }));

    // Convert build config to DTO
    const buildConfigDTO: UpdateProgrammingExerciseBuildConfigDTO | undefined = exercise.buildConfig
        ? {
              sequentialTestRuns: exercise.buildConfig.sequentialTestRuns,
              buildPlanConfiguration: exercise.buildConfig.buildPlanConfiguration,
              buildScript: exercise.buildConfig.buildScript,
              checkoutSolutionRepository: exercise.buildConfig.checkoutSolutionRepository ?? false,
              testCheckoutPath: exercise.buildConfig.testCheckoutPath,
              assignmentCheckoutPath: exercise.buildConfig.assignmentCheckoutPath,
              solutionCheckoutPath: exercise.buildConfig.solutionCheckoutPath,
              timeoutSeconds: exercise.buildConfig.timeoutSeconds ?? 120,
              dockerFlags: exercise.buildConfig.dockerFlags,
              theiaImage: exercise.buildConfig.theiaImage,
              allowBranching: exercise.buildConfig.allowBranching ?? false,
              branchRegex: exercise.buildConfig.branchRegex,
          }
        : undefined;

    // Determine courseId and exerciseGroupId - only one should be set (mutually exclusive)
    // For course exercises: set courseId, leave exerciseGroupId undefined
    // For exam exercises: set exerciseGroupId, leave courseId undefined
    const exerciseGroupId = exercise.exerciseGroup?.id;
    const courseId = exerciseGroupId ? undefined : exercise.course?.id;

    // Convert categories to JSON strings
    const categories = ExerciseService.stringifyExerciseDTOCategories(exercise);

    return {
        id: exercise.id!,
        title: exercise.title,
        channelName: exercise.channelName,
        shortName: exercise.shortName,
        problemStatement: exercise.problemStatement,
        categories,
        difficulty: exercise.difficulty,
        maxPoints: exercise.maxPoints,
        bonusPoints: exercise.bonusPoints,
        includedInOverallScore: exercise.includedInOverallScore,
        allowComplaintsForAutomaticAssessments: exercise.allowComplaintsForAutomaticAssessments,
        allowFeedbackRequests: exercise.allowFeedbackRequests,
        presentationScoreEnabled: exercise.presentationScoreEnabled,
        secondCorrectionEnabled: exercise.secondCorrectionEnabled,
        feedbackSuggestionModule: exercise.feedbackSuggestionModule,
        gradingInstructions: exercise.gradingInstructions,
        releaseDate: convertDateFromClient(exercise.releaseDate),
        startDate: convertDateFromClient(exercise.startDate),
        dueDate: convertDateFromClient(exercise.dueDate),
        assessmentDueDate: convertDateFromClient(exercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(exercise.exampleSolutionPublicationDate),
        courseId,
        exerciseGroupId,
        gradingCriteria: gradingCriteriaDTOs,
        competencyLinks: competencyLinkDTOs,
        testRepositoryUri: exercise.testRepositoryUri,
        auxiliaryRepositories: auxiliaryRepositoryDTOs,
        allowOnlineEditor: exercise.allowOnlineEditor,
        allowOfflineIde: exercise.allowOfflineIde,
        allowOnlineIde: exercise.allowOnlineIde ?? false,
        staticCodeAnalysisEnabled: exercise.staticCodeAnalysisEnabled,
        maxStaticCodeAnalysisPenalty: exercise.maxStaticCodeAnalysisPenalty,
        programmingLanguage: exercise.programmingLanguage,
        packageName: exercise.packageName,
        showTestNamesToStudents: exercise.showTestNamesToStudents ?? false,
        buildAndTestStudentSubmissionsAfterDueDate: convertDateFromClient(exercise.buildAndTestStudentSubmissionsAfterDueDate),
        testCasesChanged: exercise.testCasesChanged,
        projectKey: exercise.projectKey,
        submissionPolicy: exercise.submissionPolicy,
        projectType: exercise.projectType,
        releaseTestsWithExampleSolution: exercise.releaseTestsWithExampleSolution ?? false,
        buildConfig: buildConfigDTO,
    };
}
