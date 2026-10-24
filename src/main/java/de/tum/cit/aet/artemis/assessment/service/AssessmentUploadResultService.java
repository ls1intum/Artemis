package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

/**
 * Service for creating and updating manual results from assessment uploads.
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

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    /**
     * Creates a service for storing and updating uploaded manual results.
     * <p>
     * <b>Preconditions:</b> all parameters are non-{@code null}.
     *
     * @param userRepository                   repository used to load the current assessor
     * @param assessmentUploadResultRepository repository used to store and load uploaded results
     * @param ltiApi                           optional LTI integration notified about new results
     * @param resultWebsocketService           websocket service notified about new results
     * @param testCaseFeedbackRepository       repository used to drop the test-case feedback of an overwritten assessment
     * @param scaFeedbackRepository            repository used to drop the static-code-analysis feedback of an overwritten assessment
     * @throws IllegalArgumentException if a parameter is {@code null}
     */
    public AssessmentUploadResultService(final UserRepository userRepository, final AssessmentUploadResultRepository assessmentUploadResultRepository,
            final Optional<LtiApi> ltiApi, final ResultWebsocketService resultWebsocketService, final TestCaseFeedbackRepository testCaseFeedbackRepository,
            final ScaFeedbackRepository scaFeedbackRepository) {
        if (Stream.of(userRepository, assessmentUploadResultRepository, ltiApi, resultWebsocketService, testCaseFeedbackRepository, scaFeedbackRepository)
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The assessment upload result service dependencies must not be null");
        }
        this.userRepository = userRepository;
        this.assessmentUploadResultRepository = assessmentUploadResultRepository;
        this.ltiApi = ltiApi;
        this.resultWebsocketService = resultWebsocketService;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
    }

    /**
     * Stores the manual results of one upload — newly created ones and existing ones that were edited in place — while loading the current assessor and the websocket payload
     * graph only once for the whole upload.
     * <p>
     * Existing assessments are edited rather than deleted and re-created, so everything that references them (ratings, participant scores, complaints) stays valid and the
     * {@code @PostUpdate} {@link de.tum.cit.aet.artemis.assessment.ResultListener} recomputes the participant scores just as it does for an assessment edited in the assessment
     * editor.
     * <p>
     * <b>Preconditions:</b> both collections are non-{@code null} and contain no {@code null} elements; every result in {@code newResults} is transient and already attached to
     * its submission's {@code results} collection (via {@link Submission#addResult}) with the submission reference set; every result in {@code updatedResults} is a persisted
     * result whose feedback was replaced in place. Empty collections are permitted and produce an empty result.
     * <p>
     * <b>Postcondition:</b> every supplied result is stored as a manual result and its notification has been sent. The results are written by a single repository call and are
     * therefore stored all or not at all; an exception from this method means none of them was written, which is what lets the caller compensate for what it created before.
     *
     * @param newResults         newly created results, each attached to its submission's results collection
     * @param updatedResults     existing persisted results that were edited in place
     * @param ratedResult        override value for the rated property of every result
     * @param exercise           the programming exercise being assessed, needed to cap and store the score
     * @param pointsByTestCaseId derived points per test case, as {@code TestCasePointsService} computes them for this exercise
     * @return the stored results with eagerly loaded submissions and feedback
     * @throws IllegalArgumentException if a precondition is violated
     */
    public List<Result> saveManualResults(final Collection<Result> newResults, final Collection<Result> updatedResults, final boolean ratedResult,
            final ProgrammingExercise exercise, final Map<Long, Double> pointsByTestCaseId) {
        if (newResults == null || updatedResults == null) {
            throw new IllegalArgumentException("The manual results must not be null");
        }
        if (Stream.concat(newResults.stream(), updatedResults.stream()).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("The manual results must not contain null elements");
        }
        if (updatedResults.stream().anyMatch(result -> result.getId() == null)) {
            throw new IllegalArgumentException("The updated manual results must be persisted");
        }
        final List<Result> results = Stream.concat(newResults.stream(), updatedResults.stream()).toList();
        if (results.isEmpty()) {
            return List.of();
        }

        final User assessor = userRepository.getUserWithAuthorities();
        final ZonedDateTime completionDate = ZonedDateTime.now();
        results.forEach(result -> initializeManualResult(result, ratedResult, assessor, completionDate));

        // An overwritten assessment keeps the automatic feedback it carries; the uploaded points are manual feedback on top of it, exactly as a tutor's feedback is in the
        // assessment editor. The score therefore has to come out of the shared calculation rather than from the uploaded number, and that calculation refuses to run on
        // collections it cannot see, so the rows of the results being overwritten are loaded here. A new result already carries the copies its caller made.
        hydrateTypedFeedback(updatedResults);
        results.forEach(result -> result.calculateScoreForProgrammingExercise(exercise, pointsByTestCaseId));

        // Write every result explicitly. There is no surrounding transaction, so the results the caller edited are detached and nothing would be written by dirty checking.
        // Saving an existing result merges those edits, and its feedback association (cascade ALL with orphan removal) both persists the replacement feedback and deletes the
        // feedback the upload removed; saving a new result inserts it together with its feedback.
        final List<Result> storedResults = assessmentUploadResultRepository.saveAll(results);

        // Past this point the assessments are stored: saveAll is one repository call and therefore one transaction, so it either wrote every result or none. Announcing them is
        // a separate concern that must not turn a stored upload into a failed one — the caller treats an exception from this method as "nothing was written" and compensates
        // accordingly — so the reload and the notifications are isolated here, exactly as notifyAboutNewResultSafely isolates a single notification.
        try {
            final List<Long> resultIds = storedResults.stream().map(Result::getId).toList();
            final List<Result> reloadedResults = assessmentUploadResultRepository.findAllWithSubmissionAndFeedbackAndTeamStudentsByIds(resultIds);
            // Each save has already committed, so "after the results are stored" is simply the next statement.
            reloadedResults.forEach(this::notifyAboutNewResultSafely);
            return reloadedResults;
        }
        catch (final RuntimeException e) {
            log.warn("Stored {} uploaded assessment result(s) but could not reload them to announce them", storedResults.size(), e);
            return storedResults;
        }
    }

    /**
     * Loads the automatic test-case and static-code-analysis rows of the assessments this upload overwrites.
     * <p>
     * The upload reads results without those collections, so they arrive as uninitialized proxies. The score calculation rejects them in that state rather than quietly
     * scoring an assessment as if it had no automatic feedback, and saving a result whose collections were replaced while uninitialized would orphan-remove the stored rows.
     * Loaded in one query per table so a batch does not scale its query count with the number of overwritten assessments.
     * <p>
     * <b>Precondition:</b> {@code updatedResults} are persisted results.
     * <p>
     * <b>Postcondition:</b> every supplied result carries its stored typed feedback.
     *
     * @param updatedResults the assessments being overwritten
     */
    private void hydrateTypedFeedback(final Collection<Result> updatedResults) {
        final List<Long> resultIds = updatedResults.stream().map(Result::getId).toList();
        if (resultIds.isEmpty()) {
            return;
        }
        final Map<Long, List<TestCaseFeedback>> testCaseFeedbackByResultId = testCaseFeedbackRepository.findWithTestCaseByResultIds(resultIds).stream()
                .collect(Collectors.groupingBy(feedback -> feedback.getResult().getId()));
        final Map<Long, List<ScaFeedback>> scaFeedbackByResultId = scaFeedbackRepository.findByResultIds(resultIds).stream()
                .collect(Collectors.groupingBy(feedback -> feedback.getResult().getId()));
        updatedResults.forEach(result -> {
            result.setTestCaseFeedbacks(testCaseFeedbackByResultId.getOrDefault(result.getId(), List.of()));
            result.setScaFeedbacks(scaFeedbackByResultId.getOrDefault(result.getId(), List.of()));
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
     * Finds, among the results an upload is about to overwrite, the participations whose result carries a complaint and therefore must not be overwritten. Overwriting such a
     * result would change the assessment the student is complaining about while the complaint is still open, leaving the complaint and its response referring to an assessment
     * that no longer exists in that form. The caller rejects the whole upload for these participations instead. The lookup is scoped to the exact results being overwritten (the
     * manual results on each participation's latest submission), so a complaint on a superseded submission's result — which the upload leaves untouched — does not block the
     * upload.
     * <p>
     * <b>Precondition:</b> {@code resultIds} is non-{@code null} (an empty collection yields an empty result) and contains only persisted result ids.
     * <p>
     * <b>Postcondition:</b> read-only; the returned ids are the participations of the supplied results that have a complaint.
     *
     * @param resultIds ids of the manual results that would be overwritten
     * @return the participation ids that must not be overwritten because a complaint exists on a result being overwritten
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
