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
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Service providing tools for the Main Atlas Agent (Requirements Engineer/Orchestrator).
 * This agent detects user intent and routes to specialists.
 * Request-scoped to track tool calls per HTTP request.
 */
@RequestScope
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolsService {

    private final ObjectMapper objectMapper;

    private final CompetencyRepository competencyRepository;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    public AtlasAgentToolsService(ObjectMapper objectMapper, CompetencyRepository competencyRepository, CourseRepository courseRepository, ExerciseRepository exerciseRepository) {
        this.objectMapper = objectMapper;
        this.competencyRepository = competencyRepository;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
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

        var exerciseList = exercises.stream().map(exercise -> {
            Map<String, Object> exerciseData = new LinkedHashMap<>();
            exerciseData.put("id", exercise.getId());
            exerciseData.put("title", exercise.getTitle());
            exerciseData.put("type", exercise.getClass().getSimpleName());
            exerciseData.put("maxPoints", exercise.getMaxPoints() != null ? exercise.getMaxPoints() : 0);
            exerciseData.put("releaseDate", exercise.getReleaseDate() != null ? exercise.getReleaseDate().toString() : "");
            exerciseData.put("dueDate", exercise.getDueDate() != null ? exercise.getDueDate().toString() : "");
            return exerciseData;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("courseId", courseId);
        response.put("exercises", exerciseList);

        return toJson(response);
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
