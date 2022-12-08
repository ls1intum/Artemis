class StudentScoresForStudentStatistics {
    absoluteScore: number;
    relativeScore: number;
    currentRelativeScore: number;
    presentationScore: number;
}

export class CourseScoresForStudentStatisticsDTO {
    maxPoints: number;
    reachablePoints: number;
    studentScores: StudentScoresForStudentStatistics;
}
