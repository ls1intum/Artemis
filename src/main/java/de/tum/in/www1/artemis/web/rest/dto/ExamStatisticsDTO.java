package de.tum.in.www1.artemis.web.rest.dto;

/**
 * This is a dto for providing statistics for the exam instructor dashboard
 */
public class ExamStatisticsDTO {

    private Long numberOfGeneratedStudentExamsTransient;

    private Long numberOfTestRunsTransient;

    private Long numberOfStudentsExamStarted;

    private Long numberOfStudentsExamSubmitted;

    private Long numberOfTotalExamAssessmentsOpen;

    private Long numberOfTotalExamAssessmentsDone;

    private Long numberOfAllComplaintsOpen;

    private Long numberOfAllComplaintsDone;

    private boolean allExamExercisesAllStudentsPrepared;

    public Long getNumberOfGeneratedStudentExamsTransient() {
        return numberOfGeneratedStudentExamsTransient;
    }

    public void setNumberOfGeneratedStudentExamsTransient(Long numberOfGeneratedStudentExamsTransient) {
        this.numberOfGeneratedStudentExamsTransient = numberOfGeneratedStudentExamsTransient;
    }

    public Long getNumberOfTestRunsTransient() {
        return numberOfTestRunsTransient;
    }

    public void setNumberOfTestRunsTransient(Long numberOfTestRunsTransient) {
        this.numberOfTestRunsTransient = numberOfTestRunsTransient;
    }

    public Long getNumberOfStudentsExamStarted() {
        return numberOfStudentsExamStarted;
    }

    public void setNumberOfStudentsExamStarted(Long numberOfStudentsExamStarted) {
        this.numberOfStudentsExamStarted = numberOfStudentsExamStarted;
    }

    public Long getNumberOfStudentsExamSubmitted() {
        return numberOfStudentsExamSubmitted;
    }

    public void setNumberOfStudentsExamSubmitted(Long numberOfStudentsExamSubmitted) {
        this.numberOfStudentsExamSubmitted = numberOfStudentsExamSubmitted;
    }

    public Long getNumberOfTotalExamAssessmentsOpen() {
        return numberOfTotalExamAssessmentsOpen;
    }

    public void setNumberOfTotalExamAssessmentsOpen(Long numberOfTotalExamAssessmentsOpen) {
        this.numberOfTotalExamAssessmentsOpen = numberOfTotalExamAssessmentsOpen;
    }

    public Long getNumberOfTotalExamAssessmentsDone() {
        return numberOfTotalExamAssessmentsDone;
    }

    public void setNumberOfTotalExamAssessmentsDone(Long numberOfTotalExamAssessmentsDone) {
        this.numberOfTotalExamAssessmentsDone = numberOfTotalExamAssessmentsDone;
    }

    public Long getNumberOfAllComplaintsOpen() {
        return numberOfAllComplaintsOpen;
    }

    public void setNumberOfAllComplaintsOpen(Long numberOfAllComplaintsOpen) {
        this.numberOfAllComplaintsOpen = numberOfAllComplaintsOpen;
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
        return this.numberOfGeneratedStudentExamsTransient;
    }

    public void setNumberOfGeneratedStudentExams(Long numberOfGeneratedStudentExams) {
        this.numberOfGeneratedStudentExamsTransient = numberOfGeneratedStudentExams;
    }

    public Long getNumberOfTestRuns() {
        return this.numberOfTestRunsTransient;
    }

    public void setNumberOfTestRuns(Long numberOfTestRuns) {
        this.numberOfTestRunsTransient = numberOfTestRuns;
    }

}
