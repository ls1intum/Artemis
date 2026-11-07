/**
 * Everything that is deleted together with an exercise group of an exam. The server omits counts that are 0.
 */
export interface ExerciseGroupDeletionSummaryDTO {
    numberOfProgrammingExercises?: number;
    numberOfQuizExercises?: number;
    numberOfTextExercises?: number;
    numberOfModelingExercises?: number;
    numberOfFileUploadExercises?: number;
    numberOfStudentParticipations?: number;
    numberOfSubmissions?: number;
    numberOfResults?: number;
    numberOfAssessments?: number;
    numberOfComplaints?: number;
    numberOfPlagiarismComparisons?: number;
    numberOfBuilds?: number;
    numberOfStudentExams?: number;
}
