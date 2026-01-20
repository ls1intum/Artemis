package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.AtlasMLApi;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.BatchRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.CompetencyRelationPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphEdgeDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphNodeDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.RelationGraphPreviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.SingleRelationPreviewResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.AtlasMLCompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
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

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CompetencyRelationRepository competencyRelationRepository;

    private final CompetencyRelationService competencyRelationService;

    private final CourseRepository courseRepository;

    private final AtlasAgentService atlasAgentService;

    private final AtlasMLApi atlasMLApi;

    // ThreadLocal storage for preview data - enables deterministic extraction without parsing LLM output
    private static final ThreadLocal<SingleRelationPreviewResponseDTO> currentSingleRelationPreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<BatchRelationPreviewResponseDTO> currentBatchRelationPreview = ThreadLocal.withInitial(() -> null);

    private static final ThreadLocal<RelationGraphPreviewDTO> currentRelationGraphPreview = ThreadLocal.withInitial(() -> null);

    private static final Logger log = LoggerFactory.getLogger(CompetencyMappingToolsService.class);

    // ThreadLocal to store the current sessionId for tool calls
    private static final ThreadLocal<String> currentSessionId = ThreadLocal.withInitial(() -> null);

    public CompetencyMappingToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository,
            CompetencyRelationRepository competencyRelationRepository, CompetencyRelationService competencyRelationService, CourseRepository courseRepository,
            @Lazy AtlasAgentService atlasAgentService, @Lazy AtlasMLApi atlasMLApi) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyRelationRepository = competencyRelationRepository;
        this.competencyRelationService = competencyRelationService;
        this.courseRepository = courseRepository;
        this.atlasAgentService = atlasAgentService;
        this.atlasMLApi = atlasMLApi;
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
            errorResponse("Course not found with ID: " + courseId);
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
            errorResponse("Course not found with ID: " + courseId);
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
            errorResponse("No active session available");
        }

        List<CompetencyRelationDTO> cachedData = atlasAgentService.getCachedRelationData(sessionId);
        if (cachedData == null || cachedData.isEmpty()) {
            errorResponse("No previewed relation data found for this session");
        }

        record Response(String sessionId, List<CompetencyRelationDTO> relations) {
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
            @ToolParam(description = "list of relation operations to preview") List<CompetencyRelationDTO> relations,
            @ToolParam(description = "optional: set to true for view-only mode (no action buttons)", required = false) Boolean viewOnly) {
        if (relations == null || relations.isEmpty()) {
            return "Error: No relations provided for preview.";
        }

        List<CompetencyRelationPreviewDTO> previews = buildPreviewDTOs(relations, viewOnly);
        if (previews == null) {
            return "Error: Competency not found for relation mapping";
        }

        storePreviewData(previews, viewOnly, relations.size());

        RelationGraphPreviewDTO graphPreview = buildGraphPreview(courseId, previews, viewOnly);
        currentRelationGraphPreview.set(graphPreview);

        // Cache the relation operation data for refinement operations (possible update request)
        String sessionId = currentSessionId.get();
        if (sessionId != null && !Boolean.TRUE.equals(viewOnly)) {
            atlasAgentService.cacheRelationOperations(sessionId, new ArrayList<>(relations));
        }

        return relations.size() == 1 ? "Preview generated successfully for 1 relation mapping." : "Preview generated successfully for " + relations.size() + " relation mappings.";
    }

    /**
     * Suggests competency relation mappings using ML clustering from AtlasML.
     * The LLM can call this tool to get ML-based suggestions for relations between competencies.
     *
     * @param courseId the ID of the course
     * @return JSON response with suggested relation mappings
     */
    @Tool(description = "Get ML-based suggested competency relation mappings for a course using clustering analysis. Returns suggested relations with relation types.")
    public String suggestRelationMappingsUsingML(@ToolParam(description = "the Course ID from the CONTEXT section") Long courseId) {
        try {
            SuggestCompetencyRelationsResponseDTO suggestionsResponse = atlasMLApi.suggestCompetencyRelations(courseId);

            if (suggestionsResponse == null || suggestionsResponse.relations() == null || suggestionsResponse.relations().isEmpty()) {
                errorResponse("No relation suggestions available from ML clustering");
            }

            List<CompetencyRelationDTO> suggestedRelations = new ArrayList<>();
            for (AtlasMLCompetencyRelationDTO atlasMLRelation : suggestionsResponse.relations()) {
                RelationType relationType;
                try {
                    relationType = RelationType.valueOf(atlasMLRelation.relationType());
                }
                catch (IllegalArgumentException e) {
                    continue;
                }

                suggestedRelations.add(new CompetencyRelationDTO(null, atlasMLRelation.headId(), atlasMLRelation.tailId(), relationType));
            }

            if (suggestedRelations.isEmpty()) {
                errorResponse("No valid relation suggestions found");
            }

            record SuggestResponse(int count, List<CompetencyRelationDTO> suggestions) {
            }
            return toJson(new SuggestResponse(suggestedRelations.size(), suggestedRelations));
        }
        catch (Exception e) {
            return errorResponse("Failed to get ML-based relation suggestions: " + e.getMessage());
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
    @Tool(description = "Create or update one or multiple competency relation mappings. Automatically detects create vs update based on relationId presence.")
    public String saveRelationMappings(@ToolParam(description = "the ID of the course") Long courseId,
            @ToolParam(description = "list of relation operations to save") List<CompetencyRelationDTO> relations) {

        if (relations == null || relations.isEmpty()) {
            return errorResponse("No relations provided");
        }

        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            errorResponse("Course not found with ID: " + courseId);
        }

        Course course = courseOptional.get();
        List<String> errors = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;

        for (CompetencyRelationDTO rel : relations) {
            try {
                Optional<CompetencyPair> competencies = fetchCompetencies(rel.headCompetencyId(), rel.tailCompetencyId());
                if (competencies.isEmpty()) {
                    errors.add("Competency not found for relation");
                    continue;
                }

                if (rel.id() == null) {
                    competencyRelationService.createCompetencyRelation(competencies.get().tail(), competencies.get().head(), rel.relationType(), course);
                    createCount++;

                    // Sync with AtlasML - map competency to competency for ML clustering
                    try {
                        atlasMLApi.mapCompetencyToCompetency(rel.headCompetencyId(), rel.tailCompetencyId());
                    }
                    catch (Exception e) {
                        log.warn("Failed to sync relation to AtlasML: {}", e.getMessage());
                    }
                }
                else {
                    Optional<CompetencyRelation> existing = competencyRelationRepository.findById(rel.id());
                    if (existing.isEmpty()) {
                        errors.add("Relation not found with ID: " + rel.id());
                        continue;
                    }

                    CompetencyRelation relation = existing.get();
                    relation.setHeadCompetency(competencies.get().head());
                    relation.setTailCompetency(competencies.get().tail());
                    relation.setType(rel.relationType());

                    competencyRelationRepository.save(relation);
                    updateCount++;
                }
            }
            catch (Exception e) {
                errors.add("Failed to save relation: " + e.getMessage());
            }
        }

        if (createCount > 0 || updateCount > 0) {
            AtlasAgentService.markCompetencyModified();
        }

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
    private Optional<CompetencyPair> fetchCompetencies(Long headId, Long tailId) {
        Optional<CourseCompetency> headCompetency = courseCompetencyRepository.findById(headId);
        Optional<CourseCompetency> tailCompetency = courseCompetencyRepository.findById(tailId);

        if (headCompetency.isEmpty() || tailCompetency.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new CompetencyPair(headCompetency.get(), tailCompetency.get()));
    }

    /**
     * Builds preview DTOs from relation operations.
     *
     * @param relations list of relation operations
     * @param viewOnly  whether this is view-only mode
     * @return list of preview DTOs, or null if any competency is not found
     */
    private List<CompetencyRelationPreviewDTO> buildPreviewDTOs(List<CompetencyRelationDTO> relations, Boolean viewOnly) {
        List<CompetencyRelationPreviewDTO> previews = new ArrayList<>();

        for (CompetencyRelationDTO rel : relations) {
            Optional<CompetencyPair> competencies = fetchCompetencies(rel.headCompetencyId(), rel.tailCompetencyId());
            if (competencies.isEmpty()) {
                return null;
            }

            CompetencyRelationPreviewDTO preview = new CompetencyRelationPreviewDTO(rel.id(), rel.headCompetencyId(), competencies.get().head().getTitle(), rel.tailCompetencyId(),
                    competencies.get().tail().getTitle(), rel.relationType(), viewOnly);
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
     * Builds a graph preview showing the complete graph with proposed changes applied.
     * Shows all existing relations plus the proposed changes (new relations added, updated relations replaced).
     *
     * @param courseId the ID of the course
     * @param previews the preview DTOs representing proposed changes
     * @param viewOnly whether this is view-only mode
     * @return the graph preview DTO with complete context
     */
    private RelationGraphPreviewDTO buildGraphPreview(Long courseId, List<CompetencyRelationPreviewDTO> previews, Boolean viewOnly) {
        List<RelationGraphNodeDTO> nodes = new ArrayList<>();
        List<RelationGraphEdgeDTO> edges = new ArrayList<>();
        Set<Long> competencyIds = new HashSet<>();

        // Collect relation IDs being updated (to exclude from existing relations)
        Set<Long> updatedRelationIds = new HashSet<>();
        if (previews != null) {
            for (CompetencyRelationPreviewDTO preview : previews) {
                if (preview.relationId() != null) {
                    updatedRelationIds.add(preview.relationId());
                }
            }
        }

        // Add all existing relations (except those being updated)
        Set<CompetencyRelation> existingRelations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId);
        for (CompetencyRelation relation : existingRelations) {
            if (!updatedRelationIds.contains(relation.getId())) {
                competencyIds.add(relation.getHeadCompetency().getId());
                competencyIds.add(relation.getTailCompetency().getId());
                edges.add(new RelationGraphEdgeDTO("edge-" + relation.getId(), String.valueOf(relation.getHeadCompetency().getId()),
                        String.valueOf(relation.getTailCompetency().getId()), relation.getType().name()));
            }
        }

        // Add previewed relations (new and updated)
        if (previews != null) {
            for (CompetencyRelationPreviewDTO preview : previews) {
                competencyIds.add(preview.headCompetencyId());
                competencyIds.add(preview.tailCompetencyId());

                String edgeId = preview.relationId() != null ? "edge-" + preview.relationId() : "edge-new-" + preview.headCompetencyId() + "-" + preview.tailCompetencyId();
                edges.add(new RelationGraphEdgeDTO(edgeId, String.valueOf(preview.headCompetencyId()), String.valueOf(preview.tailCompetencyId()), preview.relationType().name()));
            }
        }

        List<CourseCompetency> competencies = courseCompetencyRepository.findAllById(competencyIds);
        for (CourseCompetency competency : competencies) {
            nodes.add(new RelationGraphNodeDTO(String.valueOf(competency.getId()), competency.getTitle()));
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
     * Creates a JSON error response with the given message.
     *
     * @param message the error message
     * @return JSON string containing the error
     */
    private String errorResponse(String message) {
        record ErrorResponse(String error) {
        }
        return toJson(new ErrorResponse(message));
    }

    /**
     * Retrieves the current single relation preview from ThreadLocal.
     *
     * @return current single relation preview
     */
    public static SingleRelationPreviewResponseDTO getSingleRelationPreview() {
        return currentSingleRelationPreview.get();
    }

    /**
     * Retrieves the current batch relation preview from ThreadLocal.
     *
     * @return current batch relation preview
     */
    public static BatchRelationPreviewResponseDTO getBatchRelationPreview() {
        return currentBatchRelationPreview.get();
    }

    /**
     * Retrieves the current relation graph preview from ThreadLocal.
     *
     * @return current relation graph preview
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
