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
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.repository.BuildLogEntryRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class ParticipationDeletionServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "participationdeletionservice";

    @Autowired
    private ParticipationDeletionService participationDeletionService;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private BuildLogEntryRepository buildLogEntryRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

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
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
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
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        // Setup: Create participation, submission and build log entries for template, solution and student
        var templateParticipation = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise).getTemplateParticipation();
        var templateSubmission = programmingExerciseUtilService.createProgrammingSubmission(templateParticipation, true);
        BuildLogEntry buildLogEntryTemplate = new BuildLogEntry(ZonedDateTime.now(), "Some sample build log");
        var templateSavedBuildLogs = buildLogEntryService.saveBuildLogs(List.of(buildLogEntryTemplate), templateSubmission);
        templateSubmission.setBuildLogEntries(templateSavedBuildLogs);
        programmingSubmissionRepository.save(templateSubmission);

        var solutionParticipation = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise).getSolutionParticipation();
        var solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(solutionParticipation, true);
        BuildLogEntry buildLogEntrySolution = new BuildLogEntry(ZonedDateTime.now(), "Some sample build log");
        var solutionSavedBuildLogs = buildLogEntryService.saveBuildLogs(List.of(buildLogEntrySolution), solutionSubmission);
        solutionSubmission.setBuildLogEntries(solutionSavedBuildLogs);
        programmingSubmissionRepository.save(solutionSubmission);

        var studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var studentSubmission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, true);
        BuildLogEntry buildLogEntryStudent = new BuildLogEntry(ZonedDateTime.now(), "Some sample build log");
        var studentSavedBuildLogs = buildLogEntryService.saveBuildLogs(List.of(buildLogEntryStudent), studentSubmission);
        studentSubmission.setBuildLogEntries(studentSavedBuildLogs);
        programmingSubmissionRepository.save(studentSubmission);

        // Delete and assert removal
        assertThat(buildLogEntryRepository.findById(templateSavedBuildLogs.getFirst().getId())).isPresent();
        participationDeletionService.deleteResultsAndSubmissionsOfParticipation(templateParticipation.getId(), true);
        assertThat(buildLogEntryRepository.findById(templateSavedBuildLogs.getFirst().getId())).isEmpty();

        assertThat(buildLogEntryRepository.findById(solutionSavedBuildLogs.getFirst().getId())).isPresent();
        participationDeletionService.deleteResultsAndSubmissionsOfParticipation(solutionParticipation.getId(), true);
        assertThat(buildLogEntryRepository.findById(solutionSavedBuildLogs.getFirst().getId())).isEmpty();

        assertThat(buildLogEntryRepository.findById(studentSavedBuildLogs.getFirst().getId())).isPresent();
        participationDeletionService.deleteResultsAndSubmissionsOfParticipation(studentParticipation.getId(), true);
        assertThat(buildLogEntryRepository.findById(studentSavedBuildLogs.getFirst().getId())).isEmpty();
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
