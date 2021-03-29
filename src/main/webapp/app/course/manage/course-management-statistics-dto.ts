import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';

export class CourseManagementStatisticsDTO {
    averageScoreOfCourse: number;
    averageScoresOfExercises: CourseManagementStatisticsModel[];

    constructor() {}
}
