package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.StaticCodeAnalysisIssue;

/**
 * Synthesizes legacy {@link Feedback} views from the typed automatic feedback rows
 * ({@link TestCaseFeedback}, {@link ScaFeedback}) of a programming result, so that all existing
 * serialization paths (result details endpoint, websocket ResultDTO, complaint views) keep producing the
 * exact JSON shape the client already understands — including derived credits and visibility, the SCA
 * identifier/JSON encoding, and the long-feedback preview contract.
 * <p>
 * <b>Synthetic ids:</b> the views carry a NEGATIVE id encoding the typed row they come from — see
 * {@link #syntheticTestCaseId(long)}. The client uses feedback ids only as list keys and for fetching long
 * feedback; the long-feedback endpoint decodes negative ids back to the typed row. Incoming feedback with
 * a negative or missing id must never be persisted (the assessment save paths strip such echoes).
 * <p>
 * <b>Read-only:</b> the synthesized views are added to the (detached) result's feedback collection purely
 * for serialization. A result that was passed through this service must never be saved — the transient
 * views would be persisted as legacy feedback rows by the cascade.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingFeedbackSynthesizerService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingFeedbackSynthesizerService.class);

    /**
     * Stride separating the two typed feedback tables inside one synthetic id space, see
     * {@link Constants#SYNTHETIC_FEEDBACK_ID_STRIDE}.
     */
    public static final long SYNTHETIC_ID_STRIDE = Constants.SYNTHETIC_FEEDBACK_ID_STRIDE;

    private static final JsonMapper objectMapper = JsonObjectMapper.get();

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    private final TestCasePointsService testCasePointsService;

    public ProgrammingFeedbackSynthesizerService(TestCaseFeedbackRepository testCaseFeedbackRepository, ScaFeedbackRepository scaFeedbackRepository,
            TestCasePointsService testCasePointsService) {
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
        this.testCasePointsService = testCasePointsService;
    }

    /**
     * Synthetic id of the legacy view of a test-case feedback row: the row id, negated so the client can tell
     * a synthesized view from a stored {@code feedback} row, and multiplied by
     * {@link Constants#SYNTHETIC_FEEDBACK_ID_STRIDE} so it cannot collide with an SCA view (the two tables
     * have independent id sequences).
     *
     * @param rowId the id of the {@code test_case_feedback} row
     * @return the synthetic (negative, even) id of the view
     */
    public static long syntheticTestCaseId(long rowId) {
        return -(rowId * SYNTHETIC_ID_STRIDE);
    }

    /**
     * Synthetic id of the legacy view of an SCA feedback row, see {@link #syntheticTestCaseId(long)}.
     *
     * @param rowId the id of the {@code sca_feedback} row
     * @return the synthetic (negative, odd) id of the view
     */
    public static long syntheticScaId(long rowId) {
        return -(rowId * SYNTHETIC_ID_STRIDE + 1);
    }

    public static boolean isSyntheticId(long feedbackId) {
        return feedbackId < 0;
    }

    /**
     * Whether the given feedback id addresses an SCA row rather than a test-case row. Stored (positive) ids belong to neither, so they are reported as not-SCA — callers that
     * distinguish the two typed tables have to establish {@link #isSyntheticId(long)} first.
     *
     * @param feedbackId the feedback id
     * @return true if the id was produced by {@link #syntheticScaId(long)}
     */
    public static boolean isSyntheticScaId(long feedbackId) {
        return isSyntheticId(feedbackId) && (-feedbackId) % SYNTHETIC_ID_STRIDE != 0;
    }

    /**
     * Decodes the row id a synthetic id addresses. Use {@link #isSyntheticScaId(long)} to learn which of the
     * two tables the id belongs to.
     *
     * @param syntheticId the synthetic (negative) feedback id
     * @return the id of the addressed {@code test_case_feedback} respectively {@code sca_feedback} row
     */
    public static long rowIdFromSyntheticId(long syntheticId) {
        return (-syntheticId) / SYNTHETIC_ID_STRIDE;
    }

    /**
     * Attaches synthesized legacy feedback views for all typed automatic feedback of the given
     * programming-exercise result to {@code result.getFeedbacks()}. Uses the already-initialized typed
     * collections when present, otherwise loads them (with test case and message) from the database.
     * No-op for results of other exercise types.
     *
     * @param result the (detached) result about to be serialized
     */
    public void attachSynthesizedFeedback(Result result) {
        ProgrammingExercise programmingExercise = exerciseOf(result);
        if (programmingExercise == null) {
            return;
        }
        attachSynthesizedFeedback(result, programmingExercise, TestCasePointsService.isForSolutionParticipation(result));
    }

    /**
     * Variant of {@link #attachSynthesizedFeedback(Result)} for callers whose result graph is detached or
     * partial (the submission → participation → exercise chain would trigger lazy loading), providing the
     * context explicitly instead.
     *
     * @param result                  the (detached) result about to be serialized
     * @param exercise                the programming exercise the result belongs to
     * @param isSolutionParticipation true if the result belongs to the solution participation
     */
    public void attachSynthesizedFeedback(Result result, ProgrammingExercise exercise, boolean isSolutionParticipation) {
        if (result == null || result.getId() == null || exercise == null) {
            return;
        }
        List<TestCaseFeedback> testCaseFeedbacks = hasAuthoritativeTestCaseFeedback(result) ? List.copyOf(result.getTestCaseFeedbacks())
                : testCaseFeedbackRepository.findWithTestCaseAndMessageByResultId(result.getId());
        List<ScaFeedback> scaFeedbacks = hasAuthoritativeScaFeedback(result) ? List.copyOf(result.getScaFeedbacks())
                : scaFeedbackRepository.findWithMessageByResultId(result.getId());

        Map<Long, Double> pointsByTestCaseId = testCaseFeedbacks.isEmpty() ? Map.of() : testCasePointsService.calculateTestCasePoints(exercise, isSolutionParticipation);
        synthesizeAndAttach(result, testCaseFeedbacks, scaFeedbacks, pointsByTestCaseId);
    }

    /**
     * Bulk variant of {@link #attachSynthesizedFeedback(Result)}: loads the typed feedback of all given
     * programming results with two grouped queries and derives the per-exercise points map once per
     * (exercise, participation-type) pair — instead of up to three queries per result.
     *
     * @param results the (detached) results about to be serialized; non-programming results are skipped
     */
    public void attachSynthesizedFeedback(Collection<Result> results) {
        List<Result> programmingResults = results.stream().filter(result -> result != null && exerciseOf(result) != null).toList();
        if (programmingResults.isEmpty()) {
            return;
        }

        List<Long> idsToLoad = programmingResults.stream().filter(result -> !hasAuthoritativeTestCaseFeedback(result) || !hasAuthoritativeScaFeedback(result)).map(Result::getId)
                .distinct().toList();
        Map<Long, List<TestCaseFeedback>> loadedTestCaseFeedback = loadByResultIdsInChunks(idsToLoad, testCaseFeedbackRepository::findWithTestCaseAndMessageByResultIds,
                feedback -> feedback.getResult().getId());
        Map<Long, List<ScaFeedback>> loadedScaFeedback = loadByResultIdsInChunks(idsToLoad, scaFeedbackRepository::findWithMessageByResultIds,
                feedback -> feedback.getResult().getId());

        Map<String, Map<Long, Double>> pointsCache = new HashMap<>();
        for (Result result : programmingResults) {
            ProgrammingExercise programmingExercise = exerciseOf(result);
            List<TestCaseFeedback> testCaseFeedbacks = hasAuthoritativeTestCaseFeedback(result) ? List.copyOf(result.getTestCaseFeedbacks())
                    : loadedTestCaseFeedback.getOrDefault(result.getId(), List.of());
            List<ScaFeedback> scaFeedbacks = hasAuthoritativeScaFeedback(result) ? List.copyOf(result.getScaFeedbacks())
                    : loadedScaFeedback.getOrDefault(result.getId(), List.of());

            Map<Long, Double> pointsByTestCaseId = Map.of();
            if (!testCaseFeedbacks.isEmpty()) {
                boolean isSolutionParticipation = TestCasePointsService.isForSolutionParticipation(result);
                pointsByTestCaseId = pointsCache.computeIfAbsent(programmingExercise.getId() + "|" + isSolutionParticipation,
                        key -> testCasePointsService.calculateTestCasePoints(programmingExercise, isSolutionParticipation));
            }
            synthesizeAndAttach(result, testCaseFeedbacks, scaFeedbacks, pointsByTestCaseId);
        }
    }

    /**
     * Bulk-loads the typed automatic feedback (test-case and SCA rows, without messages) of the given
     * results with two queries and replaces the results' collections — the light-weight hydration used by
     * score re-calculation flows on detached results. Results absent from the database get empty
     * collections.
     *
     * @param results the results to hydrate
     */
    public void hydrateTypedFeedback(Collection<Result> results) {
        List<Long> resultIds = results.stream().map(Result::getId).filter(Objects::nonNull).toList();
        if (resultIds.isEmpty()) {
            return;
        }
        var testCaseFeedbackByResult = loadByResultIdsInChunks(resultIds, testCaseFeedbackRepository::findWithTestCaseByResultIds, feedback -> feedback.getResult().getId());
        var scaFeedbackByResult = loadByResultIdsInChunks(resultIds, scaFeedbackRepository::findByResultIds, feedback -> feedback.getResult().getId());
        for (Result result : results) {
            result.setTestCaseFeedbacks(testCaseFeedbackByResult.getOrDefault(result.getId(), List.of()));
            result.setScaFeedbacks(scaFeedbackByResult.getOrDefault(result.getId(), List.of()));
        }
    }

    /**
     * Maximum number of result ids passed into a single {@code IN} predicate. A whole-course data export
     * hydrates every participation of the course at once, which can exceed PostgreSQL's limit of 65535 bind
     * parameters per statement; loading in chunks keeps every statement well below it.
     */
    private static final int RESULT_ID_CHUNK_SIZE = 1000;

    private <T> Map<Long, List<T>> loadByResultIdsInChunks(List<Long> resultIds, Function<List<Long>, List<T>> loader, Function<T, Long> resultIdOf) {
        Map<Long, List<T>> byResultId = new HashMap<>();
        for (int start = 0; start < resultIds.size(); start += RESULT_ID_CHUNK_SIZE) {
            List<Long> chunk = resultIds.subList(start, Math.min(start + RESULT_ID_CHUNK_SIZE, resultIds.size()));
            for (T row : loader.apply(chunk)) {
                byResultId.computeIfAbsent(resultIdOf.apply(row), key -> new ArrayList<>()).add(row);
            }
        }
        return byResultId;
    }

    private static ProgrammingExercise exerciseOf(Result result) {
        if (result == null || result.getId() == null || result.getSubmission() == null || result.getSubmission().getParticipation() == null
                || !(result.getSubmission().getParticipation().getExercise() instanceof ProgrammingExercise programmingExercise)) {
            return null;
        }
        return programmingExercise;
    }

    /**
     * An initialized, non-empty in-memory collection is authoritative (e.g. a result fresh from build
     * processing); an uninitialized or initialized-but-empty one is not — the rows are loaded instead,
     * because a result deserialized from client input carries empty-initialized collections although the
     * database has rows.
     */
    private static boolean hasAuthoritativeTestCaseFeedback(Result result) {
        return Hibernate.isInitialized(result.getTestCaseFeedbacks()) && !result.getTestCaseFeedbacks().isEmpty();
    }

    private static boolean hasAuthoritativeScaFeedback(Result result) {
        return Hibernate.isInitialized(result.getScaFeedbacks()) && !result.getScaFeedbacks().isEmpty();
    }

    private void synthesizeAndAttach(Result result, List<TestCaseFeedback> testCaseFeedbacks, List<ScaFeedback> scaFeedbacks, Map<Long, Double> pointsByTestCaseId) {
        // A view is addressed by the id of the row it is derived from, so the row has to be persisted first. Reaching this with an unsaved row means a caller serialized a
        // result before saving it - fail with something diagnosable rather than a NullPointerException inside the stream below.
        if (testCaseFeedbacks.stream().anyMatch(feedback -> feedback.getId() == null) || scaFeedbacks.stream().anyMatch(feedback -> feedback.getId() == null)) {
            throw new IllegalStateException("The typed automatic feedback of result " + result.getId() + " has to be saved before its legacy views can be synthesized");
        }
        if (testCaseFeedbacks.isEmpty() && scaFeedbacks.isEmpty()) {
            return;
        }
        if (!Hibernate.isInitialized(result.getFeedbacks())) {
            // The result was loaded without its feedback (e.g. the non-locking variant of the
            // submission-without-assessment endpoint, which only needs the submission id). Adding to the
            // uninitialized collection would throw outside a session, and a caller that did not ask for the
            // feedback does not serialize it either - so there is nothing to attach the views to.
            log.debug("Skipping feedback synthesis for result {}: the feedback collection was not loaded", result.getId());
            return;
        }
        long resultId = result.getId();
        testCaseFeedbacks.forEach(feedback -> result.getFeedbacks().add(synthesizeTestCaseFeedback(feedback, resultId, pointsByTestCaseId)));
        scaFeedbacks.forEach(feedback -> result.getFeedbacks().add(synthesizeScaFeedback(feedback, resultId)));
    }

    private Feedback synthesizeTestCaseFeedback(TestCaseFeedback source, long resultId, Map<Long, Double> pointsByTestCaseId) {
        Feedback view = new Feedback();
        view.setId(syntheticTestCaseId(source.getId()));
        view.setType(FeedbackType.AUTOMATIC);
        view.setTestCase(source.getTestCase());
        view.setPositive(source.isPositive());
        view.setVisibility(source.getVisibility());
        double credits = Boolean.TRUE.equals(source.isPositive()) && source.getTestCase() != null ? pointsByTestCaseId.getOrDefault(source.getTestCase().getId(), 0.0) : 0.0;
        view.setCredits(credits);
        // setDetailText implements the legacy preview contract: short messages inline, long messages as a
        // 300-char preview with hasLongFeedbackText = true (fetched separately via the synthetic id)
        view.setDetailText(source.getMessageText());
        return view;
    }

    private Feedback synthesizeScaFeedback(ScaFeedback source, long resultId) {
        Feedback view = new Feedback();
        view.setId(syntheticScaId(source.getId()));
        view.setType(FeedbackType.AUTOMATIC);
        view.setPositive(false);
        view.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + (source.getCategory() == null ? "" : source.getCategory()));
        view.setReference(source.getTool() == null ? null : source.getTool().name());
        view.setCredits(source.getCredits());

        // the legacy JSON exposed the tool-reported category; migrated rows whose original JSON was
        // unparseable have no tool category and fall back to the Artemis category
        String issueCategory = source.getToolCategory() != null ? source.getToolCategory() : source.getCategory();
        try {
            view.setDetailTextTruncated(serializeScaIssueWithinFeedbackLimit(source, issueCategory));
        }
        catch (JacksonException e) {
            log.warn("Could not serialize SCA issue {} of result {} for the client", source.getId(), resultId, e);
            view.setDetailTextTruncated(source.getMessageText());
        }
        return view;
    }

    /**
     * Keeps the legacy SCA detail-text contract (a valid JSON object within the feedback column limit). Escaping can
     * make a message much longer in JSON than its raw character count — truncating the serialized JSON would then
     * cut it in the middle of a string and make every client-side {@code JSON.parse} fail. Find the longest message
     * prefix whose complete serialized issue still fits instead.
     */
    private String serializeScaIssueWithinFeedbackLimit(ScaFeedback source, String issueCategory) {
        String message = source.getMessageText();
        StaticCodeAnalysisIssue issue = createScaIssue(source, issueCategory, message, true);
        String serialized = objectMapper.writeValueAsString(issue);
        if (serialized.length() <= Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH) {
            return serialized;
        }

        // The writer caps every metadata field, so normally only the message can make the JSON overflow. Keep a
        // defensive fallback for migrated/pathological data whose escaped metadata exceeds the limit on its own:
        // a valid message-only issue is more useful than an invalid JSON fragment that breaks result rendering.
        boolean includeMetadata = objectMapper.writeValueAsString(createScaIssue(source, issueCategory, null, true)).length() <= Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH;
        if (!includeMetadata) {
            log.warn("The metadata of SCA issue {} exceeds the feedback detail-text limit; serializing the message and penalty only", source.getId());
        }

        if (message == null || message.isEmpty()) {
            return objectMapper.writeValueAsString(createScaIssue(source, issueCategory, null, includeMetadata));
        }

        int lowerBound = 0;
        int upperBound = message.codePointCount(0, message.length());
        String bestFit = objectMapper.writeValueAsString(createScaIssue(source, issueCategory, null, includeMetadata));
        while (lowerBound <= upperBound) {
            int candidateCodePoints = lowerBound + (upperBound - lowerBound) / 2;
            int candidateEndIndex = message.offsetByCodePoints(0, candidateCodePoints);
            String candidate = objectMapper.writeValueAsString(createScaIssue(source, issueCategory, message.substring(0, candidateEndIndex), includeMetadata));
            if (candidate.length() <= Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH) {
                bestFit = candidate;
                lowerBound = candidateCodePoints + 1;
            }
            else {
                upperBound = candidateCodePoints - 1;
            }
        }
        return bestFit;
    }

    private static StaticCodeAnalysisIssue createScaIssue(ScaFeedback source, String issueCategory, String message, boolean includeMetadata) {
        return new StaticCodeAnalysisIssue(includeMetadata ? source.getFilePath() : null, includeMetadata ? source.getStartLine() : null,
                includeMetadata ? source.getEndLine() : null, includeMetadata ? source.getStartColumn() : null, includeMetadata ? source.getEndColumn() : null,
                includeMetadata ? source.getRule() : null, includeMetadata ? issueCategory : null, message, includeMetadata ? source.getPriority() : null, source.getPenalty());
    }
}
