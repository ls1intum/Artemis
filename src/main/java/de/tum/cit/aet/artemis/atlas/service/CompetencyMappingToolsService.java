package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.RelationGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.RelationGraphNodeDTO;
import de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.SingleRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyRelationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Service providing LLM-callable tools for the Competency Mapper Agent using Spring AI's function calling API.
 * Methods annotated with {@link Tool} are automatically exposed as AI functions that can be invoked
 * by large language models during conversations.
 *
 * Main Responsibilities:
 * - Preview competency relation mappings before creation
 * - Create/update competency relations
 * - Retrieve existing relations for context
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CompetencyMappingToolsService {

    /**
     * Wrapper class for competency relation operations.
     * Used by tools to accept single or multiple relation operations.
     */
    public record RelationOperation(@JsonProperty Long relationId, // null for create, set for update
            @JsonProperty @NotNull Long headCompetencyId, @JsonProperty @NotNull Long tailCompetencyId, @JsonProperty @NotNull RelationType relationType) {
    }

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyRelationService competencyRelationService;

    private final CourseRepository courseRepository;

    private final AtlasAgentService atlasAgentService;

    // ThreadLocal storage for preview data - enables deterministic extraction without parsing LLM output
    private static final ThreadLocal<SingleRelationPreviewResponseDTO> currentSingleRelationPreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<BatchRelationPreviewResponseDTO> currentBatchRelationPreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<RelationGraphPreviewDTO> currentRelationGraphPreview = ThreadLocal.withInitial(() -> null);

    // ThreadLocal to store the current sessionId for tool calls
    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    public CompetencyMappingToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyRelationRepository competencyRelationRepository, CompetencyRelationService competencyRelationService, CourseRepository courseRepository,
            @Lazy AtlasAgentService atlasAgentService) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyRelationService = competencyRelationService;
        this.courseRepository = courseRepository;
        this.atlasAgentService = atlasAgentService;
    }

    /**
     * Set the current session ID for this request.
     * Called by AtlasAgentService before routing to Competency Mapper.
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
     * Retrieves all competencies for a given course with their IDs.
     * The LLM should call this method FIRST to find competency IDs by title before creating relations.
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
     * Retrieves all competency relations for a given course.
     * The LLM can call this method to understand existing relations before suggesting new ones.
     *
     * @param courseId ID of the course
     * @return JSON response containing the list of relations or an error message
     */
    @Tool(description = "Get all competency relations for a course to understand existing mappings")
    public String getCourseCompetencyRelations(@ToolParam(description = "the ID of the course") Long courseId) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("Course not found with ID: " + courseId));
        }

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId);
        List<CompetencyRelationDTO> relationList = relations.stream().map(CompetencyRelationDTO::of).toList();
        record Response(Long courseId, List<CompetencyRelationDTO> relations) {
        }
        return toJson(new Response(courseId, relationList));
    }

    /**
     * Retrieves the last previewed relation data for refinement operations.
     * This tool enables deterministic refinements by providing access to the exact relation data
     * that was last previewed in this session.
     *
     * @return JSON response with the cached relation data or error if none exists
     */
    @Tool(description = "Get the last previewed relation data for refinement. Use this when user requests changes to a previewed relation.")
    public String getLastPreviewedRelation() {
        String sessionId = currentSessionId.get();
        if (sessionId == null) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No active session"));
        }

        List<RelationOperation> cachedData = atlasAgentService.getCachedRelationData(sessionId);
        if (cachedData == null || cachedData.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No previewed relation data found for this session"));
        }

        record Response(String sessionId, List<RelationOperation> relations) {
        }
        return toJson(new Response(sessionId, cachedData));
    }

    /**
     * Unified tool for previewing one or multiple competency relation mappings.
     * Supports both single and batch operations.
     *
     * IMPORTANT: This method stores preview data in ThreadLocal for deterministic extraction.
     * The LLM can respond naturally while the backend extracts structured data separately.
     *
     * @param courseId  the ID of the course
     * @param relations list of relation operations (single or multiple)
     * @param viewOnly  optional flag for view-only mode
     * @return Simple confirmation message for the LLM to use in its response
     */
    @Tool(description = "Preview one or multiple competency relation mappings before creating. SINGLE: pass [{relation}]. BATCH: pass [{rel1}, {rel2}]. Pass ALL relations in ONE call.")
    public String previewRelationMappings(@ToolParam(description = "the Course ID from the CONTEXT section") Long courseId,
            @ToolParam(description = "list of relation operations to preview") List<RelationOperation> relations,
            @ToolParam(description = "optional: set to true for view-only mode (no action buttons)", required = false) Boolean viewOnly) {
        if (relations == null || relations.isEmpty()) {
            return "Error: No relations provided for preview.";
        }

        // Convert operations to preview DTOs
        List<CompetencyRelationPreviewDTO> previews = buildPreviewDTOs(relations, viewOnly);
        if (previews == null) {
            return "Error: Competency not found for relation mapping";
        }

        // Store preview data in ThreadLocal for deterministic extraction by AtlasAgentService
        storePreviewData(previews, viewOnly, relations.size());

        // Generate and store graph preview data (includes all course competencies)
        RelationGraphPreviewDTO graphPreview = buildGraphPreview(courseId, previews, viewOnly);
        currentRelationGraphPreview.set(graphPreview);

        // Cache the relation operation data for refinement operations
        String sessionId = currentSessionId.get();
        if (sessionId != null && !Boolean.TRUE.equals(viewOnly)) {
            atlasAgentService.cacheRelationOperations(sessionId, new ArrayList<>(relations));
        }

        // Return simple confirmation message
        return relations.size() == 1 ? "Preview generated successfully for 1 relation mapping." : "Preview generated successfully for " + relations.size() + " relation mappings.";
    }

    /**
     * Unified tool for creating/updating one or multiple competency relation mappings.
     * Supports both single and batch operations. Continues on partial failures.
     *
     * @param courseId  the course ID
     * @param relations list of relation operations (single or multiple)
     * @return JSON response with success/failure summary
     */
    @Tool(description = "Create or update one or multiple competency relation mappings. Automatically detects create vs update based on relationId presence.")
    public String saveRelationMappings(@ToolParam(description = "the ID of the course") Long courseId,
            @ToolParam(description = "list of relation operations to save") List<RelationOperation> relations) {

        if (relations == null || relations.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("No relations provided"));
        }

        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            record ErrorResponse(String error) {
            }
            return toJson(new ErrorResponse("Course not found with ID: " + courseId));
        }

        Course course = courseOptional.get();
        List<String> errors = new ArrayList<>();
        List<RelationOperation> successfulOperations = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;

        for (RelationOperation rel : relations) {
            try {
                // Fetch competencies
                CompetencyPair competencies = fetchCompetencies(rel.headCompetencyId(), rel.tailCompetencyId());
                if (competencies == null) {
                    errors.add("Competency not found for relation");
                    continue;
                }

                if (rel.relationId() == null) {
                    // Create new relation
                    competencyRelationService.createCompetencyRelation(competencies.tail(), competencies.head(), rel.relationType(), course);
                    createCount++;
                    successfulOperations.add(rel);
                }
                else {
                    // Update existing relation by modifying in place
                    // Note: Matches old behavior - updates don't trigger circular dependency validation
                    Optional<CompetencyRelation> existing = competencyRelationRepository.findById(rel.relationId());
                    if (existing.isEmpty()) {
                        errors.add("Relation not found with ID: " + rel.relationId());
                        continue;
                    }

                    CompetencyRelation relation = existing.get();
                    relation.setHeadCompetency(competencies.head());
                    relation.setTailCompetency(competencies.tail());
                    relation.setType(rel.relationType());

                    competencyRelationRepository.save(relation);
                    updateCount++;
                    successfulOperations.add(rel);
                }
            }
            catch (Exception e) {
                errors.add("Failed to save relation: " + e.getMessage());
            }
        }

        // Store preview data for successfully saved relations in ThreadLocal
        // This is needed for AtlasAgentService to extract and send back to frontend
        if (!successfulOperations.isEmpty()) {
            List<CompetencyRelationPreviewDTO> previews = buildPreviewDTOs(successfulOperations, true);
            if (previews != null) {
                storePreviewData(previews, true, successfulOperations.size());

                // Generate and store graph preview data (includes all course competencies)
                RelationGraphPreviewDTO graphPreview = buildGraphPreview(courseId, previews, true);
                currentRelationGraphPreview.set(graphPreview);
            }
        }

        // Mark competencies as modified if any operations succeeded
        if (createCount > 0 || updateCount > 0) {
            AtlasAgentService.markCompetencyModified();
        }

        // Construct response
        List<String> messages = new ArrayList<>();
        if (createCount > 0) {
            messages.add(createCount + " relation" + (createCount == 1 ? "" : "s") + " created");
        }
        if (updateCount > 0) {
            messages.add(updateCount + " relation" + (updateCount == 1 ? "" : "s") + " updated");
        }
        if (!errors.isEmpty()) {
            messages.add(errors.size() + " failed");
        }

        String message = messages.isEmpty() ? null : String.join(", ", messages);
        record SaveResponse(boolean success, int created, int updated, int failed, List<String> errorDetails, String message) {
        }
        SaveResponse response = new SaveResponse(errors.isEmpty(), createCount, updateCount, errors.size(), errors.isEmpty() ? null : errors, message);

        return toJson(response);
    }

    /**
     * Helper record to hold head and tail competencies.
     */
    private record CompetencyPair(CourseCompetency head, CourseCompetency tail) {
    }

    /**
     * Fetches head and tail competencies by their IDs.
     *
     * @param headId the head competency ID
     * @param tailId the tail competency ID
     * @return CompetencyPair containing both competencies, or null if either is not found
     */
    private CompetencyPair fetchCompetencies(Long headId, Long tailId) {
        Optional<CourseCompetency> headCompetency = courseCompetencyRepository.findById(headId);
        Optional<CourseCompetency> tailCompetency = courseCompetencyRepository.findById(tailId);

        if (headCompetency.isEmpty() || tailCompetency.isEmpty()) {
            return null;
        }

        return new CompetencyPair(headCompetency.get(), tailCompetency.get());
    }

    /**
     * Builds preview DTOs from relation operations.
     *
     * @param relations list of relation operations
     * @param viewOnly  whether this is view-only mode
     * @return list of preview DTOs, or null if any competency is not found
     */
    private List<CompetencyRelationPreviewDTO> buildPreviewDTOs(List<RelationOperation> relations, Boolean viewOnly) {
        List<CompetencyRelationPreviewDTO> previews = new ArrayList<>();

        for (RelationOperation rel : relations) {
            CompetencyPair competencies = fetchCompetencies(rel.headCompetencyId(), rel.tailCompetencyId());
            if (competencies == null) {
                return null;
            }

            CompetencyRelationPreviewDTO preview = new CompetencyRelationPreviewDTO(rel.relationId(), rel.headCompetencyId(), competencies.head().getTitle(),
                    rel.tailCompetencyId(), competencies.tail().getTitle(), rel.relationType(), viewOnly);
            previews.add(preview);
        }

        return previews;
    }

    /**
     * Stores preview data in ThreadLocal based on whether it's single or batch.
     *
     * @param previews      the preview DTOs
     * @param viewOnly      whether this is view-only mode
     * @param relationCount the number of relations
     */
    private void storePreviewData(List<CompetencyRelationPreviewDTO> previews, Boolean viewOnly, int relationCount) {
        boolean isViewOnly = viewOnly != null && viewOnly;

        if (relationCount == 1) {
            SingleRelationPreviewResponseDTO singlePreview = new SingleRelationPreviewResponseDTO(true, previews.getFirst(), isViewOnly);
            currentSingleRelationPreview.set(singlePreview);
        }
        else {
            BatchRelationPreviewResponseDTO batchPreview = new BatchRelationPreviewResponseDTO(true, previews.size(), previews, isViewOnly);
            currentBatchRelationPreview.set(batchPreview);
        }
    }

    /**
     * Builds graph preview data from relation preview DTOs.
     * Includes ALL course competencies to ensure 1:1 mapping with the course competency graph.
     *
     * @param courseId the ID of the course
     * @param previews the preview DTOs
     * @param viewOnly whether this is view-only mode
     * @return the graph preview DTO
     */
    private RelationGraphPreviewDTO buildGraphPreview(Long courseId, List<CompetencyRelationPreviewDTO> previews, Boolean viewOnly) {
        List<RelationGraphNodeDTO> nodes = new ArrayList<>();
        List<RelationGraphEdgeDTO> edges = new ArrayList<>();

        // Fetch ALL competencies for the course to ensure 1:1 mapping
        List<CourseCompetency> allCompetencies = courseCompetencyRepository.findByCourseIdOrderById(courseId);

        // Add all competencies as nodes (including those without relations)
        for (CourseCompetency competency : allCompetencies) {
            nodes.add(new RelationGraphNodeDTO(String.valueOf(competency.getId()), competency.getTitle()));
        }

        // Create edges from relations
        for (CompetencyRelationPreviewDTO preview : previews) {
            String edgeId = preview.relationId() != null ? "edge-" + preview.relationId() : "edge-new-" + preview.headCompetencyId() + "-" + preview.tailCompetencyId();
            edges.add(new RelationGraphEdgeDTO(edgeId, String.valueOf(preview.headCompetencyId()), String.valueOf(preview.tailCompetencyId()), preview.relationType().name()));
        }

        return new RelationGraphPreviewDTO(nodes, edges, viewOnly != null && viewOnly);
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

    /**
     * Retrieves the current single relation preview from ThreadLocal.
     */
    public static SingleRelationPreviewResponseDTO getSingleRelationPreview() {
        return currentSingleRelationPreview.get();
    }

    /**
     * Retrieves the current batch relation preview from ThreadLocal.
     */
    public static BatchRelationPreviewResponseDTO getBatchRelationPreview() {
        return currentBatchRelationPreview.get();
    }

    /**
     * Retrieves the current relation graph preview from ThreadLocal.
     */
    public static RelationGraphPreviewDTO getRelationGraphPreview() {
        return currentRelationGraphPreview.get();
    }

    /**
     * Clears all preview data from ThreadLocal.
     */
    public static void clearAllPreviews() {
        currentSingleRelationPreview.remove();
        currentBatchRelationPreview.remove();
        currentRelationGraphPreview.remove();
    }
}
