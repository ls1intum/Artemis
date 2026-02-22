package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.core.exception.LocalCIException;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class LocalCIResultServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localciresultservice";

    @Autowired
    private ProgrammingExerciseGradingService gradingService;

    @Autowired
    private ParticipationUtilService participationUtilService;

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
    void testProcessNewResultCompilationSuccessfulSetsBuildFailedFalse() {
        // Create a student participation with a submission
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, student1Login);
        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setCommitHash("abc123");
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = programmingSubmissionRepository.save(submission);

        // Create a build result with compilation successful
        var buildResult = new BuildResult(null, "abc123", "test123", Collections.emptyList(), true);

        // Process the result
        var result = gradingService.processNewProgrammingExerciseResult(participation, buildResult);

        // Verify the submission's buildFailed flag is false
        var updatedSubmission = programmingSubmissionRepository.findById(submission.getId()).orElseThrow();
        assertThat(updatedSubmission.isBuildFailed()).isFalse();
        assertThat(result).isNotNull();
    }

    @Test
    void testProcessNewResultCompilationFailedSetsBuildFailedTrue() {
        // Create a student participation with a submission
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, student2Login);
        var submission = new ProgrammingSubmission();
        submission.setParticipation(participation);
        submission.setCommitHash("def456");
        submission.setType(SubmissionType.MANUAL);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = programmingSubmissionRepository.save(submission);

        // Create build logs
        var buildLogs = List.of(new BuildLogDTO(ZonedDateTime.now(), "error: compilation failed"));

        // Create a build result with compilation failed
        var buildResult = new BuildResult(null, "def456", "test456", buildLogs, false);

        // Process the result
        var result = gradingService.processNewProgrammingExerciseResult(participation, buildResult);

        // Verify the submission's buildFailed flag is true and build logs are persisted
        var updatedSubmission = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(submission.getId()).orElseThrow();
        assertThat(updatedSubmission.isBuildFailed()).isTrue();
        assertThat(updatedSubmission.getBuildLogEntries()).isNotEmpty();
        assertThat(result).isNotNull();
    }
}
