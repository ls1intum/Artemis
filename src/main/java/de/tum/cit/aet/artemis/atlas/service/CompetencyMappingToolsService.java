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
import org.springframework.web.client.RestTemplate;

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
 * - Communicate with AtlasML endpoints for relation mapping
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CompetencyMappingToolsService {

    /**
     * Wrapper class for competency relation operations.
     * Used by tools to accept single or multiple relation operations.
     */
    public static class RelationOperation {

        @JsonProperty
        private Long relationId; // null for create, set for update

        @JsonProperty
        @NotNull
        private Long headCompetencyId;

        @JsonProperty
        @NotNull
        private Long tailCompetencyId;

        @JsonProperty
        @NotNull
        private RelationType relationType;

        // Default constructor for Jackson
        public RelationOperation() {
        }

        public RelationOperation(Long relationId, Long headCompetencyId, Long tailCompetencyId, RelationType relationType) {
            this.relationId = relationId;
            this.headCompetencyId = headCompetencyId;
            this.tailCompetencyId = tailCompetencyId;
            this.relationType = relationType;
        }

        public Long getRelationId() {
            return relationId;
        }

        public void setRelationId(Long relationId) {
            this.relationId = relationId;
        }

        public Long getHeadCompetencyId() {
            return headCompetencyId;
        }

        public void setHeadCompetencyId(Long headCompetencyId) {
            this.headCompetencyId = headCompetencyId;
        }

        public Long getTailCompetencyId() {
            return tailCompetencyId;
        }

        public void setTailCompetencyId(Long tailCompetencyId) {
            this.tailCompetencyId = tailCompetencyId;
        }

        public RelationType getRelationType() {
            return relationType;
        }

        public void setRelationType(RelationType relationType) {
            this.relationType = relationType;
        }
    }

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyRelationService competencyRelationService;

    private final CourseRepository courseRepository;

    private final AtlasAgentService atlasAgentService;

    private final RestTemplate restTemplate;

    // ThreadLocal storage for preview data - enables deterministic extraction without parsing LLM output
    private static final ThreadLocal<SingleRelationPreviewResponseDTO> currentSingleRelationPreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<BatchRelationPreviewResponseDTO> currentBatchRelationPreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO> currentRelationGraphPreview = ThreadLocal.withInitial(() -> null);

    // ThreadLocal to store the current sessionId for tool calls
    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    public CompetencyMappingToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyRelationRepository competencyRelationRepository, CompetencyRelationService competencyRelationService, CourseRepository courseRepository,
            @Lazy AtlasAgentService atlasAgentService, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyRelationService = competencyRelationService;
        this.courseRepository = courseRepository;
        this.atlasAgentService = atlasAgentService;
        this.restTemplate = restTemplate;
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

        // Use CourseCompetencyRepository to get all competencies for the course
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

        // Validate all relations before processing
        for (RelationOperation rel : relations) {
            String validationError = validateRelationOperation(rel);
            if (validationError != null) {
                return "Error: " + validationError;
            }
        }

        // Convert operations to preview DTOs
        List<CompetencyRelationPreviewDTO> previews = new ArrayList<>();
        for (RelationOperation rel : relations) {
            Optional<CourseCompetency> headComp = courseCompetencyRepository.findById(rel.getHeadCompetencyId());
            Optional<CourseCompetency> tailComp = courseCompetencyRepository.findById(rel.getTailCompetencyId());

            if (headComp.isEmpty() || tailComp.isEmpty()) {
                return "Error: Competency not found for relation mapping";
            }

            CompetencyRelationPreviewDTO preview = new CompetencyRelationPreviewDTO(rel.getRelationId(), rel.getHeadCompetencyId(), headComp.get().getTitle(),
                    rel.getTailCompetencyId(), tailComp.get().getTitle(), rel.getRelationType(), viewOnly);
            previews.add(preview);
        }

        // Store preview data in ThreadLocal for deterministic extraction by AtlasAgentService
        if (relations.size() == 1) {
            // Single preview
            SingleRelationPreviewResponseDTO singlePreview = new SingleRelationPreviewResponseDTO(true, previews.getFirst(), viewOnly != null && viewOnly);
            currentSingleRelationPreview.set(singlePreview);
        }
        else {
            // Batch preview
            BatchRelationPreviewResponseDTO batchPreview = new BatchRelationPreviewResponseDTO(true, previews.size(), previews, viewOnly != null && viewOnly);
            currentBatchRelationPreview.set(batchPreview);
        }

        // Generate graph preview data
        List<de.tum.cit.aet.artemis.atlas.dto.RelationGraphNodeDTO> nodes = new ArrayList<>();
        List<de.tum.cit.aet.artemis.atlas.dto.RelationGraphEdgeDTO> edges = new ArrayList<>();

        // Collect unique nodes from the relations
        List<Long> uniqueCompetencyIds = new ArrayList<>();
        for (CompetencyRelationPreviewDTO preview : previews) {
            if (!uniqueCompetencyIds.contains(preview.headCompetencyId())) {
                uniqueCompetencyIds.add(preview.headCompetencyId());
                nodes.add(new de.tum.cit.aet.artemis.atlas.dto.RelationGraphNodeDTO(String.valueOf(preview.headCompetencyId()), preview.headCompetencyTitle()));
            }
            if (!uniqueCompetencyIds.contains(preview.tailCompetencyId())) {
                uniqueCompetencyIds.add(preview.tailCompetencyId());
                nodes.add(new de.tum.cit.aet.artemis.atlas.dto.RelationGraphNodeDTO(String.valueOf(preview.tailCompetencyId()), preview.tailCompetencyTitle()));
            }
        }

        // Create edges from relations
        for (CompetencyRelationPreviewDTO preview : previews) {
            String edgeId = preview.relationId() != null ? "edge-" + preview.relationId() : "edge-new-" + preview.headCompetencyId() + "-" + preview.tailCompetencyId();
            edges.add(new de.tum.cit.aet.artemis.atlas.dto.RelationGraphEdgeDTO(edgeId, String.valueOf(preview.headCompetencyId()), String.valueOf(preview.tailCompetencyId()),
                    preview.relationType().name()));
        }

        // Store graph preview in ThreadLocal
        de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO graphPreview = new de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO(nodes, edges,
                viewOnly != null && viewOnly);
        currentRelationGraphPreview.set(graphPreview);

        // Cache the relation operation data for refinement operations
        String sessionId = currentSessionId.get();
        if (sessionId != null && !Boolean.TRUE.equals(viewOnly)) {
            atlasAgentService.cacheRelationOperations(sessionId, new ArrayList<>(relations));
        }

        // Return simple confirmation message
        if (relations.size() == 1) {
            return "Preview generated successfully for 1 relation mapping.";
        }
        else {
            return "Preview generated successfully for " + relations.size() + " relation mappings.";
        }
    }

    /**
     * Unified tool for creating/updating one or multiple competency relation mappings.
     * Supports both single and batch operations. Continues on partial failures.
     *
     * @param courseId  the course ID
     * @param relations list of relation operations (single or multiple)
     * @return JSON response with success/failure summary
     */
    @Tool(description = "Create or update one or multiple competency relation mappings. Automatically detects create vs update based on relationId presence. Also calls AtlasML endpoints for vector DB updates.")
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

        List<String> errors = new ArrayList<>();
        List<RelationOperation> successfulOperations = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;

        for (RelationOperation rel : relations) {
            try {
                // Validate
                String validationError = validateRelationOperation(rel);
                if (validationError != null) {
                    errors.add(validationError);
                    continue;
                }

                // Fetch competencies
                Optional<CourseCompetency> headComp = courseCompetencyRepository.findById(rel.getHeadCompetencyId());
                Optional<CourseCompetency> tailComp = courseCompetencyRepository.findById(rel.getTailCompetencyId());

                if (headComp.isEmpty() || tailComp.isEmpty()) {
                    errors.add("Competency not found for relation");
                    continue;
                }

                if (rel.getRelationId() == null) {
                    // Create new relation
                    CompetencyRelation relation = new CompetencyRelation();
                    relation.setHeadCompetency(headComp.get());
                    relation.setTailCompetency(tailComp.get());
                    relation.setType(rel.getRelationType());
                    competencyRelationRepository.save(relation);

                    // Call AtlasML endpoint for competency-to-competency mapping
                    callAtlasMLMapCompetencyToCompetency(rel.getHeadCompetencyId(), rel.getTailCompetencyId());

                    createCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(rel);
                }
                else {
                    // Update existing relation
                    Optional<CompetencyRelation> existing = competencyRelationRepository.findById(rel.getRelationId());
                    if (existing.isEmpty()) {
                        errors.add("Relation not found with ID: " + rel.getRelationId());
                        continue;
                    }

                    CompetencyRelation relation = existing.get();
                    relation.setHeadCompetency(headComp.get());
                    relation.setTailCompetency(tailComp.get());
                    relation.setType(rel.getRelationType());
                    competencyRelationRepository.save(relation);

                    // Call AtlasML endpoint for update
                    callAtlasMLMapCompetencyToCompetency(rel.getHeadCompetencyId(), rel.getTailCompetencyId());

                    updateCount++;
                    AtlasAgentService.markCompetencyModified();
                    successfulOperations.add(rel);
                }
            }
            catch (Exception e) {
                errors.add("Failed to save relation: " + e.getMessage());
            }
        }

        // Store preview data for successfully saved relations
        if (!successfulOperations.isEmpty()) {
            List<CompetencyRelationPreviewDTO> previews = new ArrayList<>();
            for (RelationOperation rel : successfulOperations) {
                Optional<CourseCompetency> headComp = courseCompetencyRepository.findById(rel.getHeadCompetencyId());
                Optional<CourseCompetency> tailComp = courseCompetencyRepository.findById(rel.getTailCompetencyId());

                if (headComp.isPresent() && tailComp.isPresent()) {
                    CompetencyRelationPreviewDTO preview = new CompetencyRelationPreviewDTO(rel.getRelationId(), rel.getHeadCompetencyId(), headComp.get().getTitle(),
                            rel.getTailCompetencyId(), tailComp.get().getTitle(), rel.getRelationType(), false);
                    previews.add(preview);
                }
            }

            if (successfulOperations.size() == 1) {
                SingleRelationPreviewResponseDTO singlePreview = new SingleRelationPreviewResponseDTO(true, previews.getFirst(), false);
                currentSingleRelationPreview.set(singlePreview);
            }
            else {
                BatchRelationPreviewResponseDTO batchPreview = new BatchRelationPreviewResponseDTO(true, previews.size(), previews, false);
                currentBatchRelationPreview.set(batchPreview);
            }
        }

        // Construct success message
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

        if (createCount > 0 || updateCount > 0) {
            AtlasAgentService.markCompetencyModified();
        }

        return toJson(response);
    }

    /**
     * Validates a relation operation for required fields.
     */
    private String validateRelationOperation(RelationOperation rel) {
        if (rel.getHeadCompetencyId() == null) {
            return "Missing head competency ID";
        }
        if (rel.getTailCompetencyId() == null) {
            return "Missing tail competency ID";
        }
        if (rel.getRelationType() == null) {
            return "Missing relation type";
        }
        if (rel.getHeadCompetencyId().equals(rel.getTailCompetencyId())) {
            return "Cannot create relation from competency to itself";
        }
        return null;
    }

    /**
     * Calls the AtlasML endpoint to map competency to competency in the vector database.
     */
    private void callAtlasMLMapCompetencyToCompetency(Long sourceCompetencyId, Long targetCompetencyId) {
        try {
            // TODO: Replace with actual AtlasML endpoint URL from configuration
            String atlasmlBaseUrl = "http://localhost:8000"; // This should come from configuration
            String endpoint = atlasmlBaseUrl + "/api/v1/competency/map-competency-to-competency";

            record MapRequest(Long source_competency_id, Long target_competency_id) {
            }
            MapRequest request = new MapRequest(sourceCompetencyId, targetCompetencyId);

            restTemplate.postForEntity(endpoint, request, Void.class);
        }
        catch (Exception e) {
            // Log but don't fail the operation if AtlasML call fails
            // The relation is still created in the main database
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
    public static de.tum.cit.aet.artemis.atlas.dto.RelationGraphPreviewDTO getRelationGraphPreview() {
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
