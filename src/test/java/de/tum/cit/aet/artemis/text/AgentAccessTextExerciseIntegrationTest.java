package de.tum.cit.aet.artemis.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
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
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Creating, reading and changing text exercises with a personal access token: always as a draft.
 */
class AgentAccessTextExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "agenttext";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        personalAccessTokenRepository.deleteAll();
    }

    @Test
    void anExerciseWithAFutureReleaseDateIsCreatedAsADraft() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);

        perform(post("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(newExerciseJson(ZonedDateTime.now().plusDays(30))), token)
                .andExpect(status().isCreated());
    }

    @Test
    void anExerciseWithoutOrWithAPastReleaseDateIsRejected() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);

        perform(post("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(newExerciseJson(null)), token).andExpect(status().isBadRequest());
        perform(post("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(newExerciseJson(ZonedDateTime.now().minusDays(1))), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTutorTokenCannotCreateExercises() throws Exception {
        SavedToken token = tokenOf("tutor1", AgentScope.EXERCISES_DRAFT);

        perform(post("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(newExerciseJson(ZonedDateTime.now().plusDays(30))), token)
                .andExpect(status().isForbidden());
    }

    @Test
    void aDraftCanBeReadChangedAndSavedBack() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);
        TextExercise draft = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().plusDays(10), ZonedDateTime.now().plusDays(20),
                ZonedDateTime.now().plusDays(30));

        String body = perform(get("/api/text/text-exercises/" + draft.getId()), token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode exercise = JsonObjectMapper.get().readTree(body);
        assertThat(exercise.get("title")).isNotNull();
        var changed = ((ObjectNode) exercise).put("problemStatement", "A better problem statement.");

        perform(put("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(changed.toString()), token).andExpect(status().isOk());
    }

    @Test
    void aTokenCannotChangeAnExerciseStudentsCanSee() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);
        TextExercise published = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(20),
                ZonedDateTime.now().plusDays(30));

        String body = perform(get("/api/text/text-exercises/" + published.getId()), token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var changed = ((ObjectNode) JsonObjectMapper.get().readTree(body)).put("problemStatement", "Changed after publication.");

        perform(put("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(changed.toString()), token).andExpect(status().isForbidden());
    }

    @Test
    void aTokenCannotPublishADraftByClearingItsReleaseDate() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);
        TextExercise draft = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().plusDays(10), ZonedDateTime.now().plusDays(20),
                ZonedDateTime.now().plusDays(30));

        String body = perform(get("/api/text/text-exercises/" + draft.getId()), token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var published = ((ObjectNode) JsonObjectMapper.get().readTree(body)).putNull("releaseDate");

        perform(put("/api/text/text-exercises").contentType(MediaType.APPLICATION_JSON).content(published.toString()), token).andExpect(status().isBadRequest());
    }

    @Test
    void examExercisesCannotBeReadWithAToken() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);
        TextExercise examExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);

        perform(get("/api/text/text-exercises/" + examExercise.getId()), token).andExpect(status().isForbidden());
    }

    @Test
    void theExercisesOfACourseCanBeListed() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.EXERCISES_DRAFT);
        textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().plusDays(10), ZonedDateTime.now().plusDays(20), ZonedDateTime.now().plusDays(30));

        perform(get("/api/text/courses/" + course.getId() + "/text-exercises"), token).andExpect(status().isOk());
    }

    private String newExerciseJson(ZonedDateTime releaseDate) throws Exception {
        var exercise = new LinkedHashMap<String, Object>();
        exercise.putAll(Map.of("title", "Agent essay", "problemStatement", "Write an essay.", "maxPoints", 10.0, "includedInOverallScore", "INCLUDED_COMPLETELY", "courseId",
                course.getId(), "mode", "INDIVIDUAL"));
        if (releaseDate != null) {
            exercise.put("releaseDate", releaseDate.toString());
        }
        return JsonObjectMapper.get().writeValueAsString(exercise);
    }

    private SavedToken tokenOf(String loginWithoutPrefix, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(TEST_PREFIX + loginWithoutPrefix).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> requestBuilder, SavedToken token) throws Exception {
        return request.performMvcRequest(requestBuilder.header(HttpHeaders.AUTHORIZATION, token.bearer()));
    }
}
