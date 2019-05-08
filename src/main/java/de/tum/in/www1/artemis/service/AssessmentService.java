package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.AssessmentUpdate;
import de.tum.in.www1.artemis.domain.Complaint;
import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

abstract class AssessmentService {

    private final ComplaintResponseService complaintResponseService;

    private final ComplaintRepository complaintRepository;

    protected final ResultRepository resultRepository;

    private final ParticipationRepository participationRepository;

    private final ObjectMapper objectMapper;

    public AssessmentService(ComplaintResponseService complaintResponseService, ComplaintRepository complaintRepository, ResultRepository resultRepository,
            ParticipationRepository participationRepository, ObjectMapper objectMapper) {
        this.complaintResponseService = complaintResponseService;
        this.complaintRepository = complaintRepository;
        this.resultRepository = resultRepository;
        this.participationRepository = participationRepository;
        this.objectMapper = objectMapper;
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
            complaint.setResultBeforeComplaint(getOriginalResultAsString(originalResult));
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
     * Creates a copy of the given original result with all properties except for the participation and submission and converts it to a JSON string. This method is used for storing
     * the original result of a submission before updating the result due to a complaint.
     *
     * @param originalResult the original result that was complained about
     * @return the reduced result as a JSON string
     * @throws JsonProcessingException when the conversion to JSON string fails
     */
    private String getOriginalResultAsString(Result originalResult) throws JsonProcessingException {
        Result resultCopy = new Result();
        resultCopy.setId(originalResult.getId());
        resultCopy.setResultString(originalResult.getResultString());
        resultCopy.setCompletionDate(originalResult.getCompletionDate());
        resultCopy.setSuccessful(originalResult.isSuccessful());
        resultCopy.setScore(originalResult.getScore());
        resultCopy.setRated(originalResult.isRated());
        resultCopy.hasFeedback(originalResult.getHasFeedback());
        resultCopy.setFeedbacks(originalResult.getFeedbacks());
        resultCopy.setAssessor(originalResult.getAssessor());
        resultCopy.setAssessmentType(originalResult.getAssessmentType());
        resultCopy.setHasComplaint(originalResult.getHasComplaint());
        return objectMapper.writeValueAsString(resultCopy);
    }

    private double calculateTotalScore(Double calculatedScore, Double maxScore) {
        double totalScore = Math.max(0, calculatedScore);
        return (maxScore == null) ? totalScore : Math.min(totalScore, maxScore);
    }
}
