package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.iris.IrisMessage;
import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.domain.iris.IrisMessageSender;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.IrisSessionService;

class IrisMessageIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "irismessageintegration";

    @Autowired
    private IrisSessionService irisSessionService;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 12, 0, 0, 0);

        final Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sendOneMessage() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student1"));
        var messageToSend = new IrisMessage();
        messageToSend.setSession(irisSession);
        messageToSend.setSentAt(ZonedDateTime.now());
        messageToSend.setContent(List.of(createMockContent(messageToSend), createMockContent(messageToSend), createMockContent(messageToSend)));
        var irisMessage = request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.CREATED);
        assertEquals(IrisMessageSender.USER, irisMessage.getSender());
        assertNull(irisMessage.getHelpful());
        // Compare contents of messages by only comparing the textContent field
        assertThat(irisMessage.getContent()).hasSize(3).map(IrisMessageContent::getTextContent)
                .isEqualTo(messageToSend.getContent().stream().map(IrisMessageContent::getTextContent).toList());
        var irisSessionFromDb = irisSessionRepository.findByIdWithMessages(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(1).isEqualTo(List.of(irisMessage));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void sendOneMessageToWrongSession() throws Exception {
        var irisSession1 = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student2"));
        var irisSession2 = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student3"));
        var messageToSend = new IrisMessage();
        messageToSend.setSession(irisSession2);
        messageToSend.setSentAt(ZonedDateTime.now());
        messageToSend.setContent(List.of(createMockContent(messageToSend), createMockContent(messageToSend), createMockContent(messageToSend)));
        request.postWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void sendMessageWithoutContent() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student4"));
        var messageToSend = new IrisMessage();
        messageToSend.setSession(irisSession);
        messageToSend.setSentAt(ZonedDateTime.now());
        request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend, IrisMessage.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student5", roles = "USER")
    void sendTwoMessages() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student5"));
        var messageToSend1 = new IrisMessage();
        messageToSend1.setSession(irisSession);
        messageToSend1.setSentAt(ZonedDateTime.now());
        messageToSend1.setContent(List.of(createMockContent(messageToSend1), createMockContent(messageToSend1), createMockContent(messageToSend1)));
        var irisMessage1 = request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend1, IrisMessage.class, HttpStatus.CREATED);
        assertEquals(IrisMessageSender.USER, irisMessage1.getSender());
        assertNull(irisMessage1.getHelpful());
        // Compare contents of messages by only comparing the textContent field
        assertThat(irisMessage1.getContent()).hasSize(3).map(IrisMessageContent::getTextContent)
                .isEqualTo(messageToSend1.getContent().stream().map(IrisMessageContent::getTextContent).toList());
        var irisSessionFromDb = irisSessionRepository.findByIdWithMessages(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(1).isEqualTo(List.of(irisMessage1));

        var messageToSend2 = new IrisMessage();
        messageToSend2.setSession(irisSession);
        messageToSend2.setSentAt(ZonedDateTime.now());
        messageToSend2.setContent(List.of(createMockContent(messageToSend2), createMockContent(messageToSend2), createMockContent(messageToSend2)));
        var irisMessage2 = request.postWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages", messageToSend2, IrisMessage.class, HttpStatus.CREATED);
        assertEquals(IrisMessageSender.USER, irisMessage2.getSender());
        assertNull(irisMessage2.getHelpful());
        // Compare contents of messages by only comparing the textContent field
        assertThat(irisMessage2.getContent()).hasSize(3).map(IrisMessageContent::getTextContent)
                .isEqualTo(messageToSend2.getContent().stream().map(IrisMessageContent::getTextContent).toList());
        irisSessionFromDb = irisSessionRepository.findByIdWithMessages(irisSession.getId());
        assertThat(irisSessionFromDb.getMessages()).hasSize(2).isEqualTo(List.of(irisMessage1, irisMessage2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student6", roles = "USER")
    void getMessages() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student6"));
        var message1 = new IrisMessage();
        message1.setSession(irisSession);
        message1.setSentAt(ZonedDateTime.now());
        message1.setContent(List.of(createMockContent(message1), createMockContent(message1), createMockContent(message1)));
        var message2 = new IrisMessage();
        message2.setSession(irisSession);
        message2.setSentAt(ZonedDateTime.now());
        message2.setContent(List.of(createMockContent(message2), createMockContent(message2), createMockContent(message2)));
        var message3 = new IrisMessage();
        message3.setSession(irisSession);
        message3.setSentAt(ZonedDateTime.now());
        message3.setContent(List.of(createMockContent(message3), createMockContent(message3), createMockContent(message3)));
        var message4 = new IrisMessage();
        message4.setSession(irisSession);
        message4.setSentAt(ZonedDateTime.now());
        message4.setContent(List.of(createMockContent(message4), createMockContent(message4), createMockContent(message4)));

        irisMessageService.saveMessage(message1, irisSession, IrisMessageSender.ARTEMIS);
        irisMessageService.saveMessage(message2, irisSession, IrisMessageSender.LLM);
        irisMessageService.saveMessage(message3, irisSession, IrisMessageSender.USER);
        irisMessageService.saveMessage(message4, irisSession, IrisMessageSender.LLM);

        var messages = request.getList("/api/iris/sessions/" + irisSession.getId() + "/messages", HttpStatus.OK, IrisMessage.class);
        assertThat(messages).hasSize(4).usingElementComparator((o1, o2) -> {
            return o1.getContent().size() == o2.getContent().size()
                    && o1.getContent().stream().map(IrisMessageContent::getTextContent).toList().equals(o2.getContent().stream().map(IrisMessageContent::getTextContent).toList())
                            ? 0
                            : -1;
        }).isEqualTo(List.of(message1, message2, message3, message4));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student7", roles = "USER")
    void rateMessageHelpfulTrue() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student7"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.setSentAt(ZonedDateTime.now());
        message.setContent(List.of(createMockContent(message)));
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful/true", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student8", roles = "USER")
    void rateMessageHelpfulFalse() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student8"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.setSentAt(ZonedDateTime.now());
        message.setContent(List.of(createMockContent(message)));
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful/false", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student9", roles = "USER")
    void rateMessageHelpfulNull() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student9"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.setSentAt(ZonedDateTime.now());
        message.setContent(List.of(createMockContent(message)));
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.LLM);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful/null", null, IrisMessage.class, HttpStatus.OK);
        irisMessage = irisMessageRepository.findById(irisMessage.getId()).orElseThrow();
        assertThat(irisMessage.getHelpful()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student10", roles = "USER")
    void rateMessageWrongSender() throws Exception {
        var irisSession = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student10"));
        var message = new IrisMessage();
        message.setSession(irisSession);
        message.setSentAt(ZonedDateTime.now());
        message.setContent(List.of(createMockContent(message)));
        var irisMessage = irisMessageService.saveMessage(message, irisSession, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession.getId() + "/messages/" + irisMessage.getId() + "/helpful/true", null, IrisMessage.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void rateMessageWrongSession() throws Exception {
        var irisSession1 = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student11"));
        var irisSession2 = irisSessionService.createSessionForProgrammingExercise(exercise, database.getUserByLogin(TEST_PREFIX + "student12"));
        var message = new IrisMessage();
        message.setSession(irisSession1);
        message.setSentAt(ZonedDateTime.now());
        message.setContent(List.of(createMockContent(message)));
        var irisMessage = irisMessageService.saveMessage(message, irisSession1, IrisMessageSender.USER);
        request.putWithResponseBody("/api/iris/sessions/" + irisSession2.getId() + "/messages/" + irisMessage.getId() + "/helpful/true", null, IrisMessage.class,
                HttpStatus.CONFLICT);
    }

    private IrisMessageContent createMockContent(IrisMessage message) {
        var content = new IrisMessageContent();
        var rdm = ThreadLocalRandom.current();
        content.setId(rdm.nextLong());
        content.setMessage(message);
        String[] adjectives = { "happy", "sad", "angry", "funny", "silly", "crazy", "beautiful", "smart" };
        String[] nouns = { "dog", "cat", "house", "car", "book", "computer", "phone", "shoe" };

        String randomAdjective = adjectives[rdm.nextInt(adjectives.length)];
        String randomNoun = nouns[rdm.nextInt(nouns.length)];

        content.setTextContent("The " + randomAdjective + " " + randomNoun + " jumped over the lazy dog.");
        return content;
    }
}
