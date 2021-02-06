import { CourseManagementOverviewExerciseDetailsDTO } from 'app/entities/course-management-overview-exercise-details-dto.model';

export class CourseManagementOverviewCourseDetailDto {
    public courseId: number;
    public exerciseDetails: CourseManagementOverviewExerciseDetailsDTO[];

    constructor() {}
}
