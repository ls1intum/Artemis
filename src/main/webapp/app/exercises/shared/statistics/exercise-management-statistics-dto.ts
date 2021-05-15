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

    constructor() {}
}
