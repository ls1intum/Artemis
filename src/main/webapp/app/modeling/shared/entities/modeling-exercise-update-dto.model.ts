import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/shared/util/date.utils';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';

export interface UpdateModelingExerciseDTO {
    id: number;

    title?: string;
    channelName?: string;
    shortName?: string;
    problemStatement?: string;
    categories?: string[];

    difficulty?: DifficultyLevel;
    maxPoints?: number;
    bonusPoints?: number;
    includedInOverallScore?: IncludedInOverallScore;
    allowFeedbackRequests?: boolean;

    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;

    exampleSolutionModel?: string;
    exampleSolutionExplanation?: string;

    courseId?: number;
    exerciseGroupId?: number;

    gradingCriteria?: GradingCriterion[];
    gradingInstructions?: string;
    competencyLinks?: CompetencyExerciseLink[];
}

/**
 * Convert ModelingExercise → Update DTO.
 */
export function toUpdateModelingExerciseDTO(modelingExercise: ModelingExercise): UpdateModelingExerciseDTO {
    modelingExercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(modelingExercise);
    const categories = ExerciseService.stringifyExerciseDTOCategories(modelingExercise);
    return {
        id: modelingExercise.id!,
        title: modelingExercise.title,
        channelName: modelingExercise.channelName,
        shortName: modelingExercise.shortName,
        problemStatement: modelingExercise.problemStatement,
        categories: categories,
        difficulty: modelingExercise.difficulty,
        maxPoints: modelingExercise.maxPoints,
        bonusPoints: modelingExercise.bonusPoints,
        includedInOverallScore: modelingExercise.includedInOverallScore,
        allowFeedbackRequests: modelingExercise.allowFeedbackRequests,
        releaseDate: convertDateFromClient(modelingExercise.releaseDate),
        startDate: convertDateFromClient(modelingExercise.startDate),
        dueDate: convertDateFromClient(modelingExercise.dueDate),
        assessmentDueDate: convertDateFromClient(modelingExercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(modelingExercise.exampleSolutionPublicationDate),
        exampleSolutionModel: modelingExercise.exampleSolutionModel,
        exampleSolutionExplanation: modelingExercise.exampleSolutionExplanation,
        courseId: modelingExercise.course?.id,
        exerciseGroupId: modelingExercise.exerciseGroup?.id,
        gradingCriteria: modelingExercise.gradingCriteria ?? [],
        gradingInstructions: modelingExercise.gradingInstructions,
        competencyLinks: modelingExercise.competencyLinks,
    };
}
