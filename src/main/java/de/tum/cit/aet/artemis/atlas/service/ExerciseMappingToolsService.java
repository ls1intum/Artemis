package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentExerciseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Service providing LLM-callable tools for the Exercise Mapper Agent using Spring AI's function calling API.
 * Methods annotated with {@link Tool} are automatically exposed as AI functions that can be invoked
 * by large language models during conversations.
 *
 * Main Responsibilities:
 * - List exercises in a course with their current competency mappings
 * - Preview exercise-to-competency mappings before creation
 * - Create/update/delete exercise-competency links
 * - Provide AI suggestions for competency mappings based on exercise content
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class ExerciseMappingToolsService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseMappingToolsService.class);

    /**
     * Wrapper class for exercise-competency mapping operations.
     * Used by tools to accept mapping data from the LLM.
     */
    public static class ExerciseCompetencyMappingOperation {

        @JsonProperty
        @NotNull
        private Long competencyId;

        @JsonProperty
        @NotNull
        private Double weight;

        @JsonProperty
        private Boolean alreadyMapped;

        @JsonProperty
        private Boolean suggested;

        public ExerciseCompetencyMappingOperation(Long competencyId, Double weight, Boolean alreadyMapped, Boolean suggested) {
            this.competencyId = competencyId;
            this.weight = weight;
            this.alreadyMapped = alreadyMapped != null && alreadyMapped;
            this.suggested = suggested != null && suggested;
        }

        public Long getCompetencyId() {
            return competencyId;
        }

        public void setCompetencyId(Long competencyId) {
            this.competencyId = competencyId;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }

        public Boolean getAlreadyMapped() {
            return alreadyMapped;
        }

        public void setAlreadyMapped(Boolean alreadyMapped) {
            this.alreadyMapped = alreadyMapped;
        }

        public Boolean getSuggested() {
            return suggested;
        }

        public void setSuggested(Boolean suggested) {
            this.suggested = suggested;
        }
    }

    /**
     * ThreadLocal storage for exercise mapping preview data.
     * Used to pass preview data from tool methods to the service layer for frontend rendering.
     */
    private static final ThreadLocal<ExerciseCompetencyMappingDTO> exerciseMappingPreview = new ThreadLocal<>();

    private final ExerciseRepository exerciseRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExerciseMappingToolsService(ExerciseRepository exerciseRepository, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyExerciseLinkRepository competencyExerciseLinkRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository) {
        this.exerciseRepository = exerciseRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Retrieves all competencies for a given course with their IDs.
     * The LLM should call this method FIRST to find competency IDs by title before creating mappings.
     *
     * @param courseId ID of the course
     * @return JSON response containing the list of competencies with IDs and titles
     */
    @Tool(description = "Get all course competencies with their IDs. CRITICAL: Call this FIRST to find competency IDs by title before creating relations.")
    public String getCourseCompetencies(@ToolParam(description = "the ID of the course") Long courseId) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("Course not found with ID: " + courseId));
        }

        List<CourseCompetency> competencies = courseCompetencyRepository.findByCourseIdOrderById(courseId);

        record CompetencyInfo(Long id, String title) {
        }
        List<CompetencyInfo> competencyList = competencies.stream().map(c -> new CompetencyInfo(c.getId(), c.getTitle())).toList();

        record Response(Long courseId, List<CompetencyInfo> competencies) {
        }
        return toJson(new Response(courseId, competencyList));
    }

    /**
     * Lists all exercises in a course with their current competency mappings.
     * Returns a formatted list of exercises showing ID, title, type, and currently mapped competencies.
     *
     * @param courseId The course ID
     * @return JSON string with exercise list or error message
     */
    @Tool(description = "Lists all exercises in a course with their IDs, titles, types, and currently mapped competencies. Use this when the user wants to map an exercise to competencies."
            + "Returns a formatted list of exercises with enumeration for easy selection.")
    public String listCourseExercises(@ToolParam(description = "The ID of the course") Long courseId) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return toJson(Map.of("error", "Course not found with ID: " + courseId));
        }

        Set<Exercise> exercises = exerciseRepository.findByCourseIds(Set.of(courseId));
        List<AtlasAgentExerciseDTO> exerciseList = exercises.stream()
                .map(exercise -> new AtlasAgentExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), exercise.getMaxPoints(),
                        exercise.getReleaseDate() != null ? exercise.getReleaseDate().toString() : null, exercise.getDueDate() != null ? exercise.getDueDate().toString() : null))
                .toList();

        record Response(Long courseId, List<AtlasAgentExerciseDTO> exercises) {
        }
        return toJson(new Response(courseId, exerciseList));
    }

    /**
     * Generates a preview of exercise-to-competency mappings before saving.
     * Shows which competencies will be mapped to the exercise with their weights.
     * Existing mappings are marked as alreadyMapped for frontend styling.
     *
     * @param courseId   The course ID
     * @param exerciseId The exercise ID to map
     * @param mappings   List of competency mappings (can include suggested mappings)
     * @param viewOnly   If true, no action buttons shown (for display only)
     * @return JSON string with preview data or error message
     */
    @Tool(description = """
            Generates a preview of exercise-to-competency mappings before saving.
            Shows which competencies will be linked to the exercise with their weights (LOW=0.25, MEDIUM=0.5, HIGH=1.0).
            Existing mappings are automatically marked as 'alreadyMapped'.
            The preview is displayed to the user for confirmation before applying changes.
            """)
    public String previewExerciseCompetencyMapping(@ToolParam(description = "The ID of the course") Long courseId,
            @ToolParam(description = "The ID of the exercise to map") Long exerciseId,
            @ToolParam(description = "List of competency mappings with competencyId, weight (0.25/0.5/1.0), and suggested flag") List<ExerciseCompetencyMappingOperation> mappings,
            @ToolParam(description = "If true, display only (no action buttons)") Boolean viewOnly) {

        try {
            log.info("Generating exercise mapping preview for exercise {} in course {}", exerciseId, courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return error("Course not found with ID: " + courseId);
            }

            Optional<Exercise> exerciseOpt = exerciseRepository.findWithCompetenciesById(exerciseId);
            if (exerciseOpt.isEmpty()) {
                return error("Exercise not found with ID: " + exerciseId);
            }

            Exercise exercise = exerciseOpt.get();

            List<CompetencyExerciseLink> existingLinks = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId);
            Set<Long> existingCompetencyIds = existingLinks.stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

            ExerciseCompetencyMappingDTO preview = new ExerciseCompetencyMappingDTO(exercise.getId(), exercise.getTitle(),
                    mappings.stream()
                            .map(op -> new ExerciseCompetencyMappingDTO.CompetencyMappingOption(op.getCompetencyId(), getCompetencyTitle(op.getCompetencyId()), op.getWeight(),
                                    existingCompetencyIds.contains(op.getCompetencyId()), op.getSuggested() != null && op.getSuggested()))
                            .collect(Collectors.toList()),
                    viewOnly != null && viewOnly);

            exerciseMappingPreview.set(preview);

            return success("Preview generated successfully for exercise-to-competency mapping.");

        }
        catch (Exception e) {
            log.error("Error generating preview for exercise {}", exerciseId, e);
            return error("Failed to generate preview: " + e.getMessage());
        }
    }

    /**
     * Saves exercise-to-competency mappings to the database.
     * Creates new links and removes unchecked existing links.
     * Avoids re-persisting unchanged existing mappings.
     *
     * @param courseId   The course ID
     * @param exerciseId The exercise ID
     * @param mappings   List of competency mappings to save
     * @return Success or error message
     */
    @Transactional
    @Tool(description = """
            Saves exercise-to-competency mappings to the database.
            Creates new links for newly checked competencies.
            Deletes links for unchecked existing competencies.
            Avoids re-persisting unchanged existing mappings.
            Use this after the user has confirmed the preview.
            """)
    public String saveExerciseCompetencyMappings(@ToolParam(description = "The ID of the course") Long courseId, @ToolParam(description = "The ID of the exercise") Long exerciseId,
            @ToolParam(description = "List of competency mappings to save") List<ExerciseCompetencyMappingOperation> mappings) {

        try {
            log.info("Saving exercise mappings for exercise {} in course {}", exerciseId, courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return error("Course not found with ID: " + courseId);
            }

            Course course = courseOpt.get();
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, userRepository.getUser());

            Optional<Exercise> exerciseOpt = exerciseRepository.findWithCompetenciesById(exerciseId);
            if (exerciseOpt.isEmpty()) {
                return error("Exercise not found with ID: " + exerciseId);
            }

            Exercise exercise = exerciseOpt.get();

            List<CompetencyExerciseLink> existingLinks = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId);

            Set<Long> newCompetencyIds = mappings.stream().map(ExerciseCompetencyMappingOperation::getCompetencyId).collect(Collectors.toSet());
            Set<Long> existingCompetencyIds = existingLinks.stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

            List<CompetencyExerciseLink> linksToDelete = existingLinks.stream().filter(link -> !newCompetencyIds.contains(link.getCompetency().getId()))
                    .collect(Collectors.toList());

            List<CompetencyExerciseLink> linksToCreate = new ArrayList<>();
            for (ExerciseCompetencyMappingOperation mapping : mappings) {
                if (!existingCompetencyIds.contains(mapping.getCompetencyId())) {
                    Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(mapping.getCompetencyId());
                    if (competencyOpt.isPresent()) {
                        CompetencyExerciseLink link = new CompetencyExerciseLink();
                        link.setCompetency(competencyOpt.get());
                        link.setExercise(exercise);
                        link.setWeight(mapping.getWeight());
                        linksToCreate.add(link);
                    }
                }
            }

            if (!linksToDelete.isEmpty()) {
                competencyExerciseLinkRepository.deleteAll(linksToDelete);
                log.info("Deleted {} competency links for exercise {}", linksToDelete.size(), exerciseId);
            }

            if (!linksToCreate.isEmpty()) {
                competencyExerciseLinkRepository.saveAll(linksToCreate);
                log.info("Created {} new competency links for exercise {}", linksToCreate.size(), exerciseId);
            }

            exerciseMappingPreview.remove();

            return success(String.format("Successfully updated exercise-competency mappings. Created %d, deleted %d.", linksToCreate.size(), linksToDelete.size()));

        }
        catch (Exception e) {
            log.error("Error saving exercise mappings for exercise {}", exerciseId, e);
            exerciseMappingPreview.remove();
            return error("Failed to save mappings: " + e.getMessage());
        }
    }

    /**
     * Get the preview data stored in ThreadLocal.
     * Called by the service layer to retrieve preview for frontend rendering.
     *
     * @return The exercise mapping preview DTO or null
     */
    public static ExerciseCompetencyMappingDTO getExerciseMappingPreview() {
        return exerciseMappingPreview.get();
    }

    /**
     * Clear the ThreadLocal preview data.
     * Should be called after preview has been consumed by frontend.
     */
    public static void clearExerciseMappingPreview() {
        exerciseMappingPreview.remove();
    }

    /**
     * Helper method to get competency title by ID.
     *
     * @param competencyId The competency ID
     * @return The competency title or "Unknown"
     */
    private String getCompetencyTitle(Long competencyId) {
        return courseCompetencyRepository.findById(competencyId).map(CourseCompetency::getTitle).orElse("Unknown Competency");
    }

    /**
     * Format a success response for the LLM.
     *
     * @param message The success message
     * @return Formatted JSON success response
     */
    private String success(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("success", true, "message", message));
        }
        catch (JsonProcessingException e) {
            return "{\"success\": true, \"message\": \"Operation completed\"}";
        }
    }

    /**
     * Format an error response for the LLM.
     *
     * @param message The error message
     * @return Formatted JSON error response
     */
    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("success", false, "error", message));
        }
        catch (JsonProcessingException e) {
            return "{\"success\": false, \"error\": \"Operation failed\"}";
        }
    }

    /**
     * Convert object to JSON using Jackson ObjectMapper.
     */
    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
