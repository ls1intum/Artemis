import { DifficultyLevel, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/foundation/util/date.utils';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { CompetencyLinkDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';
import { UMLDiagramType } from '@tumaet/apollon';

/** Minimal team assignment configuration sent to the server (matches the server-side TeamAssignmentConfigDTO). */
export interface TeamAssignmentConfigDTO {
    minTeamSize?: number;
    maxTeamSize?: number;
}

export interface UpdateModelingExerciseDTO {
    id?: number;

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
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;

    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;

    diagramType?: UMLDiagramType;
    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;

    courseId?: number;
    exerciseGroupId?: number;

    // Mode and team configuration (only honored at creation time on the server)
    mode?: ExerciseMode;
    teamAssignmentConfig?: TeamAssignmentConfigDTO;
    plagiarismDetectionConfig?: ModelingExercise['plagiarismDetectionConfig'];

    gradingCriteria?: GradingCriterion[];
    gradingInstructions?: string;
    competencyLinks?: CompetencyLinkDTO[];
}

/** DTO for importing modeling exercises. Matches the server-side ImportModelingExerciseDTO record. */
export type ImportModelingExerciseDTO = UpdateModelingExerciseDTO;

/**
 * Convert ModelingExercise → Update DTO.
 */
export function toUpdateModelingExerciseDTO(modelingExercise: ModelingExercise): UpdateModelingExerciseDTO {
    modelingExercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(modelingExercise);
    const categories = ExerciseService.stringifyExerciseDTOCategories(modelingExercise);
    // Determine courseId and exerciseGroupId - only one should be set (mutually exclusive).
    // For course exercises: set courseId, leave exerciseGroupId undefined.
    // For exam exercises: set exerciseGroupId, leave courseId undefined (checkCourseAndExerciseGroupExclusivity rejects both).
    const exerciseGroupId = modelingExercise.exerciseGroup?.id;
    const courseId = exerciseGroupId ? undefined : (modelingExercise.course?.id ?? modelingExercise.exerciseGroup?.exam?.course?.id);
    return {
        id: modelingExercise.id,
        title: modelingExercise.title,
        channelName: modelingExercise.channelName,
        shortName: modelingExercise.shortName,
        problemStatement: modelingExercise.problemStatement,
        categories: categories,
        difficulty: modelingExercise.difficulty,
        maxPoints: modelingExercise.maxPoints,
        bonusPoints: modelingExercise.bonusPoints,
        includedInOverallScore: modelingExercise.includedInOverallScore,
        allowComplaintsForAutomaticAssessments: modelingExercise.allowComplaintsForAutomaticAssessments ?? false,
        presentationScoreEnabled: modelingExercise.presentationScoreEnabled ?? false,
        secondCorrectionEnabled: modelingExercise.secondCorrectionEnabled ?? false,
        releaseDate: convertDateFromClient(modelingExercise.releaseDate),
        startDate: convertDateFromClient(modelingExercise.startDate),
        dueDate: convertDateFromClient(modelingExercise.dueDate),
        assessmentDueDate: convertDateFromClient(modelingExercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(modelingExercise.exampleSolutionPublicationDate),
        diagramType: modelingExercise.diagramType,
        exampleSolutionModel: modelingExercise.exampleSolutionModel,
        exampleSolutionExplanation: modelingExercise.exampleSolutionExplanation,
        courseId,
        exerciseGroupId,
        mode: modelingExercise.mode,
        teamAssignmentConfig: modelingExercise.teamAssignmentConfig
            ? { minTeamSize: modelingExercise.teamAssignmentConfig.minTeamSize, maxTeamSize: modelingExercise.teamAssignmentConfig.maxTeamSize }
            : undefined,
        plagiarismDetectionConfig: modelingExercise.plagiarismDetectionConfig,
        gradingCriteria: modelingExercise.gradingCriteria ?? [],
        gradingInstructions: modelingExercise.gradingInstructions,
        competencyLinks: (modelingExercise.competencyLinks ?? []).map((link) => ({
            competency: { id: link.competency!.id! },
            weight: link.weight ?? 1,
        })),
    };
}

/**
 * Converts a ModelingExercise entity to an ImportModelingExerciseDTO (flat course/exerciseGroup ids plus mode, team and
 * plagiarism configuration), so the import endpoint receives the dumb DTO shape instead of a nested entity.
 *
 * @param modelingExercise the (adapted) source exercise to import
 * @returns the corresponding ImportModelingExerciseDTO
 */
export function toImportModelingExerciseDTO(modelingExercise: ModelingExercise): ImportModelingExerciseDTO {
    return toUpdateModelingExerciseDTO(modelingExercise);
}
