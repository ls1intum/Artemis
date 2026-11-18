import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * DTO for displaying ratings in the instructor dashboard rating list.
 * This is a flat structure matching the server-side RatingListItemDTO.
 */
export interface RatingListItem {
    id: number;
    rating: number;
    assessmentType?: AssessmentType;
    assessorLogin?: string;
    assessorName?: string;
    resultId: number;
    submissionId: number;
    participationId: number;
    exerciseId: number;
    exerciseTitle: string;
    exerciseType: ExerciseType;
}
