import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';

export class CourseManagementOverviewDto {
    public courseId: number;
    public exerciseDetails: CourseManagementOverviewExerciseDetailsDTO[];

    constructor() {}
}
