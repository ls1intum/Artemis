/**
 * Everything that is deleted together with an exam. The server sends every count, including those that are 0.
 * The fields are optional so that a missing count is read as 0.
 */
export interface ExamDeletionSummaryDTO {
    numberOfBuilds?: number;
    numberOfCommunicationPosts?: number;
    numberOfAnswerPosts?: number;
    numberRegisteredStudents?: number;
    numberNotStartedExams?: number;
    numberStartedExams?: number;
    numberSubmittedExams?: number;
    numberOfExerciseGroups?: number;
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
    numberOfTestRuns?: number;
    numberOfExamSessions?: number;
    numberOfExamLiveEvents?: number;
}
