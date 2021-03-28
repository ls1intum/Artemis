export class CourseManagementStatisticsDTO {
    averagePointsOfCourse: number;
    maxPointsOfCourse: number;
    averageRatingInCourse: number;
    exerciseNameToAveragePointsMap: Map<string, number>;
    exerciseNameToMaxPointsMap: Map<string, number>;
    tutorToAverageRatingMap: Map<string, number>;

    constructor() {}
}
