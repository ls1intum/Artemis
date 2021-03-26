export class CourseManagementStatisticsDTO {
    averagePointsOfCourse: number;
    maxPointsOfCourse: number;
    exerciseNameToAveragePointsMap: Map<string, number>;
    exerciseNameToMaxPointsMap: Map<string, number>;

    constructor() {}
}
