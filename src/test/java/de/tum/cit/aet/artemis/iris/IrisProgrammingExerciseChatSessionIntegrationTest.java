package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisStatusDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisExerciseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisProgrammingExerciseChatSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischatsessionintegration";

    @Autowired
    private IrisExerciseChatSessionRepository irisExerciseChatSessionRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    private IrisProgrammingExerciseChatSession createSessionForUser(String userLogin) {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisExerciseChatSessionRepository.save(new IrisProgrammingExerciseChatSession(exercise, user));
    }

    private IrisMessage createDefaultMockTextMessage(IrisSession irisSession) {
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
        messageToSend.addContent(createMockTextContent(), createMockTextContent(), createMockTextContent());
        return messageToSend;
    }

    private IrisMessageContent createMockTextContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        return new IrisTextMessageContent(text);
    }

    private IrisMessage createDefaultMockJsonMessage(IrisSession irisSession) {
        IrisMessage messageToSend = IrisMessageFactory.createIrisMessageForSession(irisSession);
        messageToSend.addContent(createMockJsonContent(), createMockJsonContent(), createMockJsonContent());
        return messageToSend;
    }

    private IrisJsonMessageContent createMockJsonContent() {
        var jsonMap = Map.of("key1", "value1", "key2", "value2", "key3", "value3");
        JsonNode jsonNode = new ObjectMapper().valueToTree(jsonMap);
        return new IrisJsonMessageContent(jsonNode);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisProgrammingExerciseChatSession.class, HttpStatus.CREATED);
        var actualIrisSession = irisExerciseChatSessionRepository.findByIdElseThrow(irisSession.getId());
        assertThat(actualIrisSession.getUserId()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        assertThat(exercise.getId()).isEqualTo(actualIrisSession.getExerciseId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var currentIrisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()) + "/current", null, IrisSession.class, HttpStatus.OK);
        assertThat(currentIrisSession).isEqualTo(irisSession);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllSessions() throws Exception {
        var irisSession1 = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        var irisSession2 = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        List<IrisSession> irisSessions = request.getList(exerciseChatUrl(exercise.getId()), HttpStatus.OK, IrisSession.class);
        assertThat(irisSessions).hasSize(2).containsAll(List.of(irisSession1, irisSession2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void irisStatus() throws Exception {
        irisRequestMockProvider.mockStatusResponses();
        var courseId = exercise.getCourseViaExerciseGroupOrCourseMember().getId();
        assertThat(request.get("/api/iris/courses/" + courseId + "/status", HttpStatus.OK, IrisStatusDTO.class).active()).isTrue();

        // Pyris now became unavailable (mockStatusResponses mocks a failure for the second call)

        // Should still return true, as the status is cached
        assertThat(request.get("/api/iris/courses/" + courseId + "/status", HttpStatus.OK, IrisStatusDTO.class).active()).isTrue();

        // Wait the TTL time for the cache to expire
        // In tests, this is 500ms
        Thread.sleep(510);

        // Should now return false
        assertThat(request.get("/api/iris/courses/" + courseId + "/status", HttpStatus.OK, IrisStatusDTO.class).active()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseWithIrisSession() throws Exception {
        var irisSession = request.postWithResponseBody(exerciseChatUrl(exercise.getId()), null, IrisSession.class, HttpStatus.CREATED);
        assertThat(irisExerciseChatSessionRepository.findByIdElseThrow(irisSession.getId())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisExerciseChatSessionRepository.findAll().stream().anyMatch(s -> Objects.equals(s.getId(), irisSession.getId()))).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseWithIrisMessagesWithTextMessageContent() throws Exception {
        var irisSession = createSessionForUser("instructor1");

        // Create and some messages to the session
        irisMessageService.saveMessage(createDefaultMockTextMessage(irisSession), irisSession, IrisMessageSender.USER);
        irisMessageService.saveMessage(createDefaultMockTextMessage(irisSession), irisSession, IrisMessageSender.LLM);
        irisMessageService.saveMessage(createDefaultMockTextMessage(irisSession), irisSession, IrisMessageSender.USER);
        irisMessageService.saveMessage(createDefaultMockTextMessage(irisSession), irisSession, IrisMessageSender.LLM);

        assertThat(irisExerciseChatSessionRepository.findByIdElseThrow(irisSession.getId())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisExerciseChatSessionRepository.findAll().stream().anyMatch(s -> Objects.equals(s.getId(), irisSession.getId()))).isFalse();
        assertThat(irisMessageRepository.findAllBySessionId(irisSession.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExerciseWithIrisMessagesWithJsonMessageContent() throws Exception {
        var irisSession = createSessionForUser("instructor1");

        // Create and some messages to the session
        irisMessageService.saveMessage(createDefaultMockJsonMessage(irisSession), irisSession, IrisMessageSender.USER);
        irisMessageService.saveMessage(createDefaultMockJsonMessage(irisSession), irisSession, IrisMessageSender.LLM);
        irisMessageService.saveMessage(createDefaultMockJsonMessage(irisSession), irisSession, IrisMessageSender.USER);
        irisMessageService.saveMessage(createDefaultMockJsonMessage(irisSession), irisSession, IrisMessageSender.LLM);

        assertThat(irisExerciseChatSessionRepository.findByIdElseThrow(irisSession.getId())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisExerciseChatSessionRepository.findAll().stream().anyMatch(s -> Objects.equals(s.getId(), irisSession.getId()))).isFalse();
        assertThat(irisMessageRepository.findAllBySessionId(irisSession.getId())).isEmpty();
    }

    private static String exerciseChatUrl(long sessionId) {
        return "/api/iris/programming-exercise-chat/" + sessionId + "/sessions";
    }
}
