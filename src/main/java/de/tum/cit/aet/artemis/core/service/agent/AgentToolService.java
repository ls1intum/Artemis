package de.tum.cit.aet.artemis.core.service.agent;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.config.AgentAccessEnabled;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyRelationDTO;
import de.tum.cit.aet.artemis.atlas.dto.UpdateCourseCompetencyRelationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreateAnnouncementDTO;
import de.tum.cit.aet.artemis.core.dto.agent.AgentCourseRefDTO;
import de.tum.cit.aet.artemis.core.dto.agent.AgentLectureDTO;
import de.tum.cit.aet.artemis.core.dto.agent.JsonSchemaDTO;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ChangeRelationArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.CompetencyArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.CourseArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.CreateCompetenciesArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.CreateLectureArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.CreateProgrammingExerciseArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.CreateRelationArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ExerciseArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ImportCompetenciesArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.LectureArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ListExercisesArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ModelingExerciseArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.NoArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.PostAnnouncementArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ProblemStatementArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.ProgrammingExerciseArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.TestCaseWeightsArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.TextExerciseArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.TimelineArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.UpdateCompetencyArguments;
import de.tum.cit.aet.artemis.core.service.agent.AgentToolArguments.UpdateProgrammingExerciseArguments;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;

/**
 * The tools of the MCP endpoint.
 * <p>
 * There are few on purpose, each one a task an instructor would name rather than a wrapper of one endpoint: a model chooses better
 * among a handful of clear tools than among a thousand endpoints. A tool does its work by calling the REST API, see {@link AgentApi},
 * so what a tool may do is decided by the endpoints, never here. Uploading slides is not a tool: a file does not fit into a tool call.
 * <p>
 * The arguments of a tool are read as the DTO the endpoint it calls takes, see {@link AgentToolArguments}, and are sent on as that DTO.
 */
@Profile(PROFILE_CORE)
@Conditional(AgentAccessEnabled.class)
@Lazy
@Service
public class AgentToolService {

    private static final JsonSchemaDTO COURSE_ID = JsonSchemaDTO.integer("The id of the course, from list_my_courses.");

    private static final JsonSchemaDTO EXERCISE_ID = JsonSchemaDTO.integer("The id of the exercise.");

    private static final JsonSchemaDTO EXERCISE_TYPE = JsonSchemaDTO.choice("The kind of exercise.", List.of("text", "modeling", "programming"));

    private static final JsonSchemaDTO RELATION_TYPE = JsonSchemaDTO.choice(
            "ASSUMES: the tail assumes the student achieved the head. EXTENDS: the tail covers the head's topic in more detail. MATCHES: they are the same, for example a duplicate.",
            List.of("ASSUMES", "EXTENDS", "MATCHES"));

    private static final Set<AgentScope> EXERCISE_SCOPES = Set.of(AgentScope.EXERCISES_DRAFT, AgentScope.PROGRAMMING_DRAFT);

    private static final String EXERCISE_GUIDANCE = " The exercise must have a releaseDate in the future: that date publishes it on its own, so the instructor must review it before. "
            + "See the API description at /agent/openapi.yaml for the fields.";

    private final List<AgentTool<?>> tools;

    private final JsonMapper jsonMapper;

    public AgentToolService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.tools = List.of(courseTools(), competencyTools(), announcementTools(), exerciseTools(), programmingTools(), lectureTools()).stream().flatMap(List::stream).toList();
    }

    /**
     * Every tool, whatever the scopes of the token asking.
     *
     * @return the tools
     */
    public List<AgentTool<?>> getTools() {
        return tools;
    }

    private List<AgentTool<?>> courseTools() {
        return List.of(tool("list_my_courses", "List the courses the instructor belongs to, with their ids. Start here: every other tool needs a course id.", true,
                Set.of(AgentScope.COURSES_READ), schema(List.of()), NoArguments.class,
                (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.GET, "/api/course/courses/for-dropdown", null))));
    }

    private List<AgentTool<?>> competencyTools() {
        Set<AgentScope> read = Set.of(AgentScope.COMPETENCIES_READ);
        Set<AgentScope> write = Set.of(AgentScope.COMPETENCIES_WRITE);
        return List.of(tool("get_competency_graph", "Read the competency graph of a course: its competencies and the relations between them. Read this before you change anything.",
                true, read, schema(List.of("courseId"), property("courseId", COURSE_ID)), CourseArguments.class, (arguments, api) -> {
                    String base = "/api/atlas/courses/" + id(arguments.courseId(), "courseId");
                    AgentApiResponse competencies = api.send(HttpMethod.GET, base + "/course-competencies", null);
                    if (!competencies.isSuccess()) {
                        return AgentToolResult.of(competencies);
                    }
                    AgentApiResponse relations = api.send(HttpMethod.GET, base + "/course-competencies/relations", null);
                    if (!relations.isSuccess()) {
                        return AgentToolResult.of(relations);
                    }
                    return AgentToolResult.ok("Competencies:\n" + competencies.body() + "\n\nRelations:\n" + relations.body());
                }),
                tool("get_competency", "Read one competency with the exercises and lecture units linked to it.", true, read,
                        schema(List.of("courseId", "competencyId"), property("courseId", COURSE_ID), property("competencyId", JsonSchemaDTO.integer("The id of the competency."))),
                        CompetencyArguments.class,
                        (arguments,
                                api) -> AgentToolResult.of(api.send(HttpMethod.GET,
                                        "/api/atlas/courses/" + id(arguments.courseId(), "courseId") + "/competencies/" + id(arguments.competencyId(), "competencyId"), null))),
                tool("create_competencies", "Create competencies in a course, all or none. Show the instructor the list and get agreement first. Students see them at once.", false,
                        write,
                        schema(List.of("courseId", "competencies"), property("courseId", COURSE_ID),
                                property("competencies",
                                        JsonSchemaDTO.array(
                                                JsonSchemaDTO.document("A competency: title (at most 255 characters), description, masteryThreshold from 1 to 100, taxonomy.")))),
                        CreateCompetenciesArguments.class,
                        (arguments,
                                api) -> AgentToolResult.of(api.send(HttpMethod.POST, "/api/atlas/courses/" + id(arguments.courseId(), "courseId") + "/competencies/bulk",
                                        required(arguments.competencies(), "competencies")))),
                tool("update_competency", "Change a competency. Send the whole competency including its id; nothing is deleted. Students see the change at once.", false, write,
                        schema(List.of("courseId", "competency"), property("courseId", COURSE_ID),
                                property("competency", JsonSchemaDTO.document("The whole competency: id, title, description, masteryThreshold, taxonomy."))),
                        UpdateCompetencyArguments.class,
                        (arguments,
                                api) -> AgentToolResult.of(api.send(HttpMethod.PUT, "/api/atlas/courses/" + id(arguments.courseId(), "courseId") + "/competencies",
                                        required(arguments.competency(), "competency")))),
                tool("import_standardized_competencies", "Add competencies from the catalogue of standardized competencies to a course.", false, write,
                        schema(List.of("courseId", "catalogueIds"), property("courseId", COURSE_ID),
                                property("catalogueIds", JsonSchemaDTO.array(JsonSchemaDTO.integer("An entry of the catalogue.")))),
                        ImportCompetenciesArguments.class,
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.POST, "/api/atlas/courses/" + id(arguments.courseId(), "courseId") + "/competencies/import-standardized",
                                        required(arguments.catalogueIds(), "catalogueIds")))),
                tool("create_competency_relation", "Add an edge to the competency graph. A relation that would close a cycle is rejected.", false, write,
                        schema(List.of("courseId", "tailCompetencyId", "headCompetencyId", "relationType"), property("courseId", COURSE_ID),
                                property("tailCompetencyId", JsonSchemaDTO.integer("The competency that assumes, extends or matches the head.")),
                                property("headCompetencyId", JsonSchemaDTO.integer("The other competency.")), property("relationType", RELATION_TYPE)),
                        CreateRelationArguments.class,
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.POST, "/api/atlas/courses/" + id(arguments.courseId(), "courseId") + "/course-competencies/relations",
                                        new CompetencyRelationDTO(null, id(arguments.tailCompetencyId(), "tailCompetencyId"), id(arguments.headCompetencyId(), "headCompetencyId"),
                                                required(arguments.relationType(), "relationType"))))),
                tool("change_competency_relation", "Change the type of an existing relation. To move a relation, create a new one.", false, write,
                        schema(List.of("courseId", "relationId", "relationType"), property("courseId",
                                COURSE_ID), property("relationId", JsonSchemaDTO.integer("The id of the relation.")), property("relationType", RELATION_TYPE)),
                        ChangeRelationArguments.class,
                        (arguments,
                                api) -> AgentToolResult.of(api.send(HttpMethod.PATCH,
                                        "/api/atlas/courses/" + id(arguments.courseId(), "courseId") + "/course-competencies/relations/" + id(arguments.relationId(), "relationId"),
                                        new UpdateCourseCompetencyRelationDTO(required(arguments.relationType(), "relationType"))))));
    }

    private List<AgentTool<?>> announcementTools() {
        return List.of(tool("post_announcement",
                "Post an announcement to a course. Students see it and are notified AT ONCE and it cannot be taken back. Show the instructor the exact title and text first, and set confirm to true only after they clearly approved.",
                false, Set.of(AgentScope.ANNOUNCEMENTS_POST),
                schema(List.of("courseId", "content", "confirm"), property("courseId", COURSE_ID), property("title", JsonSchemaDTO.string("An optional headline.")),
                        property("content", JsonSchemaDTO.string("Markdown.")),
                        property("channelId", JsonSchemaDTO.integer("Only if the course has several announcement channels.")),
                        property("confirm", JsonSchemaDTO.bool("True only if the instructor approved this exact text."))),
                PostAnnouncementArguments.class, (arguments, api) -> {
                    if (!Boolean.TRUE.equals(arguments.confirm())) {
                        return AgentToolResult.error("Not posted. Show the instructor the exact text and call again with confirm set to true only after they approve it.");
                    }
                    return AgentToolResult.of(api.send(HttpMethod.POST, "/api/communication/courses/" + id(arguments.courseId(), "courseId") + "/announcements",
                            new CreateAnnouncementDTO(text(arguments.content(), "content"), arguments.title(), arguments.channelId())));
                }));
    }

    private List<AgentTool<?>> exerciseTools() {
        return List.of(
                tool("list_exercises", "List the text, modeling or programming exercises of a course, as summaries with their ids.", true, EXERCISE_SCOPES,
                        schema(List.of("courseId", "type"), property("courseId", COURSE_ID), property("type", EXERCISE_TYPE)), ListExercisesArguments.class,
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.GET, required(arguments.type(), "type").courseExercisesPath(id(arguments.courseId(), "courseId")), null))),
                tool("get_exercise", "Read all fields of one exercise. Read it before you change it.", true, EXERCISE_SCOPES,
                        schema(List.of("type", "exerciseId"), property("type", EXERCISE_TYPE), property("exerciseId", EXERCISE_ID)), ExerciseArguments.class,
                        (arguments, api) -> AgentToolResult
                                .complete(api.send(HttpMethod.GET, required(arguments.type(), "type").exercisesPath() + "/" + id(arguments.exerciseId(), "exerciseId"), null))),
                tool("create_text_exercise", "Create a text exercise as a draft." + EXERCISE_GUIDANCE, false, Set.of(AgentScope.EXERCISES_DRAFT),
                        schema(List.of("exercise"), property("exercise", JsonSchemaDTO.document("UpdateTextExerciseDTO of the API description, with courseId and without id."))),
                        TextExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.POST, AgentExerciseType.TEXT.exercisesPath(), required(arguments.exercise(), "exercise")))),
                tool("update_text_exercise",
                        "Change a text exercise that students cannot see yet. Send the whole exercise as get_exercise returned it, with your changes; a smaller body resets the rest."
                                + EXERCISE_GUIDANCE,
                        false, Set.of(AgentScope.EXERCISES_DRAFT),
                        schema(List.of("exercise"), property("exercise", JsonSchemaDTO.document("UpdateTextExerciseDTO of the API description, with its id."))),
                        TextExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.PUT, AgentExerciseType.TEXT.exercisesPath(), required(arguments.exercise(), "exercise")))),
                tool("create_modeling_exercise", "Create a modeling exercise as a draft. The diagramType cannot be changed later." + EXERCISE_GUIDANCE, false,
                        Set.of(AgentScope.EXERCISES_DRAFT),
                        schema(List.of("exercise"),
                                property("exercise", JsonSchemaDTO.document("UpdateModelingExerciseDTO of the API description, with courseId and without id."))),
                        ModelingExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.POST, AgentExerciseType.MODELING.exercisesPath(), required(arguments.exercise(), "exercise")))),
                tool("update_modeling_exercise",
                        "Change a modeling exercise that students cannot see yet. Send the whole exercise as get_exercise returned it, with your changes; a smaller body resets the rest."
                                + EXERCISE_GUIDANCE,
                        false, Set.of(AgentScope.EXERCISES_DRAFT),
                        schema(List.of("exercise"), property("exercise", JsonSchemaDTO.document("UpdateModelingExerciseDTO of the API description, with its id."))),
                        ModelingExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.PUT, AgentExerciseType.MODELING.exercisesPath(), required(arguments.exercise(), "exercise")))));
    }

    private List<AgentTool<?>> programmingTools() {
        Set<AgentScope> scopes = Set.of(AgentScope.PROGRAMMING_DRAFT);
        String base = AgentExerciseType.PROGRAMMING.exercisesPath();
        return List.of(
                tool("create_programming_exercise",
                        "Create a programming exercise as a draft. This sets up repositories and a build plan, so it takes a while." + EXERCISE_GUIDANCE, false, scopes,
                        schema(List.of("exercise"),
                                property("exercise", JsonSchemaDTO.document("CreateProgrammingExerciseDTO of the API description, with course and without id."))),
                        CreateProgrammingExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.POST, base + "/setup", required(arguments.exercise(), "exercise")))),
                tool("update_programming_exercise",
                        "Change a programming exercise that students cannot see and have not started. Send the whole exercise with your changes; a smaller body resets the rest. "
                                + "get_exercise returns the course as an object, but this tool needs courseId: copy course.id into it." + EXERCISE_GUIDANCE,
                        false, scopes,
                        schema(List.of("exercise"), property("exercise", JsonSchemaDTO.document("UpdateProgrammingExerciseDTO of the API description, with its id and courseId."))),
                        UpdateProgrammingExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.PUT, base, required(arguments.exercise(), "exercise")))),
                tool("get_programming_test_cases",
                        "List the test cases of a programming exercise with their ids, weights and visibility. Needed to set weights, which refer to the ids.", true, scopes,
                        schema(List.of("exerciseId"), property("exerciseId", EXERCISE_ID)), ProgrammingExerciseArguments.class,
                        (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.GET, base + "/" + id(arguments.exerciseId(), "exerciseId") + "/test-cases", null))),
                tool("set_programming_problem_statement", "Replace the problem statement (Markdown) of a programming exercise that students cannot see and have not started.",
                        false, scopes,
                        schema(List.of("exerciseId", "problemStatement"), property("exerciseId", EXERCISE_ID), property("problemStatement", JsonSchemaDTO.string("Markdown."))),
                        ProblemStatementArguments.class,
                        (arguments,
                                api) -> AgentToolResult.of(api.sendText(HttpMethod.PATCH, base + "/" + id(arguments.exerciseId(), "exerciseId") + "/problem-statement",
                                        text(arguments.problemStatement(), "problemStatement")))),
                tool("set_programming_timeline",
                        "Change selected dates of a programming exercise. Omitted fields retain the fetched values; explicit null clears a date. Normal server validation and automatic rebuild scheduling apply. releaseDate must stay in the future.",
                        false, scopes,
                        schema(List.of("timeline"),
                                property("timeline", JsonSchemaDTO.document("ProgrammingExerciseTimelineUpdateDTO of the API description: id, releaseDate, startDate, dueDate."))),
                        TimelineArguments.class, (arguments, api) -> {
                            var patch = required(arguments.timeline(), "timeline");
                            long exerciseId = id(patch.values().id(), "timeline.id");
                            var current = api.send(HttpMethod.GET, base + "/" + exerciseId, null);
                            if (!current.isSuccess()) {
                                return AgentToolResult.of(current);
                            }
                            var timeline = jsonMapper.readValue(required(current.body(), "exercise response"), ProgrammingExerciseTimelineUpdateDTO.class);
                            return AgentToolResult.of(api.send(HttpMethod.PUT, base + "/timeline", patch.merge(timeline)));
                        }),
                tool("set_programming_test_case_weights",
                        "Set weight, bonusMultiplier, bonusPoints and visibility of existing test cases of a programming exercise, by test case id.", false, scopes,
                        schema(List.of("exerciseId", "testCases"), property("exerciseId", EXERCISE_ID),
                                property("testCases", JsonSchemaDTO.array(JsonSchemaDTO.document("A test case: id, weight, bonusMultiplier, bonusPoints, visibility.")))),
                        TestCaseWeightsArguments.class, (arguments, api) -> AgentToolResult.of(api.send(HttpMethod.PATCH,
                                base + "/" + id(arguments.exerciseId(), "exerciseId") + "/update-test-cases", required(arguments.testCases(), "testCases")))));
    }

    private List<AgentTool<?>> lectureTools() {
        Set<AgentScope> scopes = Set.of(AgentScope.LECTURES_WRITE);
        return List.of(tool("create_lecture",
                "Create an empty lecture in a course. Students can see the lecture with its title and description AT ONCE, only its units can stay hidden with a releaseDate in the future. Show the instructor the title and description first, and set confirm to true only after they approved. Slides are uploaded with the REST API, not with a tool.",
                false, scopes,
                schema(List.of("courseId", "title", "confirm"), property("courseId", COURSE_ID), property("title", JsonSchemaDTO.string("The title.")),
                        property("description", JsonSchemaDTO.string("What the lecture is about.")), property("startDate", JsonSchemaDTO.dateTime("When it starts.")),
                        property("endDate", JsonSchemaDTO.dateTime("When it ends.")),
                        property("confirm", JsonSchemaDTO.bool("True only if the instructor approved this title and description."))),
                CreateLectureArguments.class, (arguments, api) -> {
                    if (!Boolean.TRUE.equals(arguments.confirm())) {
                        return AgentToolResult.error(
                                "Not created. Students see a lecture and its title and description at once. Show the instructor the exact title and description and call again with confirm set to true only after they approve them.");
                    }
                    var lecture = new AgentLectureDTO(text(arguments.title(), "title"), arguments.description(), arguments.startDate(), arguments.endDate(),
                            new AgentCourseRefDTO(id(arguments.courseId(), "courseId")));
                    return AgentToolResult.of(api.send(HttpMethod.POST, "/api/lecture/lectures", lecture));
                }),
                tool("get_lecture_processing_status",
                        "Report how far the background processing of the slides and videos of a lecture has come. processingPhase is IDLE, TRANSCRIBING, INGESTING, DONE, FAILED or SKIPPED.",
                        true, scopes, schema(List.of("lectureId"), property("lectureId", JsonSchemaDTO.integer("The id of the lecture."))), LectureArguments.class,
                        (arguments, api) -> AgentToolResult
                                .of(api.send(HttpMethod.GET, "/api/lecture/lectures/" + id(arguments.lectureId(), "lectureId") + "/lecture-units/statuses", null))));
    }

    private <A> AgentTool<A> tool(String name, String description, boolean readOnly, Set<AgentScope> scopes, JsonSchemaDTO inputSchema, Class<A> argumentsType,
            AgentTool.AgentToolHandler<A> handler) {
        return new AgentTool<>(name, description, scopes, readOnly, inputSchema, argumentsType, handler);
    }

    private static Map.Entry<String, JsonSchemaDTO> property(String name, JsonSchemaDTO schema) {
        return Map.entry(name, schema);
    }

    /**
     * The schema of the arguments of a tool.
     *
     * @param required   the names of the arguments the tool needs
     * @param properties the arguments in the order they are described
     * @return the schema
     */
    @SafeVarargs
    private static JsonSchemaDTO schema(List<String> required, Map.Entry<String, JsonSchemaDTO>... properties) {
        Map<String, JsonSchemaDTO> described = new LinkedHashMap<>();
        for (Map.Entry<String, JsonSchemaDTO> property : properties) {
            described.put(property.getKey(), property.getValue());
        }
        return JsonSchemaDTO.object(described, required);
    }

    private static long id(Long value, String field) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(field + " must be a whole number");
        }
        return value;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be a text that is not empty");
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value;
    }
}
