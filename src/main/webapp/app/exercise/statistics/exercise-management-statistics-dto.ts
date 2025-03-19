export class ExerciseManagementStatisticsDto {
    // average score
    averageScoreOfExercise: number;
    maxPointsOfExercise: number;
    scoreDistribution: number[];
    numberOfExerciseScores: number;

    // participation rate
    numberOfParticipations: number;
    numberOfStudentsOrTeamsInCourse: number;

    // posts
    numberOfPosts: number;
    numberOfResolvedPosts: number;

    // helper
    absoluteAveragePoints?: number;
    participationsInPercent?: number;
    resolvedPostsInPercent?: number;
}
