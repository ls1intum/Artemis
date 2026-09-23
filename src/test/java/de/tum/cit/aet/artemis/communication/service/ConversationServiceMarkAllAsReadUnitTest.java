package de.tum.cit.aet.artemis.communication.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

        conversationService.markAllConversationOfAUserAsRead(COURSE_ID, user);

        verify(conversationParticipantRepository, times(2)).save(any(ConversationParticipant.class));
        verify(conversationParticipantRepository).save(argThat(participant -> participant != null && participant.getConversation() == notYetAccessed));
        verify(conversationParticipantRepository).updateMultipleLastReadAsync(eq(user.getId()), eq(List.of(openedConcurrently.getId())), any());
    }
}
