package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.JobWithDetails;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.BuildLogEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.util.TestConstants;

class RepositoryProgrammingExerciseParticipationJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private BuildLogEntryRepository buildLogEntryRepository;

    @BeforeEach
    void setup() throws Exception {
        database.addUsers(1, 1, 0, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetLatestBuildLogsFails() throws Exception {
        var course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercise = (ProgrammingExercise) course.getExercises().stream().filter(ex -> ex instanceof ProgrammingExercise).findFirst().get();
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).get();
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

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

        database.addProgrammingSubmission(programmingExercise, submission, "student1");
        buildLogEntryRepository.deleteAll();

        var participation = studentParticipationRepository.findAll().get(0);

        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) submission.getParticipation();

        var jobWithDetails = mock(JobWithDetails.class);
        jenkinsRequestMockProvider.mockGetJob(programmingExercise.getProjectKey(), programmingExerciseParticipation.getBuildPlanId(), jobWithDetails, false);
        var lastBuild = mock(Build.class);
        doReturn(lastBuild).when(jobWithDetails).getLastBuild();
        doThrow(IOException.class).when(lastBuild).details();

        var url = "/api/repository/" + participation.getId() + "/buildlogs";
        request.getList(url, HttpStatus.INTERNAL_SERVER_ERROR, BuildLogEntry.class);
    }
}
