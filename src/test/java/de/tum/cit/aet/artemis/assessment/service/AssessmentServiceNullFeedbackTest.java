package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.notification.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Plain unit test of the grading instruction validation that is skipped when an assessment carries no feedback list.
 */
class AssessmentServiceNullFeedbackTest {

    private ResultService resultService;

    private ResultTestRepository resultRepository;

    private SubmissionService submissionService;

    private UserTestRepository userRepository;

    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        resultService = mock(ResultService.class);
        resultRepository = mock(ResultTestRepository.class);
        submissionService = mock(SubmissionService.class);
        userRepository = mock(UserTestRepository.class);
        assessmentService = new AssessmentService(mock(ComplaintResponseService.class), mock(ComplaintRepository.class), mock(FeedbackRepository.class), resultRepository,
                mock(StudentParticipationTestRepository.class), resultService, submissionService, mock(SubmissionTestRepository.class), Optional.empty(), userRepository,
                Optional.empty(), mock(SingleUserNotificationService.class), mock(ResultWebsocketService.class));
    }

    private void prepareSave() {
        when(submissionService.saveNewEmptyResult(any(), anyLong())).thenReturn(new Result());
        when(userRepository.getUser()).thenReturn(new User());
        when(resultRepository.save(any(Result.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void saveManualAssessment_withoutFeedbackList_skipsTheValidation() {
        prepareSave();

        var result = assessmentService.saveManualAssessment(new TextSubmission(), null, null, null, 3L);

        assertThat(result).isNotNull();
        verify(resultService, never()).validateGradingInstructions(any(), anyLong());
    }

    @Test
    void saveManualAssessment_withFeedbackList_validatesIt() {
        prepareSave();
        List<Feedback> feedbackList = List.of();

        assessmentService.saveManualAssessment(new TextSubmission(), feedbackList, null, null, 3L);

        verify(resultService).validateGradingInstructions(feedbackList, 3L);
    }

    private static AssessmentUpdate updateWithoutComplaintResponse(List<Feedback> feedbacks) {
        return new AssessmentUpdate(feedbacks, null, null);
    }

    @Test
    void updateAssessmentAfterComplaint_withoutFeedbackList_skipsTheValidation() {
        var exercise = new TextExercise();
        exercise.setId(3L);

        assertThatThrownBy(() -> assessmentService.updateAssessmentAfterComplaint(new Result(), exercise, updateWithoutComplaintResponse(null)))
                .isInstanceOf(BadRequestAlertException.class);

        verify(resultService, never()).validateGradingInstructions(any(), anyLong());
    }

    @Test
    void updateAssessmentAfterComplaint_withFeedbackList_validatesIt() {
        var exercise = new TextExercise();
        exercise.setId(3L);
        List<Feedback> feedbacks = List.of();

        assertThatThrownBy(() -> assessmentService.updateAssessmentAfterComplaint(new Result(), exercise, updateWithoutComplaintResponse(feedbacks)))
                .isInstanceOf(BadRequestAlertException.class);

        verify(resultService).validateGradingInstructions(feedbacks, 3L);
    }
}
