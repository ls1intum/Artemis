import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CompetencyExerciseLinkDTO, mapCompetencyLinks } from 'app/atlas/shared/dto/competency-exercise-link-dto';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/shared/util/date.utils';

export interface UpdateFileUploadExerciseDto {
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
    allowComplaintsForAutomaticAssessments?: boolean;
    allowFeedbackRequests?: boolean;
    presentationScoreEnabled?: boolean;
    secondCorrectionEnabled?: boolean;

    releaseDate?: string;
    startDate?: string;
    dueDate?: string;
    assessmentDueDate?: string;
    exampleSolutionPublicationDate?: string;

    exampleSolution?: string;
    filePattern?: string;

    courseId?: number;
    exerciseGroupId?: number;

    gradingCriteria?: GradingCriterion[];
    gradingInstructions?: string;
    feedbackSuggestionModule?: string;
    competencyLinks?: CompetencyExerciseLinkDTO[];
}

/**
 * Convert FileUploadExercise → Update DTO.
 */
export function toUpdateFileUploadExerciseDTO(fileUploadExercise: FileUploadExercise): UpdateFileUploadExerciseDto {
    fileUploadExercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(fileUploadExercise);
    const categories = ExerciseService.stringifyExerciseDTOCategories(fileUploadExercise);
    return {
        id: fileUploadExercise.id!,
        title: fileUploadExercise.title,
        channelName: fileUploadExercise.channelName,
        shortName: fileUploadExercise.shortName,
        problemStatement: fileUploadExercise.problemStatement,
        categories: categories,
        difficulty: fileUploadExercise.difficulty,
        maxPoints: fileUploadExercise.maxPoints,
        bonusPoints: fileUploadExercise.bonusPoints,
        includedInOverallScore: fileUploadExercise.includedInOverallScore,
        allowComplaintsForAutomaticAssessments: fileUploadExercise.allowComplaintsForAutomaticAssessments ?? false,
        allowFeedbackRequests: fileUploadExercise.allowFeedbackRequests ?? false,
        presentationScoreEnabled: fileUploadExercise.presentationScoreEnabled ?? false,
        secondCorrectionEnabled: fileUploadExercise.secondCorrectionEnabled ?? false,
        releaseDate: convertDateFromClient(fileUploadExercise.releaseDate),
        startDate: convertDateFromClient(fileUploadExercise.startDate),
        dueDate: convertDateFromClient(fileUploadExercise.dueDate),
        assessmentDueDate: convertDateFromClient(fileUploadExercise.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateFromClient(fileUploadExercise.exampleSolutionPublicationDate),
        exampleSolution: fileUploadExercise.exampleSolution,
        filePattern: fileUploadExercise.filePattern,
        courseId: fileUploadExercise.course?.id,
        exerciseGroupId: fileUploadExercise.exerciseGroup?.id,
        gradingCriteria: fileUploadExercise.gradingCriteria ?? [],
        gradingInstructions: fileUploadExercise.gradingInstructions,
        feedbackSuggestionModule: fileUploadExercise.feedbackSuggestionModule,
        competencyLinks: mapCompetencyLinks(fileUploadExercise.competencyLinks),
    };
}
