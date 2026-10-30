package de.tum.cit.aet.artemis.core.service.agent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;

/**
 * The tools of the MCP endpoint.
 * <p>
 * There are few on purpose, each one a task an instructor would name rather than a wrapper of one endpoint: a model chooses better
 * among a handful of clear tools than among a thousand endpoints. A tool does its work by calling the REST API, see {@link AgentApi},
 * so what a tool may do is decided by the endpoints, never here. Uploading slides is not a tool: a file does not fit into a tool call.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@Service
public class AgentToolService {

    private static final String COURSE_ID = "\"courseId\": {\"type\": \"integer\", \"description\": \"The id of the course, from list_my_courses.\"}";

    private static final String EXERCISE_TYPE = "\"type\": {\"type\": \"string\", \"enum\": [\"text\", \"modeling\", \"programming\"], \"description\": \"The kind of exercise.\"}";

    private static final String RELATION_TYPE = "\"relationType\": {\"type\": \"string\", \"enum\": [\"ASSUMES\", \"EXTENDS\", \"MATCHES\"], \"description\": \"ASSUMES: the tail assumes the student achieved the head. EXTENDS: the tail covers the head's topic in more detail. MATCHES: they are the same, for example a duplicate.\"}";

    private static final Set<AgentScope> EXERCISE_SCOPES = Set.of(AgentScope.EXERCISES_DRAFT, AgentScope.PROGRAMMING_DRAFT);

    private final JsonMapper jsonMapper;

    private final List<AgentTool> tools;

    public AgentToolService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.tools = List.of(courseTools(), competencyTools(), announcementTools(), exerciseTools(), lectureTools()).stream().flatMap(List::stream).toList();
    }

    /**
     * Every tool, whatever the scopes of the token asking.
     *
     * @return the tools
     */
    public List<AgentTool> getTools() {
        return tools;
    }

    private List<AgentTool> courseTools() {
        return List.of(tool("list_my_courses", "List the courses the instructor belongs to, with their ids. Start here: every other tool needs a course id.", true,
                Set.of(AgentScope.COURSES_READ), object(), (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.GET, "/api/course/courses/for-dropdown", null))));
    }

    private List<AgentTool> competencyTools() {
        Set<AgentScope> read = Set.of(AgentScope.COMPETENCIES_READ);
        Set<AgentScope> write = Set.of(AgentScope.COMPETENCIES_WRITE);
        return List.of(tool("get_competency_graph", "Read the competency graph of a course: its competencies and the relations between them. Read this before you change anything.",
                true, read, object(COURSE_ID), (arguments, api) -> {
                    String base = "/api/atlas/courses/" + id(arguments, "courseId");
                    AgentApiResponse competencies = api.send(HttpMethod.GET, base + "/course-competencies", null);
                    if (!competencies.isSuccess()) {
                        return AgentToolResult.of(competencies);
                    }
                    AgentApiResponse relations = api.send(HttpMethod.GET, base + "/course-competencies/relations", null);
                    if (!relations.isSuccess()) {
                        return AgentToolResult.of(relations);
                    }
                    ObjectNode graph = jsonMapper.createObjectNode();
                    graph.set("competencies", competencies.body());
                    graph.set("relations", relations.body());
                    return AgentToolResult.ok(graph.toString());
                }),
                tool("get_competency", "Read one competency with the exercises and lecture units linked to it.", true, read,
                        object(COURSE_ID, "\"competencyId\": {\"type\": \"integer\"}"),
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.GET, "/api/atlas/courses/" + id(arguments, "courseId") + "/competencies/" + id(arguments, "competencyId"), null))),
                tool("create_competencies", "Create competencies in a course, all or none. Show the instructor the list and get agreement first.", false, write, object(
                        List.of("courseId", "competencies"), COURSE_ID,
                        "\"competencies\": {\"type\": \"array\", \"items\": {\"type\": \"object\", \"properties\": {\"title\": {\"type\": \"string\", \"maxLength\": 255}, \"description\": {\"type\": \"string\"}, \"masteryThreshold\": {\"type\": \"integer\", \"minimum\": 1, \"maximum\": 100}, \"taxonomy\": {\"type\": \"string\"}}, \"required\": [\"title\", \"masteryThreshold\"]}}"),
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.POST, "/api/atlas/courses/" + id(arguments, "courseId") + "/competencies/bulk", array(arguments, "competencies")))),
                tool("update_competency", "Change a competency. Send the whole competency including its id; nothing is deleted.", false, write,
                        object(List.of("courseId", "competency"), COURSE_ID,
                                "\"competency\": {\"type\": \"object\", \"description\": \"id, title, description, masteryThreshold, taxonomy.\"}"),
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.PUT, "/api/atlas/courses/" + id(arguments, "courseId") + "/competencies", objectArgument(arguments, "competency")))),
                tool("import_standardized_competencies", "Add competencies from the catalogue of standardized competencies to a course.", false, write,
                        object(List.of("courseId", "catalogueIds"), COURSE_ID, "\"catalogueIds\": {\"type\": \"array\", \"items\": {\"type\": \"integer\"}}"),
                        (arguments,
                                api) -> AgentToolResult.of(api.send(HttpMethod.POST, "/api/atlas/courses/" + id(arguments, "courseId") + "/competencies/import-standardized",
                                        array(arguments, "catalogueIds")))),
                tool("create_competency_relation", "Add an edge to the competency graph. A relation that would close a cycle is rejected.", false, write,
                        object(List.of("courseId", "tailCompetencyId", "headCompetencyId", "relationType"), COURSE_ID, "\"tailCompetencyId\": {\"type\": \"integer\"}",
                                "\"headCompetencyId\": {\"type\": \"integer\"}", RELATION_TYPE),
                        (arguments, api) -> {
                            ObjectNode relation = jsonMapper.createObjectNode();
                            relation.put("tailCompetencyId", id(arguments, "tailCompetencyId"));
                            relation.put("headCompetencyId", id(arguments, "headCompetencyId"));
                            relation.put("relationType", text(arguments, "relationType"));
                            return AgentToolResult.of(api.send(HttpMethod.POST, "/api/atlas/courses/" + id(arguments, "courseId") + "/course-competencies/relations", relation));
                        }),
                tool("change_competency_relation", "Change the type of an existing relation. To move a relation, create a new one.", false, write,
                        object(List.of("courseId", "relationId", "relationType"), COURSE_ID, "\"relationId\": {\"type\": \"integer\"}", RELATION_TYPE), (arguments, api) -> {
                            ObjectNode change = jsonMapper.createObjectNode();
                            change.put("newRelationType", text(arguments, "relationType"));
                            return AgentToolResult.of(api.send(HttpMethod.PATCH,
                                    "/api/atlas/courses/" + id(arguments, "courseId") + "/course-competencies/relations/" + id(arguments, "relationId"), change));
                        }));
    }

    private List<AgentTool> announcementTools() {
        return List.of(tool("post_announcement",
                "Post an announcement to a course. Students see it and are notified AT ONCE and it cannot be taken back. Show the instructor the exact title and text first, and set confirm to true only after they clearly approved.",
                false, Set.of(AgentScope.ANNOUNCEMENTS_POST),
                object(List.of("courseId", "content", "confirm"), COURSE_ID, "\"title\": {\"type\": \"string\"}",
                        "\"content\": {\"type\": \"string\", \"description\": \"Markdown.\"}",
                        "\"channelId\": {\"type\": \"integer\", \"description\": \"Only if the course has several announcement channels.\"}",
                        "\"confirm\": {\"type\": \"boolean\", \"description\": \"True only if the instructor approved this exact text.\"}"),
                (arguments, api) -> {
                    if (!arguments.path("confirm").asBoolean(false)) {
                        return AgentToolResult.error("Not posted. Show the instructor the exact text and call again with confirm set to true only after they approve it.");
                    }
                    ObjectNode announcement = jsonMapper.createObjectNode();
                    announcement.put("content", text(arguments, "content"));
                    copyIfPresent(announcement, arguments, "title", "channelId");
                    return AgentToolResult.of(api.send(HttpMethod.POST, "/api/communication/courses/" + id(arguments, "courseId") + "/announcements", announcement));
                }));
    }

    private List<AgentTool> exerciseTools() {
        String exerciseGuidance = " The exercise must have a releaseDate in the future, so that it stays a draft; the instructor publishes it in the Artemis web interface. See the API description at /agent/openapi.yaml for the fields.";
        return List.of(
                tool("list_exercises", "List the text, modeling or programming exercises of a course, as summaries with their ids.", true, EXERCISE_SCOPES,
                        object(List.of("courseId", "type"), COURSE_ID, EXERCISE_TYPE),
                        (arguments,
                                api) -> AgentToolResult.of(api.send(HttpMethod.GET,
                                        "/api/" + exerciseType(arguments) + "/courses/" + id(arguments, "courseId") + "/" + exerciseType(arguments) + "-exercises", null))),
                tool("get_exercise", "Read all fields of one exercise. Read it before you change it.", true, EXERCISE_SCOPES,
                        object(List.of("type", "exerciseId"), EXERCISE_TYPE, "\"exerciseId\": {\"type\": \"integer\"}"),
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.GET, exerciseBase(exerciseType(arguments)) + "/" + id(arguments, "exerciseId"), null))),
                tool("create_exercise", "Create an exercise as a draft." + exerciseGuidance, false, EXERCISE_SCOPES,
                        object(List.of("type", "exercise"), EXERCISE_TYPE, "\"exercise\": {\"type\": \"object\", \"description\": \"The exercise as the REST API takes it.\"}"),
                        (arguments, api) -> {
                            String type = exerciseType(arguments);
                            String path = exerciseBase(type) + ("programming".equals(type) ? "/setup" : "");
                            return AgentToolResult.of(api.send(HttpMethod.POST, path, objectArgument(arguments, "exercise")));
                        }),
                tool("update_exercise",
                        "Change an exercise that students cannot see yet. Send the whole exercise as get_exercise returned it, with your changes; a smaller body resets the rest."
                                + exerciseGuidance,
                        false, EXERCISE_SCOPES,
                        object(List.of("type", "exercise"), EXERCISE_TYPE, "\"exercise\": {\"type\": \"object\", \"description\": \"The whole exercise, with its id.\"}"),
                        (arguments, api) -> {
                            String type = exerciseType(arguments);
                            return AgentToolResult.of(api.send(HttpMethod.PUT, exerciseBase(type), updateShape(type, objectArgument(arguments, "exercise"))));
                        }),
                tool("get_programming_test_cases",
                        "List the test cases of a programming exercise with their ids, weights and visibility. Needed to set weights, which refer to the ids.", true,
                        Set.of(AgentScope.PROGRAMMING_DRAFT), object(List.of("exerciseId"), "\"exerciseId\": {\"type\": \"integer\"}"),
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.GET, "/api/programming/programming-exercises/" + id(arguments, "exerciseId") + "/test-cases", null))),
                tool("set_programming_problem_statement", "Replace the problem statement (Markdown) of a programming exercise that students cannot see and have not started.",
                        false, Set.of(AgentScope.PROGRAMMING_DRAFT),
                        object(List.of("exerciseId", "problemStatement"), "\"exerciseId\": {\"type\": \"integer\"}", "\"problemStatement\": {\"type\": \"string\"}"),
                        (arguments,
                                api) -> AgentToolResult.of(api.sendText(HttpMethod.PATCH,
                                        "/api/programming/programming-exercises/" + id(arguments, "exerciseId") + "/problem-statement", text(arguments, "problemStatement")))),
                tool("set_programming_timeline", "Change the dates of a programming exercise. releaseDate must stay in the future.", false, Set.of(AgentScope.PROGRAMMING_DRAFT),
                        object(List.of("timeline"), "\"timeline\": {\"type\": \"object\", \"description\": \"id, releaseDate, startDate, dueDate.\"}"),
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.PUT, "/api/programming/programming-exercises/timeline", objectArgument(arguments, "timeline")))),
                tool("set_programming_test_case_weights",
                        "Set weight, bonusMultiplier, bonusPoints and visibility of existing test cases of a programming exercise, by test case id.", false,
                        Set.of(AgentScope.PROGRAMMING_DRAFT),
                        object(List.of("exerciseId", "testCases"), "\"exerciseId\": {\"type\": \"integer\"}",
                                "\"testCases\": {\"type\": \"array\", \"items\": {\"type\": \"object\"}}"),
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.PATCH,
                                "/api/programming/programming-exercises/" + id(arguments, "exerciseId") + "/update-test-cases", array(arguments, "testCases")))));
    }

    private List<AgentTool> lectureTools() {
        Set<AgentScope> scopes = Set.of(AgentScope.LECTURES_WRITE);
        return List.of(tool("create_lecture",
                "Create an empty lecture in a course. Students can see the lecture with its title and description AT ONCE, only its units can stay hidden with a releaseDate in the future. Show the instructor the title and description first, and set confirm to true only after they approved. Slides are uploaded with the REST API, not with a tool.",
                false, scopes,
                object(List.of("courseId", "title", "confirm"), COURSE_ID, "\"title\": {\"type\": \"string\"}", "\"description\": {\"type\": \"string\"}",
                        "\"startDate\": {\"type\": \"string\", \"format\": \"date-time\"}", "\"endDate\": {\"type\": \"string\", \"format\": \"date-time\"}",
                        "\"confirm\": {\"type\": \"boolean\", \"description\": \"True only if the instructor approved this title and description.\"}"),
                (arguments, api) -> {
                    if (!arguments.path("confirm").asBoolean(false)) {
                        return AgentToolResult.error(
                                "Not created. Students see a lecture and its title and description at once. Show the instructor the exact title and description and call again with confirm set to true only after they approve them.");
                    }
                    ObjectNode lecture = jsonMapper.createObjectNode();
                    lecture.put("title", text(arguments, "title"));
                    lecture.putObject("course").put("id", id(arguments, "courseId"));
                    copyIfPresent(lecture, arguments, "description", "startDate", "endDate");
                    return AgentToolResult.of(api.send(HttpMethod.POST, "/api/lecture/lectures", lecture));
                }),
                tool("get_lecture_processing_status",
                        "Report how far the background processing of the slides and videos of a lecture has come. processingPhase is IDLE, TRANSCRIBING, INGESTING, DONE, FAILED or SKIPPED.",
                        true, scopes, object(List.of("lectureId"), "\"lectureId\": {\"type\": \"integer\"}"),
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.GET, "/api/lecture/lectures/" + id(arguments, "lectureId") + "/lecture-units/statuses", null))));
    }

    private AgentTool tool(String name, String description, boolean readOnly, Set<AgentScope> scopes, String inputSchema, AgentTool.AgentToolHandler handler) {
        return new AgentTool(name, description, scopes, readOnly, jsonMapper.readTree(inputSchema), handler);
    }

    /** A schema with no required property, given as the text of each property. */
    private static String object(String... properties) {
        return objectSchema(List.of(), properties);
    }

    private static String object(List<String> required, String... properties) {
        return objectSchema(required, properties);
    }

    private static String objectSchema(List<String> required, String... properties) {
        String requiredNames = String.join(", ", required.stream().map(name -> "\"" + name + "\"").toList());
        return "{\"type\": \"object\", \"properties\": {" + String.join(", ", properties) + "}, \"required\": [" + requiredNames + "]}";
    }

    private static long id(JsonNode arguments, String field) {
        JsonNode value = arguments.path(field);
        if (!value.isIntegralNumber() || value.asLong() < 0) {
            throw new IllegalArgumentException(field + " must be a whole number");
        }
        return value.asLong();
    }

    private static String text(JsonNode arguments, String field) {
        JsonNode value = arguments.path(field);
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a text that is not empty");
        }
        return value.asString();
    }

    private static JsonNode objectArgument(JsonNode arguments, String field) {
        JsonNode value = arguments.path(field);
        if (!value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value;
    }

    private static ArrayNode array(JsonNode arguments, String field) {
        JsonNode value = arguments.path(field);
        if (!(value instanceof ArrayNode arrayNode)) {
            throw new IllegalArgumentException(field + " must be a list");
        }
        return arrayNode;
    }

    /** The kind of exercise, checked against the three that exist, so that it can be part of a path. */
    private static String exerciseType(JsonNode arguments) {
        String type = text(arguments, "type");
        if (!List.of("text", "modeling", "programming").contains(type)) {
            throw new IllegalArgumentException("type must be text, modeling or programming");
        }
        return type;
    }

    private static String exerciseBase(String type) {
        return "/api/" + type + "/" + type + "-exercises";
    }

    /**
     * The programming exercise is read with a nested course but changed with a courseId, so the exercise as get_exercise returned
     * it would be rejected. Text and modeling exercises are read with a courseId and need no conversion.
     */
    private static JsonNode updateShape(String type, JsonNode exercise) {
        JsonNode courseId = exercise.path("course").path("id");
        if (!"programming".equals(type) || exercise.hasNonNull("courseId") || !courseId.isNumber()) {
            return exercise;
        }
        ObjectNode shaped = ((ObjectNode) exercise).deepCopy();
        shaped.set("courseId", courseId);
        return shaped;
    }

    private static void copyIfPresent(ObjectNode target, JsonNode source, String... fields) {
        for (String field : fields) {
            JsonNode value = source.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                target.set(field, value);
            }
        }
    }
}
