package de.tum.cit.aet.artemis.tutorialgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

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
 * Unit tests for {@link TutorialGroupChannelManagementService}.
 */
class TutorialGroupChannelManagementServiceTest {

    private ChannelService channelService;

    private ConversationService conversationService;

    private TutorialGroupTestRepository tutorialGroupRepository;

    private ChannelRepository channelRepository;

    private TutorialGroupChannelManagementService service;

    private TutorialGroup tutorialGroup;

    private User teachingAssistant;

    private Channel channel;

    @BeforeEach
    void setUp() {
        channelService = mock(ChannelService.class);
        conversationService = mock(ConversationService.class);
        tutorialGroupRepository = mock(TutorialGroupTestRepository.class);
        channelRepository = mock(ChannelRepository.class);
        service = new TutorialGroupChannelManagementService(channelService, conversationService, tutorialGroupRepository, mock(TutorialGroupRegistrationTestRepository.class),
                channelRepository);

        tutorialGroup = new TutorialGroup();
        tutorialGroup.setId(1L);
        tutorialGroup.setCourse(new Course());
        tutorialGroup.getCourse().setId(4L);
        teachingAssistant = new User();
        teachingAssistant.setId(2L);
        channel = new Channel();
        channel.setId(3L);
        // the PersistenceUtil cannot tell whether a detached object has loaded the association, so the fallback lookup returns the same group
        when(tutorialGroupRepository.findByIdWithTeachingAssistantAndCourseElseThrow(1L)).thenReturn(tutorialGroup);
    }

    @Test
    void updateName_withCollision_addsHyphenatedNumericSuffix() {
        tutorialGroup.setTitle("Renamed");
        channel.setName("tutorgroup-original");
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.of(channel));
        when(channelRepository.existsChannelByNameAndCourseId("tutorgroup-renamed", 4L)).thenReturn(true);

        service.updateNameOfTutorialGroupChannelIfItExists(tutorialGroup);

        assertThat(channel.getName()).matches("tutorgroup-renamed-[0-9]+").hasSizeLessThanOrEqualTo(30);
        verify(channelRepository).save(channel);
    }

    @ParameterizedTest
    @ValueSource(strings = { "a", "abcdefghijklmnopqr" })
    void updateName_withRepeatedCollisions_keepsMaximumLengthAndHyphenatedSuffix(String title) {
        tutorialGroup.setTitle(title);
        channel.setName("tutorgroup-original");
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.of(channel));
        when(channelRepository.existsChannelByNameAndCourseId(anyString(), eq(4L))).thenReturn(true, true, true, false);

        service.updateNameOfTutorialGroupChannelIfItExists(tutorialGroup);

        var names = ArgumentCaptor.forClass(String.class);
        verify(channelRepository, times(4)).existsChannelByNameAndCourseId(names.capture(), eq(4L));
        assertThat(names.getAllValues()).allSatisfy(name -> assertThat(name).hasSizeLessThanOrEqualTo(30));
        assertThat(names.getAllValues().subList(1, 4)).allSatisfy(name -> assertThat(name).matches("tutorgroup-[a-z]+-[0-9]+"));
        assertThat(channel.getName()).hasSize(Math.min(30, "tutorgroup-".length() + title.length() + 4)).matches("tutorgroup-[a-z]+-[0-9]{3}");
    }

    @Test
    void updateName_whenAllCandidatesCollide_failsWithoutInvalidChannelNames() {
        tutorialGroup.setTitle("a");
        channel.setName("tutorgroup-original");
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.of(channel));
        when(channelRepository.existsChannelByNameAndCourseId(anyString(), eq(4L))).thenReturn(true);

        assertThatIllegalStateException().isThrownBy(() -> service.updateNameOfTutorialGroupChannelIfItExists(tutorialGroup));

        var names = ArgumentCaptor.forClass(String.class);
        verify(channelRepository, times(29)).existsChannelByNameAndCourseId(names.capture(), eq(4L));
        assertThat(names.getAllValues()).allSatisfy(name -> assertThat(name).matches("[a-z0-9][a-z0-9:-]{0,29}"));
        assertThat(channel.getName()).isEqualTo("tutorgroup-original");
        verify(channelRepository, never()).save(any());
    }

    @Test
    void updateName_whenChannelAlreadyOwnsNormalizedName_preservesName() {
        tutorialGroup.setTitle("ReNamed");
        channel.setName("tutorgroup-renamed");
        when(tutorialGroupRepository.getTutorialGroupChannel(1L)).thenReturn(Optional.of(channel));

        service.updateNameOfTutorialGroupChannelIfItExists(tutorialGroup);

        assertThat(channel.getName()).isEqualTo("tutorgroup-renamed");
        verify(channelRepository, never()).existsChannelByNameAndCourseId(anyString(), any());
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
