package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

class VariantBuildVerificationServiceFeedbackTest {

    private final BuildLogEntryService logs = mock(BuildLogEntryService.class);

    private final VariantBuildVerificationService service = new VariantBuildVerificationService(mock(TemplateProgrammingExerciseParticipationTestRepository.class),
            mock(SolutionProgrammingExerciseParticipationRepository.class), logs, mock(ResultTestRepository.class), mock(GitService.class),
            mock(ContinuousIntegrationTriggerService.class), mock(ProgrammingExerciseParticipationService.class), mock(ProgrammingFeedbackSynthesizerService.class));

    @ParameterizedTest
    @CsvSource({ "TEMPLATE,0,1,false,true", "TEMPLATE,0,0,true,false", "TEMPLATE,0,,true,false", "TEMPLATE,1,1,true,false", "TEMPLATE,,1,true,false", "SOLUTION,100,0,false,true",
            "SOLUTION,99.999,1,true,false", "SOLUTION,,1,true,false", "TESTS,,0,true,true", "TESTS,100,1,false,false", "AUXILIARY,100,1,true,false", "USER,100,1,true,false" })
    void buildTargetsRequireExactRepositorySpecificOutcome(RepositoryType type, Double score, Integer tests, boolean successful, boolean expected) {
        Result result = mock(Result.class);
        when(result.getScore()).thenReturn(score);
        when(result.getTestCaseCount()).thenReturn(tests);
        when(result.isSuccessful()).thenReturn(successful);
        assertThat(service.hasReachedTargetResult(type, result)).isEqualTo(expected);
        assertThat(service.hasReachedTargetResult(type, null)).isFalse();
    }

    @Test
    void descriptionsRepresentMissingResultAndMissingLogsExactly() {
        assertThat(service.describeBuildResult(null)).isEqualTo("The build did not produce a result.");
        Result absent = mock(Result.class);
        when(absent.getScore()).thenReturn(null);
        when(absent.getTestCaseCount()).thenReturn(null);
        assertThat(service.describeBuildResult(absent)).isEqualTo("Score: unknown\n\nBuild logs:\n(no build logs available)");
        assertThat(service.unexpectedlyPassingTestNames(null)).isEmpty();
    }

    @Test
    void descriptionsPrioritizeFailedTestsUseFirstDetailLineAndFallbackToText() {
        Result result = mock(Result.class);
        when(result.getScore()).thenReturn(50.0);
        when(result.getTestCaseCount()).thenReturn(2);
        when(result.getPassedTestCaseCount()).thenReturn(1);
        Feedback passed = feedback("passing", true, "ignored", "fine\nsecond line");
        Feedback failed = feedback("", false, "fallback", " ");
        Feedback noText = feedback(null, null, null, null);
        Feedback noTest = mock(Feedback.class);
        when(result.getFeedbacks()).thenReturn(new java.util.LinkedHashSet<>(Arrays.asList(passed, null, noTest, failed, noText)));

        String resultText = service.describeBuildResult(result);

        assertThat(resultText).isEqualTo(
                "Score: 50.0% (1/2 tests passed)\n\nTest results:\n- Unnamed test: FAILED - fallback\n- Unnamed test: FAILED\n- passing: PASSED - fine\n\nBuild logs:\n(no build logs available)");
        assertThat(service.unexpectedlyPassingTestNames(result)).containsExactly("passing");
    }

    @Test
    void descriptionsBoundFeedbackAndKeepTailOfCompilerLogs() {
        Result result = mock(Result.class);
        ProgrammingSubmission submission = new ProgrammingSubmission();
        when(result.getSubmission()).thenReturn(submission);
        when(logs.getLatestBuildLogs(submission)).thenReturn(List.of(new BuildLogEntry(ZonedDateTime.now(), "discard-prefix" + "x".repeat(10000))));
        java.util.Set<Feedback> feedbacks = java.util.stream.IntStream.range(0, 25).mapToObj(index -> feedback("test-" + index, false, null, "y".repeat(501)))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        when(result.getFeedbacks()).thenReturn(feedbacks);

        String description = service.describeBuildResult(result);

        assertThat(description).contains("- test-19: FAILED - " + "y".repeat(500) + "...").doesNotContain("test-20", "discard-prefix");
        assertThat(description.substring(description.indexOf(VariantBuildVerificationService.BUILD_LOGS_SECTION) + VariantBuildVerificationService.BUILD_LOGS_SECTION.length()))
                .isEqualTo("x".repeat(10000));
    }

    @Test
    void unavailableLogsAndDetachedFeedbackDegradeWithoutFailingDescriptions() {
        Result result = mock(Result.class);
        when(result.getSubmission()).thenReturn(new ProgrammingSubmission());
        when(result.getScore()).thenReturn(null);
        when(result.getTestCaseCount()).thenReturn(null);
        when(result.getFeedbacks()).thenThrow(new LazyInitializationException("detached"));
        when(logs.getLatestBuildLogs(any())).thenThrow(new IllegalStateException("gone"));
        assertThat(service.describeBuildResult(result)).isEqualTo("Score: unknown\n\nBuild logs:\n(build logs could not be retrieved)");
        assertThat(service.unexpectedlyPassingTestNames(result)).isEmpty();
        org.mockito.Mockito.doReturn(List.of(new BuildLogEntry(ZonedDateTime.now(), "short compiler log"))).when(logs).getLatestBuildLogs(any());
        assertThat(service.describeBuildResult(result)).endsWith("Build logs:\nshort compiler log");
        when(logs.getLatestBuildLogs(any())).thenReturn(List.of());
        assertThat(service.describeBuildResult(result)).endsWith("(build logs could not be retrieved)");
    }

    @Test
    void passingNamesExcludeMissingBlankAndNegativeFeedback() {
        Result result = mock(Result.class);
        java.util.Set<Feedback> feedbacks = new java.util.LinkedHashSet<>(Arrays.asList(null, mock(Feedback.class), feedback(null, true, null, null),
                feedback(" ", true, null, null), feedback("negative", false, null, null), feedback("actual", true, null, null)));
        when(result.getFeedbacks()).thenReturn(feedbacks);
        assertThat(service.unexpectedlyPassingTestNames(result)).containsExactly("actual");
    }

    private Feedback feedback(String name, Boolean positive, String text, String detail) {
        Feedback feedback = mock(Feedback.class);
        ProgrammingExerciseTestCase test = mock(ProgrammingExerciseTestCase.class);
        when(test.getTestName()).thenReturn(name);
        when(feedback.getTestCase()).thenReturn(test);
        when(feedback.isPositive()).thenReturn(positive);
        when(feedback.getText()).thenReturn(text);
        when(feedback.getDetailText()).thenReturn(detail);
        return feedback;
    }
}
