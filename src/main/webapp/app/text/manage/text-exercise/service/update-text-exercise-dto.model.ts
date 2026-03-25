import dayjs from 'dayjs/esm';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/shared/util/date.utils';
import { CompetencyLinkDTO, GradingCriterionDTO } from 'app/exercise/shared/exercise-update-shared-dto.model';

/**
 * DTO for updating text exercises.
 * Matches the server-side UpdateTextExerciseDTO record structure.
 * Uses IDs instead of entity objects to avoid Hibernate detached entity issues.
 */
export interface UpdateTextExerciseDTO {
    // Base exercise fields
    id?: number;
    title?: string;
    shortName?: string;
    maxPoints: number;
    bonusPoints?: number;
    releaseDate?: dayjs.Dayjs | string | null;
    startDate?: dayjs.Dayjs | string | null;
    dueDate?: dayjs.Dayjs | string | null;
    assessmentDueDate?: dayjs.Dayjs | string | null;
    exampleSolutionPublicationDate?: dayjs.Dayjs | string | null;
    difficulty?: DifficultyLevel;

    // Exercise fields
    includedInOverallScore?: IncludedInOverallScore;
    problemStatement?: string;
    gradingInstructions?: string;
    categories?: string[];
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;
    feedbackSuggestionModule?: string;
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    channelName?: string;

    // Grading criteria
    gradingCriteria?: GradingCriterionDTO[];

    // Competency links as DTOs
    competencyLinks?: CompetencyLinkDTO[];

    // Course/ExerciseGroup references (by ID)
    courseId?: number;
    exerciseGroupId?: number;

    // TextExercise specific fields
    exampleSolution?: string;
}

/**
 * Converts a TextExercise entity to an UpdateTextExerciseDTO.
 * This ensures the correct data structure is sent to the server,
 * with courseId and exerciseGroupId as IDs instead of full objects.
 *
 * @param textExercise the text exercise to convert
 * @returns the corresponding DTO
 */
export function toUpdateTextExerciseDTO(textExercise: TextExercise): UpdateTextExerciseDTO {
    // Compute constrained bonus points without mutating the input
    const bonusPoints = textExercise.includedInOverallScore !== IncludedInOverallScore.INCLUDED_COMPLETELY ? 0 : (textExercise.bonusPoints ?? 0);

    // Convert competency links to DTOs (just competency ID and weight)
    const competencyLinkDTOs: CompetencyLinkDTO[] = (textExercise.competencyLinks ?? []).map((link) => ({
        competency: { id: link.competency!.id! },
        weight: link.weight,
    }));

    // Convert grading criteria to DTOs
    const gradingCriteriaDTOs: GradingCriterionDTO[] | undefined = textExercise.gradingCriteria?.map((criterion) => ({
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

    // Determine courseId and exerciseGroupId - only one should be set (mutually exclusive)
    // For course exercises: set courseId, leave exerciseGroupId undefined
    // For exam exercises: set exerciseGroupId, leave courseId undefined
    const exerciseGroupId = textExercise.exerciseGroup?.id;
    const courseId = exerciseGroupId ? undefined : textExercise.course?.id;

    // Convert categories to JSON strings
    const categories = ExerciseService.stringifyExerciseDTOCategories(textExercise);

    return {
        id: textExercise.id,
        title: textExercise.title,
        shortName: textExercise.shortName,
        maxPoints: textExercise.maxPoints!,
        bonusPoints,
        releaseDate: convertDateFromClient(textExercise.releaseDate),
        startDate: convertDateFromClient(textExercise.startDate),
        dueDate: convertDateFromClient(textExercise.dueDate),
        assessmentDueDate: convertDateFromClient(textExercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(textExercise.exampleSolutionPublicationDate),
        difficulty: textExercise.difficulty,
        includedInOverallScore: textExercise.includedInOverallScore,
        problemStatement: textExercise.problemStatement,
        gradingInstructions: textExercise.gradingInstructions,
        categories,
        presentationScoreEnabled: textExercise.presentationScoreEnabled,
        secondCorrectionEnabled: textExercise.secondCorrectionEnabled,
        feedbackSuggestionModule: textExercise.feedbackSuggestionModule,
        allowComplaintsForAutomaticAssessments: textExercise.allowComplaintsForAutomaticAssessments,
        allowFeedbackRequests: textExercise.allowFeedbackRequests,
        channelName: textExercise.channelName,
        gradingCriteria: gradingCriteriaDTOs,
        competencyLinks: competencyLinkDTOs,
        courseId,
        exerciseGroupId,
        exampleSolution: textExercise.exampleSolution,
    };
}
