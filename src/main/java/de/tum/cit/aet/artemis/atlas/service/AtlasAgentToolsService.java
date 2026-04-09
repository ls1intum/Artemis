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
 * Rationale: This service allows the Atlas Agent to autonomously retrieve course information and
 * delegate to specialized sub-agents, enabling an interactive AI assistant for instructors.
 *
 * Main Responsibilities:
 * - Expose course-related data (competencies, exercises, descriptions) as AI-callable tools
 * - Delegate to specialized sub-agents (Competency Expert, Competency Mapper, Exercise Mapper) via tool calls
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/tools.html">Spring AI Function Calling</a>
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class AtlasAgentToolsService {

    private static final String RETURN_TO_MAIN_AGENT_MARKER = "%%ARTEMIS_RETURN_TO_MAIN_AGENT%%";

    private static final ThreadLocal<Long> currentCourseId = new ThreadLocal<>();

    private static final ThreadLocal<String> currentSessionId = new ThreadLocal<>();

    private final ObjectMapper objectMapper;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final AtlasAgentDelegationService delegationService;

    private final AtlasAgentToolCallbackService toolCallbackFactory;

    public AtlasAgentToolsService(ObjectMapper objectMapper, CourseRepository courseRepository, ExerciseRepository exerciseRepository,
            AtlasAgentDelegationService delegationService, AtlasAgentToolCallbackService toolCallbackFactory) {
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.delegationService = delegationService;
        this.toolCallbackFactory = toolCallbackFactory;
    }

    public static void setCurrentCourseId(Long courseId) {
        currentCourseId.set(courseId);
    }

    public static void clearCurrentCourseId() {
        currentCourseId.remove();
    }

    public static void setCurrentSessionId(String sessionId) {
        currentSessionId.set(sessionId);
    }

    public static void clearCurrentSessionId() {
        currentSessionId.remove();
    }

    /**
     * @param courseId ID of the course
     * @return course description or empty string if not found
     */
    @Tool(description = "Get the description of a course")
    public String getCourseDescription(@ToolParam(description = "the ID of the course") Long courseId) {
        return courseRepository.findById(courseId).map(Course::getDescription).orElse("");
    }

    /**
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
        List<AtlasAgentExerciseDTO> exerciseList = exercises.stream()
                .map(exercise -> new AtlasAgentExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), exercise.getMaxPoints(),
                        exercise.getReleaseDate() != null ? exercise.getReleaseDate().toString() : null, exercise.getDueDate() != null ? exercise.getDueDate().toString() : null))
                .toList();

        record Response(Long courseId, List<AtlasAgentExerciseDTO> exercises) {
        }
        return toJson(new Response(courseId, exerciseList));
    }

    /**
     * @param topic        what competency topic(s) to work on
     * @param requirements what the instructor wants done
     * @param constraints  any limitations or preferences
     * @param context      course context and background
     * @return the Competency Expert's response
     */
    @Tool(description = "Delegate to the Competency Expert sub-agent for creating, updating, viewing, or refining competencies. " + "Pass the gathered instructor requirements.")
    public String delegateToCompetencyExpert(@ToolParam(description = "What competency topic(s) to work on") String topic,
            @ToolParam(description = "What the instructor wants done") String requirements, @ToolParam(description = "Any limitations or preferences") String constraints,
            @ToolParam(description = "Course context and background") String context) {
        Long courseId = currentCourseId.get();
        String sessionId = currentSessionId.get();
        if (courseId == null || sessionId == null) {
            return "{\"error\": \"Internal error: missing request context\"}";
        }
        String brief = formatBrief("TOPIC", topic, requirements, constraints, context);

        CompetencyExpertToolsService.setCurrentSessionId(sessionId);
        String response = delegationService.delegateToAgent(AtlasAgentService.getPromptResourcePath(AtlasAgentService.AgentType.COMPETENCY_EXPERT), brief, courseId, sessionId,
                false, toolCallbackFactory.createCompetencyExpertProvider());
        return stripReturnMarker(response);
    }

    /**
     * @param topic        what competencies to map or relate
     * @param requirements what relation type and mapping to create
     * @param constraints  any limitations
     * @param context      why this relation makes sense
     * @return the Competency Mapper's response
     */
    @Tool(description = "Delegate to the Competency Mapper sub-agent for creating competency relations (ASSUMES, EXTENDS, MATCHES) "
            + "or viewing the competency relation graph. Pass the gathered mapping requirements.")
    public String delegateToCompetencyMapper(@ToolParam(description = "What competencies to map or relate") String topic,
            @ToolParam(description = "What relation type and mapping to create") String requirements, @ToolParam(description = "Any limitations") String constraints,
            @ToolParam(description = "Why this relation makes sense") String context) {
        Long courseId = currentCourseId.get();
        String sessionId = currentSessionId.get();
        if (courseId == null || sessionId == null) {
            return "{\"error\": \"Internal error: missing request context\"}";
        }
        String brief = formatBrief("TOPIC", topic, requirements, constraints, context);

        CompetencyMappingToolsService.setCurrentSessionId(sessionId);
        String response = delegationService.delegateToAgent(AtlasAgentService.getPromptResourcePath(AtlasAgentService.AgentType.COMPETENCY_MAPPER), brief, courseId, sessionId,
                false, toolCallbackFactory.createCompetencyMapperProvider());
        return stripReturnMarker(response);
    }

    /**
     * @param exerciseId    the numeric exercise ID from getExercisesListed
     * @param exerciseTitle the exercise title
     * @param requirements  what mapping to create
     * @param context       additional context
     * @return the Exercise Mapper's response
     */
    @Tool(description = "Delegate to the Exercise Mapper sub-agent for mapping exercises to competencies. "
            + "Always call getExercisesListed first to get the exercise ID and title.")
    public String delegateToExerciseMapper(@ToolParam(description = "The numeric exercise ID from getExercisesListed") Long exerciseId,
            @ToolParam(description = "The exercise title") String exerciseTitle, @ToolParam(description = "What mapping to create") String requirements,
            @ToolParam(description = "Additional context") String context) {
        Long courseId = currentCourseId.get();
        String sessionId = currentSessionId.get();
        if (courseId == null || sessionId == null) {
            return "{\"error\": \"Internal error: missing request context\"}";
        }
        String brief = "EXERCISE_ID: " + exerciseId + "\nEXERCISE_TITLE: " + exerciseTitle + "\nREQUIREMENTS: " + requirements + "\nCONTEXT: " + context;

        ExerciseMappingToolsService.setCurrentSessionId(sessionId);
        String response = delegationService.delegateToAgent(AtlasAgentService.getPromptResourcePath(AtlasAgentService.AgentType.EXERCISE_MAPPER), brief, courseId, sessionId, false,
                toolCallbackFactory.createExerciseMapperProvider());
        return stripReturnMarker(response);
    }

    private static String formatBrief(String topicLabel, String topic, String requirements, String constraints, String context) {
        return topicLabel + ": " + topic + "\nREQUIREMENTS: " + requirements + "\nCONSTRAINTS: " + constraints + "\nCONTEXT: " + context;
    }

    private static String stripReturnMarker(String response) {
        return response.replace(RETURN_TO_MAIN_AGENT_MARKER, "").trim();
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
