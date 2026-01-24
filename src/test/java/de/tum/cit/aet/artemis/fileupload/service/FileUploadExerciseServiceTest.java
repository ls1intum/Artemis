package de.tum.cit.aet.artemis.fileupload.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.dto.ExerciseCommunicationDeletionSummaryDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;

class FileUploadExerciseServiceTest {

    @Mock
    private ExerciseSpecificationService exerciseSpecificationService;

    @Mock
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Mock
    private ChannelService channelService;

    @Mock
    private ParticipationRepository participationRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private ResultRepository resultRepository;

    private FileUploadExerciseService fileUploadExerciseService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        fileUploadExerciseService = new FileUploadExerciseService(exerciseSpecificationService, fileUploadExerciseRepository, channelService, participationRepository,
                submissionRepository, resultRepository);
    }

    @Test
    void testGetDeletionSummary() {
        long exerciseId = 1L;
        when(participationRepository.countByExerciseId(exerciseId)).thenReturn(5L);
        when(submissionRepository.countByExerciseId(exerciseId)).thenReturn(10L);
        when(resultRepository.countNumberOfFinishedAssessmentsForExercise(exerciseId)).thenReturn(15L);
        when(channelService.getExerciseCommunicationDeletionSummary(exerciseId)).thenReturn(new ExerciseCommunicationDeletionSummaryDTO(20L, 25L));

        ExerciseDeletionSummaryDTO summary = fileUploadExerciseService.getDeletionSummary(exerciseId);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(5);
        assertThat(summary.numberOfSubmissions()).isEqualTo(10);
        assertThat(summary.numberOfAssessments()).isEqualTo(15);
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(20);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(25);
        assertThat(summary.numberOfBuilds()).isNull();
    }
}
