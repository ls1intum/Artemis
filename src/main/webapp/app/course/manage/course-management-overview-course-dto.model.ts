import { Exercise } from 'app/entities/exercise.model';
import { CourseExerciseStatisticsDTO } from 'app/exercises/shared/exercise/exercise-statistics-dto.model';

export class CourseManagementOverviewCourseDto {
    public courseId: number;
    public activeStudents: number[];
    public exercises: Exercise[];
    public exerciseDTOS: CourseExerciseStatisticsDTO[];

    constructor() {}
}
