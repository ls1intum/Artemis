export class ExerciseManagementStatisticsDto {
    // average score
    averageScoreOfExercise: number;
    maxPointsOfExercise: number;
    scoreDistribution: number[];
    numberOfExerciseScores: number;

    // participation rate
    numberOfParticipations: number;
    numberOfStudentsInCourse: number;

    // questions
    numberOfQuestions: number;
    numberOfAnsweredQuestions: number;

    // helper
    absoluteAveragePoints?: number;
    participationsInPercent?: number;
    questionsAnsweredInPercent?: number;
}
