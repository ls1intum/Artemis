package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.util.TestConstants;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.service.jenkins.jobs.JenkinsJobService;

class RepositoryProgrammingExerciseParticipationJenkinsIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "repoprogexpartjenk";

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws IOException {
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestBuildLogsFails() throws Exception {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
        var programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        var submission = new ProgrammingSubmission();
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission.setSubmitted(true);
        submission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        submission.setType(SubmissionType.MANUAL);
        submission.setBuildFailed(true);

        List<BuildLogEntry> buildLogEntries = new ArrayList<>();
        buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), "LogEntry1", submission));
        buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), "LogEntry2", submission));
        buildLogEntries.add(new BuildLogEntry(ZonedDateTime.now(), "LogEntry3", submission));
        submission.setBuildLogEntries(buildLogEntries);

        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");

        var buildPlanId = programmingExerciseParticipation.getBuildPlanId();
        var jobWithDetails = new JenkinsJobService.JobWithDetails(buildPlanId, "description", false);
        jenkinsRequestMockProvider.mockGetJob(programmingExercise.getProjectKey(), buildPlanId, jobWithDetails, false);
        jenkinsRequestMockProvider.mockGetBuildStatus(programmingExercise.getProjectKey(), buildPlanId, true, true, false, true);

        var url = "/api/programming/participations/" + programmingExerciseParticipation.getId() + "/buildlogs";
        var buildLogs = request.getList(url, HttpStatus.OK, BuildLogEntry.class);
        assertThat(buildLogs).hasSize(3);
    }
}
