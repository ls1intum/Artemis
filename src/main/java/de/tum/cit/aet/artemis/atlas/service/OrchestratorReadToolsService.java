package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.belongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.courseIdFromContext;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.exerciseBelongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.missingCourseContextError;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyDetailDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Read-only orchestrator tools that let the LLM inspect a single competency or an exercise's content.
 * Split from the former monolithic orchestrator tools service so the read surface is registered as
 * its own {@link org.springframework.ai.tool.ToolCallbackProvider} bean, separate from the
 * batch-planning read ({@link OrchestratorPlanningToolsService}) and the write tools.
 * <p>
 * Both tools are course-scoped through the Spring AI {@link ToolContext}: the LLM cannot forge the
 * current course id because the context parameter is stripped from the JSON schema Spring AI exposes.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class OrchestratorReadToolsService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorReadToolsService.class);

    /**
     * Hard cap on the learning text returned by {@link #getExerciseContent}. Mirrors the
     * {@code PROBLEM_STATEMENT_MAX} the batch path applies via {@code sanitizeForPrompt}, so a single
     * oversized exercise (e.g. a quiz whose assembled questions + answers are large) cannot inflate
     * per-call tokens now that this tool extracts real content for every exercise type.
     */
    private static final int MAX_EXERCISE_CONTENT_LENGTH = 8_000;

    /** Cap on the title returned by {@link #getExerciseContent}; matches the batch path's {@code EXERCISE_TITLE_MAX}. */
    private static final int MAX_EXERCISE_TITLE_LENGTH = 200;

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final ExerciseRepository exerciseRepository;

    private final ContentExtractionService contentExtractionService;

    /**
     * Creates the read tools service.
     *
     * @param objectMapper               JSON serialiser for tool responses
     * @param courseCompetencyRepository repository for competency lookups
     * @param exerciseRepository         repository for exercise lookups
     * @param contentExtractionService   service extracting learning-relevant exercise content
     */
    public OrchestratorReadToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository, ExerciseRepository exerciseRepository,
            ContentExtractionService contentExtractionService) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.exerciseRepository = exerciseRepository;
        this.contentExtractionService = contentExtractionService;
    }

    /**
     * LLM tool: returns full details for a single competency in the current course as JSON.
     *
     * @param competencyId id to inspect
     * @param toolContext  carries the current course id
     * @return the JSON-serialized details, or a JSON error
     */
    @Tool(description = "Get the full details (description, soft due date, mastery threshold, optional flag, and linked exercises/lecture units with their ids and types; "
            + "each exercise ref also carries its current link weight — 1.0 / 0.5 / 0.3) for a single competency in the current course.")
    public String getCompetencyDetails(@ToolParam(description = "id of the competency to inspect") Long competencyId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError(objectMapper);
        }
        if (competencyId == null) {
            return errorJson(objectMapper, "competencyId is required.");
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson(objectMapper, "Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson(objectMapper, "Competency " + competencyId + " does not belong to the current course.");
        }
        return toJson(objectMapper, toDetail(competency));
    }

    /**
     * LLM tool: extracts learning-relevant content for an exercise in the current course as JSON.
     *
     * @param exerciseId  id to extract
     * @param toolContext carries the current course id
     * @return the JSON-serialized content, or a JSON error
     */
    @Tool(description = "Extract the learning-relevant content for an exercise that belongs to the current course. Returns a title, the learning text, and metadata. "
            + "For programming, text, modeling and file-upload exercises the learning text is the problem statement (plus example solution where available); for quizzes "
            + "it is the assembled questions with their correct answers/solutions. Metadata always carries the exercise type and, when set, difficulty / maxPoints "
            + "(plus type-specific keys such as questionCount for quizzes). The content is extracted fresh on every call, so don't call this tool repeatedly for the same exercise id.")
    public String getExerciseContent(@ToolParam(description = "id of the exercise whose content should be extracted") Long exerciseId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError(objectMapper);
        }
        if (exerciseId == null) {
            return errorJson(objectMapper, "exerciseId is required.");
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
        try {
            // Skip the LLM flavor-strip on this read path: it costs an extra model round-trip per call, so a repeated
            // lookup would burn tokens on the strip model. The raw problem statement is complete enough for the
            // orchestrator to judge fit; the batch's system prompt already carries the stripped versions.
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise, false);
            // Neutralize prompt-injection fences and cap length before this instructor-authored content re-enters the
            // model as a tool result — the same hardening the batch path applies via CompetencyOrchestrationService.sanitizeForPrompt.
            String safeTitle = CompetencyOrchestrationService.sanitizeForPrompt(extracted.title(), MAX_EXERCISE_TITLE_LENGTH);
            String safeText = CompetencyOrchestrationService.sanitizeForPrompt(extracted.extractedLearningText(), MAX_EXERCISE_CONTENT_LENGTH);
            return toJson(objectMapper, new ExtractedContentDTO(safeTitle, safeText, extracted.metadata()));
        }
        catch (RuntimeException ex) {
            // Generic message — raw exception text could leak Hibernate/SQL detail into the LLM's summary.
            log.warn("getExerciseContent failed for exercise {}: {}", exerciseId, ex.getMessage(), ex);
            return errorJson(objectMapper, "Failed to extract content for exercise " + exerciseId + ".");
        }
    }

    /**
     * Projects a competency onto its detail view. Exercises and lecture units are ordered by id so repeated
     * inspections of an unchanged competency yield an identical tool response, and lecture units without a name are
     * dropped to match the competency index built by {@link OrchestratorPlanningToolsService}.
     *
     * @param competency the competency to project, with exercise and lecture-unit links already fetched
     * @return the detail view for this competency
     */
    private static CompetencyDetailDTO toDetail(CourseCompetency competency) {
        List<CompetencyDetailDTO.ExerciseRefDTO> exercises = competency.getExerciseLinks().stream()
                .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId())).map(link -> {
                    Exercise exercise = link.getExercise();
                    return new CompetencyDetailDTO.ExerciseRefDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), link.getWeight());
                }).toList();
        // Mirror the index's null-name filter so both tools expose the same lecture-unit set.
        List<CompetencyDetailDTO.LectureUnitRefDTO> lectureUnits = competency.getLectureUnitLinks().stream().filter(link -> link.getLectureUnit().getName() != null)
                .sorted(Comparator.comparing((CompetencyLectureUnitLink link) -> link.getLectureUnit().getId()))
                .map(link -> new CompetencyDetailDTO.LectureUnitRefDTO(link.getLectureUnit().getId(), link.getLectureUnit().getName(), link.getLectureUnit().getType())).toList();
        return new CompetencyDetailDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getType(),
                competency.getSoftDueDate(), competency.getMasteryThreshold(), competency.isOptional(), exercises, lectureUnits);
    }
}
