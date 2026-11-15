package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;

class PyrisSubmissionMappingTest {

    private BuildLogEntryService logs;

    private ProgrammingFeedbackSynthesizerService feedback;

    private PyrisDTOService service;

    @BeforeEach
    void setUp() {
        logs = mock(BuildLogEntryService.class);
        feedback = mock(ProgrammingFeedbackSynthesizerService.class);
        service = new PyrisDTOService(null, feedback, logs, new IrisProactiveProperties());
    }

    @Test
    void submissionWithoutParticipationRetainsLocalFilesWithoutFabricatingSubmittedDiff() {
        var submission = new ProgrammingSubmission();
        submission.setId(7L);
        var local = Map.of("Main.java", "class Main {}");
        var dto = service.toPyrisSubmissionDTO(submission, local);
        assertThat(dto.repository()).containsExactlyEntriesOf(local);
        assertThat(dto.submittedRepository()).isEmpty();
        assertThat(dto.submittedRepositoryAvailable()).isFalse();
        assertThat(dto.isPractice()).isFalse();
        assertThat(dto.latestResult()).isNull();
    }

    @Test
    void longFeedbackAndNullableFeedbackOutcomesArePreservedInResultDto() {
        var submission = new ProgrammingSubmission();
        submission.setId(7L);
        var result = new Result();
        result.setId(8L);
        result.setSuccessful(false);
        var longFeedback = new Feedback();
        longFeedback.setId(9L);
        longFeedback.setText("Test output");
        longFeedback.setDetailText("details ".repeat(1000));
        longFeedback.setPositive(null);
        longFeedback.setCredits(null);
        result.setFeedbacks(Set.of(longFeedback));
        submission.addResult(result);
        var dto = service.toPyrisSubmissionDTO(submission);
        assertThat(dto.latestResult().successful()).isFalse();
        assertThat(dto.latestResult().feedbacks()).singleElement().satisfies(mapped -> {
            assertThat(mapped.text()).isEqualTo("details ".repeat(1000));
            assertThat(mapped.testCaseName()).isEqualTo("Test output");
            assertThat(mapped.credits()).isZero();
            assertThat(mapped.positive()).isNull();
            assertThat(mapped.hasTestCase()).isFalse();
        });
        org.mockito.Mockito.verifyNoInteractions(feedback);
    }

    @Test
    void programmingResultTriggersFeedbackSynthesisAndPreservesPracticeFlag() {
        var exercise = new ProgrammingExercise();
        exercise.setId(1L);
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setExercise(exercise);
        participation.setTestRun(true);
        var submission = new ProgrammingSubmission();
        submission.setId(7L);
        submission.setParticipation(participation);
        var result = new Result();
        result.setId(8L);
        result.setSuccessful(false);
        submission.addResult(result);
        var dto = service.toPyrisSubmissionDTO(submission);
        assertThat(dto.isPractice()).isTrue();
        assertThat(dto.submittedRepositoryAvailable()).isFalse();
        org.mockito.Mockito.verify(feedback).attachSynthesizedFeedback(result, exercise, false);
    }
}
