package de.tum.cit.aet.artemis.text.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSuggestionsService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_CORE)
@Service
public class TextExerciseFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseFeedbackService.class);

    private final Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService;

    private final ResultWebsocketService resultWebsocketService;

    private final SubmissionService submissionService;

    private final ParticipationService participationService;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final TextBlockService textBlockService;

    public TextExerciseFeedbackService(Optional<AthenaFeedbackSuggestionsService> athenaFeedbackSuggestionsService, SubmissionService submissionService,
            ResultService resultService, ResultRepository resultRepository, ResultWebsocketService resultWebsocketService, ParticipationService participationService,
            TextBlockService textBlockService) {
        this.athenaFeedbackSuggestionsService = athenaFeedbackSuggestionsService;
        this.submissionService = submissionService;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.resultWebsocketService = resultWebsocketService;
        this.participationService = participationService;
        this.textBlockService = textBlockService;
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
        if (this.athenaFeedbackSuggestionsService.isPresent()) {
            this.athenaFeedbackSuggestionsService.get().checkRateLimitOrThrow(participation);

            var submissionOptional = participationService.findExerciseParticipationWithLatestSubmissionAndResultElseThrow(participation.getId()).findLatestSubmission();
            if (submissionOptional.isEmpty()) {
                throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmissionExists", true);
            }
            TextSubmission textSubmission = (TextSubmission) submissionOptional.get();

            this.athenaFeedbackSuggestionsService.orElseThrow().checkLatestSubmissionHasNoAthenaResultOrThrow(textSubmission);

            if (textSubmission.isEmpty()) {
                throw new BadRequestAlertException("Submission can not be empty for an AI feedback request", "submission", "noAthenaFeedbackOnEmptySubmission", true);
            }

            CompletableFuture.runAsync(() -> this.generateAutomaticNonGradedFeedback(textSubmission, participation, textExercise));
        }
        return participation;
    }

    /**
     * Generates automatic non-graded feedback for a text exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param textSubmission the text submission associated with the student participation.
     * @param participation  the student participation associated with the exercise.
     * @param textExercise   the text exercise object.
     */
    public void generateAutomaticNonGradedFeedback(TextSubmission textSubmission, StudentParticipation participation, TextExercise textExercise) {
        log.debug("Using athena to generate (text exercise) feedback request: {}", textExercise.getId());

        // athena takes over the control here
        Result automaticResult = new Result();
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        automaticResult.setRated(true);
        automaticResult.setScore(0.0);
        automaticResult.setSuccessful(null);
        automaticResult.setSubmission(textSubmission);
        automaticResult.setParticipation(participation);
        try {
            // This broadcast signals the client that feedback is being generated, does not save empty result
            this.resultWebsocketService.broadcastNewResult(participation, automaticResult);

            log.debug("Submission id: {}", textSubmission.getId());

            var athenaResponse = this.athenaFeedbackSuggestionsService.orElseThrow().getTextFeedbackSuggestions(textExercise, textSubmission, false);

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
            automaticResult.setCompletionDate(ZonedDateTime.now());
            automaticResult.setScore(Math.clamp(totalFeedbacksScore, 0, 100));

            // For Athena automatic results successful = true will mean that the generation was successful
            // undefined in progress and false it failed
            automaticResult.setSuccessful(true);

            automaticResult = this.resultRepository.save(automaticResult);
            resultService.storeFeedbackInResult(automaticResult, feedbacks, true);
            textBlockService.saveAll(textBlocks);
            textSubmission.setBlocks(textBlocks);
            submissionService.saveNewResult(textSubmission, automaticResult);
            // This broadcast signals the client that feedback generation succeeded, result is saved in this case only
            this.resultWebsocketService.broadcastNewResult(participation, automaticResult);
        }
        catch (Exception e) {
            log.error("Could not generate feedback", e);
            // Broadcast the failed result but don't save, note that successful = false is normally used to indicate a score < 100
            // but since we do not differentiate for athena feedback we use it to indicate a failed generation
            automaticResult.setSuccessful(false);
            automaticResult.setCompletionDate(null);
            participation.addResult(automaticResult); // for proper change detection
            // This broadcast signals the client that feedback generation failed, does not save empty result
            this.resultWebsocketService.broadcastNewResult(participation, automaticResult);
        }
    }
}
