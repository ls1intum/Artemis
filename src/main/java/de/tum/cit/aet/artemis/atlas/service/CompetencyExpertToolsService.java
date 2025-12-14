package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyErrorDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyOperationDTO;
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

        List<CompetencyOperationDTO> cachedData = atlasAgentService.getCachedPendingCompetencyOperations(sessionId);
        if (cachedData == null || cachedData.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No previewed competency data found for this session"));
        }

        record Response(String sessionId, List<CompetencyOperationDTO> competencies) {
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
            @ToolParam(description = "list of competency operations to preview ") List<CompetencyOperationDTO> competencies,
            @ToolParam(description = "optional: set to true for view-only mode (no action buttons)", required = false) Boolean viewOnly) {
        if (competencies == null || competencies.isEmpty()) {
            return "Error: No competencies provided for preview.";
        }

        List<CompetencyPreviewDTO> previewResponses = competencies.stream().map(competency -> new CompetencyPreviewDTO(competency.title(), competency.description(),
                competency.taxonomy().toString(), competency.taxonomy().getIcon(), competency.competencyId(), viewOnly)).toList();

        currentPreviews.set(previewResponses);

        // Cache the competency operation data for refinement operations
        String sessionId = currentSessionId.get();
        if (sessionId != null && !Boolean.TRUE.equals(viewOnly)) {
            // Only cache if not in view-only mode (view-only is for browsing, not editing)
            atlasAgentService.cachePendingCompetencyOperations(sessionId, new ArrayList<>(competencies));
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
            @ToolParam(description = "list of competency operations to save") List<CompetencyOperationDTO> competencies) {

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
        List<CompetencyOperationDTO> successfulOperations = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;

        for (CompetencyOperationDTO competencyOperation : competencies) {
            try {
                if (competencyOperation.competencyId() == null) {
                    // Create Operation as the id is null
                    Competency competency = new Competency();
                    competency.setTitle(competencyOperation.title());
                    competency.setDescription(competencyOperation.description());
                    competency.setTaxonomy(competencyOperation.taxonomy());
                    competency.setCourse(course);
                    competencyRepository.save(competency);
                    createCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(competencyOperation);
                }
                else {
                    // Update Operation
                    Optional<Competency> existing = competencyRepository.findById(competencyOperation.competencyId());
                    CompetencyErrorDTO notFound = new CompetencyErrorDTO(competencyOperation.title(), "NOT_FOUND", "ID: " + competencyOperation.competencyId());
                    if (existing.isEmpty()) {
                        errors.add(notFound);
                        continue;
                    }

                    Competency competency = existing.get();
                    if (!competency.getCourse().getId().equals(course.getId())) {
                        errors.add(notFound);
                        continue;
                    }

                    competency.setTitle(competencyOperation.title());
                    competency.setDescription(competencyOperation.description());
                    competency.setTaxonomy(competencyOperation.taxonomy());
                    competencyRepository.save(competency);
                    CompetencyOperationDTO competencyOperationwithID = new CompetencyOperationDTO(competency.getId(), competencyOperation.title(),
                            competencyOperation.description(), competencyOperation.taxonomy());
                    updateCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(competencyOperationwithID);
                }
            }
            catch (Exception e) {
                String titleForError = competencyOperation.title() != null ? competencyOperation.title().trim() : null;
                errors.add(new CompetencyErrorDTO(titleForError, "SAVE_FAILED", e.getMessage()));
            }
        }

        // Store preview data in ThreadLocal so the interface can display cards for what was just saved
        // This ensures the cards appear in the response showing what was created/updated
        if (!successfulOperations.isEmpty()) {
            List<CompetencyPreviewDTO> previewResponses = successfulOperations.stream()
                    .map(competencyOperation -> new CompetencyPreviewDTO(competencyOperation.title(), competencyOperation.description(), competencyOperation.taxonomy().toString(),
                            competencyOperation.taxonomy().getIcon(), competencyOperation.competencyId(), false))
                    .toList();

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
     * Used by AtlasAgentService to extract preview data after tool execution.
     *
     * @return The stored list of previews, or null if none exists
     */
    public static List<CompetencyPreviewDTO> getPreviews() {
        return currentPreviews.get();
    }

    /**
     * Clears all preview data from ThreadLocal.
     * Should be called at the start of each request to ensure clean state.
     */
    public static void clearAllPreviews() {
        currentPreviews.remove();
    }
}
