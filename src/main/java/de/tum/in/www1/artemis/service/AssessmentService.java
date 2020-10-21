package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
public class AssessmentService {

    private final ComplaintResponseService complaintResponseService;

    private final ComplaintRepository complaintRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    protected final ResultService resultService;

    private final ExamService examService;

    private final SubmissionRepository submissionRepository;

    protected final GradingCriterionService gradingCriterionService;

    public AssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, FeedbackRepository feedbackRepository,
            ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            SubmissionRepository submissionRepository, ExamService examService, GradingCriterionService gradingCriterionService) {
        this.complaintResponseService = complaintResponseService;
        this.complaintRepository = complaintRepository;
        this.feedbackRepository = feedbackRepository;
        this.resultRepository = resultRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultService = resultService;
        this.submissionRepository = submissionRepository;
        this.examService = examService;
        this.gradingCriterionService = gradingCriterionService;
    }

    Result submitResult(Result result, Exercise exercise, Double calculatedScore) {
        double maxScore = exercise.getMaxScore();
        double bonusPoints = Optional.ofNullable(exercise.getBonusPoints()).orElse(0.0);

        // Exam results and manual results of programming exercises are always to rated
        if (exercise.hasExerciseGroup() || exercise instanceof ProgrammingExercise) {
            result.setRated(true);
        }
        else {
            result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        }

        result.setCompletionDate(ZonedDateTime.now());
        // Take bonus points into account to achieve a result score > 100%
        double totalScore = calculateTotalScore(calculatedScore, maxScore + bonusPoints);
        // Set score and resultString according to maxScore, to establish results with score > 100%
        result.setScore(totalScore, maxScore);
        result.setResultString(totalScore, maxScore);
        return resultRepository.save(result);
    }

    /**
     * Handles an assessment update after a complaint. It first saves the corresponding complaint response and then updates the Result that was complaint about. Note, that it
     * updates the score and the feedback of the original Result, but NOT the assessor. The user that is responsible for the update can be found in the 'reviewer' field of the
     * complaint. The original Result gets stored in the 'resultBeforeComplaint' field of the ComplaintResponse for future lookup.
     *
     * @param originalResult   the original assessment that was complained about
     * @param exercise         the exercise to which the result belongs
     * @param assessmentUpdate the assessment update containing a ComplaintResponse and the updated Feedback list
     * @return the updated Result
     */
    // NOTE: transactional makes sense here because we change multiple objects in the database and the changes might be invalid in case, one save operation fails
    @Transactional
    public Result updateAssessmentAfterComplaint(Result originalResult, Exercise exercise, AssessmentUpdate assessmentUpdate) {
        if (assessmentUpdate.getFeedbacks() == null || assessmentUpdate.getComplaintResponse() == null) {
            throw new BadRequestAlertException("Feedbacks and complaint response must not be null.", "AssessmentUpdate", "notnull");
        }
        // Save the complaint response
        ComplaintResponse complaintResponse = complaintResponseService.createComplaintResponse(assessmentUpdate.getComplaintResponse());

        try {
            // Store the original result with the complaint
            Complaint complaint = complaintResponse.getComplaint();
            complaint.setResultBeforeComplaint(resultService.getOriginalResultAsString(originalResult));
            complaintRepository.save(complaint);
        }
        catch (JsonProcessingException exception) {
            throw new InternalServerErrorException("Failed to store original result");
        }

        // Update the result that was complained about with the new feedback
        originalResult.updateAllFeedbackItems(assessmentUpdate.getFeedbacks());
        Double calculatedScore = calculateTotalScore(originalResult.getFeedbacks());
        return submitResult(originalResult, exercise, calculatedScore);
    }

    /**
     * Checks if the user can create or override a submitted result.
     * If the result does not yet exist or is not yet submitted, this tutors can create or override results.
     * Overriding is only possible if the same tutor overrides before the assessment due date.
     * Instructors can always create and override results also after the assessment due date.
     *
     * @param existingResult the existing result in case the result is updated (submitted or overridden)
     * @param exercise the exercise to which the submission and result belong and which potentially includes an assessment due date
     * @param user the user who initiates a request
     * @param isAtLeastInstructor whether the given user is an instructor for the given exercise
     * @param participation the participation to which the submission and result belongs to
     * @return true of the the given user can override a potentially existing result
     */
    public boolean isAllowedToCreateOrOverrideResult(Result existingResult, Exercise exercise, StudentParticipation participation, User user, boolean isAtLeastInstructor) {

        final boolean isExamMode = exercise.hasExerciseGroup();
        ZonedDateTime assessmentDueDate;

        // For exam exercises, tutors cannot override submissions when the publish result date is in the past (assessmentDueDate)
        if (isExamMode) {
            assessmentDueDate = exercise.getExerciseGroup().getExam().getPublishResultsDate();
        }
        else {
            assessmentDueDate = exercise.getAssessmentDueDate();
        }

        final boolean isAllowedToBeAssessor = isAllowedToBeAssessorOfResult(existingResult, exercise, participation, user);
        // TODO make sure that tutors cannot assess the first assessment after the assessmentDueDate/publish result date (post). This is currently just used in the put request.
        // Check if no result is available (first assessment)
        if (existingResult == null) {
            // Tutors can assess exam exercises only after the last student has finished the exam and before the publish result date
            if (isExamMode && !isAtLeastInstructor) {
                final Exam exam = exercise.getExerciseGroup().getExam();
                ZonedDateTime latestExamDueDate = examService.getLatestIndiviudalExamEndDate(exam.getId());
                if (latestExamDueDate.isAfter(ZonedDateTime.now()) || (exam.getPublishResultsDate() != null && exam.getPublishResultsDate().isBefore(ZonedDateTime.now()))) {
                    return false;
                }
            }
            return isAllowedToBeAssessor || isAtLeastInstructor;
        }

        // If the result exists, but was not yet submitted (i.e. completionDate not set), the tutor and the instructor can override, independent of the assessment due date
        if (existingResult.getCompletionDate() == null) {
            return isAllowedToBeAssessor || isAtLeastInstructor;
        }

        // If the result was already submitted, the tutor can only override before a potentially existing assessment due date
        final boolean isBeforeAssessmentDueDate = assessmentDueDate != null ? ZonedDateTime.now().isBefore(assessmentDueDate) : true;
        return (isAllowedToBeAssessor && isBeforeAssessmentDueDate) || isAtLeastInstructor;
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submission the submission for which the current assessment should be canceled
     */
    @Transactional // NOTE: As we use delete methods with underscores, we need a transactional context here!
    public void cancelAssessmentOfSubmission(Submission submission) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithEagerResults(submission.getParticipation().getId())
                .orElseThrow(() -> new BadRequestAlertException("Participation could not be found", "participation", "notfound"));
        Result result = submission.getResult();

        // For programming exercise the first result is always automatic and must not be deleted
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            // Get manual result, as it has a higher id than the automatic result
            List<Result> results = participation.getResults().stream().sorted(Comparator.comparing(Result::getId).reversed()).collect(Collectors.toList());
            result = results.get(0);

            if (result.getAssessmentType().equals(AssessmentType.AUTOMATIC)) {
                return;
            }
        }

        participation.removeResult(result);
        feedbackRepository.deleteByResult_Id(result.getId());
        resultRepository.deleteById(result.getId());
    }

    /**
     * Finds the example result for the given submission ID. The submission has to be an example submission
     *
     * @param submissionId The ID of the submission for which the result should be fetched
     * @return The example result, which is linked to the submission
     */
    public Submission getSubmissionOfExampleSubmissionWithResult(long submissionId) {
        return submissionRepository.findExampleSubmissionByIdWithEagerResult(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Example Submission with id \"" + submissionId + "\" does not exist"));
    }

    /**
     * Returns whether a user is allowed to be the assessor of an existing result
     * @param result Result for which to check if the user can be the assessor
     * @param exercise Exercise to which the result belongs to
     * @param user User for whom to check if they can be the assessor of the given result
     * @return true if the user is allowed to be the assessor, false otherwise
     */
    private boolean isAllowedToBeAssessorOfResult(Result result, Exercise exercise, StudentParticipation participation, User user) {
        if (exercise.isTeamMode()) {
            // for team exercises only the team tutor is allowed to be the assessor
            return participation.getTeam().orElseThrow().isOwner(user);
        }
        else if (result != null) {
            // for individual exercises a tutor can be the assessor if they already are the assessor or if there is no assessor yet
            return result.getAssessor() == null || user.equals(result.getAssessor());
        }
        else {
            return true;
        }
    }

    public double calculateTotalScore(Double calculatedScore, Double maxScore) {
        double totalScore = Math.max(0, calculatedScore);
        return (maxScore == null) ? totalScore : Math.min(totalScore, maxScore);
    }

    /**
     * Helper function to calculate the total score of a feedback list. It loops through all assessed model elements and sums the credits up.
     * The score of an assessment model is not summed up only in the case the usageCount limit is exceeded
     * meaning the structured grading instruction was applied on the assessment model more often than allowed
     *
     * @param assessments the List of Feedback
     * @return the total score
     */
    public Double calculateTotalScore(List<Feedback> assessments) {
        double totalScore = 0.0;
        var gradingInstructions = new HashMap<Long, Integer>(); // { instructionId: noOfEncounters }

        for (Feedback feedback : assessments) {
            if (feedback.getGradingInstruction() != null) {
                totalScore = gradingCriterionService.computeTotalScore(feedback, totalScore, gradingInstructions);
            }
            else {
                // in case no structured grading instruction was applied on the assessment model we just sum the feedback credit
                totalScore += feedback.getCredits();
            }
        }
        return totalScore;
    }
}
