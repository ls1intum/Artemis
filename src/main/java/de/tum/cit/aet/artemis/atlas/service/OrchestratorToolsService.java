package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * Read-only tools exposed to the Atlas orchestrator LLM. The orchestrator is currently advisory:
 * it inspects course state via {@link #listCompetencyIndex(ToolContext)},
 * {@link #getCompetencyDetails(Long, ToolContext)}, and {@link #getExerciseContent(Long, ToolContext)},
 * then writes a natural-language summary. Mutation tools (create / edit / assign / unassign /
 * delete) are deferred to a follow-up PR.
 *
 * <p>
 * The current course id is injected via Spring AI's {@link ToolContext}; that parameter is
 * stripped from the JSON schema exposed to the LLM, so the model cannot forge it.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class OrchestratorToolsService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorToolsService.class);

    /**
     * Tool-context key carrying the course id the orchestrator is operating on. Package-private:
     * only {@link CompetencyOrchestrationService} (same package) populates the context, the LLM
     * cannot inject this key because tool-context parameters are stripped from the JSON schema
     * exposed to the model.
     */
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

    // -----------------------------------------------------------------------------------------------
    // Read tools
    // -----------------------------------------------------------------------------------------------

    /**
     * LLM tool: returns the competency index for the current course as JSON.
     *
     * @param toolContext Spring AI tool context carrying the current course id
     * @return JSON-serialized competency index, or a JSON error when course context is missing
     */
    @Tool(description = "List the competency index for the current course. Returns two sections: (1) competencies — id, title, taxonomy, type (competency or prerequisite), "
            + "linked exercises (with title and the current link weight — 1.0 / 0.5 / 0.3) and linked lecture-unit names; (2) unassignedExercises — exercises in the course "
            + "that are currently not linked to any competency (id, title, type), which are prime candidates for closing coverage gaps.")
    public String listCompetencyIndex(ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        return toJson(listCompetencyIndex(courseId));
    }

    /**
     * Builds the competency index for the given course so callers can seed the system prompt with
     * the initial state (saves a tool-call round-trip for the LLM).
     *
     * @param courseId the course whose competencies to fetch
     * @return wrapper with one entry per competency plus the list of course exercises currently linked to no competency
     */
    public CompetencyIndexResponseDTO listCompetencyIndex(long courseId) {
        Set<CourseCompetency> competencies = courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(courseId);
        List<CompetencyIndexDTO> entries = competencies.stream().map(OrchestratorToolsService::toIndexEntry).sorted(Comparator.comparing(CompetencyIndexDTO::id)).toList();
        Set<Long> linkedExerciseIds = competencies.stream().flatMap(c -> c.getExerciseLinks() == null ? Stream.<CompetencyExerciseLink>empty() : c.getExerciseLinks().stream())
                .map(CompetencyExerciseLink::getExercise).filter(e -> e != null && e.getId() != null).map(Exercise::getId).collect(Collectors.toSet());
        List<CompetencyIndexResponseDTO.UnassignedExerciseRef> unassigned = exerciseRepository.findAllExercisesByCourseId(courseId).stream()
                .filter(exercise -> exercise.getId() != null && !linkedExerciseIds.contains(exercise.getId()))
                .map(exercise -> new CompetencyIndexResponseDTO.UnassignedExerciseRef(exercise.getId(), exercise.getTitle(),
                        exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName()))
                .sorted(Comparator.comparing(CompetencyIndexResponseDTO.UnassignedExerciseRef::id)).toList();
        return new CompetencyIndexResponseDTO(entries, unassigned);
    }

    /**
     * LLM tool: returns the full details for a single competency in the current course as JSON.
     *
     * @param competencyId id of the competency to inspect
     * @param toolContext  Spring AI tool context carrying the current course id
     * @return JSON-serialized competency detail, or a JSON error when inputs or access fail
     */
    @Tool(description = "Get the full details (description, soft due date, mastery threshold, optional flag, and linked exercises/lecture units with their ids and types; "
            + "each exercise ref also carries its current link weight — 1.0 / 0.5 / 0.3) for a single competency in the current course.")
    public String getCompetencyDetails(@ToolParam(description = "id of the competency to inspect") Long competencyId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (competencyId == null) {
            return toJson(Map.of("error", "competencyId is required."));
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return toJson(Map.of("error", "Competency not found: " + competencyId));
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return toJson(Map.of("error", "Competency " + competencyId + " does not belong to the current course."));
        }
        return toJson(toDetail(competency));
    }

    /**
     * LLM tool: extracts the learning-relevant content for an exercise in the current course as JSON.
     *
     * @param exerciseId  id of the exercise whose content should be extracted
     * @param toolContext Spring AI tool context carrying the current course id
     * @return JSON-serialized extracted content, or a JSON error when inputs or access fail
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
            return toJson(Map.of("error", "exerciseId is required."));
        }
        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException ex) {
            return toJson(Map.of("error", "Exercise not found: " + exerciseId));
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return toJson(Map.of("error", "Exercise " + exerciseId + " does not belong to the current course."));
        }
        if (!(exercise instanceof ProgrammingExercise)) {
            String type = exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName();
            String title = exercise.getTitle() != null ? exercise.getTitle() : "";
            return toJson(Map.of("id", exerciseId, "title", title, "type", type, "textExtractable", false, "note",
                    "Content extraction is only available for programming exercises. Use the title and type to decide fit."));
        }
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            return toJson(extracted);
        }
        catch (IllegalArgumentException ex) {
            // Generic message back to the LLM — raw exception text could leak Hibernate/SQL detail
            // into the instructor-facing summary the model writes at the end of the run.
            log.debug("getExerciseContent failed for exercise {}: {}", exerciseId, ex.getMessage());
            return toJson(Map.of("error", "Failed to extract content for exercise " + exerciseId + "."));
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------

    private static CompetencyIndexDTO toIndexEntry(CourseCompetency competency) {
        List<CompetencyIndexDTO.ExerciseLinkRef> exercises = new ArrayList<>();
        if (competency.getExerciseLinks() != null) {
            competency.getExerciseLinks().stream().filter(link -> link.getExercise() != null && link.getExercise().getId() != null && link.getExercise().getTitle() != null)
                    .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId()))
                    .forEach(link -> exercises.add(new CompetencyIndexDTO.ExerciseLinkRef(link.getExercise().getTitle(), link.getWeight())));
        }
        List<String> lectureUnitNames = competency.getLectureUnitLinks() == null ? List.of()
                : competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit).filter(lu -> lu != null).map(lu -> lu.getName()).filter(n -> n != null)
                        .sorted().toList();
        return new CompetencyIndexDTO(competency.getId(), competency.getTitle(), competency.getTaxonomy(), competency.getType(), exercises, lectureUnitNames);
    }

    private static CompetencyDetailDTO toDetail(CourseCompetency competency) {
        List<CompetencyDetailDTO.ExerciseRef> exercises = new ArrayList<>();
        if (competency.getExerciseLinks() != null) {
            competency.getExerciseLinks().stream().filter(link -> link.getExercise() != null && link.getExercise().getId() != null)
                    .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId())).forEach(link -> {
                        Exercise exercise = link.getExercise();
                        exercises.add(new CompetencyDetailDTO.ExerciseRef(exercise.getId(), exercise.getTitle(), exercise.getType(), link.getWeight()));
                    });
        }
        List<CompetencyDetailDTO.LectureUnitRef> lectureUnits = new ArrayList<>();
        if (competency.getLectureUnitLinks() != null) {
            competency.getLectureUnitLinks().stream().filter(link -> link.getLectureUnit() != null && link.getLectureUnit().getId() != null)
                    .sorted(Comparator.comparing((CompetencyLectureUnitLink link) -> link.getLectureUnit().getId())).forEach(link -> lectureUnits
                            .add(new CompetencyDetailDTO.LectureUnitRef(link.getLectureUnit().getId(), link.getLectureUnit().getName(), link.getLectureUnit().getType())));
        }
        return new CompetencyDetailDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getType(),
                competency.getSoftDueDate(), competency.getMasteryThreshold(), competency.isOptional(), exercises, lectureUnits);
    }

    private static boolean belongsToCourse(CourseCompetency competency, long courseId) {
        return competency.getCourse() != null && courseId == competency.getCourse().getId();
    }

    /**
     * Defense-in-depth course check: rejects exam exercises outright (the orchestrator's entry
     * point in {@link CompetencyOrchestrationService#run(long)} already rejects them, but a tool
     * call with an exam exercise id would otherwise walk the lazy
     * {@code exerciseGroup.exam.course} chain outside any transaction).
     */
    private static boolean exerciseBelongsToCourse(Exercise exercise, long courseId) {
        if (exercise.isExamExercise()) {
            return false;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course != null && courseId == course.getId();
    }

    private String missingCourseContextError() {
        return toJson(Map.of("error", "No course context available for this tool call."));
    }

    private static @Nullable Long courseIdFromContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(COURSE_ID_KEY);
        return value instanceof Long longValue ? longValue : null;
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
