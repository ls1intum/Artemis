/**
 * Everything that is deleted together with an exercise. The server omits counts that are 0.
 */
export interface ExerciseDeletionSummaryDTO {
    numberOfStudentParticipations?: number;
    numberOfTeams?: number;
    numberOfSubmissions?: number;
    numberOfResults?: number;
    numberOfAssessments?: number;
    numberOfComplaints?: number;
    numberOfPlagiarismComparisons?: number;
    numberOfExampleSubmissions?: number;
    numberOfBuilds?: number;
    numberOfCommunicationPosts?: number;
    numberOfAnswerPosts?: number;
    numberOfCompetencyLinks?: number;
    numberOfLectureUnitLinks?: number;
    numberOfStudentExams?: number;
}
