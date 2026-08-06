package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
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

    private final Optional<LtiApi> ltiApi;

    private final ResultWebsocketService resultWebsocketService;

    private final RatingRepository ratingRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final SubmissionRepository submissionRepository;

    /**
     * Creates a service for storing and replacing uploaded manual results.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}.
     *
     * @param userRepository                   repository used to load the current assessor
     * @param assessmentUploadResultRepository repository used to store and load uploaded results
     * @param ltiApi                           optional LTI integration notified about new results
     * @param resultWebsocketService           websocket service notified about new results
     * @param ratingRepository                 repository used to delete dependent ratings
     * @param participantScoreRepository       repository used to clear dependent participant scores
     * @param submissionRepository             repository used to persist the ordered {@code Submission.results} collection so {@code results_order} stays unique and contiguous
     * @throws IllegalArgumentException if a parameter is {@code null}
     */
    public AssessmentUploadResultService(final UserRepository userRepository, final AssessmentUploadResultRepository assessmentUploadResultRepository,
            final Optional<LtiApi> ltiApi, final ResultWebsocketService resultWebsocketService, final RatingRepository ratingRepository,
            final ParticipantScoreRepository participantScoreRepository, final SubmissionRepository submissionRepository) {
        if (Stream.of(userRepository, assessmentUploadResultRepository, ltiApi, resultWebsocketService, ratingRepository, participantScoreRepository, submissionRepository)
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The assessment upload result service dependencies must not be null");
        }
        this.userRepository = userRepository;
        this.assessmentUploadResultRepository = assessmentUploadResultRepository;
        this.ltiApi = ltiApi;
        this.resultWebsocketService = resultWebsocketService;
        this.ratingRepository = ratingRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.submissionRepository = submissionRepository;
    }

    /**
     * Creates multiple manual results while loading the current assessor and the websocket payload graph only once for the whole upload.
     * <p>
     * <b>Preconditions:</b> {@code results} is non-{@code null}, contains no {@code null} elements, and every result is already attached to its submission's {@code results}
     * collection (via {@link Submission#addResult}) with the submission reference set. An empty collection is permitted and produces an empty result.
     * <p>
     * <b>Postcondition:</b> every supplied result is stored as a manual result — persisted through its owning submission so the ordered {@code Submission.results} collection keeps
     * unique, contiguous {@code results_order} values — and its notification is sent immediately when no transaction is active, or scheduled for the surrounding transaction's
     * successful commit.
     *
     * @param results     newly created results, each attached to its submission's results collection
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

        // Persist through the owning (already managed) submissions: flushing cascade-persists each result attached to its submission's results collection, so Hibernate maintains
        // the ordered collection and writes unique, contiguous results_order values (persisting a Result directly leaves the @OrderColumn unmanaged) and assigns each result its
        // id.
        // Every result must already be attached to its submission's results collection by the caller.
        submissionRepository.flush();

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
     * Deletes the references to the given results that Hibernate cannot cascade-delete from a {@link Result} (ratings and participant scores).
     * <p>
     * The assessment-upload replacement removes existing manual results via orphan removal on {@code Submission.results}, which cascades feedback, long feedback text and
     * assessment notes but not these entities; they must be deleted first to avoid a foreign-key violation. A complaint cannot reference one of these results: the caller rejects
     * any participation whose current manual assessment has a complaint, under a write lock that serializes a concurrent complaint behind the upload, so the orphan-removal delete
     * never encounters one and no defensive complaint cleanup is needed here.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null} (an empty collection is a no-op) and contains only persisted result ids.
     * <p>
     * <b>Postcondition:</b> no rating or participant score references any of the supplied results.
     *
     * @param resultIds ids of the results whose non-cascaded references are removed
     */
    public void deleteNonCascadedResultReferences(final Collection<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return;
        }
        ratingRepository.deleteByResultIds(resultIds);
        participantScoreRepository.clearAllByResultIds(resultIds);
    }

    /**
     * Write-locks the manual results an upload is about to replace, so that a complaint being created concurrently for one of them serializes behind the upload and the complaint
     * check the caller runs next stays valid through commit. Creating a complaint updates its result row (setting {@code hasComplaint}) and inserts the complaint, both of which
     * block on the {@code FOR UPDATE} lock until the upload's transaction commits — by then the locked result is gone, so a complaint cannot slip in between the check and the
     * orphan-removal delete (which would otherwise fail with a foreign-key violation). The caller must acquire this lock before checking for complaints and before mutating the
     * ordered {@code Submission.results} collection.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null} (an empty collection is a no-op) and contains only persisted result ids; the caller has an active transaction.
     * <p>
     * <b>Postcondition:</b> the identified result rows are write-locked until the caller's transaction completes.
     *
     * @param resultIds ids of the manual results about to be replaced
     * @throws IllegalArgumentException if a result id is not a persisted id
     */
    public void lockResultsForReplacement(final Collection<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return;
        }
        if (resultIds.stream().anyMatch(resultId -> resultId == null || resultId <= 0)) {
            throw new IllegalArgumentException("The result ids must identify persisted results");
        }
        assessmentUploadResultRepository.lockResultsForReplacement(resultIds);
    }

    /**
     * Finds, among the results an upload is about to replace, the participations whose result carries a complaint and therefore must not be overwritten. Deleting such a result
     * would silently destroy the student's complaint and any instructor response, which the supported single-assessment deletion path also forbids. The caller rejects the whole
     * upload for these participations instead of replacing their results. The lookup is scoped to the exact results being replaced (the manual results on each participation's
     * latest submission), so a complaint on a superseded submission's result — which the upload leaves untouched — does not block the upload.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null} (an empty collection yields an empty result) and contains only persisted result ids.
     * <p>
     * <b>Postcondition:</b> read-only; the returned ids are the participations of the supplied results that have a complaint.
     *
     * @param resultIds ids of the manual results that would be replaced
     * @return the participation ids that must not be overwritten because a complaint exists on a result being replaced
     * @throws IllegalArgumentException if a result id is not a persisted id
     */
    public Set<Long> findParticipationsWithComplaintOnResults(final Collection<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return Set.of();
        }
        if (resultIds.stream().anyMatch(resultId -> resultId == null || resultId <= 0)) {
            throw new IllegalArgumentException("The result ids must identify persisted results");
        }
        return assessmentUploadResultRepository.findParticipationIdsWithComplaintOnResults(resultIds);
    }
}
