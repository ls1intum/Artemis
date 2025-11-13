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
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.BatchCompetencyPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencySaveResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.SingleCompetencyPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Service providing LLM-callable tools for the Atlas Agent using Spring AI's function calling API.
 * Methods annotated with {@link Tool} are automatically exposed as AI functions that can be invoked
 * by large language models during conversations. When the LLM determines it needs data or wants to
 * perform an action, it calls these methods, receives structured JSON responses, and uses that
 * information to generate natural language answers.
 *
 * Rationale: This service allows the Atlas Agent to autonomously retrieve course information and create
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
     * Wrapper class for competency operations.
     * Used by tools to accept single or multiple competency operations.
     */
    public static class CompetencyOperation {

        @JsonProperty
        private Long competencyId; // null for create, set for update

        @JsonProperty
        @NotBlank(message = "Title is required for all competencies")
        private String title;

        @JsonProperty
        private String description;

        @JsonProperty
        @NotNull(message = "Taxonomy is required for all competencies")
        private CompetencyTaxonomy taxonomy;

        // Default constructor for Jackson
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

    // ThreadLocal storage for preview data - enables deterministic extraction without parsing LLM output
    private static final ThreadLocal<SingleCompetencyPreviewResponseDTO> currentSinglePreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<BatchCompetencyPreviewResponseDTO> currentBatchPreview = ThreadLocal.withInitial(() -> null);

    // ThreadLocal to store the current sessionId for tool calls
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
     * “Show me the competencies for course 123” or “What are the learning goals for this course?”
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
        List<AtlasAgentCompetencyDTO> competencyList = competencies.stream().map(AtlasAgentCompetencyDTO::of).toList();
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
     * This tool enables deterministic refinements by providing access to the exact competency data
     * that was last previewed in this session.
     *
     * Use this when:
     * - User requests changes to a previewed competency (e.g., "change taxonomy to UNDERSTAND")
     * - You need to modify only specific fields while preserving others
     * - You want to ensure you're working with the exact data from the last preview
     *
     * @return JSON response with the cached competency data or error if none exists
     */
    @Tool(description = "Get the last previewed competency data for refinement. Use this when user requests changes to a previewed competency.")
    public String getLastPreviewedCompetency() {
        String sessionId = currentSessionId.get();
        if (sessionId == null) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No active session"));
        }

        List<CompetencyOperation> cachedData = atlasAgentService.getCachedCompetencyData(sessionId);
        if (cachedData == null || cachedData.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No previewed competency data found for this session"));
        }

        record Response(String sessionId, List<CompetencyOperation> competencies) {
        }
        return toJson(new Response(sessionId, cachedData));
    }

    /**
     * Validates a competency operation for required fields.
     *
     * @param comp the competency operation to validate
     * @return error message if validation fails, null if valid
     */
    private String validateCompetencyOperation(CompetencyOperation comp) {
        if (comp.getTaxonomy() == null) {
            String titleInfo = comp.getTitle() != null ? " for competency '" + comp.getTitle() + "'" : "";
            return "Missing taxonomy" + titleInfo;
        }
        if (comp.getTitle() == null || comp.getTitle().isBlank()) {
            return "Missing or empty title for competency";
        }
        return null;
    }

    /**
     * Unified tool for previewing one or multiple competencies.
     * Supports both single and batch operations.
     *
     * IMPORTANT: This method stores preview data in ThreadLocal for deterministic extraction.
     * The LLM can respond naturally while the backend extracts structured data separately.
     *
     * @param competencies list of competency operations (single or multiple)
     * @param viewOnly     optional flag for view-only mode
     * @return Simple confirmation message for the LLM to use in its response
     */
    @Tool(description = "Preview one or multiple competencies before creating/updating. SINGLE: pass [{comp}]. BATCH: pass [{comp1}, {comp2}, {comp3}]. CRITICAL: For batch operations, pass ALL competencies in ONE call, not multiple separate calls.")
    public String previewCompetencies(@ToolParam(description = "list of competency operations to preview") List<CompetencyOperation> competencies,
            @ToolParam(description = "optional: set to true for view-only mode (no action buttons)", required = false) Boolean viewOnly) {
        if (competencies == null || competencies.isEmpty()) {
            return "Error: No competencies provided for preview.";
        }

        // Validate all competencies before processing
        for (CompetencyOperation comp : competencies) {
            String validationError = validateCompetencyOperation(comp);
            if (validationError != null) {
                return "Error: " + validationError;
            }
        }

        // Convert operations to preview DTOs with consistent taxonomy-to-icon mapping
        List<CompetencyPreviewDTO> previews = competencies.stream().map(comp -> {
            String iconName = getTaxonomyIcon(comp.getTaxonomy());
            return new CompetencyPreviewDTO(comp.getTitle(), comp.getDescription(), comp.getTaxonomy().toString(), iconName, comp.getCompetencyId());
        }).toList();

        // Store preview data in ThreadLocal for deterministic extraction by AtlasAgentService
        if (competencies.size() == 1) {
            // Single preview
            CompetencyOperation firstComp = competencies.getFirst();
            CompetencyPreviewDTO firstPreview = previews.getFirst();
            SingleCompetencyPreviewResponseDTO singlePreview = new SingleCompetencyPreviewResponseDTO(true, firstPreview, firstComp.getCompetencyId(), viewOnly);
            currentSinglePreview.set(singlePreview);
        }
        else {
            // Batch preview
            BatchCompetencyPreviewResponseDTO batchPreview = new BatchCompetencyPreviewResponseDTO(true, previews.size(), previews, viewOnly);
            currentBatchPreview.set(batchPreview);
        }

        // Cache the competency operation data for refinement operations
        // This enables deterministic modifications (e.g., changing taxonomy while preserving title/description)
        String sessionId = currentSessionId.get();
        if (sessionId != null && !Boolean.TRUE.equals(viewOnly)) {
            // Only cache if not in view-only mode (view-only is for browsing, not editing)
            atlasAgentService.cacheCompetencyData(sessionId, new ArrayList<>(competencies));
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
     * Maps a CompetencyTaxonomy to its corresponding icon name.
     * This mapping is critical for client-side display and must remain stable.
     *
     * @param taxonomy the competency taxonomy
     * @return the corresponding icon name for FontAwesome
     */
    private String getTaxonomyIcon(CompetencyTaxonomy taxonomy) {
        return switch (taxonomy) {
            case REMEMBER -> "brain";
            case UNDERSTAND -> "comments";
            case APPLY -> "pen-fancy";
            case ANALYZE -> "magnifying-glass";
            case EVALUATE -> "plus-minus";
            case CREATE -> "cubes-stacked";
        };
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
        List<String> errors = new ArrayList<>();
        List<CompetencyOperation> successfulOperations = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;

        for (CompetencyOperation comp : competencies) {
            try {
                // Validate and normalize title once before using it
                String rawTitle = comp.getTitle();
                if (rawTitle == null) {
                    errors.add("Missing or null title for competency");
                    continue;
                }

                String sanitizedTitle = rawTitle.trim();
                if (sanitizedTitle.isBlank()) {
                    errors.add("Missing or empty title for competency");
                    continue;
                }

                // Validate taxonomy to prevent NPE during preview generation
                if (comp.getTaxonomy() == null) {
                    errors.add("Missing taxonomy for competency: " + sanitizedTitle);
                    continue;
                }

                if (comp.getCompetencyId() == null) {
                    // Create new competency
                    Competency competency = new Competency();
                    competency.setTitle(sanitizedTitle);
                    competency.setDescription(comp.getDescription());
                    competency.setTaxonomy(comp.getTaxonomy());
                    competency.setCourse(course);
                    competencyRepository.save(competency);
                    createCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(comp);
                }
                else {
                    // Update existing competency
                    Optional<Competency> existing = competencyRepository.findById(comp.getCompetencyId());
                    if (existing.isEmpty()) {
                        errors.add("Competency not found with ID: " + comp.getCompetencyId());
                        continue;
                    }

                    Competency competency = existing.get();
                    competency.setTitle(sanitizedTitle);
                    competency.setDescription(comp.getDescription());
                    competency.setTaxonomy(comp.getTaxonomy());
                    competencyRepository.save(competency);
                    updateCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(comp);
                }
            }
            catch (Exception e) {
                // Use sanitized title if available, otherwise use a placeholder for error message
                String titleForError = comp.getTitle() != null ? comp.getTitle().trim() : "[no title]";
                errors.add("Failed to save '" + titleForError + "': " + e.getMessage());
            }
        }

        // Store preview data in ThreadLocal so client can display cards for what was just saved
        // This ensures the cards appear in the response showing what was created/updated
        // Only generate previews for successfully processed competencies to prevent NPE
        if (!successfulOperations.isEmpty()) {
            List<CompetencyPreviewDTO> previews = successfulOperations.stream().map(comp -> {
                String iconName = getTaxonomyIcon(comp.getTaxonomy());
                return new CompetencyPreviewDTO(comp.getTitle(), comp.getDescription(), comp.getTaxonomy().toString(), iconName, comp.getCompetencyId());
            }).toList();

            if (successfulOperations.size() == 1) {
                // Single save - store as single preview with viewOnly=false (action completed)
                CompetencyOperation firstComp = successfulOperations.getFirst();
                CompetencyPreviewDTO firstPreview = previews.getFirst();
                SingleCompetencyPreviewResponseDTO singlePreview = new SingleCompetencyPreviewResponseDTO(true, firstPreview, firstComp.getCompetencyId(), false);
                currentSinglePreview.set(singlePreview);
            }
            else {
                // Batch save - store as batch preview with viewOnly=false (action completed)
                BatchCompetencyPreviewResponseDTO batchPreview = new BatchCompetencyPreviewResponseDTO(true, previews.size(), previews, false);
                currentBatchPreview.set(batchPreview);
            }
        }

        // Construct success message
        List<String> messages = new ArrayList<>();
        if (createCount > 0) {
            messages.add(createCount + " competenc" + (createCount == 1 ? "y" : "ies") + " created");
        }
        if (updateCount > 0) {
            messages.add(updateCount + " competenc" + (updateCount == 1 ? "y" : "ies") + " updated");
        }
        if (!errors.isEmpty()) {
            messages.add(errors.size() + " failed");
        }

        String message = messages.isEmpty() ? null : String.join(", ", messages);
        CompetencySaveResponseDTO response = new CompetencySaveResponseDTO(errors.isEmpty(), createCount, updateCount, errors.size(), errors.isEmpty() ? null : errors, message);

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
     * Retrieves and clears the current single competency preview from ThreadLocal.
     * Used by AtlasAgentService to extract preview data after tool execution.
     *
     * @return The stored preview, or null if none exists
     */
    public static SingleCompetencyPreviewResponseDTO getAndClearSinglePreview() {
        return currentSinglePreview.get();
    }

    /**
     * Retrieves and clears the current batch competency preview from ThreadLocal.
     * Used by AtlasAgentService to extract preview data after tool execution.
     *
     * @return The stored batch preview, or null if none exists
     */
    public static BatchCompetencyPreviewResponseDTO getAndClearBatchPreview() {
        return currentBatchPreview.get();
    }

    /**
     * Clears all preview data from ThreadLocal.
     * Should be called at the start of each request to ensure clean state.
     */
    public static void clearAllPreviews() {
        currentSinglePreview.remove();
        currentBatchPreview.remove();
    }
}
