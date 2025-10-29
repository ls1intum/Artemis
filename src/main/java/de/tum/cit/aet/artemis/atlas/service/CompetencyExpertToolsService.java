package de.tum.cit.aet.artemis.atlas.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

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
     * Tool for generating a preview of how a competency will look.
     * Uses the same data structure as the student view competency cards.
     *
     * @param title         the competency title
     * @param description   the competency description
     * @param taxonomyLevel the taxonomy level (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE)
     * @param competencyId  optional ID of existing competency (for updates)
     * @return JSON representation of the competency preview (same format as the frontend receives)
     */
    @Tool(description = "REQUIRED: Display a visual competency card preview to the instructor. Must be called before creating/updating any competency. Returns formatted card data.")
    public String previewCompetency(@ToolParam(description = "the title of the competency") String title,
            @ToolParam(description = "the description of the competency") String description,
            @ToolParam(description = "the taxonomy level: REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, or CREATE") CompetencyTaxonomy taxonomyLevel,
            @ToolParam(description = "optional: the ID of the competency being updated (omit for new competencies)", required = false) Long competencyId) {

        // Create a preview competency object (without saving to database)
        Map<String, Object> competencyPreview = new LinkedHashMap<>();
        competencyPreview.put("title", title);
        competencyPreview.put("description", description);
        competencyPreview.put("taxonomy", taxonomyLevel.toString());

        // Map taxonomy to icon representation (matching frontend getIcon function)
        String iconName = switch (taxonomyLevel) {
            case REMEMBER -> "brain";
            case UNDERSTAND -> "comments";
            case APPLY -> "pen-fancy";
            case ANALYZE -> "magnifying-glass";
            case EVALUATE -> "plus-minus";
            case CREATE -> "cubes-stacked";
        };
        competencyPreview.put("icon", iconName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("preview", true);
        response.put("competency", competencyPreview);

        // Add competencyId OUTSIDE the competency object if this is an update
        if (competencyId != null) {
            response.put("competencyId", competencyId);
        }

        return toJson(response);
    }

    /**
     * Tool for creating a new competency in a course.
     * This is the creation tool available to the Competency Expert sub-agent.
     *
     * @param courseId      the course ID
     * @param title         the competency title
     * @param description   the competency description
     * @param taxonomyLevel the taxonomy level (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE)
     * @return JSON response indicating success or error
     */
    @Tool(description = "Create a new competency for a course")
    public String createCompetency(@ToolParam(description = "the ID of the course") Long courseId, @ToolParam(description = "the title of the competency") String title,
            @ToolParam(description = "the description of the competency") String description,
            @ToolParam(description = "the taxonomy level (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE)") CompetencyTaxonomy taxonomyLevel) {
        try {

            Optional<Course> courseOptional = courseRepository.findById(courseId);
            if (courseOptional.isEmpty()) {
                return toJson(Map.of("error", "Course not found with ID: " + courseId));
            }

            Course course = courseOptional.get();
            Competency competency = new Competency();
            competency.setTitle(title);
            competency.setDescription(description);
            competency.setCourse(course);
            competency.setTaxonomy(taxonomyLevel);

            Competency savedCompetency = competencyRepository.save(competency);

            this.competencyCreated = true;
            Map<String, Object> competencyData = new LinkedHashMap<>();
            competencyData.put("id", savedCompetency.getId());
            competencyData.put("title", savedCompetency.getTitle());
            competencyData.put("description", savedCompetency.getDescription());
            competencyData.put("taxonomy", savedCompetency.getTaxonomy() != null ? savedCompetency.getTaxonomy().toString() : "");
            competencyData.put("courseId", courseId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("competency", competencyData);

            return toJson(response);
        }
        catch (Exception e) {
            return toJson(Map.of("error", "Failed to create competency: " + e.getMessage()));
        }
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
     * Tool for updating an existing competency.
     * Allows modification of title, description, and taxonomy.
     *
     * @param competencyId  the ID of the competency to update
     * @param title         the new title (required)
     * @param description   the new description (required)
     * @param taxonomyLevel the new taxonomy level (required)
     * @return JSON response indicating success or error
     */
    @Tool(description = "Update an existing competency's title, description, and taxonomy")
    public String updateCompetency(@ToolParam(description = "the ID of the competency to update") Long competencyId,
            @ToolParam(description = "the new title of the competency") String title, @ToolParam(description = "the new description of the competency") String description,
            @ToolParam(description = "the new taxonomy level (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE)") CompetencyTaxonomy taxonomyLevel) {
        try {
            // Find the existing competency
            Optional<Competency> competencyOptional = competencyRepository.findById(competencyId);
            if (competencyOptional.isEmpty()) {
                return toJson(Map.of("error", "Competency not found with ID: " + competencyId));
            }

            Competency competency = competencyOptional.get();

            // Update the fields
            competency.setTitle(title.trim());
            competency.setDescription(description);
            competency.setTaxonomy(taxonomyLevel);

            // Save the updated competency
            Competency updatedCompetency = competencyRepository.save(competency);

            this.competencyUpdated = true;

            // Prepare response
            Map<String, Object> competencyData = new LinkedHashMap<>();
            competencyData.put("id", updatedCompetency.getId());
            competencyData.put("title", updatedCompetency.getTitle());
            competencyData.put("description", updatedCompetency.getDescription());
            competencyData.put("taxonomy", updatedCompetency.getTaxonomy() != null ? updatedCompetency.getTaxonomy().toString() : "");

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", "Competency updated successfully");
            response.put("competency", competencyData);

            return toJson(response);
        }
        catch (Exception e) {
            return toJson(Map.of("error", "Failed to update competency: " + e.getMessage()));
        }
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
