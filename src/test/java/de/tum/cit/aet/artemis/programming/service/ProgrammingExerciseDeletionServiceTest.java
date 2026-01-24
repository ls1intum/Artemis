package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.communication.dto.ExerciseCommunicationDeletionSummaryDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;

class ProgrammingExerciseDeletionServiceTest {

    @Mock
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Mock
    private ParticipationDeletionService participationDeletionService;

    @Mock
    private ContinuousIntegrationService continuousIntegrationService;

    @Mock
    private InstanceMessageSendService instanceMessageSendService;

    @Mock
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Mock
    private BuildJobRepository buildJobRepository;

    @Mock
    private ChannelService channelService;

    @Mock
    private SubmissionRepository submissionRepository;

    private ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        programmingExerciseDeletionService = new ProgrammingExerciseDeletionService(programmingExerciseRepositoryService, programmingExerciseRepository,
                participationDeletionService, Optional.of(continuousIntegrationService), instanceMessageSendService, programmingExerciseTaskRepository, buildJobRepository,
                channelService, submissionRepository);
    }

    @Test
    void testGetDeletionSummary() {
        long exerciseId = 1L;
        when(programmingExerciseRepository.countStudentParticipationsByExerciseId(exerciseId)).thenReturn(5L);
        when(buildJobRepository.countBuildJobsByExerciseIds(Set.of(exerciseId))).thenReturn(10L);
        when(submissionRepository.countByExerciseId(exerciseId)).thenReturn(15L);
        when(channelService.getExerciseCommunicationDeletionSummary(exerciseId)).thenReturn(new ExerciseCommunicationDeletionSummaryDTO(20L, 25L));

        ExerciseDeletionSummaryDTO summary = programmingExerciseDeletionService.getDeletionSummary(exerciseId);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(5);
        assertThat(summary.numberOfBuilds()).isEqualTo(10);
        assertThat(summary.numberOfSubmissions()).isEqualTo(15);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(20);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(25);
    }
}
