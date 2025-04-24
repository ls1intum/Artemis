export class ExamChecklist {
    public numberOfGeneratedStudentExams?: number; // transient

    public numberOfTestRuns?: number; // transient

    public numberOfExamsSubmitted: number;

    public numberOfExamsStarted: number;

    public numberOfTotalParticipationsForAssessment: number;

    public numberOfTotalExamAssessmentsFinishedByCorrectionRound?: number[]; // all exercises summed up

    public numberOfAllComplaints?: number;

    public numberOfAllComplaintsDone?: number;

    public allExamExercisesAllStudentsPrepared?: boolean;

    public existsUnassessedQuizzes: boolean;

    public existsUnsubmittedExercises: boolean;
}
