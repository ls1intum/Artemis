import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient } from 'app/foundation/util/date.utils';
import { toCompetencyLinkDTO } from 'app/fileupload/shared/entities/file-upload-exercise-dto';
import { UpdateFileUploadExercise } from 'app/openapi/model/update-file-upload-exercise';

/**
 * Convert FileUploadExercise → Update DTO.
 */
export function toUpdateFileUploadExerciseDTO(fileUploadExercise: FileUploadExercise): UpdateFileUploadExercise {
    const id = fileUploadExercise.id;
    const title = fileUploadExercise.title;
    if (id === undefined) {
        throw new Error('Cannot create a file upload exercise update request without an ID');
    }
    if (title === undefined) {
        throw new Error('Cannot create a file upload exercise update request without a title');
    }
    fileUploadExercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(fileUploadExercise);
    const categories = ExerciseService.stringifyExerciseDTOCategories(fileUploadExercise);
    return {
        id,
        title,
        channelName: fileUploadExercise.channelName,
        shortName: fileUploadExercise.shortName,
        problemStatement: fileUploadExercise.problemStatement,
        categories: categories,
        difficulty: fileUploadExercise.difficulty,
        maxPoints: fileUploadExercise.maxPoints,
        bonusPoints: fileUploadExercise.bonusPoints,
        includedInOverallScore: fileUploadExercise.includedInOverallScore,
        allowComplaintsForAutomaticAssessments: fileUploadExercise.allowComplaintsForAutomaticAssessments ?? false,
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
        competencyLinks: (fileUploadExercise.competencyLinks ?? []).map((link) => toCompetencyLinkDTO(link, 1)),
    };
}
