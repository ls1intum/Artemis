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
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentExerciseDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 *
 * Service providing tools for the Main Atlas Agent (Requirements Engineer/Orchestrator).
 * This agent detects user intent and routes to specialists.
 * Request-scoped to track tool calls per HTTP request.
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
     * @param courseId the course ID
     * @return the course description or empty string if not found
     */
    @Tool(description = "Get the description of a course")
    public String getCourseDescription(@ToolParam(description = "the ID of the course") Long courseId) {
        return courseRepository.findById(courseId).map(Course::getDescription).orElse("");
    }

    /**
     * Tool for getting exercises for a course.
     *
     * @param courseId the course ID
     * @return JSON representation of exercises
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
