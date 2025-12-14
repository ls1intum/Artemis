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
import de.tum.cit.aet.artemis.atlas.dto.atlasAgent.AtlasAgentExerciseDTO;
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

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    public AtlasAgentToolsService(ObjectMapper objectMapper, CourseRepository courseRepository, ExerciseRepository exerciseRepository) {
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Tool for getting course description.
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
        List<AtlasAgentExerciseDTO> exerciseList = exercises.stream().map(exercise -> new AtlasAgentExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getType(),
                exercise.getMaxPoints(), exercise.getReleaseDate().toString(), exercise.getDueDate().toString())).toList();

        record Response(Long courseId, List<AtlasAgentExerciseDTO> exercises) {
        }
        return toJson(new Response(courseId, exerciseList));
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
