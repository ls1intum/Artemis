package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Service providing tools for the Competency Expert sub-agent.
 * This agent handles competency creation through conversational refinement.
 * Request-scoped to track tool calls per HTTP request.
 */
@RequestScope
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
        private String title;

        @JsonProperty
        private String description;

        @JsonProperty
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

    // Track which modification tools were called during this request
    private boolean competencyCreated = false;

    private boolean competencyUpdated = false;

    public CompetencyExpertToolsService(ObjectMapper objectMapper, CompetencyRepository competencyRepository, CourseRepository courseRepository) {
        this.objectMapper = objectMapper;
        this.competencyRepository = competencyRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Tool for getting course competencies.
     *
     * @param courseId the course ID
     * @return JSON representation of competencies
     */
    @Tool(description = "Get all competencies for a course")
    public String getCourseCompetencies(@ToolParam(description = "the ID of the course") Long courseId) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return toJson(Map.of("error", "Course not found with ID: " + courseId));
        }

        Set<Competency> competencies = competencyRepository.findAllByCourseId(courseId);

        var competencyList = competencies.stream().map(competency -> {
            Map<String, Object> competencyData = new LinkedHashMap<>();
            competencyData.put("id", competency.getId());
            competencyData.put("title", competency.getTitle());
            competencyData.put("description", competency.getDescription());
            competencyData.put("taxonomy", competency.getTaxonomy() != null ? competency.getTaxonomy().toString() : "");
            return competencyData;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("courseId", courseId);
        response.put("competencies", competencyList);

        return toJson(response);
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
     * Unified tool for previewing one or multiple competencies.
     * Supports both single and batch operations.
     *
     * @param courseId     the course ID
     * @param competencies list of competency operations (single or multiple)
     * @param viewOnly     optional flag for view-only mode
     * @return JSON response with single preview or batch preview
     */
    @Tool(description = "Preview one or multiple competencies before creating/updating. Pass a list with one item for single preview, or multiple items for batch preview.")
    public String previewCompetencies(@ToolParam(description = "the ID of the course") Long courseId,
            @ToolParam(description = "list of competency operations to preview") List<CompetencyOperation> competencies,
            @ToolParam(description = "optional: set to true for view-only mode (no action buttons)", required = false) Boolean viewOnly) {

        if (competencies == null || competencies.isEmpty()) {
            return toJson(Map.of("error", "No competencies provided"));
        }

        List<Map<String, Object>> previews = new ArrayList<>();

        for (CompetencyOperation comp : competencies) {
            Map<String, Object> competencyPreview = new LinkedHashMap<>();
            competencyPreview.put("title", comp.getTitle());
            competencyPreview.put("description", comp.getDescription());
            competencyPreview.put("taxonomy", comp.getTaxonomy().toString());

            // Map taxonomy to icon
            String iconName = switch (comp.getTaxonomy()) {
                case REMEMBER -> "brain";
                case UNDERSTAND -> "comments";
                case APPLY -> "pen-fancy";
                case ANALYZE -> "magnifying-glass";
                case EVALUATE -> "plus-minus";
                case CREATE -> "cubes-stacked";
            };
            competencyPreview.put("icon", iconName);

            // Add competencyId if this is an update
            if (comp.getCompetencyId() != null) {
                competencyPreview.put("competencyId", comp.getCompetencyId());
            }

            previews.add(competencyPreview);
        }

        Map<String, Object> response = new LinkedHashMap<>();

        // Single item: return single preview format (backward compatible)
        if (competencies.size() == 1) {
            response.put("preview", true);
            response.put("competency", previews.getFirst());

            if (competencies.getFirst().getCompetencyId() != null) {
                response.put("competencyId", competencies.getFirst().getCompetencyId());
            }

            if (viewOnly != null && viewOnly) {
                response.put("viewOnly", true);
            }
        }
        else {
            // Multiple items: return batch preview format
            response.put("batchPreview", true);
            response.put("count", competencies.size());
            response.put("competencies", previews);

            if (viewOnly != null && viewOnly) {
                response.put("viewOnly", true);
            }
        }

        return toJson(response);
    }

    /**
     * Unified tool for creating/updating one or multiple competencies.
     * Supports both single and batch operations. Continues on partial failures.
     *
     * @param courseId     the course ID
     * @param competencies list of competency operations (single or multiple)
     * @return JSON response with success/failure summary
     */
    @Tool(description = "Create or update one or multiple competencies. Automatically detects create vs update based on competencyId presence.")
    public String saveCompetencies(@ToolParam(description = "the ID of the course") Long courseId,
            @ToolParam(description = "list of competency operations to save") List<CompetencyOperation> competencies) {

        if (competencies == null || competencies.isEmpty()) {
            return toJson(Map.of("error", "No competencies provided"));
        }

        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return toJson(Map.of("error", "Course not found with ID: " + courseId));
        }

        Course course = courseOptional.get();
        List<String> errors = new ArrayList<>();
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
                    createCount++;
                    this.competencyCreated = true;
                }
                else {
                    // Update existing competency
                    Optional<Competency> existing = competencyRepository.findById(comp.getCompetencyId());
                    if (existing.isEmpty()) {
                        errors.add("Competency not found with ID: " + comp.getCompetencyId());
                        continue;
                    }

                    Competency competency = existing.get();
                    competency.setTitle(comp.getTitle().trim());
                    competency.setDescription(comp.getDescription());
                    competency.setTaxonomy(comp.getTaxonomy());
                    competencyRepository.save(competency);
                    updateCount++;
                    this.competencyUpdated = true;
                }
            }
            catch (Exception e) {
                errors.add("Failed to save '" + comp.getTitle() + "': " + e.getMessage());
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", errors.isEmpty());
        response.put("created", createCount);
        response.put("updated", updateCount);
        response.put("failed", errors.size());

        if (!errors.isEmpty()) {
            response.put("errors", errors);
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

        response.put("message", String.join(", ", messages));

        return toJson(response);
    }

    /**
     * Check if any competency was created during this request.
     *
     * @return true if createCompetency was called during this request
     */
    public boolean wasCompetencyCreated() {
        return this.competencyCreated;
    }

    /**
     * Check if any competency was updated during this request.
     *
     * @return true if updateCompetency was called during this request
     */
    public boolean wasCompetencyUpdated() {
        return this.competencyUpdated;
    }

    /**
     * Check if any competency was modified (created or updated) during this request.
     *
     * @return true if any modification tool was called
     */
    public boolean wasCompetencyModified() {
        return this.competencyCreated || this.competencyUpdated;
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
}
