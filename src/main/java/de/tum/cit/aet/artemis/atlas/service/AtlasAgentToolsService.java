package de.tum.cit.aet.artemis.atlas.service;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Service providing tools for the Atlas Agent using Spring AI's @Tool annotation.
 * Note: Not marked as @Lazy to ensure @Tool methods are properly scanned by MethodToolCallbackProvider.
 */
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolsService {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentToolsService.class);

    private final CompetencyRepository competencyRepository;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    // Thread-safe flag to track if competencies were modified in current request
    private static final ThreadLocal<Boolean> competenciesModified = ThreadLocal.withInitial(() -> false);

    public AtlasAgentToolsService(CompetencyRepository competencyRepository, CourseRepository courseRepository, ExerciseRepository exerciseRepository) {
        this.competencyRepository = competencyRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Check if competencies were modified in the current request.
     *
     * @return true if competencies were created/modified
     */
    public static boolean wereCompetenciesModified() {
        return competenciesModified.get();
    }

    /**
     * Reset the competencies modified flag. Should be called at the start of each request.
     */
    public static void resetCompetenciesModified() {
        competenciesModified.set(false);
    }

    /**
     * Clean up ThreadLocal to prevent memory leaks.
     */
    public static void cleanup() {
        competenciesModified.remove();
    }

    /**
     * Tool for getting course competencies.
     *
     * @param courseId the course ID
     * @return JSON representation of competencies
     */
    @Tool(description = "Get all competencies for a course")
    public String getCourseCompetencies(@ToolParam(description = "the ID of the course") Long courseId) {
        try {
            log.debug("Agent tool: Getting competencies for course {}", courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return "{\"error\": \"Course not found with ID: " + courseId + "\"}";
            }

            Set<Competency> competencies = competencyRepository.findAllByCourseId(courseId);

            StringBuilder result = new StringBuilder();
            result.append("{\"courseId\": ").append(courseId).append(", \"competencies\": [");

            competencies.forEach(competency -> result.append("{").append("\"id\": ").append(competency.getId()).append(", ").append("\"title\": \"")
                    .append(escapeJson(competency.getTitle())).append("\", ").append("\"description\": \"").append(escapeJson(competency.getDescription())).append("\", ")
                    .append("\"taxonomy\": \"").append(competency.getTaxonomy() != null ? competency.getTaxonomy() : "").append("\"").append("}, "));

            if (!competencies.isEmpty()) {
                result.setLength(result.length() - 2); // Remove last comma
            }

            result.append("]}");
            return result.toString();
        }
        catch (Exception e) {
            log.error("Error getting course competencies for course {}: {}", courseId, e.getMessage(), e);
            return "{\"error\": \"Failed to retrieve competencies: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Tool for creating a new competency in a course.
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
            @ToolParam(description = "the taxonomy level (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE)") String taxonomyLevel) {
        try {
            log.debug("Agent tool: Creating competency '{}' for course {}", title, courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return "{\"error\": \"Course not found with ID: " + courseId + "\"}";
            }

            Course course = courseOpt.get();
            Competency competency = new Competency();
            competency.setTitle(title);
            competency.setDescription(description);
            competency.setCourse(course);

            if (taxonomyLevel != null && !taxonomyLevel.isEmpty()) {
                try {
                    CompetencyTaxonomy taxonomy = CompetencyTaxonomy.valueOf(taxonomyLevel.toUpperCase());
                    competency.setTaxonomy(taxonomy);
                }
                catch (IllegalArgumentException e) {
                    log.warn("Invalid taxonomy level '{}', using default", taxonomyLevel);
                }
            }

            Competency savedCompetency = competencyRepository.save(competency);

            // Mark that competencies were modified in this request
            competenciesModified.set(true);

            return String.format("""
                    {
                        "success": true,
                        "competency": {
                            "id": %d,
                            "title": "%s",
                            "description": "%s",
                            "taxonomy": "%s",
                            "courseId": %d
                        }
                    }
                    """, savedCompetency.getId(), escapeJson(savedCompetency.getTitle()), escapeJson(savedCompetency.getDescription()),
                    savedCompetency.getTaxonomy() != null ? savedCompetency.getTaxonomy() : "", courseId);
        }
        catch (Exception e) {
            log.error("Error creating competency for course {}: {}", courseId, e.getMessage(), e);
            return "{\"error\": \"Failed to create competency: " + e.getMessage() + "\"}";
        }
    }

    /**
     * Tool for getting course description.
     *
     * @param courseId the course ID
     * @return the course description or empty string if not found
     */
    @Tool(description = "Get the description of a course")
    public String getCourseDescription(@ToolParam(description = "the ID of the course") Long courseId) {
        try {
            log.debug("Agent tool: Getting course description for course {}", courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return "";
            }

            Course course = courseOpt.get();
            String description = course.getDescription();
            return description != null ? description : "";
        }
        catch (Exception e) {
            log.error("Error getting course description for course {}: {}", courseId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * Tool for getting exercises for a course.
     *
     * @param courseId the course ID
     * @return JSON representation of exercises
     */
    @Tool(description = "List exercises for a course")
    public String getExercisesListed(@ToolParam(description = "the ID of the course") Long courseId) {
        try {
            log.debug("Agent tool: Getting exercises for course {}", courseId);

            Optional<Course> courseOpt = courseRepository.findById(courseId);
            if (courseOpt.isEmpty()) {
                return "{\"error\": \"Course not found with ID: " + courseId + "\"}";
            }

            // NOTE: adapt to your ExerciseRepository method
            Set<Exercise> exercises = exerciseRepository.findByCourseIds(Set.of(courseId));

            StringBuilder result = new StringBuilder();
            result.append("{\"courseId\": ").append(courseId).append(", \"exercises\": [");

            exercises.forEach(exercise -> result.append("{").append("\"id\": ").append(exercise.getId()).append(", ").append("\"title\": \"")
                    .append(escapeJson(exercise.getTitle())).append("\", ").append("\"type\": \"").append(exercise.getClass().getSimpleName()).append("\", ")
                    .append("\"maxPoints\": ").append(exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0).append(", ").append("\"releaseDate\": \"")
                    .append(exercise.getReleaseDate() != null ? exercise.getReleaseDate().toString() : "").append("\", ").append("\"dueDate\": \"")
                    .append(exercise.getDueDate() != null ? exercise.getDueDate().toString() : "").append("\"").append("}, "));

            if (!exercises.isEmpty()) {
                result.setLength(result.length() - 2);
            }

            result.append("]}");
            return result.toString();
        }
        catch (Exception e) {
            log.error("Error getting exercises for course {}: {}", courseId, e.getMessage(), e);
            return "{\"error\": \"Failed to retrieve exercises: " + e.getMessage() + "\"}";
        }
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
