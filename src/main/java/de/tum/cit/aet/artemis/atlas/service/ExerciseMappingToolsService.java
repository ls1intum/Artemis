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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.ExerciseCompetencyMappingDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
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
 * - Preview exercise-to-competency mappings before creation (with server-side AtlasML suggestions)
 * - Create/update/delete exercise-competency links
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

        public Double getWeight() {
            return weight;
        }

        public Boolean getAlreadyMapped() {
            return alreadyMapped;
        }

        public Boolean getSuggested() {
            return suggested;
        }
    }

    /**
     * ThreadLocal storage for the current session ID.
     * Set by AtlasAgentService before delegating to the Exercise Mapper sub-agent.
     */
    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    /**
     * ThreadLocal storage for exercise mapping preview data.
     * Used to pass preview data from tool methods to the service layer for frontend rendering.
     */
    private static final ThreadLocal<ExerciseCompetencyMappingDTO> exerciseMappingPreview = new ThreadLocal<>();

    /**
     * ThreadLocal storage for user-selected exercise mappings (from the frontend approval payload).
     * When set, {@link #saveExerciseCompetencyMappings} uses these instead of the LLM-provided mappings,
     * ensuring the user's checkbox selections and weight choices are respected.
     */
    private static final ThreadLocal<List<ExerciseCompetencyMappingOperation>> userSelectedMappings = new ThreadLocal<>();

    /**
     * Set the user-selected mappings before delegating to the exercise mapper agent for saving.
     *
     * @param mappings the competency mappings selected by the user in the frontend
     */
    public static void setUserSelectedMappings(List<ExerciseCompetencyMappingOperation> mappings) {
        userSelectedMappings.set(mappings);
    }

    /**
     * Clear the user-selected mappings ThreadLocal.
     */
    public static void clearUserSelectedMappings() {
        userSelectedMappings.remove();
    }

    /**
     * Set the current session ID for this request.
     * Called by AtlasAgentService before routing to the Exercise Mapper sub-agent.
     *
     * @param sessionId the session ID
     */
    public static void setCurrentSessionId(String sessionId) {
        currentSessionId.set(sessionId);
    }

    /**
     * Clear the current session ID after request completes.
     */
    public static void clearCurrentSessionId() {
        currentSessionId.remove();
    }

    private final ExerciseRepository exerciseRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final AtlasMLApi atlasMLApi;

    private final AtlasAgentSessionCacheService sessionCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExerciseMappingToolsService(ExerciseRepository exerciseRepository, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyExerciseLinkRepository competencyExerciseLinkRepository, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, AtlasMLApi atlasMLApi, AtlasAgentSessionCacheService sessionCacheService) {
        this.exerciseRepository = exerciseRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.atlasMLApi = atlasMLApi;
        this.sessionCacheService = sessionCacheService;
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
            Do NOT set suggested — the server determines AI suggestions automatically via AtlasML.
            """)
    public String previewExerciseCompetencyMapping(@ToolParam(description = "The ID of the course") Long courseId,
            @ToolParam(description = "The ID of the exercise to map") Long exerciseId,
            @ToolParam(description = "List of competency mappings with competencyId and weight (0.25/0.5/1.0). Do NOT set suggested — the server determines this automatically via AtlasML.") List<ExerciseCompetencyMappingOperation> mappings,
            @ToolParam(description = "If true, display only (no action buttons)") Boolean viewOnly) {

        try {
            log.info("Generating exercise mapping preview for exercise {} in course {}", exerciseId, courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return error("Course not found with ID: " + courseId);
            }

            Exercise exercise = loadAndValidateExercise(exerciseId, courseId);

            List<CompetencyExerciseLink> existingLinks = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId);
            Set<Long> existingCompetencyIds = existingLinks.stream().map(link -> link.getCompetency().getId()).collect(Collectors.toSet());

            // Fetch AtlasML suggestions using the exercise description (same logic as the exercise edit lightbulb).
            // Returns null when AtlasML is unavailable — in that case fall back to the LLM's own suggested flags.
            String description = exercise.getProblemStatement() != null && !exercise.getProblemStatement().isBlank() ? exercise.getProblemStatement() : exercise.getTitle();
            Set<Long> suggestedIds = fetchSuggestedCompetencyIds(courseId, description);
            boolean useAtlasML = suggestedIds != null;

            // Bulk-load titles to avoid N+1 queries
            List<Long> allCompetencyIds = mappings.stream().map(ExerciseCompetencyMappingOperation::getCompetencyId).toList();
            Map<Long, String> titleById = courseCompetencyRepository.findAllById(allCompetencyIds).stream()
                    .collect(Collectors.toMap(CourseCompetency::getId, CourseCompetency::getTitle));

            ExerciseCompetencyMappingDTO preview = new ExerciseCompetencyMappingDTO(exercise.getId(), exercise.getTitle(), mappings.stream().map(op -> {
                boolean suggested = useAtlasML ? suggestedIds.contains(op.getCompetencyId()) : Boolean.TRUE.equals(op.getSuggested());
                return new ExerciseCompetencyMappingDTO.CompetencyMappingOptionDTO(op.getCompetencyId(), titleById.getOrDefault(op.getCompetencyId(), "Unknown Competency"),
                        op.getWeight(), existingCompetencyIds.contains(op.getCompetencyId()), suggested);
            }).toList(), viewOnly != null && viewOnly);

            exerciseMappingPreview.set(preview);

            // Also write to Hazelcast so cross-node requests can retrieve the preview
            String sessionId = currentSessionId.get();
            if (sessionId != null) {
                sessionCacheService.cacheExerciseMappingPreview(sessionId, preview);
            }

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

            Exercise exercise = loadAndValidateExercise(exerciseId, courseId);

            // If the user explicitly selected mappings via the frontend approval, use those
            // instead of the LLM-provided ones to honour checkbox and weight choices.
            List<ExerciseCompetencyMappingOperation> selected = userSelectedMappings.get();
            if (selected != null) {
                mappings = selected;
                userSelectedMappings.remove();
            }

            List<CompetencyExerciseLink> existingLinks = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId);

            List<Long> newCompetencyIds = mappings.stream().map(ExerciseCompetencyMappingOperation::getCompetencyId).distinct().toList();
            Map<Long, CompetencyExerciseLink> existingLinksByCompetencyId = existingLinks.stream().collect(Collectors.toMap(link -> link.getCompetency().getId(), link -> link));

            List<CompetencyExerciseLink> linksToDelete = existingLinks.stream().filter(link -> !newCompetencyIds.contains(link.getCompetency().getId())).toList();

            // Only load competencies not yet linked — and validate they belong to this course
            List<Long> missingCompetencyIds = newCompetencyIds.stream().filter(id -> !existingLinksByCompetencyId.containsKey(id)).toList();

            Map<Long, CourseCompetency> competencyById = courseCompetencyRepository.findAllById(missingCompetencyIds).stream().filter(c -> courseId.equals(c.getCourse().getId()))
                    .collect(Collectors.toMap(CourseCompetency::getId, c -> c));

            List<CompetencyExerciseLink> linksToCreate = new ArrayList<>();
            List<CompetencyExerciseLink> linksToUpdate = new ArrayList<>();

            for (ExerciseCompetencyMappingOperation mapping : mappings) {
                CompetencyExerciseLink existing = existingLinksByCompetencyId.get(mapping.getCompetencyId());
                if (existing != null) {
                    // Update weight if it changed
                    if (Double.compare(existing.getWeight(), mapping.getWeight()) != 0) {
                        existing.setWeight(mapping.getWeight());
                        linksToUpdate.add(existing);
                    }
                }
                else {
                    CourseCompetency competency = competencyById.get(mapping.getCompetencyId());
                    if (competency != null) {
                        CompetencyExerciseLink link = new CompetencyExerciseLink();
                        link.setCompetency(competency);
                        link.setExercise(exercise);
                        link.setWeight(mapping.getWeight());
                        linksToCreate.add(link);
                    }
                    else {
                        log.warn("Skipping competency {} — not found in course {}", mapping.getCompetencyId(), courseId);
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
                // Notify AtlasML so the exercise's vector embedding reflects the new competency links
                for (CompetencyExerciseLink link : linksToCreate) {
                    atlasMLApi.mapCompetencyToExercise(exerciseId, link.getCompetency().getId());
                }
            }

            if (!linksToUpdate.isEmpty()) {
                competencyExerciseLinkRepository.saveAll(linksToUpdate);
                log.info("Updated weight for {} competency links for exercise {}", linksToUpdate.size(), exerciseId);
            }

            exerciseMappingPreview.remove();

            return success(String.format("Successfully updated exercise-competency mappings. Created %d, updated %d, deleted %d.", linksToCreate.size(), linksToUpdate.size(),
                    linksToDelete.size()));

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
        currentSessionId.remove();
    }

    /**
     * Loads an exercise by ID and validates it belongs to the given course.
     *
     * @param exerciseId the exercise ID to load
     * @param courseId   the expected course ID
     * @return the validated Exercise
     * @throws IllegalArgumentException if the exercise is not found or does not belong to the course
     */
    private Exercise loadAndValidateExercise(Long exerciseId, Long courseId) {
        Exercise exercise = exerciseRepository.findWithCompetenciesById(exerciseId).orElseThrow(() -> new IllegalArgumentException("Exercise not found with ID: " + exerciseId));

        if (!courseId.equals(exercise.getCourseViaExerciseGroupOrCourseMember().getId())) {
            throw new IllegalArgumentException("Exercise " + exerciseId + " does not belong to course " + courseId);
        }

        return exercise;
    }

    /**
     * Calls AtlasML to get competency IDs semantically relevant to the given exercise description.
     * Uses the same logic as the exercise edit page lightbulb button.
     *
     * @param courseId    the course ID
     * @param description the exercise problem statement (or title as fallback)
     * @return set of suggested competency IDs, or {@code null} if AtlasML is unavailable (callers should fall back to LLM judgment)
     */
    private Set<Long> fetchSuggestedCompetencyIds(Long courseId, String description) {
        try {
            SuggestCompetencyResponseDTO response = atlasMLApi.suggestCompetencies(new SuggestCompetencyRequestDTO(description, courseId));
            if (response == null || response.competencies() == null) {
                return Set.of();
            }
            return response.competencies().stream().map(c -> c.id()).collect(Collectors.toSet());
        }
        catch (Exception e) {
            log.warn("AtlasML suggestion unavailable for exercise in course {}: {}", courseId, e.getMessage());
            return null;
        }
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
