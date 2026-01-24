package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.communication.dto.ExerciseCommunicationDeletionSummaryDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.quiz.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.ShortAnswerMappingRepository;

class QuizExerciseServiceTest {

    @Mock
    private QuizExerciseRepository quizExerciseRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private QuizSubmissionRepository quizSubmissionRepository;

    @Mock
    private InstanceMessageSendService instanceMessageSendService;

    @Mock
    private QuizStatisticService quizStatisticService;

    @Mock
    private QuizBatchService quizBatchService;

    @Mock
    private ExerciseSpecificationService exerciseSpecificationService;

    @Mock
    private DragAndDropMappingRepository dragAndDropMappingRepository;

    @Mock
    private ShortAnswerMappingRepository shortAnswerMappingRepository;

    @Mock
    private ExerciseService exerciseService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizBatchRepository quizBatchRepository;

    @Mock
    private ChannelService channelService;

    @Mock
    private GroupNotificationScheduleService groupNotificationScheduleService;

    @Mock
    private CompetencyProgressApi competencyProgressApi;

    @Mock
    private SlideApi slideApi;

    @Mock
    private CourseCompetencyApi courseCompetencyApi;

    @Mock
    private ParticipationRepository participationRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    private QuizExerciseService quizExerciseService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        quizExerciseService = new QuizExerciseService(quizExerciseRepository, resultRepository, quizSubmissionRepository, instanceMessageSendService, quizStatisticService,
                quizBatchService, exerciseSpecificationService, dragAndDropMappingRepository, shortAnswerMappingRepository, exerciseService, userRepository, quizBatchRepository,
                channelService, groupNotificationScheduleService, Optional.of(competencyProgressApi), Optional.of(slideApi), Optional.of(courseCompetencyApi),
                participationRepository, submissionRepository);
    }

    @Test
    void testGetDeletionSummary() {
        long exerciseId = 1L;
        when(participationRepository.countByExerciseId(exerciseId)).thenReturn(5L);
        when(submissionRepository.countByExerciseId(exerciseId)).thenReturn(10L);
        when(channelService.getExerciseCommunicationDeletionSummary(exerciseId)).thenReturn(new ExerciseCommunicationDeletionSummaryDTO(20L, 25L));

        ExerciseDeletionSummaryDTO summary = quizExerciseService.getDeletionSummary(exerciseId);

        assertThat(summary.numberOfStudentParticipations()).isEqualTo(5);
        assertThat(summary.numberOfSubmissions()).isEqualTo(10);
        assertThat(summary.numberOfAssessments()).isNull(); // QuizExerciseService does not count assessments in getDeletionSummary
        assertThat(summary.numberOfCommunicationPosts()).isEqualTo(20);
        assertThat(summary.numberOfAnswerPosts()).isEqualTo(25);
        assertThat(summary.numberOfBuilds()).isNull();
    }
}
