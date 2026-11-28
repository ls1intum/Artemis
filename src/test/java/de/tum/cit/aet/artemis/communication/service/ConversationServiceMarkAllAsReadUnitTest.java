package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.GroupChatRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationDTOService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class ConversationServiceMarkAllAsReadUnitTest {

    private static final long COURSE_ID = 1L;

    private ConversationParticipantTestRepository conversationParticipantRepository;

    private ConversationTestRepository conversationRepository;

    private AuthorizationCheckService authorizationCheckService;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationParticipantRepository = mock(ConversationParticipantTestRepository.class);
        conversationRepository = mock(ConversationTestRepository.class);
        authorizationCheckService = mock(AuthorizationCheckService.class);
        conversationService = new ConversationService(mock(ConversationDTOService.class), mock(UserRepository.class), mock(ChannelRepository.class),
                conversationParticipantRepository, conversationRepository, mock(WebsocketMessagingService.class), mock(OneToOneChatRepository.class), mock(PostRepository.class),
                mock(GroupChatRepository.class), authorizationCheckService, mock(CourseRepository.class));
    }

    private static Channel channel(long id) {
        var channel = new Channel();
        channel.setId(id);
        return channel;
    }

    /**
     * A channel of an exercise that is released in the future, which students must not see yet.
     */
    private static Channel channelOfUnreleasedExercise(long id) {
        var exercise = new TextExercise();
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        var channel = channel(id);
        channel.setExercise(exercise);
        return channel;
    }

    @Test
    void markAllAsRead_createsTheMissingParticipantsAsRead() {
        var user = new User();
        user.setId(42L);
        Channel notYetAccessed = channel(11L);
        when(conversationParticipantRepository.findConversationIdsByUserIdAndCourseId(user.getId(), COURSE_ID)).thenReturn(List.of());
        when(conversationRepository.findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(COURSE_ID, user.getId())).thenReturn(List.of(notYetAccessed));
        when(conversationParticipantRepository.createIfAbsent(eq(notYetAccessed.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L))).thenReturn(true);

        conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user);

        verify(conversationParticipantRepository).createIfAbsent(eq(notYetAccessed.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L));
        verify(conversationParticipantRepository, never()).updateMultipleLastRead(eq(user.getId()), anyList(), any(ZonedDateTime.class));
    }

    @Test
    void markAllAsRead_whenAParticipantIsCreatedConcurrently_createsTheOthersAndMarksItAsRead() {
        var user = new User();
        user.setId(42L);
        Channel openedConcurrently = channel(10L);
        Channel notYetAccessed = channel(11L);
        when(conversationParticipantRepository.findConversationIdsByUserIdAndCourseId(user.getId(), COURSE_ID)).thenReturn(List.of());
        when(conversationRepository.findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(COURSE_ID, user.getId()))
                .thenReturn(List.of(openedConcurrently, notYetAccessed));
        // the participant of the first channel was created by another request in the meantime
        when(conversationParticipantRepository.createIfAbsent(eq(openedConcurrently.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L))).thenReturn(false);
        when(conversationParticipantRepository.createIfAbsent(eq(notYetAccessed.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L))).thenReturn(true);

        conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user);

        ArgumentCaptor<ZonedDateTime> createdAsReadAt = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(conversationParticipantRepository, times(2)).createIfAbsent(any(Long.class), eq(user.getId()), eq(false), createdAsReadAt.capture(), eq(0L));
        ArgumentCaptor<ZonedDateTime> markedAsReadAt = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(conversationParticipantRepository).updateMultipleLastRead(eq(user.getId()), eq(List.of(openedConcurrently.getId())), markedAsReadAt.capture());
        assertThat(createdAsReadAt.getAllValues()).as("every participant is read at the same time, the one created concurrently as well").containsOnly(markedAsReadAt.getValue());
    }

    @Test
    void markAllAsRead_forAStudent_doesNotCreateParticipantsOfChannelsHiddenFromStudents() {
        var user = new User();
        user.setId(42L);
        Channel visible = channel(10L);
        Channel hidden = channelOfUnreleasedExercise(11L);
        when(authorizationCheckService.isAtLeastTeachingAssistantInCourse(COURSE_ID, user)).thenReturn(false);
        when(conversationParticipantRepository.findConversationIdsByUserIdAndCourseId(user.getId(), COURSE_ID)).thenReturn(List.of());
        when(conversationRepository.findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(COURSE_ID, user.getId())).thenReturn(List.of(visible, hidden));
        when(conversationParticipantRepository.createIfAbsent(eq(visible.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L))).thenReturn(true);

        conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user);

        verify(conversationParticipantRepository).createIfAbsent(eq(visible.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L));
        verify(conversationParticipantRepository, never()).createIfAbsent(eq(hidden.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L));
    }

    @Test
    void markAllAsRead_forStaff_createsParticipantsOfChannelsHiddenFromStudents() {
        var user = new User();
        user.setId(42L);
        Channel hidden = channelOfUnreleasedExercise(11L);
        when(authorizationCheckService.isAtLeastTeachingAssistantInCourse(COURSE_ID, user)).thenReturn(true);
        when(conversationParticipantRepository.findConversationIdsByUserIdAndCourseId(user.getId(), COURSE_ID)).thenReturn(List.of());
        when(conversationRepository.findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(COURSE_ID, user.getId())).thenReturn(List.of(hidden));
        when(conversationParticipantRepository.createIfAbsent(eq(hidden.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L))).thenReturn(true);

        conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user);

        verify(conversationParticipantRepository).createIfAbsent(eq(hidden.getId()), eq(user.getId()), eq(false), any(ZonedDateTime.class), eq(0L));
    }
}
