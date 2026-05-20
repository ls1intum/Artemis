package de.tum.cit.aet.artemis.iris.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.iris.AbstractIrisChatSessionTest;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;

class IrisAdminDashboardRepositoryTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisadmindashboardrepo";

    @Autowired
    private IrisAdminDashboardRepository irisAdminDashboardRepository;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    void findsNextRelevantMessageWithSameTimestampOrdering() {
        ZonedDateTime timestamp = zdt("2026-01-01T10:00:00Z");
        IrisChatSession session = new IrisChatSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        session.setCreationDate(timestamp.minusMinutes(5));
        session.getMessages().add(message(session, IrisMessageSender.USER, timestamp, null));
        session.getMessages().add(message(session, IrisMessageSender.USER, timestamp, null));
        session.getMessages().add(message(session, IrisMessageSender.LLM, timestamp.plusSeconds(4), true));
        IrisChatSession savedSession = (IrisChatSession) irisSessionRepository.saveAndFlush(session);

        var results = irisAdminDashboardRepository.findUserMessageResults(timestamp.minusHours(1), timestamp.plusHours(1), null).stream()
                .filter(result -> Objects.equals(result.sessionId(), savedSession.getId())).toList();

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().nextSender()).isEqualTo(IrisMessageSender.USER.name());
        assertThat(results.get(1).nextSender()).isEqualTo(IrisMessageSender.LLM.name());
        assertThat(results.get(1).nextSentAt().toInstant()).isEqualTo(timestamp.plusSeconds(4).toInstant());
    }

    @Test
    void findsRealChatAndTutorSuggestionSessionTypes() {
        ZonedDateTime timestamp = zdt("2026-01-01T10:30:00Z");
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession chatSession = new IrisChatSession(course, user);
        chatSession.setCreationDate(timestamp.minusMinutes(5));
        chatSession.getMessages().add(message(chatSession, IrisMessageSender.USER, timestamp, null));
        IrisChatSession savedChatSession = (IrisChatSession) irisSessionRepository.saveAndFlush(chatSession);

        IrisTutorSuggestionSession tutorSuggestionSession = new IrisTutorSuggestionSession(createPostForTutorSuggestion().getId(), user);
        tutorSuggestionSession.setCreationDate(timestamp.minusMinutes(4));
        tutorSuggestionSession.getMessages().add(message(tutorSuggestionSession, IrisMessageSender.USER, timestamp.plusMinutes(1), null));
        IrisTutorSuggestionSession savedTutorSuggestionSession = (IrisTutorSuggestionSession) irisSessionRepository.saveAndFlush(tutorSuggestionSession);

        var sessions = irisAdminDashboardRepository.findSessions(timestamp.minusHours(1), timestamp.plusHours(1), null);
        var messages = irisAdminDashboardRepository.findMessages(timestamp.minusHours(1), timestamp.plusHours(1), null);

        assertThat(sessions).anySatisfy(row -> {
            assertThat(row.sessionId()).isEqualTo(savedChatSession.getId());
            assertThat(row.sessionType()).isEqualTo("COURSE_CHAT");
        }).anySatisfy(row -> {
            assertThat(row.sessionId()).isEqualTo(savedTutorSuggestionSession.getId());
            assertThat(row.sessionType()).isEqualTo("TUTOR_SUGGESTION");
        });
        assertThat(messages).anySatisfy(row -> {
            assertThat(row.sessionId()).isEqualTo(savedChatSession.getId());
            assertThat(row.sessionType()).isEqualTo("COURSE_CHAT");
        }).anySatisfy(row -> {
            assertThat(row.sessionId()).isEqualTo(savedTutorSuggestionSession.getId());
            assertThat(row.sessionType()).isEqualTo("TUTOR_SUGGESTION");
        });
    }

    @Test
    void classifiesChatAttributedAndOtherIrisTokenCost() {
        ZonedDateTime timestamp = zdt("2026-01-01T11:00:00Z");
        IrisChatSession session = new IrisChatSession(course, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        session.setCreationDate(timestamp.minusMinutes(5));
        IrisMessage llmMessage = message(session, IrisMessageSender.LLM, timestamp, true);
        session.getMessages().add(llmMessage);
        irisSessionRepository.saveAndFlush(session);

        saveTrace(timestamp.plusMinutes(1), llmMessage.getId());
        saveTrace(timestamp.plusMinutes(2), null);

        var rows = irisAdminDashboardRepository.findTokenUsage(timestamp.minusHours(1), timestamp.plusHours(1), null);

        assertThat(rows).filteredOn(row -> row.traceId() != 0).hasSizeGreaterThanOrEqualTo(2);
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.chatAttributed()).isTrue();
            assertThat(row.costEur()).isEqualTo(0.003);
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.chatAttributed()).isFalse();
            assertThat(row.costEur()).isEqualTo(0.003);
        });
    }

    private void saveTrace(ZonedDateTime time, Long irisMessageId) {
        LLMTokenUsageTrace trace = new LLMTokenUsageTrace();
        trace.setServiceType(LLMServiceType.IRIS);
        trace.setCourseId(course.getId());
        trace.setUserId(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId());
        trace.setTime(time);
        trace.setIrisMessageId(irisMessageId);

        LLMTokenUsageRequest request = new LLMTokenUsageRequest();
        request.setTrace(trace);
        request.setModel("gpt-test");
        request.setNumInputTokens(1000);
        request.setCostPerMillionInputTokens(1.0F);
        request.setNumOutputTokens(1000);
        request.setCostPerMillionOutputTokens(2.0F);
        request.setServicePipelineId("IRIS_TEST_PIPELINE");
        trace.setLlmRequests(Set.of(request));
        llmTokenUsageTraceRepository.saveAndFlush(trace);
    }

    private Post createPostForTutorSuggestion() {
        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName("iris-dashboard-" + UUID.randomUUID().toString().substring(0, 8));
        channel = channelRepository.save(channel);

        Post post = new Post();
        post.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        post.setContent("Test tutor suggestion post");
        post.setConversation(channel);
        post.setDisplayPriority(DisplayPriority.NONE);
        return postRepository.save(post);
    }

    private static IrisMessage message(IrisSession session, IrisMessageSender sender, ZonedDateTime sentAt, Boolean helpful) {
        IrisMessage message = session.newMessage();
        message.setSender(sender);
        message.setSentAt(sentAt);
        message.setHelpful(helpful);
        return message;
    }

    private static ZonedDateTime zdt(String value) {
        return Instant.parse(value).atZone(ZoneOffset.UTC);
    }
}
