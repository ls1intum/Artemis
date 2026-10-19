import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';

export interface CourseManagementStatisticsModel {
    exerciseId: number;
    exerciseName: string;
    releaseDate?: dayjs.Dayjs;
    averageScore: number;
    exerciseType: ExerciseType;
    categories?: ExerciseCategory[];
}
