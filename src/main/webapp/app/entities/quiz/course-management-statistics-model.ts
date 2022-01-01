import dayjs from 'dayjs';
import { ExerciseType } from 'app/entities/exercise.model';

export class CourseManagementStatisticsModel {
    public exerciseId: number;
    public exerciseName: string;
    public releaseDate?: dayjs.Dayjs;
    public averageScore: number;
    public exerciseType: ExerciseType;

    constructor() {}
}
