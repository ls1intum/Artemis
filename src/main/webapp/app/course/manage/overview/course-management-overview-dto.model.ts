import { Exercise } from 'app/entities/exercise.model';

export class CourseManagementOverviewDto {
    public courseId: number;
    public exerciseDetails: Exercise[];

    constructor() {}
}
