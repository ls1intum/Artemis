package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.athena.api.AthenaFeedbackApi;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.core.exception.ApiProfileNotPresentException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Service class for managing code review feedback on programming exercises.
 * This service handles the processing of feedback requests for programming exercises,
 * including automatic generation of feedback through integration with external services
 * such as Athena.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseCodeReviewFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseCodeReviewFeedbackService.class);

    public static final String NON_GRADED_FEEDBACK_SUGGESTION = "NonGradedFeedbackSuggestion:";

    private final GroupNotificationService groupNotificationService;

    private final Optional<AthenaFeedbackApi> athenaFeedbackApi;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final SubmissionService submissionService;

    private final ResultService resultService;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingMessagingService programmingMessagingService;

    public ProgrammingExerciseCodeReviewFeedbackService(GroupNotificationService groupNotificationService, Optional<AthenaFeedbackApi> athenaFeedbackApi,
            SubmissionService submissionService, ResultService resultService, ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository,
            ResultRepository resultRepository, ProgrammingExerciseParticipationService programmingExerciseParticipationService1,
            ProgrammingMessagingService programmingMessagingService) {
        this.groupNotificationService = groupNotificationService;
        this.athenaFeedbackApi = athenaFeedbackApi;
        this.submissionService = submissionService;
        this.resultService = resultService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.resultRepository = resultRepository;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService1;
        this.programmingMessagingService = programmingMessagingService;
    }

    /**
     * Handles the request for generating feedback for a programming exercise.
     * This method decides whether to generate feedback automatically using Athena,
     * or notify a tutor to manually process the feedback.
     *
     * @param exerciseId          the id of the programming exercise.
     * @param participation       the student participation associated with the exercise.
     * @param programmingExercise the programming exercise object.
     * @return ProgrammingExerciseStudentParticipation updated programming exercise for a tutor assessment
     */
    public ProgrammingExerciseStudentParticipation handleNonGradedFeedbackRequest(Long exerciseId, ProgrammingExerciseStudentParticipation participation,
            ProgrammingExercise programmingExercise) {
        if (this.athenaFeedbackApi.isPresent()) {
            this.athenaFeedbackApi.get().checkRateLimitOrThrow(participation);
            CompletableFuture.runAsync(() -> this.generateAutomaticNonGradedFeedback(participation, programmingExercise));
            return participation;
        }
        else {
            log.debug("tutor is responsible to process feedback request: {}", exerciseId);
            groupNotificationService.notifyTutorGroupAboutNewFeedbackRequest(programmingExercise);
            return setIndividualDueDate(participation, true);
        }
    }

    /**
     * Generates automatic non-graded feedback for a programming exercise submission.
     * This method leverages the Athena service to generate feedback based on the latest submission.
     *
     * @param participation       the student participation associated with the exercise.
     * @param programmingExercise the programming exercise object.
     */
    public void generateAutomaticNonGradedFeedback(ProgrammingExerciseStudentParticipation participation, ProgrammingExercise programmingExercise) {
        log.debug("Using athena to generate (programming exercise) feedback request: {}", programmingExercise.getId());

        // athena takes over the control here
        var submissionOptional = programmingExerciseParticipationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(participation.getId())
                .findLatestSubmission();
        if (submissionOptional.isEmpty()) {
            throw new BadRequestAlertException("No legal submissions found", "submission", "noSubmissionExists", true);
        }
        var submission = submissionOptional.get();

        // save result and transmit it over websockets to notify the client about the status
        var automaticResult = this.submissionService.saveNewEmptyResult(submission);
        automaticResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        automaticResult.setRated(true); // we want to use this feedback to give the grade in the future
        automaticResult.setScore(100.0);
        automaticResult.setSuccessful(null);
        automaticResult.setCompletionDate(ZonedDateTime.now().plusMinutes(5)); // we do not want to show dates without a completion date, but we want the students to know their
                                                                               // feedback request is in work
        automaticResult = this.resultRepository.save(automaticResult);

        try {

            this.programmingMessagingService.notifyUserAboutNewResult(automaticResult, participation);
            // now the client should be able to see new result

            log.debug("Submission id: {}", submission.getId());

            AthenaFeedbackApi api = athenaFeedbackApi.orElseThrow(() -> new ApiProfileNotPresentException(AthenaFeedbackApi.class, PROFILE_ATHENA));
            var athenaResponse = api.getProgrammingFeedbackSuggestions(programmingExercise, (ProgrammingSubmission) submission, false);

            List<Feedback> feedbacks = athenaResponse.stream().filter(individualFeedbackItem -> individualFeedbackItem.filePath() != null)
                    .filter(individualFeedbackItem -> individualFeedbackItem.description() != null).map(individualFeedbackItem -> {
                        var feedback = new Feedback();
                        String feedbackText;
                        if (Objects.nonNull(individualFeedbackItem.lineStart())) {
                            if (Objects.nonNull(individualFeedbackItem.lineEnd()) && !individualFeedbackItem.lineStart().equals(individualFeedbackItem.lineEnd())) {
                                feedbackText = String.format(NON_GRADED_FEEDBACK_SUGGESTION + "File %s at lines %d-%d", individualFeedbackItem.filePath(),
                                        individualFeedbackItem.lineStart(), individualFeedbackItem.lineEnd());
                            }
                            else {
                                feedbackText = String.format(NON_GRADED_FEEDBACK_SUGGESTION + "File %s at line %d", individualFeedbackItem.filePath(),
                                        individualFeedbackItem.lineStart());
                            }
                            feedback.setReference(String.format("file:%s_line:%d", individualFeedbackItem.filePath(), individualFeedbackItem.lineStart()));
                        }
                        else {
                            feedbackText = String.format(NON_GRADED_FEEDBACK_SUGGESTION + "File %s", individualFeedbackItem.filePath());
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

    /**
     * Sets an individual due date for a participation, locks the repository,
     * and invalidates previous results to prepare for new feedback.
     *
     * @param participation             the programming exercise student participation.
     * @param invalidatePreviousResults flag indicating whether to invalidate previous results.
     */
    private ProgrammingExerciseStudentParticipation setIndividualDueDate(ProgrammingExerciseStudentParticipation participation, boolean invalidatePreviousResults) {
        // The participations due date is a flag showing that a feedback request is sent
        participation.setIndividualDueDate(now());

        participation = programmingExerciseStudentParticipationRepository.save(participation);
        // Circumvent lazy loading after save
        participation.setParticipant(participation.getParticipant());

        if (invalidatePreviousResults) {
            Set<Result> participationResults = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull))
                    .collect(Collectors.toSet());
            participationResults.forEach(participationResult -> participationResult.setRated(false));
            this.resultRepository.saveAll(participationResults);
        }

        return participation;
    }
}
