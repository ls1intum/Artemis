package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.programming.ProgrammingExerciseResourceEndpoints;

class ProgrammingExerciseGitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progexgitintegration";

    private static final String COMBINE_COMMITS_ENDPOINT = ProgrammingExerciseResourceEndpoints.ROOT + ProgrammingExerciseResourceEndpoints.COMBINE_COMMITS;

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private File localRepoFile;

    private Git localGit;

    private File originRepoFile;

    private Git originGit;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        localRepoFile = Files.createTempDirectory("repo").toFile();
        localGit = LocalRepository.initialize(localRepoFile, defaultBranch);

        // create commits
        // the following 2 lines prepare the generation of the structural test oracle
        var testJsonFilePath = Path.of(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test.json");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath);
        GitService.commit(localGit).setMessage("add test.json").setAuthor("test", "test@test.com").call();
        var testJsonFilePath2 = Path.of(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test2.json");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath2);
        GitService.commit(localGit).setMessage("add test2.json").setAuthor("test", "test@test.com").call();
        var testJsonFilePath3 = Path.of(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test3.json");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath3);
        GitService.commit(localGit).setMessage("add test3.json").setAuthor("test", "test@test.com").call();

        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(VcsRepositoryUri.class), anyString(), anyBoolean());
        doNothing().when(gitService).fetchAll(any());
        var objectId = localGit.reflog().call().iterator().next().getNewId();
        doReturn(objectId).when(gitService).getLastCommitHash(any());
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (localGit != null) {
            localGit.close();
        }
        if (localRepoFile != null && localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepoFile);
        }
        if (originGit != null) {
            originGit.close();
        }
        if (originRepoFile != null && originRepoFile.exists()) {
            FileUtils.deleteDirectory(originRepoFile);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void testRepositoryMethods() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingExerciseRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingExerciseRepository.findByIdWithAuxiliaryRepositoriesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationLatestResultFeedbackTestCasesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits() throws Exception {
        originRepoFile = Files.createTempDirectory("repoOrigin").toFile();
        originGit = LocalRepository.initialize(originRepoFile, defaultBranch);
        StoredConfig config = localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", originRepoFile.getAbsolutePath());
        config.save();
        localGit.push().call();
        assertThat(getAllCommits(localGit)).hasSize(3);
        assertThat(getAllCommits(originGit)).hasSize(3);

        final var path = COMBINE_COMMITS_ENDPOINT.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(path, Void.class, HttpStatus.OK);
        assertThat(getAllCommits(localGit)).hasSize(1);
        assertThat(getAllCommits(originGit)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits_invalidId_notFound() throws Exception {
        programmingExercise.setId(798724305923532L);
        final var path = COMBINE_COMMITS_ENDPOINT.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(path, Void.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructoralt1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", TEST_PREFIX + "instructoralt");
        final var path = COMBINE_COMMITS_ENDPOINT.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(path, Void.class, HttpStatus.FORBIDDEN);
    }

    private List<RevCommit> getAllCommits(Git gitRepo) throws Exception {
        return StreamSupport.stream(gitRepo.log().call().spliterator(), false).toList();
    }
}
