package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.belongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.courseIdFromContext;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.exerciseBelongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.exerciseType;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.missingCourseContextError;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

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
    @Tool(description = "Extract the learning-relevant content (title, problem statement text, and metadata) for an exercise that belongs to the current course. "
            + "Only programming exercises are text-extractable; for quiz, text, modeling, or file-upload exercises this returns a stub with the title and type "
            + "(no problem statement) — judge their fit by title alone in that case, don't call this tool repeatedly for the same non-programming id.")
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
        if (!(exercise instanceof ProgrammingExercise)) {
            String title = Objects.requireNonNullElse(exercise.getTitle(), "");
            return toJson(objectMapper, Map.of("id", exerciseId, "title", title, "type", exerciseType(exercise), "textExtractable", false, "note",
                    "Content extraction is only available for programming exercises. Use the title and type to decide fit."));
        }
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            return toJson(objectMapper, extracted);
        }
        catch (RuntimeException ex) {
            // Generic message — raw exception text could leak Hibernate/SQL detail into the LLM's summary.
            log.warn("getExerciseContent failed for exercise {}: {}", exerciseId, ex.getMessage(), ex);
            return errorJson(objectMapper, "Failed to extract content for exercise " + exerciseId + ".");
        }
    }

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
