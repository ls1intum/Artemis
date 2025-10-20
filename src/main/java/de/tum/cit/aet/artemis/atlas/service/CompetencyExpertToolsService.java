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
     * @return JSON representation of the competency preview (same format as the frontend receives)
     */
    @Tool(description = "REQUIRED: Display a visual competency card preview to the instructor. Must be called before creating any competency. Returns formatted card data.")
    public String previewCompetency(@ToolParam(description = "the title of the competency") String title,
            @ToolParam(description = "the description of the competency") String description,
            @ToolParam(description = "the taxonomy level: REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, or CREATE") CompetencyTaxonomy taxonomyLevel) {

        System.out.println("===== previewCompetency TOOL CALLED =====");
        System.out.println("Title: " + title);
        System.out.println("Description: " + description);
        System.out.println("Taxonomy: " + taxonomyLevel);
        System.out.println("==========================================");

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
        response.put("message", "This is how the competency will appear to students. The card will display the icon, title, description, and taxonomy level as shown above.");

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
     * Check if any competency was created during this request.
     *
     * @return true if createCompetency was called during this request
     */
    public boolean wasCompetencyCreated() {
        return this.competencyCreated;
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
