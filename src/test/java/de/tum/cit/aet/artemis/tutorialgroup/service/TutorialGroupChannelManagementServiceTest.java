package de.tum.cit.aet.artemis.tutorialgroup.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupRegistrationTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;

/**
 * Unit tests for {@link TutorialGroupChannelManagementService#addTeachingAssistantToTutorialGroupChannel}.
 */
class TutorialGroupChannelManagementServiceTest {

    private ChannelService channelService;

    private ConversationService conversationService;

    private TutorialGroupTestRepository tutorialGroupRepository;

    private TutorialGroupChannelManagementService service;

    private TutorialGroup tutorialGroup;

    private User teachingAssistant;

    private Channel channel;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        conversationService = mock(ConversationService.class);
        tutorialGroupRepository = mock(TutorialGroupTestRepository.class);
        service = new TutorialGroupChannelManagementService(channelService, conversationService, tutorialGroupRepository, mock(TutorialGroupRegistrationTestRepository.class),
                mock(ChannelRepository.class));

        tutorialGroup = new TutorialGroup();
        tutorialGroup.setId(1L);
        tutorialGroup.setCourse(new Course());
        teachingAssistant = new User();
        teachingAssistant.setId(2L);
        channel = new Channel();
        channel.setId(3L);
        // the PersistenceUtil cannot tell whether a detached object has loaded the association, so the fallback lookup returns the same group
        when(tutorialGroupRepository.findByIdWithTeachingAssistantAndCourseElseThrow(1L)).thenReturn(tutorialGroup);
    }

    @Test
    void addTeachingAssistantToTutorialGroupChannel_withTeachingAssistantAndChannel_registersAndGrantsModeratorRole() {
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.of(channel));

        service.addTeachingAssistantToTutorialGroupChannel(tutorialGroup);

        verify(conversationService).registerUsersToConversation(tutorialGroup.getCourse(), Set.of(teachingAssistant), channel, Optional.empty());
        verify(channelService).grantChannelModeratorRole(channel, Set.of(teachingAssistant));
    }

    @Test
    void addTeachingAssistantToTutorialGroupChannel_withoutTeachingAssistant_doesNothing() {
        tutorialGroup.setTeachingAssistant(null);
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.of(channel));

        service.addTeachingAssistantToTutorialGroupChannel(tutorialGroup);

        verify(conversationService, never()).registerUsersToConversation(any(), any(), any(), any());
        verify(channelService, never()).grantChannelModeratorRole(any(), any());
    }

    @Test
    void addTeachingAssistantToTutorialGroupChannel_withoutChannel_doesNothing() {
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.empty());

        service.addTeachingAssistantToTutorialGroupChannel(tutorialGroup);

        verify(conversationService, never()).registerUsersToConversation(any(), any(), any(), any());
        verify(channelService, never()).grantChannelModeratorRole(any(), any());
    }
}
