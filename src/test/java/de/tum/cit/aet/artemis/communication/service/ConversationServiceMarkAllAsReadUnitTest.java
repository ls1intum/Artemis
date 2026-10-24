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

class ConversationServiceMarkAllAsReadUnitTest {

    private static final long COURSE_ID = 1L;

    private ConversationParticipantTestRepository conversationParticipantRepository;

    private ConversationTestRepository conversationRepository;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationParticipantRepository = mock(ConversationParticipantTestRepository.class);
        conversationRepository = mock(ConversationTestRepository.class);
        conversationService = new ConversationService(mock(ConversationDTOService.class), mock(UserRepository.class), mock(ChannelRepository.class),
                conversationParticipantRepository, conversationRepository, mock(WebsocketMessagingService.class), mock(OneToOneChatRepository.class), mock(PostRepository.class),
                mock(GroupChatRepository.class), mock(AuthorizationCheckService.class), mock(CourseRepository.class));
    }

    private static Channel channel(long id) {
        var channel = new Channel();
        channel.setId(id);
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
}
