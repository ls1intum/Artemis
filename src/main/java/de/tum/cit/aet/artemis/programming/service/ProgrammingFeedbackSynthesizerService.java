package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;

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
     * items per result (the sequence is a SMALLINT, so the real bound is far lower).
     */
    public static final long SYNTHETIC_ID_FACTOR = 100_000L;

    private static final ObjectMapper objectMapper = JsonObjectMapper.get();

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    public ProgrammingFeedbackSynthesizerService(TestCaseFeedbackRepository testCaseFeedbackRepository, ScaFeedbackRepository scaFeedbackRepository,
            @Lazy ProgrammingExerciseGradingService programmingExerciseGradingService) {
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
        this.programmingExerciseGradingService = programmingExerciseGradingService;
    }

    public static long syntheticId(long resultId, int seq) {
        return -(resultId * SYNTHETIC_ID_FACTOR + seq);
    }

    public static boolean isSyntheticId(long feedbackId) {
        return feedbackId < 0;
    }

    public static long resultIdFromSyntheticId(long syntheticId) {
        return (-syntheticId) / SYNTHETIC_ID_FACTOR;
    }

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
        if (result.getId() == null || result.getSubmission() == null || result.getSubmission().getParticipation() == null
                || !(result.getSubmission().getParticipation().getExercise() instanceof ProgrammingExercise programmingExercise)) {
            return;
        }

        List<TestCaseFeedback> testCaseFeedbacks = Hibernate.isInitialized(result.getTestCaseFeedbacks()) && !result.getTestCaseFeedbacks().isEmpty()
                ? List.copyOf(result.getTestCaseFeedbacks())
                : testCaseFeedbackRepository.findWithTestCaseAndMessageByResultId(result.getId());
        List<ScaFeedback> scaFeedbacks = Hibernate.isInitialized(result.getScaFeedbacks()) && !result.getScaFeedbacks().isEmpty() ? List.copyOf(result.getScaFeedbacks())
                : scaFeedbackRepository.findWithMessageByResultId(result.getId());

        if (testCaseFeedbacks.isEmpty() && scaFeedbacks.isEmpty()) {
            return;
        }

        Map<Long, Double> pointsByTestCaseId = testCaseFeedbacks.isEmpty() ? Map.of() : programmingExerciseGradingService.calculateTestCasePoints(programmingExercise, result);

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
        view.setId(syntheticId(resultId, source.getSeq()));
        view.setType(FeedbackType.AUTOMATIC);
        view.setPositive(false);
        view.setText(Feedback.STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + (source.getCategory() == null ? "" : source.getCategory()));
        view.setReference(source.getTool() == null ? null : source.getTool().name());
        view.setCredits(source.getCredits());

        StaticCodeAnalysisIssue issue = new StaticCodeAnalysisIssue(source.getFilePath(), source.getStartLine(), source.getEndLine(), source.getStartColumn(),
                source.getEndColumn(), source.getRule(), source.getCategory(), source.getMessageText(), source.getPriority(), source.getPenalty());
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
