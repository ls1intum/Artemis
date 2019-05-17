package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.dto.StatsTutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

abstract class AssessmentService {

    private final ComplaintResponseService complaintResponseService;

    private final ComplaintRepository complaintRepository;

    protected final ResultRepository resultRepository;

    private final ParticipationRepository participationRepository;

    private final ResultService resultService;

    public AssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, ResultRepository resultRepository,
            ParticipationRepository participationRepository, ResultService resultService) {
        this.complaintResponseService = complaintResponseService;
        this.complaintRepository = complaintRepository;
        this.resultRepository = resultRepository;
        this.participationRepository = participationRepository;
        this.resultService = resultService;
    }

    Result submitResult(Result result, Exercise exercise, Double calculatedScore) {
        Double maxScore = exercise.getMaxScore();
        result.setRatedIfNotExceeded(exercise.getDueDate(), result.getSubmission().getSubmissionDate());
        result.setCompletionDate(ZonedDateTime.now());
        double totalScore = calculateTotalScore(calculatedScore, maxScore);
        result.setScore(totalScore, maxScore);
        result.setResultString(totalScore, maxScore);
        resultRepository.save(result);
        return result;
    }

    /**
     * Handles an assessment update after a complaint. It first saves the corresponding complaint response and then updates the Result that was complaint about. Note, that it
     * updates the score and the feedback of the original Result, but NOT the assessor. The user that is responsible for the update can be found in the 'reviewer' field of the
     * complaint. The original Result gets stored in the 'resultBeforeComplaint' field of the ComplaintResponse for future lookup.
     *
     * @param originalResult   the original assessment that was complained about
     * @param assessmentUpdate the assessment update containing a ComplaintResponse and the updated Feedback list
     * @return the updated Result
     */
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
        originalResult.setNewFeedback(assessmentUpdate.getFeedbacks());
        originalResult.evaluateFeedback(exercise.getMaxScore());
        // Note: This also saves the feedback objects in the database because of the 'cascade =
        // CascadeType.ALL' option.
        return resultRepository.save(originalResult);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submission the submission for which the current assessment should be canceled
     */
    @Transactional
    public void cancelAssessmentOfSubmission(Submission submission) {
        Participation participation = participationRepository.findByIdWithEagerResults(submission.getParticipation().getId())
                .orElseThrow(() -> new BadRequestAlertException("Participation could not be found", "participation", "notfound"));
        Result result = submission.getResult();
        participation.removeResult(result);
        participationRepository.save(participation);
        resultRepository.deleteById(result.getId());
    }

    /**
     * Checks the assessment for general (without reference) feedback entries. Throws a BadRequestAlertException if there is more than one general feedback.
     *
     * @param assessment the assessment to check
     */
    void checkGeneralFeedback(List<Feedback> assessment) {
        final long generalFeedbackCount = assessment.stream().filter(feedback -> feedback.getReference() == null).count();
        if (generalFeedbackCount > 1) {
            throw new BadRequestAlertException("There cannot be more than one general Feedback per Assessment", "assessment", "moreThanOneGeneralFeedback");
        }
    }

    private double calculateTotalScore(Double calculatedScore, Double maxScore) {
        double totalScore = Math.max(0, calculatedScore);
        return (maxScore == null) ? totalScore : Math.min(totalScore, maxScore);
    }

    /**
     * Given a courseId, this method creates the tutor leaderboard collecting all the results of the course, checking who is the assessor and if there is any related complaint
     *
     * @param courseId - the course we are interested in
     * @return a NOT SORTED tutor leaderboard with name, login, number of assessments and number of complaints
     */
    public List<StatsTutorLeaderboardDTO> calculateTutorLeaderboardForCourse(Long courseId) {
        List<Result> resultsForCourse = resultRepository.findAllByParticipation_Exercise_CourseIdWithEagerAssessor(courseId);

        return createTutorLeaderboardFromResults(resultsForCourse);
    }

    /**
     * Given a exerciseId, this method creates the tutor leaderboard collecting all the results of the exercise, checking who is the assessor and if there is any related complaint
     *
     * @param exerciseId - the exercise we are interested in
     * @return a NOT SORTED tutor leaderboard with name, login, number of assessments and number of complaints
     */
    public List<StatsTutorLeaderboardDTO> calculateTutorLeaderboardForExercise(Long exerciseId) {
        List<Result> resultsForExercise = resultRepository.findAllByParticipation_Exercise_IdWithEagerAssessor(exerciseId);

        return createTutorLeaderboardFromResults(resultsForExercise);
    }

    /**
     * Given a list of results, create a leaderboard counting how many assessments and how many complaints every tutor has
     *
     * @param results - the results to iterate over
     * @return a tutor leaderboard
     */
    @NotNull
    private List<StatsTutorLeaderboardDTO> createTutorLeaderboardFromResults(List<Result> results) {
        List<StatsTutorLeaderboardDTO> tutorWithNumberAssessmentList = new ArrayList<>();

        results.forEach(result -> {
            User assessor = result.getAssessor();

            if (assessor != null && assessor.getLogin() != null) {
                Optional<StatsTutorLeaderboardDTO> existingElement = tutorWithNumberAssessmentList.stream().filter(o -> o.login.equals(assessor.getLogin())).findFirst();
                StatsTutorLeaderboardDTO element;

                if (!existingElement.isPresent()) {
                    String name = result.getAssessor().getFirstName().concat(" ").concat(result.getAssessor().getLastName());
                    element = new StatsTutorLeaderboardDTO(name, result.getAssessor().getLogin(), 0, 0);
                    tutorWithNumberAssessmentList.add(element);
                }
                else {
                    element = existingElement.get();
                }

                element.numberOfAssessments += 1;

                Optional<Boolean> hasComplaint = result.getHasComplaint();

                if (hasComplaint.isPresent() && hasComplaint.get()) {
                    element.numberOfComplaints += 1;
                }
            }
        });

        return tutorWithNumberAssessmentList;
    }
}
