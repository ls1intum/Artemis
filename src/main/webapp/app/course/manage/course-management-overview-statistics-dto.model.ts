import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/entities/course-management-overview-exercise-statistics-dto.model';

export class CourseManagementOverviewStatisticsDto {
    public courseId: number;
    public exerciseDTOS: CourseManagementOverviewExerciseStatisticsDTO[];
    public activeStudents: number[];

    constructor() {}
}
