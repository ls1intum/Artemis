import { CourseManagementStatisticsModel } from 'app/quiz/shared/entities/course-management-statistics-model';

export interface CourseManagementStatisticsDTO {
    averageScoreOfCourse: number;
    averageScoresOfExercises: CourseManagementStatisticsModel[];
}
