import dayjs from 'dayjs';

export class CourseManagementStatisticsModel {
    public exerciseId: number;
    public exerciseName: string;
    public releaseDate?: dayjs.Dayjs;
    public averageScore: number;

    constructor() {}
}
