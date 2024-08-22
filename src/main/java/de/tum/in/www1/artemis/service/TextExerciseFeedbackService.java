package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaFeedbackSuggestionsService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.ResultWebsocketService;

@Profile(PROFILE_CORE)
@Service
public class TextExerciseFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseFeedbackService.class);

    public static final String NON_GRADED_FEEDBACK_SUGGESTION = "NonGradedFeedbackSuggestion:";

    private final Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService;

    private final GroupNotificationService groupNotificationService;

    private final ResultWebsocketService resultWebsocketService;

    private final SubmissionService submissionService;

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final TextSubmissionService textSubmissionService;

    public TextExerciseFeedbackService(GroupNotificationService groupNotificationService, Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService,
            SubmissionService submissionService, ResultService resultService, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository,
            ResultWebsocketService resultWebsocketService, ParticipationService participationService, TextSubmissionService textSubmissionService) {
        this.groupNotificationService = groupNotificationService;
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.submissionService = submissionService;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.resultWebsocketService = resultWebsocketService;
        this.participationService = participationService;
        this.textSubmissionService = textSubmissionService;
    }

    private void checkRateLimitOrThrow(StudentParticipation participation) {

        List<Result> athenaResults = participation.getResults().stream().filter(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA).toList();

        long countOfAthenaResultsInProcessOrSuccessful = athenaResults.stream().filter(result -> result.isSuccessful() == null || result.isSuccessful() == Boolean.TRUE).count();

        long countOfSuccessfulRequests = athenaResults.stream().filter(result -> result.isSuccessful() == Boolean.TRUE).count();

        // TODO For testing purposes, will be configurable in the future
        if (countOfAthenaResultsInProcessOrSuccessful >= 10) {
            throw new BadRequestAlertException("Cannot send additional AI feedback requests now. Try again later!", "participation", "preconditions not met");
        }
        if (countOfSuccessfulRequests >= 10) {
            throw new BadRequestAlertException("Maximum number of AI feedback requests reached.", "participation", "preconditions not met");
        }
    }

    /**
     * Handles the request for generating feedback for a text exercise.
     * This method decides whether to generate feedback automatically using Athena,
     * or notify a tutor to manually process the feedback.
     *
     * @param exerciseId    the id of the programming exercise.
     * @param participation the student participation associated with the exercise.
     * @param textExercise  the text exercise object.
     * @return StudentParticipation updated text exercise for an AI assessment
     */
    public StudentParticipation handleNonGradedFeedbackRequest(Long exerciseId, StudentParticipation participation, TextExercise textExercise) {
        if (this.athenaFeedbackSuggestionsService.isPresent()) {
            this.checkRateLimitOrThrow(participation);
            CompletableFuture.runAsync(() -> this.generateAutomaticNonGradedFeedback(participation, textExercise));
            return participation;
        }
        else {
            log.debug("tutor is responsible to process feedback request: {}", exerciseId);
            groupNotificationService.notifyTutorGroupAboutNewFeedbackRequest(textExercise);
            return participation;
        }
    }

    /**
     * Generates automatic non-graded feedback for a programming exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param participation the student participation associated with the exercise.
     * @param textExercise  the text exercise object.
     */
    public void generateAutomaticNonGradedFeedback(StudentParticipation participation, TextExercise textExercise) {
        log.debug("Using athena to generate (text exercise) feedback request: {}", textExercise.getId());

        // athena takes over the control here
        var submissionOptional = participationService.findTextExerciseParticipationWithLatestSubmissionAndResult(participation.getId()).findLatestSubmission();

        if (submissionOptional.isEmpty()) {
            throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmission");
        }
        var submission = submissionOptional.get();

        var automaticResult = this.submissionService.saveNewEmptyResult(submission);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        automaticResult.setRated(false);
        automaticResult.setScore(0.0);
        automaticResult.setSuccessful(null);
        automaticResult.setCompletionDate(ZonedDateTime.now().plusSeconds(15));
        automaticResult = this.resultRepository.save(automaticResult);

        // This will create a new submission without results, this is important so that tutor assessment works as it used to.
        textSubmissionService.handleTextSubmission((TextSubmission) submission, textExercise, participation.getStudent().get());

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

            automaticResult.setSuccessful(true);
            automaticResult.setCompletionDate(ZonedDateTime.now());
            automaticResult.setScore(totalFeedbacksScore / textExercise.getMaxPoints() * 100);
            this.resultService.storeFeedbackInResult(automaticResult, feedbacks, true);
            this.resultWebsocketService.broadcastNewResult((Participation) participation, automaticResult);

        }
        catch (Exception e) {
            log.error("Could not generate feedback", e);
            automaticResult.setSuccessful(false);
            automaticResult.setCompletionDate(ZonedDateTime.now());
            this.resultRepository.save(automaticResult);
            this.resultWebsocketService.broadcastNewResult((Participation) participation, automaticResult);

        }
    }
}
