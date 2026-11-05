package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Programming exercises are created and changed with a personal access token only while they are a draft that no student has started.
 */
class AgentAccessProgrammingExerciseIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "agentprog";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository exerciseRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        personalAccessTokenRepository.deleteAll();
    }

    private void releaseAt(ZonedDateTime releaseDate) {
        exercise.setReleaseDate(releaseDate.truncatedTo(ChronoUnit.MILLIS));
        exercise.setStartDate(null);
        // The dates of the fixture lie before a release date in the future, which an update would reject as invalid.
        exercise.setDueDate(null);
        exercise.setAssessmentDueDate(null);
        exercise.setExampleSolutionPublicationDate(null);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        exercise = exerciseRepository.save(exercise);
    }

    @Test
    void creatingAnExerciseWithoutAFutureReleaseDateIsRejectedBeforeAnythingIsSetUp() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);

        perform(post("/api/programming/programming-exercises/setup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Agent exercise\",\"course\":{\"id\":" + course.getId() + "}}"), token).andExpect(status().isBadRequest());
    }

    @Test
    void aTutorTokenCannotCreateProgrammingExercises() throws Exception {
        SavedToken token = tokenOf("tutor1", AgentScope.PROGRAMMING_DRAFT);

        perform(post("/api/programming/programming-exercises/setup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Agent exercise\",\"course\":{\"id\":" + course.getId() + "}}"), token).andExpect(status().isForbidden());
    }

    @Test
    void aTokenCanReadAnExercise() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);

        perform(get("/api/programming/programming-exercises/" + exercise.getId()), token).andExpect(status().isOk());
        perform(get("/api/programming/courses/" + course.getId() + "/programming-exercises"), token).andExpect(status().isOk());
    }

    @Test
    void aTokenCanReadTheTestCasesOfAnExerciseToFindTheirIds() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);

        perform(get("/api/programming/programming-exercises/" + exercise.getId() + "/test-cases"), token).andExpect(status().isOk());
    }

    @Test
    void theTestCasesOfAnExamExerciseCannotBeReadWithAToken() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();

        perform(get("/api/programming/programming-exercises/" + examExercise.getId() + "/test-cases"), token).andExpect(status().isForbidden());
    }

    @Test
    void aTokenCannotReadTestCasesWithoutTheProgrammingScope() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);

        perform(get("/api/programming/programming-exercises/" + exercise.getId() + "/test-cases"), token).andExpect(status().isForbidden());
    }

    @Test
    void aProgrammingExerciseReadByTheToolCanBeSentBackToTheUpdateTool() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));

        JsonNode read = callTool(token, "get_exercise", Map.of("type", "programming", "exerciseId", exercise.getId()));
        assertThat(read.at("/result/isError").asBoolean()).isFalse();
        ObjectNode readExercise = (ObjectNode) JsonObjectMapper.get().readTree(read.at("/result/content/0/text").asString());
        assertThat(readExercise.has("courseId")).as("the read shape carries the course as an object").isFalse();
        // The tool needs the course as courseId, which the description of the tool says to copy from the course object.
        readExercise.put("courseId", readExercise.at("/course/id").asLong());

        JsonNode update = callTool(token, "update_programming_exercise", Map.of("exercise", readExercise));

        assertThat(update.at("/result/isError").asBoolean()).isFalse();
        String text = update.at("/result/content/0/text").asString();
        assertThat(text).doesNotContain("Invalid arguments").doesNotContain("courseId").doesNotContain("HTTP 400");
    }

    @Test
    void aLargeExerciseIsReturnedAsCompleteJsonAndCanBeUpdated() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));
        String problem = "A long problem statement. ".repeat(1_000);
        exercise.setProblemStatement(problem);
        exercise = exerciseRepository.save(exercise);

        JsonNode read = callTool(token, "get_exercise", Map.of("type", "programming", "exerciseId", exercise.getId()));
        assertThat(read.at("/result/isError").asBoolean()).isFalse();
        ObjectNode body = (ObjectNode) JsonObjectMapper.get().readTree(read.at("/result/content/0/text").asString());
        assertThat(body.path("problemStatement").asString()).isEqualTo(problem);
        body.put("courseId", body.at("/course/id").asLong());
        body.put("title", "Updated large exercise");

        JsonNode update = callTool(token, "update_programming_exercise", Map.of("exercise", body));
        assertThat(update.at("/result/isError").asBoolean()).isFalse();
        var stored = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(stored.getTitle()).isEqualTo("Updated large exercise");
        assertThat(stored.getProblemStatement()).isEqualTo(problem);
    }

    @Test
    void changingOneTimelineDatePreservesOmittedDatesAndAssessmentSettings() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));
        exercise.setDueDate(exercise.getReleaseDate().plusDays(1));
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exercise.setAssessmentDueDate(exercise.getReleaseDate().plusDays(5));
        exercise.setExampleSolutionPublicationDate(exercise.getReleaseDate().plusDays(6));
        exercise = exerciseRepository.save(exercise);
        ZonedDateTime newDueDate = exercise.getReleaseDate().plusDays(2);

        JsonNode result = callTool(token, "set_programming_timeline",
                Map.of("timeline", Map.of("id", exercise.getId(), "releaseDate", exercise.getReleaseDate().toString(), "dueDate", newDueDate.toString())));

        assertThat(result.at("/result/isError").asBoolean()).as(result.toString()).isFalse();
        var stored = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(stored.getDueDate()).isEqualTo(newDueDate);
        assertThat(stored.getReleaseDate()).isEqualTo(exercise.getReleaseDate());
        assertThat(stored.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(stored.getAssessmentDueDate()).isEqualTo(exercise.getAssessmentDueDate());
        assertThat(stored.getExampleSolutionPublicationDate()).isEqualTo(exercise.getExampleSolutionPublicationDate());
        // The server derives this date from the automatic-build configuration, which this fixture leaves disabled.
        assertThat(stored.getBuildAndTestStudentSubmissionsAfterDueDate()).isNull();
    }

    @Test
    void anExplicitNullClearsADateWithoutClearingOmittedDates() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));
        exercise.setExampleSolutionPublicationDate(exercise.getReleaseDate().plusDays(6));
        exercise = exerciseRepository.save(exercise);
        Map<String, Object> timeline = new HashMap<>();
        timeline.put("id", exercise.getId());
        timeline.put("exampleSolutionPublicationDate", null);

        JsonNode result = callTool(token, "set_programming_timeline", Map.of("timeline", timeline));

        assertThat(result.at("/result/isError").asBoolean()).as(result.toString()).isFalse();
        var stored = exerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(stored.getExampleSolutionPublicationDate()).isNull();
        assertThat(stored.getReleaseDate()).isEqualTo(exercise.getReleaseDate());
    }

    @Test
    void aTokenCannotChangeAPublishedExercise() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().minusDays(1));

        perform(patch("/api/programming/programming-exercises/" + exercise.getId() + "/problem-statement").contentType(MediaType.TEXT_PLAIN).content("Changed."), token)
                .andExpect(status().isForbidden());
        perform(patch("/api/programming/programming-exercises/" + exercise.getId() + "/update-test-cases").contentType(MediaType.APPLICATION_JSON).content("[]"), token)
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenCannotChangeADraftThatStudentsHaveStarted() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));
        participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");

        perform(patch("/api/programming/programming-exercises/" + exercise.getId() + "/problem-statement").contentType(MediaType.TEXT_PLAIN).content("Changed."), token)
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenCannotPublishADraftThroughTheTimeline() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));

        perform(put("/api/programming/programming-exercises/timeline").contentType(MediaType.APPLICATION_JSON).content("{\"id\":" + exercise.getId() + ",\"releaseDate\":null}"),
                token).andExpect(status().isBadRequest());
    }

    @Test
    void aDraftWithoutParticipationsPassesTheDraftRules() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);
        releaseAt(ZonedDateTime.now().plusDays(10));

        perform(patch("/api/programming/programming-exercises/" + exercise.getId() + "/problem-statement").contentType(MediaType.TEXT_PLAIN).content("A new problem statement."),
                token).andExpect(status().isOk());
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    private JsonNode callTool(SavedToken token, String name, Map<String, Object> arguments) throws Exception {
        var message = JsonObjectMapper.get().writeValueAsString(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params", Map.of("name", name, "arguments", arguments)));
        String body = perform(post("/api/core/agent/mcp").contentType(MediaType.APPLICATION_JSON).content(message), token).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonObjectMapper.get().readTree(body);
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> requestBuilder, SavedToken token) throws Exception {
        return request.performMvcRequest(requestBuilder.header(HttpHeaders.AUTHORIZATION, token.bearer()));
    }
}
