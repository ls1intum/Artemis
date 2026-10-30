package de.tum.cit.aet.artemis.text.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MODULE_FEATURE_ATHENA;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AthenaFeedbackRequestRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Conditional(TextEnabled.class)
@Lazy
@Service
public class TextExerciseFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseFeedbackService.class);

    private final Optional<AthenaFeedbackApi> athenaFeedbackApi;

    private final ResultWebsocketService resultWebsocketService;

    private final ParticipationService participationService;

    private final AthenaFeedbackRequestRepository athenaFeedbackRequestRepository;

    private final UserRepository userRepository;

    public TextExerciseFeedbackService(Optional<AthenaFeedbackApi> athenaFeedbackApi, AthenaFeedbackRequestRepository athenaFeedbackRequestRepository,
            ResultWebsocketService resultWebsocketService, ParticipationService participationService, UserRepository userRepository) {
        this.athenaFeedbackApi = athenaFeedbackApi;
        this.athenaFeedbackRequestRepository = athenaFeedbackRequestRepository;
        this.resultWebsocketService = resultWebsocketService;
        this.participationService = participationService;
        this.userRepository = userRepository;
    }

    /**
     * Asynchronously triggers non-graded Athena feedback for a text submission in a test exam.
     * <p>
     * The submission is the one the caller validated, not a freshly read one: test runs of the same exercise share a
     * participation, so re-reading it here could pick up an answer another run saved in the meantime.
     *
     * @param participation the student participation associated with the exercise
     * @param textExercise  the text exercise
     * @param submission    the submission that was validated as eligible for feedback
     */
    public void generateAutomaticFeedbackForTestExamAsync(StudentParticipation participation, TextExercise textExercise, Submission submission) {
        if (this.athenaFeedbackApi.isEmpty()) {
            return;
        }
        if (!(submission instanceof TextSubmission textSubmission)) {
            log.warn("Skipping Athena feedback for participation {} on text exercise {}: submission {} is not a TextSubmission", participation.getId(), textExercise.getId(),
                    submission.getId());
            return;
        }
        if (textSubmission.isEmpty()) {
            return;
        }
        AthenaFeedbackApi api = athenaFeedbackApi.orElseThrow(() -> new ApiProfileNotPresentException(AthenaFeedbackApi.class, MODULE_FEATURE_ATHENA));
        if (api.submissionHasAthenaResult(textSubmission)) {
            return;
        }
        // Capture the user on the calling (request) thread — SecurityContext is not propagated into the async executor.
        User requestingUser = userRepository.getUser();
        try {
            scheduleFeedback(textSubmission, participation, textExercise, requestingUser);
        }
        catch (BadRequestAlertException duplicateOrLimit) {
            log.debug("Skipping duplicate or rate-limited test-exam feedback for submission {}", submission.getId());
        }
    }

    /**
     * Handles the request for generating feedback for a text exercise.
     * Unlike programming exercises a tutor is not notified if Athena is not available.
     *
     * @param participation the student participation associated with the exercise.
     * @param textExercise  the text exercise object.
     * @return StudentParticipation updated text exercise for an AI assessment
     */
    public StudentParticipation handleNonGradedFeedbackRequest(StudentParticipation participation, TextExercise textExercise) {
        if (this.athenaFeedbackApi.isPresent()) {
            AthenaFeedbackApi api = athenaFeedbackApi.get();
            api.checkRateLimitOrThrow(participation);

            var submissionOptional = participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(participation.getId()).findLatestSubmission();
            if (submissionOptional.isEmpty()) {
                throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmissionExists", true);
            }
            TextSubmission textSubmission = (TextSubmission) submissionOptional.get();

            api.checkSubmissionHasNoAthenaResultOrThrow(textSubmission);

            if (textSubmission.isEmpty()) {
                throw new BadRequestAlertException("Submission can not be empty for an AI feedback request", "submission", "noAthenaFeedbackOnEmptySubmission", true);
            }

            User requestingUser = userRepository.getUser();
            scheduleFeedback(textSubmission, participation, textExercise, requestingUser);
        }
        return participation;
    }

    private void scheduleFeedback(TextSubmission submission, StudentParticipation participation, TextExercise exercise, User requestingUser) {
        Result pending = athenaFeedbackRequestRepository.reserveAthenaFeedbackRequest(submission);
        submission.addResult(pending);
        participation.getSubmissions().stream().filter(existing -> existing.getId().equals(submission.getId())).forEach(existing -> existing.addResult(pending));
        notifyResult(participation, pending);
        CompletableFuture.runAsync(() -> generateAutomaticNonGradedFeedback(submission, participation, exercise, requestingUser, pending));
    }

    private void notifyResult(StudentParticipation participation, Result result) {
        try {
            resultWebsocketService.broadcastNewResult(participation, result);
        }
        catch (RuntimeException notificationFailure) {
            // The durable state is authoritative. A disconnected websocket must not prevent work or invalidate success.
            log.warn("Could not broadcast Athena feedback state for result {}", result.getId(), notificationFailure);
        }
    }

    /**
     * Generates automatic non-graded feedback for a text exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param textSubmission  the text submission associated with the student participation.
     * @param participation   the student participation associated with the exercise.
     * @param textExercise    the text exercise object.
     * @param requestingUser  the user that requested the feedback generation
     * @param automaticResult the durable reservation owned by this worker
     */
    private void generateAutomaticNonGradedFeedback(TextSubmission textSubmission, StudentParticipation participation, TextExercise textExercise, User requestingUser,
            Result automaticResult) {
        log.debug("Using athena to generate (text exercise) feedback request: {}", textExercise.getId());

        try {
            log.debug("Submission id: {}", textSubmission.getId());

            AthenaFeedbackApi api = athenaFeedbackApi.orElseThrow(() -> new ApiProfileNotPresentException(AthenaFeedbackApi.class, MODULE_FEATURE_ATHENA));
            var athenaResponse = api.getTextFeedbackSuggestions(textExercise, textSubmission, false, requestingUser);

            Set<TextBlock> textBlocks = new HashSet<>();
            List<Feedback> feedbacks = new ArrayList<>();

            athenaResponse.stream().filter(individualFeedbackItem -> individualFeedbackItem.description() != null).forEach(individualFeedbackItem -> {
                var textBlock = new TextBlock();
                var feedback = new Feedback();

                feedback.setText(individualFeedbackItem.title());
                feedback.setDetailText(individualFeedbackItem.description());
                feedback.setHasLongFeedbackText(false);
                feedback.setType(FeedbackType.AUTOMATIC);
                feedback.setCredits(individualFeedbackItem.credits());

                if (textSubmission.getText() != null && individualFeedbackItem.indexStart() != null && individualFeedbackItem.indexEnd() != null) {
                    textBlock.setStartIndex(individualFeedbackItem.indexStart());
                    textBlock.setEndIndex(individualFeedbackItem.indexEnd());
                    textBlock.setSubmission(textSubmission);
                    textBlock.setTextFromSubmission();
                    textBlock.automatic();
                    textBlock.computeId();
                    feedback.setReference(textBlock.getId());
                    textBlock.setFeedback(feedback);
                    log.debug(textBlock.toString());

                    textBlocks.add(textBlock);
                }
                feedbacks.add(feedback);
            });

            double totalFeedbacksScore = 0.0;
            for (Feedback feedback : feedbacks) {
                totalFeedbacksScore += feedback.getCredits();
            }
            totalFeedbacksScore = totalFeedbacksScore / textExercise.getMaxPoints() * 100;
            double score = Math.clamp(totalFeedbacksScore, 0, 100);
            if (athenaFeedbackRequestRepository.completeAthenaFeedbackRequest(automaticResult, score, feedbacks, textBlocks)) {
                automaticResult.setCompletionDate(ZonedDateTime.now());
                automaticResult.setScore(score);
                automaticResult.setRated(true);
                automaticResult.setSuccessful(true);
                automaticResult.setFeedbacks(feedbacks);
                textSubmission.setBlocks(textBlocks);
                notifyResult(participation, automaticResult);
            }
        }
        catch (Exception e) {
            log.error("Could not generate feedback", e);
            if (athenaFeedbackRequestRepository.failAthenaFeedbackRequest(automaticResult)) {
                automaticResult.setSuccessful(false);
                automaticResult.setCompletionDate(ZonedDateTime.now());
                notifyResult(participation, automaticResult);
            }
        }
    }
}
