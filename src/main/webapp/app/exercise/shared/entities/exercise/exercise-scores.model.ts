import dayjs from 'dayjs/esm';
import { ExerciseType } from './exercise.model';

/**
 * Corresponds to ExerciseScoresDTO.java on the server.
 * Shared shape for exercise statistics and their type filters.
 */
export interface ExerciseScoresDTO {
    exerciseId?: number;
    exerciseTitle?: string;
    exerciseType: ExerciseType;
    releaseDate?: dayjs.Dayjs;
    scoreOfStudent?: number;
    averageScoreAchieved?: number;
    maxScoreAchieved?: number;
}
