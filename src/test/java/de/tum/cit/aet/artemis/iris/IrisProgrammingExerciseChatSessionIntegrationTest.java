package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
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

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContentDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisStatusDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.util.IrisMessageFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class IrisProgrammingExerciseChatSessionIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irischatsessionintegration";

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository irisMessageRepository;

    @Autowired
    private de.tum.cit.aet.artemis.iris.service.IrisSessionService irisSessionService;

    private ProgrammingExercise exercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        for (User user : users) {
            user.setSelectedLLMUsageTimestamp(ZonedDateTime.parse("2025-12-11T00:00:00Z"));
            user.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
            userTestRepository.save(user);
        }

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    private IrisChatSession createSessionForUser(String userLogin) {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + userLogin);
        return irisChatSessionRepository.save(new IrisChatSession(exercise, user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT));
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
        JsonNode jsonNode = JsonObjectMapper.get().valueToTree(jsonMap);
        return new IrisJsonMessageContent(jsonNode);
    }

    private String createSessionUrl() {
        return "/api/iris/chat/" + course.getId() + "/sessions?mode=PROGRAMMING_EXERCISE_CHAT&entityId=" + exercise.getId();
    }

    private String getCurrentSessionUrl() {
        return "/api/iris/chat/" + course.getId() + "/sessions/current?mode=PROGRAMMING_EXERCISE_CHAT&entityId=" + exercise.getId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession() throws Exception {
        var irisSession = request.postWithResponseBody(createSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);
        var actualIrisSession = irisChatSessionRepository.findByIdElseThrow(irisSession.id());
        assertThat(actualIrisSession.getUserId()).isEqualTo(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        assertThat(exercise.getId()).isEqualTo(actualIrisSession.getEntityId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession_alreadyExists() throws Exception {
        var firstResponse = request.postWithResponseBody(createSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);
        var secondResponse = request.postWithResponseBody(createSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);
        assertThat(firstResponse).isNotEqualTo(secondResponse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrentSession() throws Exception {
        var irisSession = request.postWithResponseBody(createSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);
        var currentIrisSession = request.postWithResponseBody(getCurrentSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);
        assertThat(currentIrisSession.id()).isEqualTo(irisSession.id());
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
        var irisSession = request.postWithResponseBody(createSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);
        assertThat(irisChatSessionRepository.findByIdElseThrow(irisSession.id())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisChatSessionRepository.findAll().stream().anyMatch(s -> Objects.equals(s.getId(), irisSession.id()))).isFalse();
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

        assertThat(irisChatSessionRepository.findByIdElseThrow(irisSession.getId())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisChatSessionRepository.findAll().stream().anyMatch(s -> Objects.equals(s.getId(), irisSession.getId()))).isFalse();
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

        assertThat(irisChatSessionRepository.findByIdElseThrow(irisSession.getId())).isNotNull();
        // Set the URL request parameters to prevent an internal server error which is irrelevant for this test
        var url = "/api/programming/programming-exercises/" + exercise.getId() + "?deleteStudentReposBuildPlans=false&deleteBaseReposBuildPlans=false";
        request.delete(url, HttpStatus.OK);
        assertThat(irisChatSessionRepository.findAll().stream().anyMatch(s -> Objects.equals(s.getId(), irisSession.getId()))).isFalse();
        assertThat(irisMessageRepository.findAllBySessionId(irisSession.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRequestMessageFromIris_withUncommittedFiles() {
        // Arrange
        var session = createSessionForUser("student1");
        var message = createDefaultMockTextMessage(session);
        irisMessageService.saveMessage(message, session, IrisMessageSender.USER);

        Map<String, String> uncommittedFiles = Map.of("src/Main.java", "public class Main { /* uncommitted changes */ }", "src/Utils.java",
                "public class Utils { /* new file */ }");

        // Mock Pyris response
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            // Verify uncommitted files are in the repository
            if (dto.programmingExerciseSubmission() != null && dto.programmingExerciseSubmission().repository() != null) {
                assertThat(dto.programmingExerciseSubmission().repository()).containsAllEntriesOf(uncommittedFiles);
            }
        });

        // Act - Call service method with uncommitted files
        irisSessionService.requestMessageFromIris(session, uncommittedFiles);

        // Assert - Verify the session was processed and messages exist
        var updatedSession = irisChatSessionRepository.findByIdElseThrow(session.getId());
        assertThat(updatedSession).isNotNull();
        var messages = irisMessageRepository.findAllBySessionId(session.getId());
        assertThat(messages).isNotEmpty();
        assertThat(messages).hasSizeGreaterThanOrEqualTo(1);
        // Verify we have the USER message
        assertThat(messages.stream().anyMatch(m -> m.getSender() == IrisMessageSender.USER)).isTrue();
        // Verify messages have content
        assertThat(messages).allMatch(m -> !m.getContent().isEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRequestMessageFromIris_withEmptyUncommittedFiles() {
        // Arrange
        var session = createSessionForUser("student1");
        var message = createDefaultMockTextMessage(session);
        irisMessageService.saveMessage(message, session, IrisMessageSender.USER);

        var uncommittedFiles = Map.<String, String>of(); // Empty map

        // Mock Pyris response
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            // No assertion needed - just verify the call was made
        });

        // Act - Call service method with empty uncommitted files
        irisSessionService.requestMessageFromIris(session, uncommittedFiles);

        // Assert - Verify the session was processed normally
        var updatedSession = irisChatSessionRepository.findByIdElseThrow(session.getId());
        assertThat(updatedSession).isNotNull();
        var messages = irisMessageRepository.findAllBySessionId(session.getId());
        assertThat(messages).isNotEmpty();
        // Verify we have the USER message
        assertThat(messages.stream().anyMatch(m -> m.getSender() == IrisMessageSender.USER)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRequestMessageFromIris_backwardCompatibility() {
        // Arrange
        var session = createSessionForUser("student1");
        var message = createDefaultMockTextMessage(session);
        irisMessageService.saveMessage(message, session, IrisMessageSender.USER);

        // Mock Pyris response
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            // No assertion needed - just verify the call was made
        });

        // Act - Call original method without uncommitted files (backward compatibility)
        irisSessionService.requestMessageFromIris(session);

        // Assert - Verify the session was processed normally
        var updatedSession = irisChatSessionRepository.findByIdElseThrow(session.getId());
        assertThat(updatedSession).isNotNull();
        var messages = irisMessageRepository.findAllBySessionId(session.getId());
        assertThat(messages).isNotEmpty();
        // Verify we have the USER message
        assertThat(messages.stream().anyMatch(m -> m.getSender() == IrisMessageSender.USER)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateMessage_withoutUncommittedFiles() throws Exception {
        // Test that sending a message without uncommitted files works
        var session = createSessionForUser("student1");
        var message = createDefaultMockTextMessage(session);

        // Mock Pyris response
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            // No assertion needed - just verify the call was made
        });

        // Create request DTO without uncommitted files
        List<IrisMessageContentDTO> contentDTOs = message.getContent().stream().map(content -> new IrisMessageContentDTO("text", content.getContentAsString(), null)).toList();
        var requestDTO = new IrisMessageRequestDTO(contentDTOs, message.getMessageDifferentiator(), Map.of());

        var response = request.postWithResponseBody("/api/iris/sessions/" + session.getId() + "/messages", requestDTO, IrisMessageResponseDTO.class, HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateMessageWithUncommittedFiles() throws Exception {
        // Test with uncommitted files
        var session = createSessionForUser("student1");
        var message = createDefaultMockTextMessage(session);

        Map<String, String> uncommittedFiles = Map.of("src/Main.java", "public class Main { // uncommitted }", "src/Utils.java", "public class Utils { }");

        // Mock Pyris response
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            // Verify uncommitted files are in the repository
            if (dto.programmingExerciseSubmission() != null && dto.programmingExerciseSubmission().repository() != null) {
                assertThat(dto.programmingExerciseSubmission().repository()).containsAllEntriesOf(uncommittedFiles);
            }
        });

        // Create request DTO with uncommitted files
        List<IrisMessageContentDTO> contentDTOs = message.getContent().stream().map(content -> new IrisMessageContentDTO("text", content.getContentAsString(), null)).toList();
        var requestDTO = new IrisMessageRequestDTO(contentDTOs, message.getMessageDifferentiator(), uncommittedFiles);

        var response = request.postWithResponseBody("/api/iris/sessions/" + session.getId() + "/messages", requestDTO, IrisMessageResponseDTO.class, HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateMessageWithJsonContent() throws Exception {
        // Test with JSON content
        var session = createSessionForUser("student1");
        var message = createDefaultMockJsonMessage(session);

        // Mock Pyris response
        irisRequestMockProvider.mockProgrammingExerciseChatResponse(dto -> {
            // No assertion needed - just verify the call was made
        });

        // Create request DTO with JSON content
        List<IrisMessageContentDTO> contentDTOs = message.getContent().stream().map(content -> new IrisMessageContentDTO("json", null, content.getContentAsString())).toList();
        var requestDTO = new IrisMessageRequestDTO(contentDTOs, message.getMessageDifferentiator(), Map.of());

        var response = request.postWithResponseBody("/api/iris/sessions/" + session.getId() + "/messages", requestDTO, IrisMessageResponseDTO.class, HttpStatus.CREATED);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
        assertThat(response.content()).hasSize(3);
        assertThat(response.content().get(0).type()).isEqualTo("json");
        assertThat(response.content().get(0).attributes()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrentSessionOrCreateIfNotExists_invokesIrisCitationService() throws Exception {
        request.postWithResponseBody(getCurrentSessionUrl(), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        verify(irisCitationService).enrichSessionWithCitationInfo(any());
    }
}
