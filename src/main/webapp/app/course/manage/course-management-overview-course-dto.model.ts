import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/entities/course-management-overview-exercise-statistics-dto.model';

export class CourseManagementOverviewCourseDto {
    public courseId: number;
    public exerciseDetails: CourseManagementOverviewExerciseStatisticsDTO[];
    public exerciseDTOS: CourseManagementOverviewExerciseStatisticsDTO[];

    constructor() {}
}
