package de.tum.cit.aet.artemis.programming;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZonedDateTime;
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

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

/**
 * Programming exercises are created and changed with a personal access token only while they are a draft that no student has started.
 */
class AgentAccessProgrammingExerciseIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "agentprog";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

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
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(null);
        exercise = exerciseRepository.save(exercise);
    }

    @Test
    void creatingAnExerciseWithoutAFutureReleaseDateIsRejectedBeforeAnythingIsSetUp() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);

        perform(post("/api/programming/programming-exercises/setup").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Agent exercise\"}"), token)
                .andExpect(status().isBadRequest());
    }

    @Test
    void aTutorTokenCannotCreateProgrammingExercises() throws Exception {
        SavedToken token = tokenOf("tutor1", AgentScope.PROGRAMMING_DRAFT);

        perform(post("/api/programming/programming-exercises/setup").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Agent exercise\"}"), token)
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenCanReadAnExercise() throws Exception {
        SavedToken token = tokenOf("editor1", AgentScope.PROGRAMMING_DRAFT);

        perform(get("/api/programming/programming-exercises/" + exercise.getId()), token).andExpect(status().isOk());
        perform(get("/api/programming/courses/" + course.getId() + "/programming-exercises"), token).andExpect(status().isOk());
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

    private ResultActions perform(AbstractMockHttpServletRequestBuilder<?> requestBuilder, SavedToken token) throws Exception {
        return request.performMvcRequest(requestBuilder.header(HttpHeaders.AUTHORIZATION, token.bearer()));
    }
}
