import { CourseManagementStatisticsModel } from 'app/quiz/shared/entities/course-management-statistics-model';

export class CourseManagementStatisticsDTO {
    averageScoreOfCourse: number;
    averageScoresOfExercises: CourseManagementStatisticsModel[];
}
