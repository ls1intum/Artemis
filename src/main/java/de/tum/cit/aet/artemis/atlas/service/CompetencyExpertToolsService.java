package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyErrorDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencySaveResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Service providing LLM-callable tools for the Competency Expert Sub-Agent using Spring AI's function calling API.
 * Methods annotated with {@link Tool} are automatically exposed as AI functions that can be invoked
 * by large language models during conversations. When the LLM determines it needs data or wants to
 * perform an action, it calls these methods, receives structured JSON responses, and uses that
 * information to generate natural language answers.
 *
 * Rationale: This service allows the Competency Expert Sub-Agent to autonomously retrieve course information and create
 * competencies based on user conversations, enabling an interactive AI assistant for instructors.
 *
 * Main Responsibilities:
 * - Expose course-related data (competencies, exercises, descriptions) as AI-callable tools
 * - Create new competencies based on LLM-generated suggestions
 * - Track whether competencies were changed during a single AI interaction
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/tools.html">Spring AI Function Calling</a>
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CompetencyExpertToolsService {

    /**
     * Mutable wrapper class for competency operations.
     */
    public static class CompetencyOperation {

        @JsonProperty
        private Long competencyId;

        @JsonProperty
        @NotBlank(message = "Title is required for all competencies")
        private String title;

        @JsonProperty
        private String description;

        @JsonProperty
        @NotNull(message = "Taxonomy is required for all competencies")
        private CompetencyTaxonomy taxonomy;

        public CompetencyOperation() {
        }

        public CompetencyOperation(Long competencyId, String title, String description, CompetencyTaxonomy taxonomy) {
            this.competencyId = competencyId;
            this.title = title;
            this.description = description;
            this.taxonomy = taxonomy;
        }

        public Long getCompetencyId() {
            return competencyId;
        }

        public void setCompetencyId(Long competencyId) {
            this.competencyId = competencyId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public CompetencyTaxonomy getTaxonomy() {
            return taxonomy;
        }

        public void setTaxonomy(CompetencyTaxonomy taxonomy) {
            this.taxonomy = taxonomy;
        }
    }

    private final ObjectMapper objectMapper;

    private final CompetencyRepository competencyRepository;

    private final CourseRepository courseRepository;

    private final AtlasAgentService atlasAgentService;

    private static final ThreadLocal<List<CompetencyPreviewDTO>> currentPreviews = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    public CompetencyExpertToolsService(ObjectMapper objectMapper, CompetencyRepository competencyRepository, CourseRepository courseRepository,
            @Lazy AtlasAgentService atlasAgentService) {
        this.objectMapper = objectMapper;
        this.competencyRepository = competencyRepository;
        this.courseRepository = courseRepository;
        this.atlasAgentService = atlasAgentService;
    }

    /**
     * Set the current session ID for this request.
     * Called by AtlasAgentService before routing to Competency Expert.
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

    /**
     * Retrieves all competencies for a given course.
     * The LLM can call this method when asked questions such as:
     * “Show me the competencies” or “What are the learning goals for this course?”
     *
     * @param courseId ID of the course
     * @return JSON response containing the list of competencies or an error message
     */
    @Tool(description = "Get all competencies for a course")
    public String getCourseCompetencies(@ToolParam(description = "the ID of the course") Long courseId) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("Course not found with ID: " + courseId));
        }

        Set<Competency> competencies = competencyRepository.findAllByCourseId(courseId);
        List<AtlasAgentCompetencyDTO> competencyList = competencies.stream().map(competency -> new AtlasAgentCompetencyDTO(competency.getId(), competency.getTitle(),
                competency.getDescription(), competency.getTaxonomy() != null ? competency.getTaxonomy().toString() : null, null)).toList();
        record Response(Long courseId, List<AtlasAgentCompetencyDTO> competencies) {
        }
        return toJson(new Response(courseId, competencyList));
    }

    /**
     * Tool for getting course description.
     *
     * @param courseId the course ID
     * @return the course description or empty string if not found
     */
    @Tool(description = "Get the description of a course")
    public String getCourseDescription(@ToolParam(description = "the ID of the course") Long courseId) {
        return courseRepository.findById(courseId).map(Course::getDescription).orElse("");
    }

    /**
     * Retrieves the last previewed competency data for refinement operations.
     * If cached competencies have null IDs, searches the database to find matching competencies
     * by title and updates the cache with their IDs.
     *
     * @param courseId the course ID
     * @return JSON response with the cached competency data or error if none exists
     */
    @Tool(description = "Get the last previewed competency data for refinement. Use this when user requests changes to a previewed competency.")
    public String getLastPreviewedCompetency(@ToolParam(description = "the ID of the course") Long courseId) {
        String sessionId = currentSessionId.get();
        if (sessionId == null) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No active session"));
        }

        List<CompetencyOperation> cachedData = atlasAgentService.getCachedPendingCompetencyOperations(sessionId);
        if (cachedData == null || cachedData.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No previewed competency data found for this session"));
        }

        boolean needsSync = cachedData.stream().anyMatch(comp -> comp.getCompetencyId() == null);

        if (needsSync) {
            if (courseId != null) {
                Set<Competency> existingCompetencies = competencyRepository.findAllByCourseId(courseId);

                for (CompetencyOperation cachedComp : cachedData) {
                    if (cachedComp.getCompetencyId() == null) {
                        existingCompetencies.stream().filter(dbComp -> dbComp.getTitle() != null && dbComp.getTitle().equalsIgnoreCase(cachedComp.getTitle())).findFirst()
                                .ifPresent(match -> cachedComp.setCompetencyId(match.getId()));
                    }
                }

                atlasAgentService.cachePendingCompetencyOperations(sessionId, cachedData);
            }
        }

        record Response(String sessionId, List<CompetencyOperation> competencies) {
        }
        return toJson(new Response(sessionId, cachedData));
    }

    /**
     * Unified tool for previewing one or multiple competencies.
     * Supports both single and batch operations.
     *
     * IMPORTANT: This method stores preview data in ThreadLocal for deterministic extraction.
     * The LLM can respond naturally while the service extracts structured data separately.
     *
     * @param courseID     the ID of the course
     * @param competencies list of competency operations (single or multiple)
     * @param viewOnly     optional flag for view-only mode
     * @return simple confirmation message for the LLM to use in its response
     */
    @Tool(description = "Preview one or multiple competencies before creating/updating. SINGLE: pass [{comp}]. BATCH: pass [{comp1}, {comp2}, {comp3}]. CRITICAL: For batch operations, pass ALL competencies in ONE call, not multiple separate calls.")
    public String previewCompetencies(@ToolParam(description = "the Course ID from the CONTEXT section") Long courseID,
            @ToolParam(description = "list of competency operations to preview ") List<CompetencyOperation> competencies,
            @ToolParam(description = "optional: set to true for view-only mode (no action buttons)", required = false) Boolean viewOnly) {
        if (competencies == null || competencies.isEmpty()) {
            return "Error: No competencies provided for preview.";
        }

        List<CompetencyPreviewDTO> previewResponses = competencies.stream().map(comp -> new CompetencyPreviewDTO(comp.getTitle(), comp.getDescription(),
                comp.getTaxonomy().toString(), comp.getTaxonomy().getIcon(), comp.getCompetencyId(), viewOnly)).toList();

        currentPreviews.set(previewResponses);

        // Cache the competency operation data for refinement operations
        // IMPORTANT: This enables the AI to refine previews by calling getLastPreviewedCompetency()
        String sessionId = currentSessionId.get();
        if (sessionId != null) {
            if (viewOnly) {
                atlasAgentService.clearCachedPendingCompetencyOperations(sessionId);
            }
            else {
                atlasAgentService.cachePendingCompetencyOperations(sessionId, new ArrayList<>(competencies));
            }
        }

        // Return simple confirmation message that the LLM can use naturally in its response
        // The actual preview data will be extracted from ThreadLocal by AtlasAgentService
        if (competencies.size() == 1) {
            return "Preview generated successfully for 1 competency.";
        }
        else {
            return "Preview generated successfully for " + competencies.size() + " competencies.";
        }
    }

    /**
     * Unified tool for creating/updating one or multiple competencies for a given course.
     * Supports both single and batch operations. Continues on partial failures.
     * The LLM typically calls this method when users request to create a new competency
     * If successful, the competency is persisted and the modification flag is set to true.
     *
     * @param courseId     the course ID
     * @param competencies list of competency operations (single or multiple)
     * @return JSON response with success/failure summary
     */
    @Tool(description = "Create or update one or multiple competencies. Automatically detects create vs update based on competencyId presence.")
    public String saveCompetencies(@ToolParam(description = "the ID of the course") Long courseId,
            @ToolParam(description = "list of competency operations to save") List<CompetencyOperation> competencies) {

        if (competencies == null || competencies.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No competencies provided"));
        }

        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("Course not found with ID: " + courseId));
        }

        Course course = courseOptional.get();
        List<CompetencyErrorDTO> errors = new ArrayList<>();
        List<CompetencyOperation> successfulOperations = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;

        for (CompetencyOperation comp : competencies) {
            try {
                if (comp.getCompetencyId() == null) {
                    // Create new competency
                    Competency competency = new Competency();
                    competency.setTitle(comp.getTitle());
                    competency.setDescription(comp.getDescription());
                    competency.setTaxonomy(comp.getTaxonomy());
                    competency.setCourse(course);
                    competencyRepository.save(competency);
                    comp.setCompetencyId(competency.getId());
                    createCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(comp);
                }
                else {
                    // Update existing competency
                    Optional<Competency> existing = competencyRepository.findById(comp.getCompetencyId());
                    if (existing.isEmpty()) {
                        errors.add(new CompetencyErrorDTO(comp.getTitle(), "NOT_FOUND", "ID: " + comp.getCompetencyId()));
                        continue;
                    }

                    Competency competency = existing.get();
                    if (!competency.getCourse().getId().equals(course.getId())) {
                        errors.add(new CompetencyErrorDTO(comp.getTitle(), "NOT_FOUND", "ID: " + comp.getCompetencyId()));
                        continue;
                    }

                    competency.setTitle(comp.getTitle());
                    competency.setDescription(comp.getDescription());
                    competency.setTaxonomy(comp.getTaxonomy());
                    competencyRepository.save(competency);
                    comp.setCompetencyId(competency.getId());
                    updateCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(comp);
                }
            }
            catch (Exception e) {
                String titleForError = comp.getTitle() != null ? comp.getTitle().trim() : null;
                errors.add(new CompetencyErrorDTO(titleForError, "SAVE_FAILED", e.getMessage()));
            }
        }

        // Store preview data in ThreadLocal so the interface can display cards for what was just saved
        // This ensures the cards appear in the response showing what was created/updated
        if (!successfulOperations.isEmpty()) {
            List<CompetencyPreviewDTO> previewResponses = successfulOperations.stream().map(comp -> new CompetencyPreviewDTO(comp.getTitle(), comp.getDescription(),
                    comp.getTaxonomy().toString(), comp.getTaxonomy().getIcon(), comp.getCompetencyId(), false)).toList();

            currentPreviews.set(previewResponses);
        }

        CompetencySaveResponseDTO response = new CompetencySaveResponseDTO(createCount, updateCount, errors.size(), errors.isEmpty() ? null : errors);

        if (createCount > 0 || updateCount > 0) {
            AtlasAgentService.markCompetencyModified();
        }

        return toJson(response);
    }

    /**
     * Convert object to JSON using Jackson ObjectMapper.
     *
     * @param object the object to serialize
     * @return JSON string representation
     */
    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }

    /**
     * Retrieves the current competency previews from ThreadLocal.
     * Clears the stored previews after retrieval to avoid stale data.
     * Used by AtlasAgentService to extract preview data after tool execution.
     *
     * @return The stored list of previews, or empty list if none exists
     */
    public static List<CompetencyPreviewDTO> getAndClearPreviews() {
        List<CompetencyPreviewDTO> previews = currentPreviews.get();
        currentPreviews.remove();
        return previews != null ? previews : List.of();
    }

}
