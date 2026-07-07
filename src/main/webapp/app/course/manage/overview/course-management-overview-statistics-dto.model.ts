import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';

/** Instantiated and/or deserialized from server data; fields are populated after construction, hence the definite-assignment (!) markers. */
export class CourseManagementOverviewStatisticsDto {
    public courseId?: number;
    public exerciseDTOS!: CourseManagementOverviewExerciseStatisticsDTO[];
    public activeStudents?: number[];
}
