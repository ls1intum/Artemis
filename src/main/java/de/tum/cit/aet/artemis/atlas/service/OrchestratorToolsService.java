package de.tum.cit.aet.artemis.atlas.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyDetailDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Read-only tools exposed to the Atlas orchestrator LLM. The current course id is injected via
 * Spring AI's {@link ToolContext} and stripped from the JSON schema, so the model cannot forge it.
 * Mutation tools are deferred to a follow-up PR.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class OrchestratorToolsService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorToolsService.class);

    /** Tool-context key carrying the current course id; package-private so only the orchestrator service can populate it. */
    static final String COURSE_ID_KEY = "courseId";

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final ExerciseRepository exerciseRepository;

    private final ContentExtractionService contentExtractionService;

    public OrchestratorToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository, ExerciseRepository exerciseRepository,
            ContentExtractionService contentExtractionService) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.exerciseRepository = exerciseRepository;
        this.contentExtractionService = contentExtractionService;
    }

    /**
     * LLM tool: returns the competency index for the current course as JSON.
     *
     * @param toolContext carries the current course id
     * @return the JSON-serialized index
     */
    @Tool(description = "List the competency index for the current course. Returns two sections: (1) competencies — id, title, taxonomy, type (competency or prerequisite), "
            + "linked exercises (with title, exercise type, and the current link weight — 1.0 / 0.5 / 0.3) and linked lecture units (with name and lecture-unit type); "
            + "(2) unassignedExercises — exercises in the course that are currently not linked to any competency (id, title, type), which are prime candidates for closing coverage gaps.")
    public String listCompetencyIndex(ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        return toJson(listCompetencyIndex(courseId));
    }

    /**
     * Builds the competency index so callers can seed the system prompt and save a tool-call round-trip.
     *
     * @param courseId the course
     * @return the index
     */
    public CompetencyIndexResponseDTO listCompetencyIndex(long courseId) {
        Set<CourseCompetency> competencies = courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(courseId);
        List<CompetencyIndexDTO> entries = competencies.stream().map(OrchestratorToolsService::toIndexEntry).sorted(Comparator.comparing(CompetencyIndexDTO::id)).toList();
        Set<Long> linkedExerciseIds = competencies.stream().flatMap(c -> c.getExerciseLinks().stream()).map(CompetencyExerciseLink::getExercise).map(Exercise::getId)
                .collect(Collectors.toSet());
        // findAllExercisesByCourseId already filters by `e.course.id`, so exam exercises are excluded.
        List<CompetencyIndexResponseDTO.UnassignedExerciseRefDTO> unassigned = exerciseRepository.findAllExercisesByCourseId(courseId).stream()
                .filter(exercise -> !linkedExerciseIds.contains(exercise.getId()))
                .map(exercise -> new CompetencyIndexResponseDTO.UnassignedExerciseRefDTO(exercise.getId(), exercise.getTitle(), exerciseType(exercise)))
                .sorted(Comparator.comparing(CompetencyIndexResponseDTO.UnassignedExerciseRefDTO::id)).toList();
        return new CompetencyIndexResponseDTO(entries, unassigned);
    }

    /**
     * LLM tool: returns full details for a single competency in the current course as JSON.
     *
     * @param competencyId id to inspect
     * @param toolContext  carries the current course id
     * @return the JSON-serialized details
     */
    @Tool(description = "Get the full details (description, soft due date, mastery threshold, optional flag, and linked exercises/lecture units with their ids and types; "
            + "each exercise ref also carries its current link weight — 1.0 / 0.5 / 0.3) for a single competency in the current course.")
    public String getCompetencyDetails(@ToolParam(description = "id of the competency to inspect") Long competencyId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (competencyId == null) {
            return errorJson("competencyId is required.");
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson("Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson("Competency " + competencyId + " does not belong to the current course.");
        }
        return toJson(toDetail(competency));
    }

    /**
     * LLM tool: extracts learning-relevant content for an exercise in the current course as JSON.
     *
     * @param exerciseId  id to extract
     * @param toolContext carries the current course id
     * @return the JSON-serialized content
     */
    @Tool(description = "Extract the learning-relevant content (title, problem statement text, and metadata) for an exercise that belongs to the current course. "
            + "Only programming exercises are text-extractable; for quiz, text, modeling, or file-upload exercises this returns a stub with the title and type "
            + "(no problem statement) — judge their fit by title alone in that case, don't call this tool repeatedly for the same non-programming id.")
    public String getExerciseContent(@ToolParam(description = "id of the exercise whose content should be extracted") Long exerciseId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (exerciseId == null) {
            return errorJson("exerciseId is required.");
        }
        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException ex) {
            return errorJson("Exercise not found: " + exerciseId);
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return errorJson("Exercise " + exerciseId + " does not belong to the current course.");
        }
        if (!(exercise instanceof ProgrammingExercise)) {
            String title = Objects.requireNonNullElse(exercise.getTitle(), "");
            return toJson(Map.of("id", exerciseId, "title", title, "type", exerciseType(exercise), "textExtractable", false, "note",
                    "Content extraction is only available for programming exercises. Use the title and type to decide fit."));
        }
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            return toJson(extracted);
        }
        catch (RuntimeException ex) {
            // Generic message — raw exception text could leak Hibernate/SQL detail into the LLM's summary.
            log.warn("getExerciseContent failed for exercise {}: {}", exerciseId, ex.getMessage(), ex);
            return errorJson("Failed to extract content for exercise " + exerciseId + ".");
        }
    }

    private static CompetencyIndexDTO toIndexEntry(CourseCompetency competency) {
        List<CompetencyIndexDTO.ExerciseLinkRefDTO> exercises = competency.getExerciseLinks().stream()
                .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId()))
                .map(link -> new CompetencyIndexDTO.ExerciseLinkRefDTO(link.getExercise().getTitle(), exerciseType(link.getExercise()), link.getWeight())).toList();
        List<CompetencyIndexDTO.LectureUnitRefDTO> lectureUnits = competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit)
                .filter(lu -> lu.getName() != null).sorted(Comparator.comparing(lu -> lu.getName())).map(lu -> new CompetencyIndexDTO.LectureUnitRefDTO(lu.getName(), lu.getType()))
                .toList();
        return new CompetencyIndexDTO(competency.getId(), competency.getTitle(), competency.getTaxonomy(), competency.getType(), exercises, lectureUnits);
    }

    private static CompetencyDetailDTO toDetail(CourseCompetency competency) {
        List<CompetencyDetailDTO.ExerciseRefDTO> exercises = competency.getExerciseLinks().stream()
                .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId())).map(link -> {
                    Exercise exercise = link.getExercise();
                    return new CompetencyDetailDTO.ExerciseRefDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), link.getWeight());
                }).toList();
        // Mirror toIndexEntry's null-name filter so both tools expose the same lecture-unit set.
        List<CompetencyDetailDTO.LectureUnitRefDTO> lectureUnits = competency.getLectureUnitLinks().stream().filter(link -> link.getLectureUnit().getName() != null)
                .sorted(Comparator.comparing((CompetencyLectureUnitLink link) -> link.getLectureUnit().getId()))
                .map(link -> new CompetencyDetailDTO.LectureUnitRefDTO(link.getLectureUnit().getId(), link.getLectureUnit().getName(), link.getLectureUnit().getType())).toList();
        return new CompetencyDetailDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getType(),
                competency.getSoftDueDate(), competency.getMasteryThreshold(), competency.isOptional(), exercises, lectureUnits);
    }

    private static String exerciseType(Exercise exercise) {
        return exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName();
    }

    private static boolean belongsToCourse(CourseCompetency competency, long courseId) {
        return competency.getCourse() != null && Objects.equals(courseId, competency.getCourse().getId());
    }

    /** Defense-in-depth: rejects exam exercises so a tool call cannot walk the lazy exam.course chain outside a transaction. */
    private static boolean exerciseBelongsToCourse(Exercise exercise, long courseId) {
        if (exercise.isExamExercise()) {
            return false;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course != null && Objects.equals(courseId, course.getId());
    }

    private String missingCourseContextError() {
        return errorJson("No course context available for this tool call.");
    }

    private String errorJson(String message) {
        return toJson(Map.of("error", message));
    }

    private static @Nullable Long courseIdFromContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(COURSE_ID_KEY);
        return value instanceof Number number ? number.longValue() : null;
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
