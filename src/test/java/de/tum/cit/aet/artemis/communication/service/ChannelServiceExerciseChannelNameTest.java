package de.tum.cit.aet.artemis.communication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;

class ChannelServiceExerciseChannelNameTest {

    private static final long EXERCISE_ID = 5L;

    private ChannelRepository channelRepository;

    private ChannelService channelService;

    private Exercise exercise;

    @BeforeEach
    void setUp() {
        channelRepository = mock(ChannelRepository.class);
        channelService = new ChannelService(mock(ConversationParticipantRepository.class), channelRepository, mock(ConversationService.class), mock(UserRepository.class),
                mock(StudentParticipationRepository.class), Optional.empty());
        exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);
    }

    private void exerciseWithChannelNamed(String currentName, String newName) {
        var channel = new Channel();
        channel.setName(currentName);
        when(channelRepository.findChannelByExerciseId(EXERCISE_ID)).thenReturn(channel);
        when(exercise.getChannelName()).thenReturn(newName);
    }

    @Test
    void aNewNameThatTheChannelRefusesIsRefusedWithoutSavingAnything() {
        exerciseWithChannelNamed("exercise-sorting", "Bad Name");

        assertThatThrownBy(() -> channelService.validateExerciseChannelUpdate(exercise)).isInstanceOfSatisfying(BadRequestAlertException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo("namePatternInvalid"));

        verify(channelRepository, never()).save(any());
    }

    @Test
    void aValidNewNameIsAccepted() {
        exerciseWithChannelNamed("exercise-sorting", "exercise-sorting-2");

        assertThatCode(() -> channelService.validateExerciseChannelUpdate(exercise)).doesNotThrowAnyException();
        verify(channelRepository, never()).save(any());
    }

    @Test
    void theSameNameIsNotCheckedAgain() {
        // the update does not check a name that stays the same, so a channel with an older name that the pattern would refuse can still be updated
        exerciseWithChannelNamed("Old Name", "Old Name");

        assertThatCode(() -> channelService.validateExerciseChannelUpdate(exercise)).doesNotThrowAnyException();
    }

    @Test
    void anExerciseWithoutAChannelNameIsNotChecked() {
        when(exercise.getChannelName()).thenReturn(null);

        assertThatCode(() -> channelService.validateExerciseChannelUpdate(exercise)).doesNotThrowAnyException();
        verifyNoInteractions(channelRepository);
    }

    @Test
    void anExerciseWithoutAChannelIsNotChecked() {
        when(exercise.getChannelName()).thenReturn("Bad Name");
        when(channelRepository.findChannelByExerciseId(EXERCISE_ID)).thenReturn(null);

        assertThatCode(() -> channelService.validateExerciseChannelUpdate(exercise)).doesNotThrowAnyException();
    }
}
