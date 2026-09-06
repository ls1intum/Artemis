package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.appendAction;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.belongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.courseIdFromContext;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.exerciseBelongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.formatWeight;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.matchAllowedBand;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.missingCourseContextError;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.tryReserveWriteSlot;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.validateJustification;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.writeQuotaError;

import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Write orchestrator tools that link or unlink an exercise and a competency in the current course:
 * {@code assignExerciseToCompetency} creates or re-weights a {@link CompetencyExerciseLink} and
 * {@code unassignExerciseFromCompetency} removes one. Split from the former monolithic orchestrator
 * tools service so the assign/unassign surface is registered as its own
 * {@link org.springframework.ai.tool.ToolCallbackProvider} bean.
 * <p>
 * Both tools are course-scoped through the Spring AI {@link ToolContext}, share the per-run write
 * quota with every other write tool via the {@link OrchestratorToolContextKeys.AppliedActionsBuffer},
 * append an ASSIGN / UNASSIGN audit entry on success, and trigger an asynchronous competency-progress
 * recalculation for the affected exercise. Exam exercises are rejected defense-in-depth.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AssignerToolsService {

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final ExerciseRepository exerciseRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    /**
     * Creates the assigner tools service.
     *
     * @param objectMapper                     JSON serialiser for tool responses
     * @param courseCompetencyRepository       repository for competency lookups
     * @param exerciseRepository               repository for exercise lookups
     * @param competencyExerciseLinkRepository repository for competency-exercise links
     * @param competencyProgressApi            optional API triggering async progress recalculation
     */
    public AssignerToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository, ExerciseRepository exerciseRepository,
            CompetencyExerciseLinkRepository competencyExerciseLinkRepository, Optional<CompetencyProgressApi> competencyProgressApi) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.exerciseRepository = exerciseRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.competencyProgressApi = competencyProgressApi;
    }

    /**
     * LLM tool: creates or updates a CompetencyExerciseLink and appends an ASSIGN action.
     *
     * @param competencyId  id of the competency to link to
     * @param exerciseId    id of the exercise to link
     * @param weight        link weight in (0.0, 1.0]; must be picked from the weight bands (1.0 / 0.5 / 0.3)
     * @param justification one-sentence evidence-test outcome explaining why the chosen band is honest
     * @param toolContext   Spring AI tool context
     * @return JSON status on success, or a JSON error / noop
     */
    @Tool(description = "Link an exercise to a competency in the current course. Creates a new CompetencyExerciseLink, or updates its weight if the link already exists. "
            + "Weight is the strength of the evidence and must be picked from the bands defined in the system prompt: 1.0 (stand-alone), 0.5 (partial), 0.3 (incidental). "
            + "If the evidence would be below 0.3, do not call this tool.")
    public String assignExerciseToCompetency(@ToolParam(description = "id of the competency to link to") Long competencyId,
            @ToolParam(description = "id of the exercise to link") Long exerciseId,
            @ToolParam(description = "evidence-strength weight: 1.0 (stand-alone evidence), 0.5 (partial), 0.3 (incidental). Must be > 0 and <= 1.0") Double weight,
            @ToolParam(description = "one-sentence outcome of the evidence test naming the chosen band (1.0 / 0.5 / 0.3) and why it is honest") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError(objectMapper);
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError(objectMapper);
        }
        if (competencyId == null || exerciseId == null) {
            return errorJson(objectMapper, "competencyId and exerciseId are required.");
        }
        if (weight == null) {
            return errorJson(objectMapper, "weight is required: pick 1.0 (stand-alone), 0.5 (partial), or 0.3 (incidental).");
        }
        Double matchedBand = matchAllowedBand(weight);
        if (matchedBand == null) {
            return errorJson(objectMapper,
                    "weight must be one of 1.0 (stand-alone), 0.5 (partial), or 0.3 (incidental). If the evidence is too weak to link, do not call this tool.");
        }
        String justificationError = validateJustification(objectMapper, justification);
        if (justificationError != null) {
            return justificationError;
        }
        double effectiveWeight = matchedBand;

        // See editCompetency: scalar-only mutation, no fetch-join needed.
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson(objectMapper, "Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson(objectMapper, "Competency " + competencyId + " does not belong to the current course.");
        }

        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException ex) {
            return errorJson(objectMapper, "Exercise not found: " + exerciseId);
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return errorJson(objectMapper, "Exercise " + exerciseId + " does not belong to the current course.");
        }

        CompetencyExerciseLink existingLink = competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(exerciseId, competencyId).orElse(null);

        String detail;
        if (existingLink != null) {
            if (Double.compare(existingLink.getWeight(), effectiveWeight) == 0) {
                return toJson(objectMapper, Map.of("status", "noop", "message",
                        "Exercise " + exerciseId + " is already linked to competency " + competencyId + " with weight " + formatWeight(effectiveWeight) + "."));
            }
            existingLink.setWeight(effectiveWeight);
            competencyExerciseLinkRepository.save(existingLink);
            detail = "Updated weight to " + formatWeight(effectiveWeight) + " for exercise " + exercise.getTitle() + " on competency " + competency.getTitle() + ".";
        }
        else {
            CompetencyExerciseLink newLink = new CompetencyExerciseLink(competency, exercise, effectiveWeight);
            competencyExerciseLinkRepository.save(newLink);
            detail = "Linked exercise " + exercise.getTitle() + " to competency " + competency.getTitle() + " (weight " + formatWeight(effectiveWeight) + ").";
        }

        appendAction(toolContext, AppliedActionDTO.assign(competencyId, competency.getTitle(), exerciseId, effectiveWeight, detail, justification.trim()));
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        return toJson(objectMapper, Map.of("status", "ok", "competencyId", competencyId, "exerciseId", exerciseId, "weight", effectiveWeight));
    }

    /**
     * LLM tool: removes the link between an exercise and a competency and appends an UNASSIGN action.
     *
     * @param competencyId  id of the competency
     * @param exerciseId    id of the exercise
     * @param justification one-sentence reason stating why the current link fails the evidence test and which competency is the better home
     * @param toolContext   Spring AI tool context
     * @return JSON status on success or noop, or a JSON error
     */
    @Tool(description = "Remove the link between an exercise and a competency in the current course. Only call this when BOTH (a) passing the exercise would not credibly "
            + "demonstrate mastery of this competency (the link fails the evidence test) AND (b) a clearly better-fitting competency exists (or is being created in this run). "
            + "Idempotent — returns a noop status if no such link exists.")
    public String unassignExerciseFromCompetency(@ToolParam(description = "id of the competency") Long competencyId, @ToolParam(description = "id of the exercise") Long exerciseId,
            @ToolParam(description = "one-sentence reason stating (a) why the current link fails the evidence test and (b) which competency is the better home") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError(objectMapper);
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError(objectMapper);
        }
        if (competencyId == null || exerciseId == null) {
            return errorJson(objectMapper, "competencyId and exerciseId are required.");
        }
        String justificationError = validateJustification(objectMapper, justification);
        if (justificationError != null) {
            return justificationError;
        }
        // See editCompetency: scalar-only mutation, no fetch-join needed.
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson(objectMapper, "Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson(objectMapper, "Competency " + competencyId + " does not belong to the current course.");
        }

        CompetencyExerciseLink existingLink = competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(exerciseId, competencyId).orElse(null);
        if (existingLink == null) {
            return toJson(objectMapper, Map.of("status", "noop", "message", "Exercise " + exerciseId + " is not linked to competency " + competencyId + "."));
        }

        Exercise exercise = existingLink.getExercise();
        // Defense-in-depth: assignExerciseToCompetency already scopes both ends, but a stale or
        // out-of-band link could still exist; refuse to delete it here so an LLM cannot cross
        // course boundaries via a forged exerciseId.
        if (exercise == null || !exerciseBelongsToCourse(exercise, courseId)) {
            return errorJson(objectMapper, "Exercise " + exerciseId + " does not belong to the current course.");
        }
        competencyExerciseLinkRepository.delete(existingLink);
        String exerciseTitle = exercise.getTitle() != null ? exercise.getTitle() : "exercise " + exerciseId;
        String detail = "Removed link between exercise " + exerciseTitle + " and competency " + competency.getTitle() + ".";
        appendAction(toolContext, AppliedActionDTO.unassign(competencyId, competency.getTitle(), exerciseId, detail, justification.trim()));
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        return toJson(objectMapper, Map.of("status", "ok", "competencyId", competencyId, "exerciseId", exerciseId));
    }
}
