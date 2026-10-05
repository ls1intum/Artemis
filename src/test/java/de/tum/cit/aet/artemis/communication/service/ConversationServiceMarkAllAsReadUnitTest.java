package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
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
    void markAllAsRead_whenAParticipantIsCreatedConcurrently_createsTheOthersAndMarksItAsRead() {
        var user = new User();
        user.setId(42L);
        Channel openedConcurrently = channel(10L);
        Channel notYetAccessed = channel(11L);
        when(conversationParticipantRepository.findConversationIdsByUserIdAndCourseId(user.getId(), COURSE_ID)).thenReturn(List.of());
        when(conversationRepository.findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(COURSE_ID, user.getId()))
                .thenReturn(List.of(openedConcurrently, notYetAccessed));
        when(conversationParticipantRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("conversation_participant_uq"));
        when(conversationParticipantRepository.save(argThat(participant -> participant != null && participant.getConversation() == openedConcurrently)))
                .thenThrow(new DataIntegrityViolationException("conversation_participant_uq"));
        when(conversationParticipantRepository.existsByConversationIdAndUserId(openedConcurrently.getId(), user.getId())).thenReturn(true);

        conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user);

        ArgumentCaptor<ConversationParticipant> savedParticipants = ArgumentCaptor.forClass(ConversationParticipant.class);
        verify(conversationParticipantRepository, times(2)).save(savedParticipants.capture());
        ArgumentCaptor<ZonedDateTime> lastRead = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(conversationParticipantRepository).updateMultipleLastReadAsync(eq(user.getId()), eq(List.of(openedConcurrently.getId())), lastRead.capture());
        assertThat(savedParticipants.getAllValues()).as("both participants are saved one by one, marked as read").hasSize(2).allSatisfy(participant -> {
            assertThat(participant.getUnreadMessagesCount()).isZero();
            assertThat(participant.getLastRead()).isEqualTo(lastRead.getValue());
        });
        assertThat(savedParticipants.getAllValues()).extracting(ConversationParticipant::getConversation).containsExactly(openedConcurrently, notYetAccessed);
    }

    @Test
    void markAllAsRead_whenSavingFailsForAnotherReason_rethrowsTheError() {
        var user = new User();
        user.setId(42L);
        Channel deletedConcurrently = channel(12L);
        when(conversationParticipantRepository.findConversationIdsByUserIdAndCourseId(user.getId(), COURSE_ID)).thenReturn(List.of());
        when(conversationRepository.findAllCourseWideChannelsByUserIdAndCourseIdWithoutConversationParticipant(COURSE_ID, user.getId())).thenReturn(List.of(deletedConcurrently));
        when(conversationParticipantRepository.saveAll(anyList())).thenThrow(new DataIntegrityViolationException("foreign key violation"));
        when(conversationParticipantRepository.save(any(ConversationParticipant.class))).thenThrow(new DataIntegrityViolationException("foreign key violation"));
        when(conversationParticipantRepository.existsByConversationIdAndUserId(deletedConcurrently.getId(), user.getId())).thenReturn(false);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user));
    }
}
