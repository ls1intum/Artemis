package de.tum.cit.aet.artemis.atlas.service;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Static utilities shared by every orchestrator tool service ({@link OrchestratorReadToolsService},
 * {@link OrchestratorPlanningToolsService}, {@link CreatorToolsService}, {@link EditorToolsService},
 * {@link AssignerToolsService}). Centralises {@link ToolContext} access, the per-run write-quota
 * reservation, JSON serialisation fallbacks, taxonomy parsing, course-membership checks, and the
 * shared input validation so the tool bodies stay focused on the mutation they actually perform.
 * <p>
 * All methods are null-safe and never throw — an unexpected state returns an error JSON payload that
 * the tool body returns verbatim to the LLM, satisfying the rule that {@code @Tool} methods never
 * propagate exceptions.
 */
public final class OrchestratorToolHelpers {

    /**
     * Allowed weight bands for {@code assignExerciseToCompetency}. The system prompt forbids any
     * other value; enforced here too because LLMs occasionally output {@code 0.7} or
     * {@code 0.30000001}. Comparison uses a small epsilon to absorb float-formatting drift.
     */
    static final Set<Double> ALLOWED_WEIGHTS = Set.of(1.0, 0.5, 0.3);

    /**
     * Match tolerance for {@link #ALLOWED_WEIGHTS}. The bands are at least 0.2 apart, so even a
     * generous tolerance cannot blur two bands together; sized to absorb realistic LLM rounding
     * (e.g. {@code 0.30000001}) without admitting actual drift like {@code 0.7}.
     */
    static final double WEIGHT_TOLERANCE = 1e-6;

    /**
     * Soft cap on the LLM-supplied justification text shown verbatim in the audit log. One full
     * sentence rarely exceeds this; longer values are almost always model hallucination or
     * copy-pasted prompt text and would bloat the per-action JSON returned to the UI.
     */
    static final int MAX_JUSTIFICATION_LENGTH = 500;

    /**
     * Hard cap on competency title length for LLM-driven create/edit. Matches Hibernate's default
     * {@code varchar(255)} for unannotated String columns on {@code BaseCompetency.title}.
     */
    static final int MAX_TITLE_LENGTH = 255;

    /**
     * Hard cap on competency description length for LLM-driven create/edit. The {@code description}
     * column is unbounded {@code text}; this cap prevents a hallucinating or prompt-injected model
     * from bloating the per-action JSON and the underlying row.
     */
    static final int MAX_DESCRIPTION_LENGTH = 10_000;

    private OrchestratorToolHelpers() {
    }

    /**
     * Extract the course id stashed by {@link CompetencyOrchestrationService} on the tool context.
     *
     * @param toolContext the Spring AI tool context (may be {@code null})
     * @return the course id, or {@code null} if the context is missing or the entry is not numeric
     */
    @Nullable
    static Long courseIdFromContext(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(OrchestratorToolContextKeys.COURSE_ID_KEY);
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * Resolve the per-run {@link OrchestratorToolContextKeys.AppliedActionsBuffer} from the tool context.
     *
     * @param toolContext the Spring AI tool context (may be {@code null})
     * @return the buffer, or {@code null} when absent
     */
    static OrchestratorToolContextKeys.@Nullable AppliedActionsBuffer appliedActionsBufferFromContext(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(OrchestratorToolContextKeys.APPLIED_ACTIONS_KEY);
        return value instanceof OrchestratorToolContextKeys.AppliedActionsBuffer buffer ? buffer : null;
    }

    /**
     * Append a successful-mutation audit entry to the per-run buffer on the tool context. The list
     * inside the buffer is a synchronized list so concurrent tool callbacks cannot race. A missing
     * buffer silently no-ops so the LLM tool never sees an exception.
     *
     * @param toolContext the Spring AI tool context
     * @param action      the audit entry to append
     */
    static void appendAction(@Nullable ToolContext toolContext, AppliedActionDTO action) {
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = appliedActionsBufferFromContext(toolContext);
        if (buffer != null) {
            buffer.actions().add(action);
        }
    }

    /**
     * Atomically reserve one slot against {@link OrchestratorToolContextKeys#MAX_WRITE_CALLS} for
     * this run. Reservation happens before any persistence or external notification so a
     * hallucinating model cannot exceed the cap even under parallel tool-call execution.
     *
     * @param toolContext the Spring AI tool context
     * @return {@code true} if a slot was reserved (or no buffer is present), {@code false} once the cap is reached
     */
    static boolean tryReserveWriteSlot(@Nullable ToolContext toolContext) {
        OrchestratorToolContextKeys.AppliedActionsBuffer buffer = appliedActionsBufferFromContext(toolContext);
        return buffer == null || buffer.tryReserveSlot(OrchestratorToolContextKeys.MAX_WRITE_CALLS);
    }

    /**
     * True if this competency belongs to the expected course (checked before every write so a
     * malicious LLM cannot mutate competencies in another course via a valid id).
     *
     * @param competency the competency to check
     * @param courseId   the expected course id
     * @return {@code true} if the competency belongs to the course
     */
    static boolean belongsToCourse(CourseCompetency competency, long courseId) {
        return competency.getCourse() != null && Objects.equals(courseId, competency.getCourse().getId());
    }

    /**
     * Analogue of {@link #belongsToCourse(CourseCompetency, long)} for exercises. Rejects exam
     * exercises (defense-in-depth) so a tool call cannot walk the lazy {@code exam.course} chain
     * outside a transaction.
     *
     * @param exercise the exercise to check
     * @param courseId the expected course id
     * @return {@code true} if the exercise is a non-exam exercise owned by the course
     */
    static boolean exerciseBelongsToCourse(Exercise exercise, long courseId) {
        if (exercise.isExamExercise()) {
            return false;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course != null && Objects.equals(courseId, course.getId());
    }

    /**
     * Resolve the display type of an exercise, falling back to its simple class name.
     *
     * @param exercise the exercise
     * @return the type label
     */
    static String exerciseType(Exercise exercise) {
        return exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName();
    }

    /**
     * Match {@code weight} to one of the {@link #ALLOWED_WEIGHTS} bands within
     * {@link #WEIGHT_TOLERANCE}. Returns the canonical band value (so callers persist exactly
     * {@code 1.0} / {@code 0.5} / {@code 0.3}, not {@code 0.30000001}) or {@code null} when the
     * value falls outside every band.
     *
     * @param weight the raw weight supplied by the model
     * @return the canonical band, or {@code null} when outside every band
     */
    @Nullable
    static Double matchAllowedBand(double weight) {
        for (Double band : ALLOWED_WEIGHTS) {
            if (Math.abs(weight - band) < WEIGHT_TOLERANCE) {
                return band;
            }
        }
        return null;
    }

    /**
     * Parse a Bloom taxonomy level case-insensitively.
     *
     * @param taxonomy the taxonomy string
     * @return the parsed {@link CompetencyTaxonomy}
     * @throws IllegalArgumentException if the value is not a valid taxonomy level
     */
    static CompetencyTaxonomy parseTaxonomyOrThrow(String taxonomy) {
        try {
            return CompetencyTaxonomy.valueOf(taxonomy.trim().toUpperCase(Locale.ROOT));
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("taxonomy must be one of REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE.");
        }
    }

    /**
     * Null-safe blank check.
     *
     * @param value the string to test (may be {@code null})
     * @return {@code true} if {@code null} or blank
     */
    static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * Format a weight with two decimals for human-readable audit detail strings.
     *
     * @param weight the weight
     * @return the formatted weight
     */
    static String formatWeight(double weight) {
        return String.format(Locale.ROOT, "%.2f", weight);
    }

    /**
     * Validate the LLM-supplied audit-log justification. Returns {@code null} on success or a
     * pre-serialized JSON error to return to the model. Centralized so all five write tools share
     * the exact same wording and cap.
     *
     * @param objectMapper  the mapper used to serialise the error payload
     * @param justification the justification supplied by the model
     * @return a JSON error string, or {@code null} when valid
     */
    @Nullable
    static String validateJustification(ObjectMapper objectMapper, @Nullable String justification) {
        if (isBlank(justification)) {
            return errorJson(objectMapper, "justification is required.");
        }
        if (justification.length() > MAX_JUSTIFICATION_LENGTH) {
            return errorJson(objectMapper, "justification must be at most " + MAX_JUSTIFICATION_LENGTH + " characters.");
        }
        return null;
    }

    /**
     * Pre-serialized error returned when the write-quota cap is reached.
     *
     * @param objectMapper the mapper used to serialise the payload
     * @return the JSON error string
     */
    static String writeQuotaError(ObjectMapper objectMapper) {
        return errorJson(objectMapper, "Write tool call cap (" + OrchestratorToolContextKeys.MAX_WRITE_CALLS + ") reached for this run; finalize and return.");
    }

    /**
     * Pre-serialized error returned when no course context is available.
     *
     * @param objectMapper the mapper used to serialise the payload
     * @return the JSON error string
     */
    static String missingCourseContextError(ObjectMapper objectMapper) {
        return errorJson(objectMapper, "No course context available for this tool call.");
    }

    /**
     * Serialise a single-field error payload to JSON.
     *
     * @param objectMapper the mapper
     * @param message      the error message
     * @return the JSON error string
     */
    static String errorJson(ObjectMapper objectMapper, String message) {
        return toJson(objectMapper, Map.of("error", message));
    }

    /**
     * Serialise an object to JSON, falling back to a fixed error payload if serialisation fails.
     *
     * @param objectMapper the mapper
     * @param object       the object to serialise
     * @return the JSON string
     */
    static String toJson(ObjectMapper objectMapper, Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException ex) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
