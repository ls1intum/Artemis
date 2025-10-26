package de.tum.cit.aet.artemis.atlas.service;

import java.util.List;
import java.util.Map;
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
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentCompetencyDTO;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentExerciseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

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
public class AtlasAgentToolsService {

    private final ObjectMapper objectMapper;

    private final CompetencyRepository competencyRepository;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    /** Thread-safe flag tracking if competencies were modified during the current request. */
    private static final ThreadLocal<Boolean> competenciesModified = ThreadLocal.withInitial(() -> false);

    /**
     * @param objectMapper         Jackson object mapper for JSON serialization
     * @param competencyRepository repository for competency data access
     * @param courseRepository     repository for course data access
     * @param exerciseRepository   repository for exercise data access
     */
    public AtlasAgentToolsService(ObjectMapper objectMapper, CompetencyRepository competencyRepository, CourseRepository courseRepository, ExerciseRepository exerciseRepository) {
        this.objectMapper = objectMapper;
        this.competencyRepository = competencyRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Checks if any competencies were modified in the current thread.
     *
     * @return true if {@link #createCompetency} was called in the current thread
     */
    public static boolean wereCompetenciesModified() {
        return competenciesModified.get();
    }

    /**
     * Resets the modification flag for the current thread.
     * Should be called at the beginning of every LLM request to ensure clean state.
     */
    public static void resetCompetenciesModified() {
        competenciesModified.set(false);
    }

    /**
     * Cleans up the ThreadLocal variable to prevent memory leaks in thread pool environments.
     * Must be called in a finally block after processing a chat request.
     */
    public static void cleanup() {
        competenciesModified.remove();
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
            return toJson(Map.of("error", "Course not found with ID: " + courseId));
        }

        Set<Competency> competencies = competencyRepository.findAllByCourseId(courseId);
        List<AtlasAgentCompetencyDTO> competencyList = competencies.stream().map(AtlasAgentCompetencyDTO::of).toList();

        record Response(Long courseId, List<AtlasAgentCompetencyDTO> competencies) {
        }
        return toJson(new Response(courseId, competencyList));
    }

    /**
     * Creates a new competency for a given course.
     * The LLM typically calls this method when users request to create a new competency
     * If successful, the competency is persisted and the modification flag is set to true.
     *
     * @param courseId      ID of the course
     * @param title         title of the new competency
     * @param description   detailed description of the competency
     * @param taxonomyLevel Bloom’s taxonomy level
     * @return JSON response containing the created competency or an error message
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
            competenciesModified.set(true);

            record Response(boolean success, AtlasAgentCompetencyDTO competency) {
            }
            return toJson(new Response(true, AtlasAgentCompetencyDTO.of(savedCompetency, courseId)));
        }
        catch (Exception e) {
            return toJson(Map.of("error", "Failed to create competency: " + e.getMessage()));
        }
    }

    /**
     * Returns the description text of a specific course.
     * This helps the LLM understand course context when generating competencies or answers.
     *
     * @param courseId ID of the course
     * @return course description or empty string if not found
     */
    @Tool(description = "Get the description of a course")
    public String getCourseDescription(@ToolParam(description = "the ID of the course") Long courseId) {
        return courseRepository.findById(courseId).map(Course::getDescription).orElse("");
    }

    /**
     * Lists all exercises for a given course.
     * The LLM can use this to reason about course structure and existing learning material.
     *
     * @param courseId ID of the course
     * @return JSON containing exercises or an error message if course not found
     */
    @Tool(description = "List exercises for a course")
    public String getExercisesListed(@ToolParam(description = "the ID of the course") Long courseId) {
        Optional<Course> courseOptional = courseRepository.findById(courseId);
        if (courseOptional.isEmpty()) {
            return toJson(Map.of("error", "Course not found with ID: " + courseId));
        }

        Set<Exercise> exercises = exerciseRepository.findByCourseIds(Set.of(courseId));
        List<AtlasAgentExerciseDTO> exerciseList = exercises.stream().map(AtlasAgentExerciseDTO::of).toList();

        record Response(Long courseId, List<AtlasAgentExerciseDTO> exercises) {
        }
        return toJson(new Response(courseId, exerciseList));
    }

    /**
     * Instance-level wrapper for {@link #wereCompetenciesModified()} to ease mocking in tests.
     *
     * @return true if a competency was created during this request
     */
    public boolean wasCompetencyCreated() {
        return wereCompetenciesModified();
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }
}
