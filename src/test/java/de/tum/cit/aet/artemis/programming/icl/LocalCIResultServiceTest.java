package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCIJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class LocalCIResultServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localciresultservice";

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    void testThrowsExceptionWhenResultIsNotLocalCIBuildResult() {
        var wrongBuildResult = ProgrammingExerciseFactory.generateTestResultDTO("some-name", "some-repository", ZonedDateTime.now().minusSeconds(10),
                programmingExercise.getProgrammingLanguage(), false, Collections.emptyList(), Collections.emptyList(), null, null, null);
        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> localCIResultService.convertBuildResult(wrongBuildResult))
                .withMessage("The request body is not of type LocalCIBuildResult");
    }

    @Test
    void testInitializationErrorIncludesClassName() {
        // Create a LocalCITestJobDTO with initializationError and classname
        LocalCITestJobDTO initErrorTest = new LocalCITestJobDTO(ProgrammingExerciseGradingService.TESTCASE_INITIALIZATION_ERROR_NAME, "de.tum.cit.aet.SortingExampleBehaviorTest",
                List.of("java.lang.RuntimeException: error"));

        LocalCIJobDTO job = new LocalCIJobDTO(List.of(initErrorTest), List.of());
        BuildResult buildResult = new BuildResult("main", "abc123", "def456", false, ZonedDateTime.now(), List.of(job), List.of(), List.of(), false);

        // Process the build result
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var result = localCIResultService.createResultFromBuildResult(buildResult, participation);

        // Verify the feedback has the qualified test name
        assertThat(result.getFeedbacks()).isNotEmpty();
        var initErrorFeedback = result.getFeedbacks().stream().filter(f -> f.getText() != null && f.getText().contains("initializationError")).findFirst();
        assertThat(initErrorFeedback).isPresent();
        // The test name should include the class name: "SortingExampleBehaviorTest.initializationError"
        assertThat(initErrorFeedback.get().getText()).isEqualTo("SortingExampleBehaviorTest.initializationError");
    }
}
