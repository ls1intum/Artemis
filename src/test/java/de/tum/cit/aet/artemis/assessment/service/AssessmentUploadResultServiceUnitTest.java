package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadResultRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;

@ExtendWith(MockitoExtension.class)
class AssessmentUploadResultServiceUnitTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AssessmentUploadResultRepository assessmentUploadResultRepository;

    @Mock
    private ResultWebsocketService resultWebsocketService;

    @Mock
    private SubmissionTestRepository submissionRepository;

    private AssessmentUploadResultService assessmentUploadResultService;

    @BeforeEach
    void setUp() {
        assessmentUploadResultService = new AssessmentUploadResultService(userRepository, assessmentUploadResultRepository, Optional.empty(), resultWebsocketService,
                submissionRepository);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void shouldNotifyOnlyAfterTransactionCommit() {
        final User assessor = new User();
        final StudentParticipation participation = new StudentParticipation();
        final Submission submission = org.mockito.Mockito.mock(Submission.class);
        when(submission.getParticipation()).thenReturn(participation);
        final Result result = new Result();
        result.setId(1L);
        result.setSubmission(submission);
        when(userRepository.getUserWithAuthorities()).thenReturn(assessor);
        when(assessmentUploadResultRepository.findAllWithSubmissionAndFeedbackAndTeamStudentsByIds(List.of(1L))).thenReturn(List.of(result));

        assessmentUploadResultService.saveManualResults(List.of(result), List.of(), true);

        verifyNoInteractions(resultWebsocketService);
        final List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCommit();

        verify(resultWebsocketService).broadcastNewResult(participation, result);
    }
}
