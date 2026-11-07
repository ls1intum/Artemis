/**
 * Everything that is deleted together with an exam. The server omits counts that are 0.
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
