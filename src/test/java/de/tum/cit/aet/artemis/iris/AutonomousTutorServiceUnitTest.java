package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.AutonomousTutorService;
import de.tum.cit.aet.artemis.iris.service.IrisBotUserService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;

@ExtendWith(MockitoExtension.class)
class AutonomousTutorServiceUnitTest {

    @Mock
    private IrisBotUserService botService;

    @Mock
    private ConversationMessageRepository messages;

    @Mock
    private AnswerPostRepository answers;

    @Mock
    private ConversationParticipantTestRepository participants;

    @Mock
    private FeatureToggleService features;

    @Mock
    private WebsocketMessagingService websocket;

    @Mock
    private CourseNotificationService notifications;

    @Mock
    private UserTestRepository users;

    private AutonomousTutorService service;

    private final AutonomousTutorJob job = new AutonomousTutorJob("job", 7L, 1L);

    @BeforeEach
    void setUp() {
        service = new AutonomousTutorService(botService, messages, answers, participants, features, websocket, notifications, users);
    }

    @ParameterizedTest
    @CsvSource({ "false,answer,0.9", "true,,0.9", "true,answer,", "true,answer,0.6999" })
    void discardedUpdatesNeverLoadPostsOrSendNotifications(boolean enabled, String answer, Double confidence) {
        when(features.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(enabled);
        service.handleStatusUpdate(job, update(answer, confidence));
        org.mockito.Mockito.verifyNoInteractions(messages, answers, websocket, notifications);
    }

    @Test
    void mismatchingCourseDoesNotCreateBotMembershipOrAnswer() {
        when(features.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(true);
        var post = postWithChannel(false);
        post.getConversation().getCourse().setId(2L);
        when(messages.findMessagePostByIdElseThrow(7L)).thenReturn(post);
        when(botService.getIrisBotUser()).thenReturn(user(99L));
        service.handleStatusUpdate(job, update("Answer", 0.9));
        org.mockito.Mockito.verifyNoInteractions(answers, participants, websocket, notifications);
    }

    @ParameterizedTest
    @CsvSource({ "0.70,false", "0.8499,false", "0.85,true", "1.0,true" })
    void confidenceBoundariesPersistExpectedVisibilityAndVerificationTime(double confidence, boolean verified) {
        when(features.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(true);
        var post = postWithChannel(true);
        var bot = user(99L);
        when(messages.findMessagePostByIdElseThrow(7L)).thenReturn(post);
        when(botService.getIrisBotUser()).thenReturn(bot);
        when(answers.save(any())).thenAnswer(call -> {
            AnswerPost answer = call.getArgument(0);
            answer.setId(10L);
            answer.setCreationDate(ZonedDateTime.now());
            return answer;
        });
        if (verified) {
            when(users.findAllNotificationRecipientsInCourseForConversation(3L, 1L)).thenReturn(Set.of());
            when(messages.findUsersWhoRepliedInMessage(7L)).thenReturn(new HashSet<>());
        }
        else {
            when(users.findStaffNotificationRecipientsInCourseForConversation(3L, 1L)).thenReturn(Set.of());
        }
        service.handleStatusUpdate(job, update("Answer", confidence));
        var captured = ArgumentCaptor.forClass(AnswerPost.class);
        verify(answers).save(captured.capture());
        var answer = captured.getValue();
        assertThat(answer.getContent()).isEqualTo("Answer");
        assertThat(answer.getAuthor()).isSameAs(bot);
        assertThat(answer.getConfidenceScore()).isEqualTo(confidence);
        assertThat(answer.isVerified()).isEqualTo(verified);
        assertThat(answer.getVerifiedAt() != null).isEqualTo(verified);
        assertThat(answer.doesResolvePost()).isFalse();
        verify(participants).save(any(ConversationParticipant.class));
        assertThat(post.getAnswers()).containsExactly(answer);
        if (verified) {
            verify(websocket).sendMessage(any(), any(Object.class));
        }
        else {
            org.mockito.Mockito.verifyNoInteractions(websocket);
        }
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void pendingPrivateReplyBroadcastsOnlyToStaffAndRespectsNotificationPreferences() {
        when(features.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(true);
        var post = postWithChannel(false);
        var bot = user(99L);
        when(messages.findMessagePostByIdElseThrow(7L)).thenReturn(post);
        when(botService.getIrisBotUser()).thenReturn(bot);
        when(participants.findConversationParticipantByConversationIdAndUserId(3L, 99L)).thenReturn(Optional.of(new ConversationParticipant()));
        var tutor = user(4L);
        tutor.getCourseRolesByCourseId().put(1L, java.util.EnumSet.of(de.tum.cit.aet.artemis.core.domain.CourseRole.TEACHING_ASSISTANT));
        var tutorParticipant = ConversationParticipant.createWithDefaultValues(tutor, post.getConversation());
        tutorParticipant.setIsHidden(true);
        var studentParticipant = ConversationParticipant.createWithDefaultValues(user(5L), post.getConversation());
        when(participants.findConversationParticipantsWithUserCourseRolesByConversationId(3L)).thenReturn(Set.of(tutorParticipant, studentParticipant));
        when(answers.save(any())).thenAnswer(call -> {
            AnswerPost answer = call.getArgument(0);
            answer.setId(10L);
            answer.setCreationDate(ZonedDateTime.now());
            return answer;
        });
        service.handleStatusUpdate(job, update("Review me", 0.8));
        verify(participants, never()).save(any());
        verify(websocket).sendMessage(org.mockito.ArgumentMatchers.eq(de.tum.cit.aet.artemis.communication.web.CommunicationWebsocketTopics.USER_CONVERSATION_POSTS.at(4L)),
                any(Object.class));
        org.mockito.Mockito.verifyNoMoreInteractions(websocket);
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void verifiedAnswerNotifiesOnlyVisibleUnmutedThreadParticipantsAndHidesOlderPendingReplies() {
        when(features.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(true);
        var post = postWithChannel(true);
        var bot = user(99L);
        bot.setLogin(User.IRIS_BOT_LOGIN);
        when(messages.findMessagePostByIdElseThrow(7L)).thenReturn(post);
        when(botService.getIrisBotUser()).thenReturn(bot);
        when(answers.save(any())).thenAnswer(call -> {
            AnswerPost answer = call.getArgument(0);
            answer.setId(10L);
            answer.setCreationDate(ZonedDateTime.now());
            return answer;
        });
        var pending = new AnswerPost();
        pending.setId(11L);
        pending.setAuthor(bot);
        pending.setVerified(false);
        pending.setConfidenceScore(0.75);
        post.addAnswerPost(pending);
        var visible = user(3L);
        var muted = user(4L);
        var hidden = user(5L);
        var outsider = user(6L);
        when(messages.findUsersWhoRepliedInMessage(7L)).thenReturn(new HashSet<>(Set.of(visible, muted, hidden, outsider, bot)));
        when(users.findAllNotificationRecipientsInCourseForConversation(3L, 1L))
                .thenReturn(Set.of(new ConversationNotificationRecipientSummary(post.getAuthor(), false, false, false),
                        new ConversationNotificationRecipientSummary(visible, false, false, true), new ConversationNotificationRecipientSummary(muted, true, false, true),
                        new ConversationNotificationRecipientSummary(hidden, false, true, false), new ConversationNotificationRecipientSummary(bot, false, false, false)));
        service.handleStatusUpdate(job, update("Published", 0.9));
        var recipients = org.mockito.ArgumentCaptor.<List<User>>captor();
        verify(notifications).sendCourseNotification(any(), recipients.capture());
        assertThat(recipients.getValue()).extracting(User::getId).containsExactlyInAnyOrder(2L, 3L);
        assertThat(post.getAnswers()).extracting(AnswerPost::getId).containsExactly(10L);
    }

    @Test
    void pendingCourseWideAnswerNotifiesOnlyReviewersWhoDidNotMuteOrHideConversation() {
        when(features.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(true);
        var post = postWithChannel(true);
        when(messages.findMessagePostByIdElseThrow(7L)).thenReturn(post);
        when(botService.getIrisBotUser()).thenReturn(user(99L));
        when(answers.save(any())).thenAnswer(call -> {
            AnswerPost answer = call.getArgument(0);
            answer.setId(10L);
            answer.setCreationDate(ZonedDateTime.now());
            return answer;
        });
        var reviewer = user(3L);
        reviewer.setTestUser(true);
        when(users.findStaffNotificationRecipientsInCourseForConversation(3L, 1L)).thenReturn(
                Set.of(new ConversationNotificationRecipientSummary(reviewer, false, false, true), new ConversationNotificationRecipientSummary(user(4L), true, false, true),
                        new ConversationNotificationRecipientSummary(user(5L), false, true, true), new ConversationNotificationRecipientSummary(user(6L), false, false, false)));
        service.handleStatusUpdate(job, update("Review", 0.8));
        var recipients = org.mockito.ArgumentCaptor.<List<User>>captor();
        verify(notifications).sendCourseNotification(any(), recipients.capture());
        assertThat(recipients.getValue()).singleElement().satisfies(recipient -> {
            assertThat(recipient.getId()).isEqualTo(3L);
            assertThat(recipient.isTestUser()).isTrue();
        });
        verify(websocket, org.mockito.Mockito.times(3)).sendMessage(any(), any(Object.class));
    }

    private Post postWithChannel(boolean courseWide) {
        var course = new Course();
        course.setId(1L);
        course.setTitle("Course");
        var channel = new Channel();
        channel.setId(3L);
        channel.setName("help");
        channel.setIsCourseWide(courseWide);
        channel.setCourse(course);
        var post = new Post();
        post.setId(7L);
        post.setContent("Question");
        post.setCreationDate(ZonedDateTime.now());
        post.setAuthor(user(2L));
        post.setConversation(channel);
        return post;
    }

    private User user(long id) {
        return new User(id, "user" + id, "First", "Last", "en", "user" + id + "@example.org");
    }

    private PyrisAutonomousTutorPipelineStatusUpdateDTO update(String answer, Double confidence) {
        return new PyrisAutonomousTutorPipelineStatusUpdateDTO(answer, false, confidence, PyrisRunState.FINISHED, null, List.of());
    }
}
