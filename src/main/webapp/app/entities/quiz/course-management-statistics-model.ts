import { Moment } from 'moment';

export class CourseManagementStatisticsModel {
    public exerciseId: number;
    public exerciseName: string;
    public releaseDate?: Moment;
    public averageScore: number;

    constructor() {}
}
