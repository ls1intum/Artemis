package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.util.ZipTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Tests the in-memory export of a student repository, which is what the download of a single participation is served
 * from.
 * <p>
 * The archive is streamed out of the bare repository, so this covers what an instructor receives and, more importantly,
 * what happens when there is nothing to stream: a participation without a repository and a repository without a single
 * commit both have to be reported rather than yielding an empty archive that looks like an empty submission.
 */
class GitRepositoryExportServiceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "gitrepoexport";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository studentParticipationTestRepository;

    @Autowired
    private GitRepositoryExportService gitRepositoryExportService;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

    @Autowired
    private LocalVCService localVCService;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @AfterEach
    void tearDown() {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportStudentRepositoryInMemory_streamsTheSubmittedWorkingTreeWithoutGitMetadata() throws Exception {
        var participation = seedParticipation(TEST_PREFIX + "student1", Map.of("src/Main.java", "public class Main {}"));
        List<String> exportErrors = new ArrayList<>();

        var exported = gitRepositoryExportService.exportStudentRepositoryInMemory(programmingExercise, participation, exportErrors);

        assertThat(exportErrors).as("a repository with a commit exports without errors").isEmpty();
        assertThat(exported).as("the download is served the archive").isNotNull();
        assertThat(exported.getFilename()).as("the archive is named after the participant")
                .isEqualTo(gitRepositoryExportService.getStudentRepositoryName(programmingExercise, participation, false) + ".zip");
        byte[] zipContent = readAllBytes(exported);
        assertThat(ZipTestUtil.readEntryAsString(zipContent, "src/Main.java")).as("the submitted file is in the archive").isEqualTo("public class Main {}");
        // A snapshot of a student submission must not carry the repository history, which would disclose more than the submission itself.
        ZipTestUtil.verifyZipDoesNotContainGitDirectory(zipContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportStudentRepositoryInMemory_withoutARepositoryUri_reportsTheParticipation() {
        var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        participation.setRepositoryUri((String) null);
        studentParticipationTestRepository.save(participation);
        List<String> exportErrors = new ArrayList<>();

        var exported = gitRepositoryExportService.exportStudentRepositoryInMemory(programmingExercise, participation, exportErrors);

        assertThat(exported).as("a participation without a repository has nothing to export").isNull();
        assertThat(exportErrors).as("the caller is told which participation could not be exported").hasSize(1);
        assertThat(exportErrors.getFirst()).contains(String.valueOf(participation.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportStudentRepositoryInMemory_forARepositoryWithoutACommit_reportsTheExerciseAndTheParticipation() throws Exception {
        // An empty archive is indistinguishable from an empty submission, so a repository whose setup never produced a commit has to be reported instead. The repository is
        // created but deliberately not seeded, so that this is the no-commit path rather than the missing-repository one.
        var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        String projectKey = programmingExercise.getProjectKey();
        String repositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey, "unseeded");
        // Created the way LocalVC creates it, without the initial commit the fixtures push: this is the state a repository is left in when its setup failed halfway.
        localVCService.createRepository(projectKey, repositorySlug);
        RepositoryExportTestUtil.trackBareRepository(localVCRepositoryTestService.repositoryUri(projectKey, repositorySlug).getLocalRepositoryPath(localVCBasePath));
        assertThat(localVCRepositoryTestService.listFilePaths(localVCRepositoryTestService.repositoryUri(projectKey, repositorySlug)))
                .as("the repository exists but carries no commit").isEmpty();
        participation.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repositorySlug));
        studentParticipationTestRepository.save(participation);
        List<String> exportErrors = new ArrayList<>();

        var exported = gitRepositoryExportService.exportStudentRepositoryInMemory(programmingExercise, participation, exportErrors);

        assertThat(exported).as("a repository without a commit has nothing to export").isNull();
        assertThat(exportErrors).as("the failure is reported rather than swallowed").hasSize(1);
        assertThat(exportErrors.getFirst()).as("the failure names the exercise and the participation").contains(programmingExercise.getTitle())
                .contains(String.valueOf(programmingExercise.getId())).contains(String.valueOf(participation.getId()));
    }

    private ProgrammingExerciseStudentParticipation seedParticipation(String login, Map<String, String> files) throws Exception {
        var participation = (ProgrammingExerciseStudentParticipation) participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, login);
        var repository = RepositoryExportTestUtil.seedStudentRepositoryForParticipation(localVCLocalCITestService, participation);
        RepositoryExportTestUtil.writeFilesAndPush(repository, files, "initial commit");
        return studentParticipationTestRepository.save(participation);
    }

    private static byte[] readAllBytes(InputStreamResource resource) throws Exception {
        try (var inputStream = resource.getInputStream(); var outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        }
    }
}
