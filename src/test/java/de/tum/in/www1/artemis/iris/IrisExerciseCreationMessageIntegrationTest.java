package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.session.IrisExerciseCreationSessionService;
import de.tum.in.www1.artemis.service.iris.session.exercisecreation.IrisExerciseMetadataDTO;
import de.tum.in.www1.artemis.service.iris.session.exercisecreation.IrisExerciseUpdateDTO;
import de.tum.in.www1.artemis.service.iris.websocket.IrisExerciseCreationWebsocketService;
import de.tum.in.www1.artemis.web.rest.dto.IrisMessageDTO;

class IrisExerciseCreationMessageIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irisexcreationmessageintegration";

    @Autowired
    private IrisExerciseCreationSessionService irisExerciseCreationSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    private ProgrammingExercise exercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 2, 0);

        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSendOneMessage() throws Exception {
        var irisSession = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var messageToSend = createDefaultMockMessage(irisSession);
        messageToSend.setMessageDifferentiator(1453);
        var extraParams = createExtraParams();
        var messageDTOToSend = new IrisMessageDTO(messageToSend, extraParams);

        irisRequestMockProvider.mockMessageV2Response(Map.of("response", "Hi there!"));

        var irisMessage = request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageDTOToSend, IrisMessage.class, HttpStatus.CREATED);
        assertThat(irisMessage.getSender()).isEqualTo(IrisMessageSender.USER);
        assertThat(irisMessage.getMessageDifferentiator()).isEqualTo(1453);
        assertThat(irisMessage.getContent().stream().map(IrisMessageContent::getContentAsString).toList())
                .isEqualTo(messageToSend.getContent().stream().map(IrisMessageContent::getContentAsString).toList());
        await().untilAsserted(() -> assertThat(irisSessionRepository.findByIdWithMessagesElseThrow(irisSession.getId()).getMessages()).hasSize(2).contains(irisMessage));

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), messageDTO(List.of(new IrisTextMessageContent("Hi there!"))));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSendOneMessageToWrongSession() throws Exception {
        var irisSession1 = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var irisSession2 = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor2"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession1);
        var extraParams = createExtraParams();
        var messageDTOToSend = new IrisMessageDTO(messageToSend, extraParams);

        request.postWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages", messageDTOToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSendMessageWithoutContent() throws Exception {
        var irisSession = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        var messageToSend = irisSession.newMessage();
        var messageDTOToSend = new IrisMessageDTO(messageToSend, JsonNodeFactory.instance.objectNode());

        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageDTOToSend, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetMessages() throws Exception {
        var irisSession = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage message1 = createDefaultMockMessage(irisSession);
        IrisMessage message2 = createDefaultMockMessage(irisSession);
        IrisMessage message3 = createDefaultMockMessage(irisSession);
        IrisMessage message4 = createDefaultMockMessage(irisSession);

        message1 = irisMessageService.saveMessage(message1, irisSession, IrisMessageSender.USER);
        message2 = irisMessageService.saveMessage(message2, irisSession, IrisMessageSender.LLM);
        message3 = irisMessageService.saveMessage(message3, irisSession, IrisMessageSender.USER);
        message4 = irisMessageService.saveMessage(message4, irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(4).containsAll(List.of(message1, message2, message3, message4));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageBadRequest() throws Exception {
        var irisSession = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);
        var extraParams = createExtraParams();
        var messageDTOToSend = new IrisMessageDTO(messageToSend, extraParams);

        irisRequestMockProvider.mockMessageV2Error(500);

        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageDTOToSend, IrisMessage.class, HttpStatus.CREATED);

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), messageExceptionDTO());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void sendOneMessageEmptyBody() throws Exception {
        var irisSession = irisExerciseCreationSessionService.createSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "editor1"));
        IrisMessage messageToSend = createDefaultMockMessage(irisSession);
        var extraParams = createExtraParams();
        var messageDTOToSend = new IrisMessageDTO(messageToSend, extraParams);

        irisRequestMockProvider.mockMessageV2Response(Map.of("invalid", "response"));

        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageDTOToSend, IrisMessage.class, HttpStatus.CREATED);

        verifyWebsocketActivityWasExactly(irisSession, messageDTO(messageToSend.getContent()), messageExceptionDTO());
    }

    private IrisMessage createDefaultMockMessage(IrisSession irisSession) {
        var messageToSend = irisSession.newMessage();
        messageToSend.addContent(createMockTextContent());
        return messageToSend;
    }

    private JsonNode createExtraParams() {
        var exerciseMetadataDTO = new IrisExerciseMetadataDTO("sort", "sort");
        var exerciseUpdateDTO = new IrisExerciseUpdateDTO("ps", exerciseMetadataDTO);
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(exerciseUpdateDTO, JsonNode.class);
    }

    private IrisTextMessageContent createMockTextContent() {
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        var rdm = ThreadLocalRandom.current();
        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        var text = "The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.";
        var content = new IrisTextMessageContent(text);
        content.setId(rdm.nextLong());
        return content;
    }

    private ArgumentMatcher<Object> messageDTO(List<IrisMessageContent> content) {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisExerciseCreationWebsocketService.IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                if (websocketDTO.type() != IrisExerciseCreationWebsocketService.IrisWebsocketMessageType.MESSAGE) {
                    return false;
                }
                return websocketDTO.message().getContent().stream().map(IrisMessageContent::getContentAsString).toList()
                        .equals(content.stream().map(IrisMessageContent::getContentAsString).toList());
            }

            @Override
            public String toString() {
                return "IrisExerciseCreationWebsocketService.IrisWebsocketDTO with type MESSAGE and content " + content;
            }
        };
    }

    private ArgumentMatcher<Object> messageExceptionDTO() {
        return new ArgumentMatcher<>() {

            @Override
            public boolean matches(Object argument) {
                if (!(argument instanceof IrisExerciseCreationWebsocketService.IrisWebsocketDTO websocketDTO)) {
                    return false;
                }
                return websocketDTO.type() == IrisExerciseCreationWebsocketService.IrisWebsocketMessageType.ERROR;
            }

            @Override
            public String toString() {
                return "IrisExerciseCreationWebsocketService.IrisWebsocketDTO with type EXCEPTION";
            }
        };
    }

}
