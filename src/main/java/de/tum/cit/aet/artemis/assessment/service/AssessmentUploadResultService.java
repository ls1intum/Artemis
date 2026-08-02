package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentNoteRepository;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Service for creating and replacing manual results from assessment uploads.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class AssessmentUploadResultService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentUploadResultService.class);

    private final UserRepository userRepository;

    private final AssessmentUploadResultRepository assessmentUploadResultRepository;

    private final AssessmentNoteRepository assessmentNoteRepository;

    private final Optional<LtiApi> ltiApi;

    private final ResultWebsocketService resultWebsocketService;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final RatingRepository ratingRepository;

    private final FeedbackRepository feedbackRepository;

    private final ComplaintRepository complaintRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final LongFeedbackTextRepository longFeedbackTextRepository;

    /**
     * Creates a service for storing and replacing uploaded manual results.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}.
     *
     * @param userRepository                   repository used to load the current assessor
     * @param assessmentUploadResultRepository repository used to store and load uploaded results
     * @param assessmentNoteRepository         repository used to delete dependent assessment notes
     * @param ltiApi                           optional LTI integration notified about new results
     * @param resultWebsocketService           websocket service notified about new results
     * @param complaintResponseRepository      repository used to delete dependent complaint responses
     * @param ratingRepository                 repository used to delete dependent ratings
     * @param feedbackRepository               repository used to delete dependent feedback
     * @param complaintRepository              repository used to delete dependent complaints
     * @param participantScoreRepository       repository used to clear dependent participant scores
     * @param longFeedbackTextRepository       repository used to delete dependent long feedback text
     * @throws IllegalArgumentException if a parameter is {@code null}
     */
    public AssessmentUploadResultService(final UserRepository userRepository, final AssessmentUploadResultRepository assessmentUploadResultRepository,
            final AssessmentNoteRepository assessmentNoteRepository, final Optional<LtiApi> ltiApi, final ResultWebsocketService resultWebsocketService,
            final ComplaintResponseRepository complaintResponseRepository, final RatingRepository ratingRepository, final FeedbackRepository feedbackRepository,
            final ComplaintRepository complaintRepository, final ParticipantScoreRepository participantScoreRepository,
            final LongFeedbackTextRepository longFeedbackTextRepository) {
        if (Stream.of(userRepository, assessmentUploadResultRepository, assessmentNoteRepository, ltiApi, resultWebsocketService, complaintResponseRepository, ratingRepository,
                feedbackRepository, complaintRepository, participantScoreRepository, longFeedbackTextRepository).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The assessment upload result service dependencies must not be null");
        }
        this.userRepository = userRepository;
        this.assessmentUploadResultRepository = assessmentUploadResultRepository;
        this.assessmentNoteRepository = assessmentNoteRepository;
        this.ltiApi = ltiApi;
        this.resultWebsocketService = resultWebsocketService;
        this.complaintResponseRepository = complaintResponseRepository;
        this.ratingRepository = ratingRepository;
        this.feedbackRepository = feedbackRepository;
        this.complaintRepository = complaintRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.longFeedbackTextRepository = longFeedbackTextRepository;
    }

    /**
     * Creates multiple manual results while loading the current assessor and the websocket payload graph only once for the whole upload.
     * <p>
     * <b>Preconditions:</b> {@code results} is non-{@code null} and contains no {@code null} elements. An empty collection is permitted and produces an empty result.
     * <p>
     * <b>Postcondition:</b> every supplied result is stored as a manual result and its notification is sent immediately when no transaction is active, or scheduled for the
     * surrounding transaction's successful commit.
     *
     * @param results     newly created results
     * @param ratedResult override value for the rated property of every result
     * @return the stored results with eagerly loaded submissions and feedback
     * @throws IllegalArgumentException if a precondition is violated
     */
    public List<Result> createNewManualResults(final Collection<Result> results, final boolean ratedResult) {
        if (results == null) {
            throw new IllegalArgumentException("The manual results must not be null");
        }
        if (results.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The manual results must not contain null elements");
        }
        if (results.isEmpty()) {
            return List.of();
        }

        final User assessor = userRepository.getUserWithAuthorities();
        final ZonedDateTime completionDate = ZonedDateTime.now();
        results.forEach(result -> initializeManualResult(result, ratedResult, assessor, completionDate));

        assessmentUploadResultRepository.saveAll(results);
        final List<Long> resultIds = results.stream().map(Result::getId).toList();
        final List<Result> savedResults = assessmentUploadResultRepository.findAllWithSubmissionAndFeedbackAndTeamStudentsByIds(resultIds);
        notifyAboutNewResultsAfterCommit(savedResults);
        return savedResults;
    }

    /**
     * Sends result notifications immediately when no transaction is active, or after the active transaction commits.
     * <p>
     * <b>Preconditions:</b> {@code savedResults} is non-{@code null}, non-empty, contains no {@code null} elements, and every result is persisted and has an initialized
     * submission.
     * <p>
     * <b>Postcondition:</b> notifications were attempted before return when no transaction synchronization is active; otherwise exactly one after-commit callback was registered
     * for all supplied results. Notification failures do not propagate.
     *
     * @param savedResults persisted results with their notification graph initialized
     */
    private void notifyAboutNewResultsAfterCommit(final List<Result> savedResults) {
        assert savedResults != null && !savedResults.isEmpty() : "savedResults must not be null or empty";
        assert savedResults.stream()
                .allMatch(result -> result != null && result.getId() != null && result.getSubmission() != null) : "savedResults must be persisted and have submissions";
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            savedResults.forEach(this::notifyAboutNewResultSafely);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                savedResults.forEach(AssessmentUploadResultService.this::notifyAboutNewResultSafely);
            }
        });
    }

    /**
     * Isolates notification failures from the already committed assessment upload.
     * <p>
     * <b>Precondition:</b> {@code savedResult} is non-{@code null}, persisted, and has an initialized submission.
     * <p>
     * <b>Postcondition:</b> notification was attempted; any {@link RuntimeException} was logged and did not propagate.
     *
     * @param savedResult result to announce
     */
    private void notifyAboutNewResultSafely(final Result savedResult) {
        assert savedResult != null && savedResult.getId() != null && savedResult.getSubmission() != null : "savedResult must be persisted and have a submission";
        try {
            notifyAboutNewResult(savedResult);
        }
        catch (final RuntimeException e) {
            log.warn("Could not notify consumers about uploaded assessment result {}", savedResult.getId(), e);
        }
    }

    /**
     * Initializes a transient result as a manual result.
     * <p>
     * <b>Preconditions:</b> {@code result}, {@code assessor}, and {@code completionDate} are non-{@code null}.
     * <p>
     * <b>Postcondition:</b> the result has manual assessment metadata and every feedback references it.
     *
     * @param result         result to initialize
     * @param ratedResult    value for the rated property
     * @param assessor       current assessor
     * @param completionDate shared completion date of the upload
     */
    private void initializeManualResult(final Result result, final boolean ratedResult, final User assessor, final ZonedDateTime completionDate) {
        assert result != null && assessor != null && completionDate != null : "result, assessor and completionDate must not be null";
        result.setAssessmentType(AssessmentType.MANUAL);
        result.setAssessor(assessor);
        result.setCompletionDate(completionDate);
        // Manual feedback is always rated, but can be overwritten for an external submission.
        result.setRated(ratedResult);
        result.getFeedbacks().forEach(feedback -> feedback.setResult(result));
    }

    /**
     * Notifies LTI and websocket consumers about a stored result where applicable.
     * <p>
     * <b>Preconditions:</b> {@code savedResult} and its submission are non-{@code null}.
     * <p>
     * <b>Postcondition:</b> non-example results have been broadcast and programming exercise results have been forwarded to LTI when that integration is available.
     *
     * @param savedResult stored result to publish
     */
    private void notifyAboutNewResult(final Result savedResult) {
        assert savedResult != null : "savedResult must not be null";
        assert savedResult.getSubmission() != null : "savedResult must have a submission";
        // If it is an example result we do not have any participation (isExampleResult can also be null).
        if (Boolean.FALSE.equals(savedResult.isExampleResult()) || savedResult.isExampleResult() == null) {
            if (savedResult.getSubmission().getParticipation() instanceof ProgrammingExerciseStudentParticipation && ltiApi.isPresent()) {
                ltiApi.get().onNewResult((StudentParticipation) savedResult.getSubmission().getParticipation());
            }
            resultWebsocketService.broadcastNewResult(savedResult.getSubmission().getParticipation(), savedResult);
        }
    }

    /**
     * Bulk-deletes results and every dependent database row in foreign-key order. This path deliberately bypasses entity loading and lifecycle callbacks. Callers that create
     * replacement results immediately afterwards retain participant-score scheduling through the new results' lifecycle callbacks.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null}, non-empty, and contains only non-{@code null} persisted result ids.
     * <p>
     * <b>Postcondition:</b> the identified results and all rows that depend on them have been deleted.
     *
     * @param resultIds ids of the results to delete
     * @throws IllegalArgumentException if a precondition is violated
     */
    public void deleteResultsByIds(final Collection<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            throw new IllegalArgumentException("The result ids must not be null or empty");
        }
        if (resultIds.stream().anyMatch(resultId -> resultId == null || resultId <= 0)) {
            throw new IllegalArgumentException("The result ids must identify persisted results");
        }
        complaintResponseRepository.deleteByResultIds(resultIds);
        complaintRepository.deleteByResultIds(resultIds);
        ratingRepository.deleteByResultIds(resultIds);
        participantScoreRepository.clearAllByResultIds(resultIds);
        longFeedbackTextRepository.deleteByFeedbackResultIds(resultIds);
        feedbackRepository.deleteByResultIds(resultIds);
        assessmentNoteRepository.deleteByResultIds(resultIds);
        assessmentUploadResultRepository.deleteAllByIds(resultIds);
    }

    /**
     * Deletes all manual results for the requested participations of one exercise using a fixed number of bulk statements. Automatic results are not selected.
     * <p>
     * <b>Preconditions:</b> {@code exerciseId} identifies a persisted exercise and {@code participationIds} is non-{@code null}, non-empty, and contains only persisted ids.
     * <p>
     * <b>Postcondition:</b> all manual results associated with the supplied exercise and participations have been deleted; automatic results remain unchanged.
     *
     * @param exerciseId       target exercise id
     * @param participationIds participations whose manual results are being replaced
     * @throws IllegalArgumentException if a precondition is violated
     */
    public void deleteManualResults(final long exerciseId, final Collection<Long> participationIds) {
        if (exerciseId <= 0) {
            throw new IllegalArgumentException("The exercise id must identify a persisted exercise");
        }
        if (participationIds == null || participationIds.isEmpty()) {
            throw new IllegalArgumentException("The participation ids must not be null or empty");
        }
        if (participationIds.stream().anyMatch(participationId -> participationId == null || participationId <= 0)) {
            throw new IllegalArgumentException("The participation ids must identify persisted participations");
        }
        final List<Long> resultIds = assessmentUploadResultRepository.findManualResultIds(exerciseId, participationIds);
        if (!resultIds.isEmpty()) {
            deleteResultsByIds(resultIds);
        }
    }
}
