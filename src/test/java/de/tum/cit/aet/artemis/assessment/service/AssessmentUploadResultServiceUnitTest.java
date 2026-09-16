package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadResultRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

@ExtendWith(MockitoExtension.class)
class AssessmentUploadResultServiceUnitTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private AssessmentUploadResultRepository assessmentUploadResultRepository;

    @Mock
    private ResultWebsocketService resultWebsocketService;

    private AssessmentUploadResultService assessmentUploadResultService;

    @BeforeEach
    void setUp() {
        assessmentUploadResultService = new AssessmentUploadResultService(userRepository, assessmentUploadResultRepository, Optional.empty(), resultWebsocketService);
    }

    @Test
    void shouldStoreResultsAndNotifyAboutThem() {
        final User assessor = new User();
        final StudentParticipation participation = new StudentParticipation();
        final Submission submission = mock(Submission.class);
        when(submission.getParticipation()).thenReturn(participation);
        final Result result = new Result();
        result.setId(1L);
        result.setSubmission(submission);
        when(userRepository.getUserWithAuthorities()).thenReturn(assessor);
        when(assessmentUploadResultRepository.saveAll(List.of(result))).thenReturn(List.of(result));
        when(assessmentUploadResultRepository.findAllWithSubmissionAndFeedbackAndTeamStudentsByIds(List.of(1L))).thenReturn(List.of(result));

        final List<Result> savedResults = assessmentUploadResultService.saveManualResults(List.of(result), List.of(), true);

        assertThat(savedResults).containsExactly(result);
        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(result.getAssessor()).isEqualTo(assessor);
        verify(assessmentUploadResultRepository).saveAll(List.of(result));
        // The save has already committed when it returns, so the broadcast happens before saveManualResults returns rather than in an after-commit callback.
        verify(resultWebsocketService).broadcastNewResult(participation, result);
    }
}
