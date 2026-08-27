package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * <b>Synthetic ids:</b> the views carry a NEGATIVE id encoding {@code (resultId, seq)} — see
 * {@link #syntheticId(long, int)}. The client uses feedback ids only as list keys and for fetching long
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
     * Factor encoding {@code (resultId, seq)} into one synthetic id; supports up to 100&nbsp;000 feedback
     * items per result (the sequence is a SMALLINT, so the real bound is far lower). SCA views additionally
     * offset the seq part by {@link Constants#SYNTHETIC_SCA_FEEDBACK_SEQ_OFFSET} so that a test view and an
     * SCA view with the same sequence number never share an id.
     */
    public static final long SYNTHETIC_ID_FACTOR = Constants.SYNTHETIC_FEEDBACK_ID_FACTOR;

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    private final TestCasePointsService testCasePointsService;

    public ProgrammingFeedbackSynthesizerService(TestCaseFeedbackRepository testCaseFeedbackRepository, ScaFeedbackRepository scaFeedbackRepository,
            TestCasePointsService testCasePointsService) {
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
        this.testCasePointsService = testCasePointsService;
    }

    public static long syntheticId(long resultId, int seq) {
        return -(resultId * SYNTHETIC_ID_FACTOR + seq);
    }

    /**
     * Synthetic id of an SCA view: like {@link #syntheticId(long, int)}, but with the seq part offset by
     * {@link Constants#SYNTHETIC_SCA_FEEDBACK_SEQ_OFFSET} so it cannot collide with a test view of the same
     * result (the two row types allocate their sequence numbers independently).
     *
     * @param resultId the id of the result the SCA row belongs to
     * @param seq      the sequence number of the SCA row
     * @return the synthetic (negative) id of the SCA view
     */
    public static long syntheticScaId(long resultId, int seq) {
        return -(resultId * SYNTHETIC_ID_FACTOR + Constants.SYNTHETIC_SCA_FEEDBACK_SEQ_OFFSET + seq);
    }

    public static boolean isSyntheticId(long feedbackId) {
        return feedbackId < 0;
    }

    public static long resultIdFromSyntheticId(long syntheticId) {
        return (-syntheticId) / SYNTHETIC_ID_FACTOR;
    }

    /**
     * Decodes the seq part of a synthetic id. For SCA ids the returned value still carries the
     * {@link Constants#SYNTHETIC_SCA_FEEDBACK_SEQ_OFFSET}, so a lookup in the test-case feedback table
     * (the only consumer - SCA views never have long feedback) finds no row and correctly yields a 404.
     *
     * @param syntheticId the synthetic (negative) feedback id
     * @return the encoded seq part
     */
    public static int seqFromSyntheticId(long syntheticId) {
        return (int) ((-syntheticId) % SYNTHETIC_ID_FACTOR);
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
        Map<Long, List<TestCaseFeedback>> loadedTestCaseFeedback = idsToLoad.isEmpty() ? Map.of()
                : testCaseFeedbackRepository.findWithTestCaseAndMessageByResultIds(idsToLoad).stream().collect(Collectors.groupingBy(feedback -> feedback.getId().getResultId()));
        Map<Long, List<ScaFeedback>> loadedScaFeedback = idsToLoad.isEmpty() ? Map.of()
                : scaFeedbackRepository.findWithMessageByResultIds(idsToLoad).stream().collect(Collectors.groupingBy(feedback -> feedback.getId().getResultId()));

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
        var testCaseFeedbackByResult = testCaseFeedbackRepository.findWithTestCaseByResultIds(resultIds).stream()
                .collect(Collectors.groupingBy(feedback -> feedback.getId().getResultId()));
        var scaFeedbackByResult = scaFeedbackRepository.findByResultIds(resultIds).stream().collect(Collectors.groupingBy(feedback -> feedback.getId().getResultId()));
        for (Result result : results) {
            result.setTestCaseFeedbacks(testCaseFeedbackByResult.getOrDefault(result.getId(), List.of()));
            result.setScaFeedbacks(scaFeedbackByResult.getOrDefault(result.getId(), List.of()));
        }
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
        view.setId(syntheticId(resultId, source.getSeq()));
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
        view.setId(syntheticScaId(resultId, source.getSeq()));
        view.setType(FeedbackType.AUTOMATIC);
        view.setPositive(false);
        view.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + (source.getCategory() == null ? "" : source.getCategory()));
        view.setReference(source.getTool() == null ? null : source.getTool().name());
        view.setCredits(source.getCredits());

        // the legacy JSON exposed the tool-reported category; migrated rows whose original JSON was
        // unparseable have no tool category and fall back to the Artemis category
        String issueCategory = source.getToolCategory() != null ? source.getToolCategory() : source.getCategory();
        StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(source.getFilePath(), source.getStartLine(), source.getEndLine(), source.getStartColumn(),
                source.getEndColumn(), source.getRule(), issueCategory, source.getMessageText(), source.getPriority(), source.getPenalty());
        try {
            view.setDetailTextTruncated(objectMapper.writeValueAsString(issue));
        }
        catch (JsonProcessingException e) {
            log.warn("Could not serialize SCA issue of result {} seq {} for the client", resultId, source.getSeq(), e);
            view.setDetailTextTruncated(source.getMessageText());
        }
        return view;
    }
}
