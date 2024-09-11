package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.domain.enumeration.AssessmentType;
import de.tum.cit.aet.artemis.domain.enumeration.FeedbackType;
import de.tum.cit.aet.artemis.domain.participation.Participation;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.service.connectors.athena.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;
import de.tum.cit.aet.artemis.web.websocket.ResultWebsocketService;

@Profile(PROFILE_CORE)
@Service
public class TextExerciseFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseFeedbackService.class);

    public static final String NON_GRADED_FEEDBACK_SUGGESTION = "NonGradedFeedbackSuggestion:";

    private final Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService;

    private final ResultWebsocketService resultWebsocketService;

    private final SubmissionService submissionService;

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    public TextExerciseFeedbackService(Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService, SubmissionService submissionService,
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

        long countOfAthenaResults = athenaResults.size();

        if (countOfAthenaResults >= 10) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "participation", "preconditions not met");
        }
    }

    /**
     * Handles the request for generating feedback for a text exercise.
     * Unlike programming exercises a tutor is not notified if Athena is not available.
     *
     * @param exerciseId    the id of the text exercise.
     * @param participation the student participation associated with the exercise.
     * @param textExercise  the text exercise object.
     * @return StudentParticipation updated text exercise for an AI assessment
     */
    public StudentParticipation handleNonGradedFeedbackRequest(Long exerciseId, StudentParticipation participation, TextExercise textExercise) {
        if (this.athenaFeedbackSuggestionsService.isPresent()) {
            this.checkRateLimitOrThrow(participation);
            CompletableFuture.runAsync(() -> this.generateAutomaticNonGradedFeedback(participation, textExercise));
        }
        return participation;
    }

    /**
     * Generates automatic non-graded feedback for a text exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param participation the student participation associated with the exercise.
     * @param textExercise  the text exercise object.
     */
    public void generateAutomaticNonGradedFeedback(StudentParticipation participation, TextExercise textExercise) {
        log.debug("Using athena to generate (text exercise) feedback request: {}", textExercise.getId());

        // athena takes over the control here
        var submissionOptional = participationService.findTextExerciseParticipationWithLatestSubmissionAndResultElseThrow(participation.getId()).findLatestSubmission();

        if (submissionOptional.isEmpty()) {
            throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmission");
        }
        var submission = submissionOptional.get();

        Result automaticResult = new Result();
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        automaticResult.setRated(true);
        automaticResult.setScore(0.0);
        automaticResult.setSuccessful(null);
        automaticResult.setSubmission(submission);
        automaticResult.setParticipation(participation);
        try {
            this.resultWebsocketService.broadcastNewResult((Participation) participation, automaticResult);

            log.debug("Submission id: {}", submission.getId());

            var athenaResponse = this.athenaFeedbackSuggestionsService.orElseThrow().getTextFeedbackSuggestions(textExercise, (TextSubmission) submission, false);

            List<Feedback> feedbacks = athenaResponse.stream().filter(individualFeedbackItem -> individualFeedbackItem.description() != null).map(individualFeedbackItem -> {
                var feedback = new Feedback();
                feedback.setText(individualFeedbackItem.title());
                feedback.setDetailText(individualFeedbackItem.description());
                feedback.setHasLongFeedbackText(false);
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setCredits(individualFeedbackItem.credits());
                return feedback;
            }).toList();

            double totalFeedbacksScore = 0.0;
            for (Feedback feedback : feedbacks) {
                totalFeedbacksScore += feedback.getCredits();
            }
            totalFeedbacksScore = totalFeedbacksScore / textExercise.getMaxPoints() * 100;
            automaticResult.setSuccessful(true);
            automaticResult.setCompletionDate(ZonedDateTime.now());

            automaticResult.setScore(Math.clamp(totalFeedbacksScore, 0, 100));

            automaticResult = this.resultRepository.save(automaticResult);
            resultService.storeFeedbackInResult(automaticResult, feedbacks, true);
            submissionService.saveNewResult(submission, automaticResult);
            this.resultWebsocketService.broadcastNewResult((Participation) participation, automaticResult);
        }
        catch (Exception e) {
            log.error("Could not generate feedback", e);
            throw new InternalServerErrorException("Something went wrong... AI Feedback could not be generated");
        }
    }
}
