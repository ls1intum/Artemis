package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentNoteRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingInstructionRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionFilterService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.localci.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain unit test of how stored long feedback is reattached when feedback is saved.
 */
class ResultServiceLongFeedbackTest {

    private LongFeedbackTextRepository longFeedbackTextRepository;

    private ResultService resultService;

    @BeforeEach
    void setUp() {
        longFeedbackTextRepository = mock(LongFeedbackTextRepository.class);
        resultService = new ResultService(mock(UserTestRepository.class), mock(ResultTestRepository.class), mock(AssessmentNoteRepository.class), Optional.empty(),
                mock(ResultWebsocketService.class), mock(ComplaintResponseRepository.class), mock(RatingRepository.class), mock(FeedbackRepository.class),
                longFeedbackTextRepository, mock(ComplaintRepository.class), mock(ParticipantScoreRepository.class), mock(AuthorizationCheckService.class),
                mock(ExerciseDateService.class), Optional.empty(), mock(BuildJobRepository.class), mock(BuildLogEntryService.class), mock(StudentParticipationTestRepository.class),
                mock(ProgrammingExerciseTaskService.class), mock(ProgrammingExerciseTestRepository.class), mock(SubmissionFilterService.class), Optional.empty(),
                mock(TestCaseFeedbackRepository.class), mock(ScaFeedbackRepository.class), mock(ProgrammingFeedbackSynthesizerService.class),
                mock(GradingInstructionRepository.class));
    }

    private static Feedback feedbackFlaggedWithLongText(long id) {
        var feedback = new Feedback();
        feedback.setId(id);
        feedback.setHasLongFeedbackText(true);
        return feedback;
    }

    @Test
    void saveFeedback_flagWithoutStoredLongText_clearsTheFlag() {
        var feedback = feedbackFlaggedWithLongText(11L);
        when(longFeedbackTextRepository.findByFeedbackIds(List.of(11L))).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(resultService, "saveFeedbackWithHibernateWorkaround", new Result(), List.of(feedback));

        assertThat(feedback.getHasLongFeedbackText()).isFalse();
        assertThat(feedback.getLongFeedback()).isEmpty();
    }

    @Test
    void saveFeedback_flagWithStoredLongText_reattachesTheText() {
        var feedback = feedbackFlaggedWithLongText(12L);
        var stored = new LongFeedbackText();
        stored.setFeedback(feedback);
        when(longFeedbackTextRepository.findByFeedbackIds(List.of(12L))).thenReturn(List.of(stored));

        ReflectionTestUtils.invokeMethod(resultService, "saveFeedbackWithHibernateWorkaround", new Result(), List.of(feedback));

        assertThat(feedback.getHasLongFeedbackText()).isTrue();
        assertThat(feedback.getLongFeedback()).containsSame(stored);
    }
}
