package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
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
 * Plain unit tests of assessment validation, including updates without a feedback list or an existing result.
 */
class AssessmentServiceNullFeedbackTest {

    private ResultService resultService;

    private ResultTestRepository resultRepository;

    private SubmissionService submissionService;

    private UserTestRepository userRepository;

    private ComplaintResponseService complaintResponseService;

    private AssessmentService assessmentService;

    @BeforeEach
    void setUp() {
        resultService = mock(ResultService.class);
        resultRepository = mock(ResultTestRepository.class);
        submissionService = mock(SubmissionService.class);
        userRepository = mock(UserTestRepository.class);
        complaintResponseService = mock(ComplaintResponseService.class);
        assessmentService = new AssessmentService(complaintResponseService, mock(ComplaintRepository.class), mock(FeedbackRepository.class), resultRepository,
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

    @Test
    void saveManualAssessment_withoutOriginalResult_acceptsNewFeedback() {
        prepareSave();
        var feedback = new Feedback();
        feedback.setCredits(2.0);

        var result = assessmentService.saveManualAssessment(new TextSubmission(), List.of(feedback), null, null, 3L);

        assertThat(result.getFeedbacks()).containsExactly(feedback);
        assertThat(feedback.getResult()).isSameAs(result);
    }

    private static Stream<List<Feedback>> feedbackWithoutStoredIds() {
        return Stream.of(null, List.of(), List.of(new Feedback()));
    }

    @ParameterizedTest
    @MethodSource("feedbackWithoutStoredIds")
    void updateAssessmentAfterComplaint_withoutOriginalResult_rejectsBeforeResolvingComplaint(List<Feedback> feedbacks) {
        var exercise = new TextExercise();
        exercise.setId(3L);
        var response = new ComplaintResponse();
        response.setId(7L);
        var update = new AssessmentUpdate(feedbacks, response, null);
        var submission = new TextSubmission();

        assertThatThrownBy(() -> assessmentService.updateAssessmentAfterComplaint(submission.getLatestResult(), exercise, update))
                .isInstanceOfSatisfying(BadRequestAlertException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getErrorKey()).isEqualTo("resultnotfound");
                });

        verifyNoInteractions(complaintResponseService, submissionService, resultRepository);
        assertThat(submission.getResults()).isEmpty();
    }

    @Test
    void updateAssessmentAfterComplaint_withForeignFeedback_rejectsBeforeResolvingComplaint() {
        var exercise = new TextExercise();
        exercise.setId(3L);
        var originalResult = new Result();
        originalResult.setId(4L);
        var foreignFeedback = new Feedback();
        foreignFeedback.setId(5L);
        var update = new AssessmentUpdate(List.of(foreignFeedback), new ComplaintResponse(), null);

        assertThatThrownBy(() -> assessmentService.updateAssessmentAfterComplaint(originalResult, exercise, update)).isInstanceOfSatisfying(BadRequestAlertException.class,
                exception -> assertThat(exception.getErrorKey()).isEqualTo("feedbackIdMismatch"));

        verifyNoInteractions(complaintResponseService, submissionService, resultRepository);
        assertThat(originalResult.getFeedbacks()).isEmpty();
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
