package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MODULE_FEATURE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Service class for managing code review feedback on programming exercises.
 * This service handles the processing of non-graded feedback requests for programming exercises
 * by generating automatic feedback through the Athena service.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseCodeReviewFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseCodeReviewFeedbackService.class);

    public static final String NON_GRADED_FEEDBACK_SUGGESTION = "NonGradedFeedbackSuggestion:";

    private final Optional<AthenaFeedbackApi> athenaFeedbackApi;

    private final SubmissionService submissionService;

    private final UserRepository userRepository;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingMessagingService programmingMessagingService;

    public ProgrammingExerciseCodeReviewFeedbackService(Optional<AthenaFeedbackApi> athenaFeedbackApi, SubmissionService submissionService, UserRepository userRepository,
            ResultService resultService, ResultRepository resultRepository, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingMessagingService programmingMessagingService) {
        this.athenaFeedbackApi = athenaFeedbackApi;
        this.submissionService = submissionService;
        this.userRepository = userRepository;
        this.resultService = resultService;
        this.resultRepository = resultRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Handles the request for generating feedback for a programming exercise by generating it automatically via Athena.
     * Callers must ensure Athena is enabled for the exercise's course before invoking this method.
     *
     * @param participation       the student participation associated with the exercise.
     * @param programmingExercise the programming exercise object.
     * @return the unchanged participation; the feedback is generated asynchronously.
     */
    public ProgrammingExerciseStudentParticipation handleNonGradedFeedbackRequest(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        AthenaFeedbackApi api = athenaFeedbackApi.orElseThrow(() -> new ApiProfileNotPresentException(AthenaFeedbackApi.class, MODULE_FEATURE_ATHENA));
        api.checkRateLimitOrThrow(participation);
        User requestingUser = userRepository.getUser();
        CompletableFuture.runAsync(() -> this.generateAutomaticNonGradedFeedback(participation, programmingExercise, requestingUser));
        return participation;
    }

    /**
     * Generates automatic non-graded feedback for a programming exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param participation       the student participation associated with the exercise.
     * @param programmingExercise the programming exercise object.
     * @param requestingUser      the user that requested the feedback generation
     */
    public void generateAutomaticNonGradedFeedback(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise, User requestingUser) {
        log.debug("Using athena to generate (programming exercise) feedback request: {}", programmingExercise.getId());

        // athena takes over the control here
        var submissionOptional = programmingExerciseParticipationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(participation.getId())
                .findLatestSubmission();
        if (submissionOptional.isEmpty()) {
            throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmissionExists", true);
        }
        var submission = submissionOptional.get();

        // save result and transmit it over websockets to notify the client about the status
        Result automaticResult = this.submissionService.saveNewEmptyResult(submission);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        automaticResult.setRated(true);
        Result firstResult = submission.getFirstResult();
        if (firstResult != null && firstResult.getScore() != null) {
            automaticResult.setScore(firstResult.getScore());
        }
        else {
            automaticResult.setScore(0.0);
        }
        automaticResult.setSuccessful(null);
        automaticResult.setCompletionDate(ZonedDateTime.now().plusMinutes(5)); // we do not want to show dates without a completion date, but we want the students to know their
                                                                               // feedback request is in work
        automaticResult = this.resultRepository.save(automaticResult);

        try {

            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
            // now the client should be able to see new result

            log.debug("Submission id: {}", submission.getId());

            AthenaFeedbackApi api = athenaFeedbackApi.orElseThrow(() -> new ApiProfileNotPresentException(AthenaFeedbackApi.class, MODULE_FEATURE_ATHENA));
            var athenaResponse = api.getProgrammingFeedbackSuggestions(programmingExercise, (ProgrammingSubmission) submission, false, requestingUser);

            List<Feedback> feedbacks = athenaResponse.stream().filter(individualFeedbackItem -> individualFeedbackItem.filePath() != null)
                    .filter(individualFeedbackItem -> individualFeedbackItem.description() != null).map(individualFeedbackItem -> {
                        var feedback = new Feedback();
                        String feedbackText;
                        Integer lineStart = individualFeedbackItem.lineStart();
                        Integer lineEnd = individualFeedbackItem.lineEnd();
                        if (Objects.nonNull(lineStart) && lineStart > 0) {
                            if (Objects.nonNull(lineEnd) && lineEnd > lineStart) {
                                feedbackText = (NON_GRADED_FEEDBACK_SUGGESTION + "File %s at lines %d-%d").formatted(individualFeedbackItem.filePath(), lineStart, lineEnd);
                                feedback.setReference("file:%s_line:%d-%d".formatted(individualFeedbackItem.filePath(), lineStart, lineEnd));
                            }
                            else {
                                feedbackText = (NON_GRADED_FEEDBACK_SUGGESTION + "File %s at line %d").formatted(individualFeedbackItem.filePath(), lineStart);
                                feedback.setReference("file:%s_line:%d".formatted(individualFeedbackItem.filePath(), lineStart));
                            }
                        }
                        else {
                            feedbackText = (NON_GRADED_FEEDBACK_SUGGESTION + "File %s").formatted(individualFeedbackItem.filePath());
                        }
                        feedback.setText(feedbackText);
                        feedback.setDetailText(individualFeedbackItem.description());
                        feedback.setHasLongFeedbackText(false);
                        feedback.setType(FeedbackType.AUTOMATIC);
                        feedback.setCredits(individualFeedbackItem.credits());
                        return feedback;
                    }).sorted(Comparator.comparing(Feedback::getCredits, Comparator.nullsLast(Comparator.naturalOrder()))).toList();

            automaticResult.setSuccessful(true);
            automaticResult.setCompletionDate(ZonedDateTime.now());

            this.resultService.storeFeedbackInResult(automaticResult, feedbacks, true);

            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
        }
        catch (Exception e) {
            log.error("Could not generate feedback", e);
            automaticResult.setSuccessful(false);
            automaticResult.setCompletionDate(ZonedDateTime.now());
            this.resultRepository.save(automaticResult);
            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
        }
    }
}
