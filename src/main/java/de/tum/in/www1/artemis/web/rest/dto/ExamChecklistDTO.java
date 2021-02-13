package de.tum.in.www1.artemis.web.rest.dto;

/**
 * This is a dto for providing statistics for the exam instructor dashboard
 */
public class ExamChecklistDTO {

    private Long numberOfGeneratedStudentExams;

    private Long numberOfTestRuns;

    private Long[] numberOfTotalExamAssessmentsFinished;

    private Long numberOfTotalExamParticipations;

    private Long numberOfAllComplaints;

    private Long numberOfAllComplaintsDone;

    private boolean allExamExercisesAllStudentsPrepared;

    public Long getNumberOfTotalExamParticipations() {
        return numberOfTotalExamParticipations;
    }

    public void setNumberOfTotalExamParticipations(Long numberOfTotalExamParticipations) {
        this.numberOfTotalExamParticipations = numberOfTotalExamParticipations;
    }

    public Long[] getNumberOfTotalExamAssessmentsFinished() {
        return numberOfTotalExamAssessmentsFinished;
    }

    public void setNumberOfTotalExamAssessmentsFinished(Long[] numberOfTotalExamAssessmentsFinished) {
        this.numberOfTotalExamAssessmentsFinished = numberOfTotalExamAssessmentsFinished;
    }

    public Long getNumberOfAllComplaints() {
        return numberOfAllComplaints;
    }

    public void setNumberOfAllComplaints(Long numberOfAllComplaints) {
        this.numberOfAllComplaints = numberOfAllComplaints;
    }

    public Long getNumberOfAllComplaintsDone() {
        return numberOfAllComplaintsDone;
    }

    public void setNumberOfAllComplaintsDone(Long numberOfAllComplaintsDone) {
        this.numberOfAllComplaintsDone = numberOfAllComplaintsDone;
    }

    public boolean isAllExamExercisesAllStudentsPrepared() {
        return allExamExercisesAllStudentsPrepared;
    }

    public boolean getAllExamExercisesAllStudentsPrepared() {
        return this.allExamExercisesAllStudentsPrepared;
    }

    public void setAllExamExercisesAllStudentsPrepared(boolean allExamExercisesAllStudentsPrepared) {
        this.allExamExercisesAllStudentsPrepared = allExamExercisesAllStudentsPrepared;
    }

    public Long getNumberOfGeneratedStudentExams() {
        return this.numberOfGeneratedStudentExams;
    }

    public void setNumberOfGeneratedStudentExams(Long numberOfGeneratedStudentExams) {
        this.numberOfGeneratedStudentExams = numberOfGeneratedStudentExams;
    }

    public Long getNumberOfTestRuns() {
        return this.numberOfTestRuns;
    }

    public void setNumberOfTestRuns(Long numberOfTestRuns) {
        this.numberOfTestRuns = numberOfTestRuns;
    }

}
