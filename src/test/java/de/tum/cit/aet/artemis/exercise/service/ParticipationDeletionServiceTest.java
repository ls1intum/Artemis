package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class ParticipationDeletionServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "participationdeletionservice";

    /** Fixed so the stored log line does not depend on when the test runs; nothing here reads the value back. */
    private static final ZonedDateTime BUILD_LOG_TIME = ZonedDateTime.parse("2026-01-15T10:00:00Z");

    @Autowired
    private ParticipationDeletionService participationDeletionService;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise programmingExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteParticipation_removesBuildLogEntries() {
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        // Setup: create a participation and a submission with the build logs of a failed build for template, solution and student
        var templateParticipation = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise).getTemplateParticipation();
        var templateSubmission = programmingExerciseUtilService.createProgrammingSubmission(templateParticipation, true);
        participationUtilService.addResultToSubmission(templateSubmission, AssessmentType.AUTOMATIC, programmingExercise.getId());
        buildLogEntryService.saveBuildLogs(List.of(new BuildLogEntry(BUILD_LOG_TIME, "Some sample build log")), templateSubmission, templateSubmission.getLatestResult());

        var solutionParticipation = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise).getSolutionParticipation();
        var solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(solutionParticipation, true);
        participationUtilService.addResultToSubmission(solutionSubmission, AssessmentType.AUTOMATIC, programmingExercise.getId());
        buildLogEntryService.saveBuildLogs(List.of(new BuildLogEntry(BUILD_LOG_TIME, "Some sample build log")), solutionSubmission, solutionSubmission.getLatestResult());

        var studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var studentSubmission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, true);
        participationUtilService.addResultToSubmission(studentSubmission, AssessmentType.AUTOMATIC, programmingExercise.getId());
        buildLogEntryService.saveBuildLogs(List.of(new BuildLogEntry(BUILD_LOG_TIME, "Some sample build log")), studentSubmission, studentSubmission.getLatestResult());

        // Delete and assert removal. The logs live on disk now, so the service is what says whether they are still there.
        assertThat(buildLogEntryService.getLatestBuildLogs(templateSubmission)).isNotEmpty();
        participationDeletionService.deleteResultsAndSubmissionsOfParticipation(templateParticipation.getId(), true);
        assertThat(buildLogEntryService.getLatestBuildLogs(templateSubmission)).isEmpty();

        assertThat(buildLogEntryService.getLatestBuildLogs(solutionSubmission)).isNotEmpty();
        participationDeletionService.deleteResultsAndSubmissionsOfParticipation(solutionParticipation.getId(), true);
        assertThat(buildLogEntryService.getLatestBuildLogs(solutionSubmission)).isEmpty();

        assertThat(buildLogEntryService.getLatestBuildLogs(studentSubmission)).isNotEmpty();
        participationDeletionService.deleteResultsAndSubmissionsOfParticipation(studentParticipation.getId(), true);
        assertThat(buildLogEntryService.getLatestBuildLogs(studentSubmission)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteParticipation_logsFullExceptionAndProceedsWhenRepositoryDeletionFails() throws Exception {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        mockDeleteBuildPlan(programmingExercise.getProjectKey(), participation.getBuildPlanId(), false);

        // Replace the bare repository directory with a plain file so that the repository deletion fails
        Path bareRepoPath = new LocalVCRepositoryUri(participation.getRepositoryUri()).getLocalRepositoryPath(localVCBasePath);
        FileUtils.deleteDirectory(bareRepoPath.toFile());
        Files.createFile(bareRepoPath);

        Logger logger = (Logger) LoggerFactory.getLogger(ParticipationDeletionService.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        try {
            participationDeletionService.delete(participation.getId(), true);

            // The deletion is best-effort: the participation must be deleted even though the repository deletion failed
            assertThat(studentParticipationRepository.findById(participation.getId())).isEmpty();

            // A failed repository deletion strands a broken repository on disk, so the full exception must be logged for diagnosis
            var repositoryDeletionErrors = listAppender.list.stream()
                    .filter(event -> event.getLevel() == Level.ERROR && event.getFormattedMessage().startsWith("Could not delete repository")).toList();
            assertThat(repositoryDeletionErrors).hasSize(1);
            assertThat(repositoryDeletionErrors.getFirst().getThrowableProxy()).as("the log entry should contain the exception including its stack trace").isNotNull();
        }
        finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
            Files.deleteIfExists(bareRepoPath);
        }
    }
}
