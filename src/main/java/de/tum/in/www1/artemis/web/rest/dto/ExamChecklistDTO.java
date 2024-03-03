package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Submission;

/**
 * This is a dto for providing statistics for the exam instructor dashboard
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamChecklistDTO {

    private Long numberOfGeneratedStudentExams;

    private Long numberOfTestRuns;

    private Long[] numberOfTotalExamAssessmentsFinishedByCorrectionRound; // all exercises summed up

    private Long numberOfTotalParticipationsForAssessment; // all exercises summed up

    private Long numberOfExamsSubmitted;

    private Long numberOfExamsStarted;

    private Long numberOfAllComplaints;

    private Long numberOfAllComplaintsDone;

    private boolean allExamExercisesAllStudentsPrepared;

    private List<Submission> unfinishedAssessments;

    public List<?> getUnfinishedAssessments() {
        return unfinishedAssessments;
    }

    public void setUnfinishedAssessments(List<Submission> unfinishedAssessments) {
        this.unfinishedAssessments = unfinishedAssessments;
    }

    public Long getNumberOfTotalParticipationsForAssessment() {
        return numberOfTotalParticipationsForAssessment;
    }

    public void setNumberOfTotalParticipationsForAssessment(Long numberOfTotalParticipationsForAssessment) {
        this.numberOfTotalParticipationsForAssessment = numberOfTotalParticipationsForAssessment;
    }

    public Long getNumberOfExamsStarted() {
        return numberOfExamsStarted;
    }

    public void setNumberOfExamsStarted(Long numberOfExamsStarted) {
        this.numberOfExamsStarted = numberOfExamsStarted;
    }

    public Long getNumberOfExamsSubmitted() {
        return numberOfExamsSubmitted;
    }

    public void setNumberOfExamsSubmitted(Long numberOfExamsSubmitted) {
        this.numberOfExamsSubmitted = numberOfExamsSubmitted;
    }

    public Long[] getNumberOfTotalExamAssessmentsFinishedByCorrectionRound() {
        return numberOfTotalExamAssessmentsFinishedByCorrectionRound;
    }

    public void setNumberOfTotalExamAssessmentsFinishedByCorrectionRound(Long[] numberOfTotalExamAssessmentsFinishedByCorrectionRound) {
        this.numberOfTotalExamAssessmentsFinishedByCorrectionRound = numberOfTotalExamAssessmentsFinishedByCorrectionRound;
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
