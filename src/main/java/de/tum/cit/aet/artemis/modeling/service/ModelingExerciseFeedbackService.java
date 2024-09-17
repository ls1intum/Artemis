package de.tum.cit.aet.artemis.modeling.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;

@Profile(PROFILE_CORE)
@Service
public class ModelingExerciseFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ModelingExerciseFeedbackService.class);

    private final Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService;

    private final ResultWebsocketService resultWebsocketService;

    private final SubmissionService submissionService;

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    public ModelingExerciseFeedbackService(Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService, SubmissionService submissionService,
            ResultService resultService, ResultRepository resultRepository, ResultWebsocketService resultWebsocketService, ParticipationService participationService) {
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.submissionService = submissionService;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.resultWebsocketService = resultWebsocketService;
        this.participationService = participationService;
    }

    private void checkRateLimitOrThrow(StudentParticipation participation) {

        List<Result> athenaResults = participation.getResults().stream().filter(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA).toList();

        if (athenaResults.size() >= 10) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "participation", "preconditions not met");
        }
    }

    /**
     * Handles the request for generating feedback for a modeling exercise.
     * Unlike programming exercises a tutor is not notified if Athena is not available.
     *
     * @param participation    the student participation associated with the exercise.
     * @param modelingExercise the modeling exercise object.
     * @return StudentParticipation updated modeling exercise for an AI assessment
     */
    public StudentParticipation handleNonGradedFeedbackRequest(StudentParticipation participation, ModelingExercise modelingExercise) {
        if (this.athenaFeedbackSuggestionsService.isPresent()) {
            this.checkRateLimitOrThrow(participation);
            CompletableFuture.runAsync(() -> this.generateAutomaticNonGradedFeedback(participation, modelingExercise));
        }
        return participation;
    }

    /**
     * Generates automatic non-graded feedback for a modeling exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param participation    the student participation associated with the exercise.
     * @param modelingExercise the modeling exercise object.
     */
    public void generateAutomaticNonGradedFeedback(StudentParticipation participation, ModelingExercise modelingExercise) {
        log.debug("Using athena to generate (modeling exercise) feedback request: {}", modelingExercise.getId());

        // athena takes over the control here
        Optional<Submission> submissionOptional = participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(participation.getId())
                .findLatestSubmission();

        if (submissionOptional.isEmpty()) {
            throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmission");
        }

        Submission submission = submissionOptional.get();

        Result automaticResult = createInitialResult(participation, submission);

        try {
            this.resultWebsocketService.broadcastNewResult(participation, automaticResult);

            log.debug("Submission id: {}", submission.getId());

            List<Feedback> feedbacks = getAthenaFeedback(modelingExercise, (ModelingSubmission) submission);

            double totalFeedbackScore = calculateTotalFeedbackScore(feedbacks, modelingExercise);

            automaticResult.setCompletionDate(ZonedDateTime.now());
            automaticResult.setScore(Math.max(0, Math.min(totalFeedbackScore, 100)));
            automaticResult.setSuccessful(true);

            automaticResult = this.resultRepository.save(automaticResult);
            resultService.storeFeedbackInResult(automaticResult, feedbacks, true);
            submissionService.saveNewResult(submission, automaticResult);
            this.resultWebsocketService.broadcastNewResult(participation, automaticResult);
        }
        catch (Exception e) {
            log.error("Could not generate feedback for exercise ID: {} and participation ID: {}", modelingExercise.getId(), participation.getId(), e);
            throw new InternalServerErrorException("Something went wrong... AI Feedback could not be generated");
        }
    }

    /**
     * Creates an initial Result object for the automatic feedback.
     *
     * @param participation the student participation
     * @param submission    the submission to which the result is associated
     * @return the initial Result object
     */
    private Result createInitialResult(StudentParticipation participation, Submission submission) {
        Result result = new Result();
        result.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        result.setRated(true);
        result.setScore(0.0);
        result.setSuccessful(null);
        result.setSubmission(submission);
        result.setParticipation(participation);
        return result;
    }

    /**
     * Retrieves feedback from the Athena service.
     *
     * @param modelingExercise the modeling exercise
     * @param submission       the modeling submission
     * @return a list of Feedback objects
     * @throws NetworkingException if there's a problem communicating with Athena
     */
    private List<Feedback> getAthenaFeedback(ModelingExercise modelingExercise, ModelingSubmission submission) throws NetworkingException {
        return this.athenaFeedbackSuggestionsService.orElseThrow().getModelingFeedbackSuggestions(modelingExercise, submission, false).stream()
                .filter(feedbackItem -> feedbackItem.description() != null).map(this::convertToFeedback).toList();
    }

    /**
     * Converts an Athena feedback suggestion to a Feedback object.
     *
     * @param feedbackItem the Athena feedback suggestion
     * @return the Feedback object
     */
    private Feedback convertToFeedback(ModelingFeedbackDTO feedbackItem) {
        Feedback feedback = new Feedback();
        feedback.setText(feedbackItem.title());
        feedback.setDetailText(feedbackItem.description());
        feedback.setHasLongFeedbackText(false);
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setCredits(feedbackItem.credits());
        return feedback;
    }

    /**
     * Calculates the total feedback score based on the list of feedbacks and the exercise's max points.
     *
     * @param feedbacks        the list of feedbacks
     * @param modelingExercise the modeling exercise
     * @return the total feedback score
     */
    private double calculateTotalFeedbackScore(List<Feedback> feedbacks, ModelingExercise modelingExercise) {
        double totalCredits = feedbacks.stream().mapToDouble(Feedback::getCredits).sum();
        Double maxPoints = modelingExercise.getMaxPoints();

        if (maxPoints == null || maxPoints == 0) {
            throw new IllegalArgumentException("Maximum points must be greater than zero.");
        }

        return (totalCredits / maxPoints) * 100;
    }
}
