package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;

/**
 * Publishing an auto-published answer: saved as a draft, published only by the repository's locked check-and-update. Not
 * an integration test because the failure it covers, the publication throwing after the draft was saved, cannot be
 * produced with real beans.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousTutorServicePublicationTest {

    private static final long COURSE_ID = 1L;

    private static final long CHANNEL_ID = 5L;

    private static final long POST_ID = 10L;

    @Mock
    private IrisBotUserService irisBotUserService;

    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @Mock
    private AnswerPostRepository answerPostRepository;

    @Mock
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Mock
    private FeatureToggleService featureToggleService;

    @Mock
    private WebsocketMessagingService websocketMessagingService;

    @Mock
    private CourseNotificationService courseNotificationService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ChannelRepository channelRepository;

    private AutonomousTutorService autonomousTutorService;

    private final List<Boolean> persistedVerified = new ArrayList<>();

    @BeforeEach
    void setUp() {
        autonomousTutorService = new AutonomousTutorService(irisBotUserService, conversationMessageRepository, answerPostRepository, conversationParticipantRepository,
                featureToggleService, websocketMessagingService, courseNotificationService, userRepository, channelRepository);

        Course course = new Course();
        course.setId(COURSE_ID);
        Channel channel = new Channel();
        channel.setId(CHANNEL_ID);
        channel.setCourse(course);
        channel.setIsCourseWide(true);
        User student = new User();
        student.setId(2L);
        student.setLogin("student");
        Post post = new Post();
        post.setId(POST_ID);
        post.setAuthor(student);
        post.setContent("When is the exam?");
        post.setCreationDate(ZonedDateTime.now());
        post.setConversation(channel);
        User bot = new User();
        bot.setId(99L);
        bot.setLogin(User.IRIS_BOT_LOGIN);

        when(featureToggleService.isFeatureEnabled(Feature.AutonomousTutor)).thenReturn(true);
        when(conversationMessageRepository.findMessagePostByIdElseThrow(POST_ID)).thenReturn(post);
        when(irisBotUserService.getIrisBotUser()).thenReturn(bot);
        when(answerPostRepository.save(any(AnswerPost.class))).thenAnswer(this::persist);
    }

    /** Records what each successful save wrote; a save that throws writes nothing. */
    private AnswerPost persist(InvocationOnMock invocation) {
        AnswerPost answer = invocation.getArgument(0);
        answer.setId(77L);
        persistedVerified.add(answer.isVerified());
        return answer;
    }

    private static PyrisAutonomousTutorPipelineStatusUpdateDTO confidentReplyFrom(long channelId) {
        return new PyrisAutonomousTutorPipelineStatusUpdateDTO("The exam is on July 30th.", true, 0.93, PyrisRunState.FINISHED, null, List.of(), List.of(channelId));
    }

    private void assertHeldForReview() {
        assertThat(persistedVerified).containsExactly(false);
        // Only staff are asked; students never get the answer.
        verify(userRepository).findStaffNotificationRecipientsInCourseForConversation(anyLong(), anyLong());
        verify(userRepository, never()).findAllNotificationRecipientsInCourseForConversation(anyLong(), anyLong());
    }

    @Test
    void replyIsSavedAsDraftAndPublishedThroughTheLockedCheck() {
        when(channelRepository.findIdsOfChannelsReadableByAllStudents(eq(COURSE_ID), any())).thenReturn(Set.of(CHANNEL_ID));
        when(answerPostRepository.publishIfConversationsReadable(eq(77L), eq(COURSE_ID), eq(List.of(CHANNEL_ID)), any())).thenReturn(true);

        autonomousTutorService.handleStatusUpdate(new AutonomousTutorJob("job", POST_ID, COURSE_ID), confidentReplyFrom(CHANNEL_ID));

        // Saved only as a draft; the publication itself is the repository's check-and-update in one transaction.
        assertThat(persistedVerified).containsExactly(false);
        verify(userRepository).findAllNotificationRecipientsInCourseForConversation(anyLong(), anyLong());
    }

    @Test
    void replyStaysADraftWhenTheLockedCheckFails() {
        when(channelRepository.findIdsOfChannelsReadableByAllStudents(eq(COURSE_ID), any())).thenReturn(Set.of(CHANNEL_ID));
        when(answerPostRepository.publishIfConversationsReadable(anyLong(), anyLong(), any(), any())).thenThrow(new IllegalStateException("database unavailable"));

        autonomousTutorService.handleStatusUpdate(new AutonomousTutorJob("job", POST_ID, COURSE_ID), confidentReplyFrom(CHANNEL_ID));

        assertHeldForReview();
    }

    @Test
    void replyStaysADraftWhenASourceBecameUnreadable() {
        when(channelRepository.findIdsOfChannelsReadableByAllStudents(eq(COURSE_ID), any())).thenReturn(Set.of(CHANNEL_ID));
        when(answerPostRepository.publishIfConversationsReadable(anyLong(), anyLong(), any(), any())).thenReturn(false);

        autonomousTutorService.handleStatusUpdate(new AutonomousTutorJob("job", POST_ID, COURSE_ID), confidentReplyFrom(CHANNEL_ID));

        assertHeldForReview();
    }
}
