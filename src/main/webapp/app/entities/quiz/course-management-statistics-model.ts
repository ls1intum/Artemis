import dayjs from 'dayjs/esm';
import { ExerciseType } from 'app/entities/exercise.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';

export class CourseManagementStatisticsModel {
    public exerciseId: number;
    public exerciseName: string;
    public releaseDate?: dayjs.Dayjs;
    public averageScore: number;
    public exerciseType: ExerciseType;
    public categories?: ExerciseCategory[];

    constructor() {}
}
