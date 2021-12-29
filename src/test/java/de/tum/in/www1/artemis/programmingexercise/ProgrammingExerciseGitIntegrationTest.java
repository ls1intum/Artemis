package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResource;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ProgrammingExerciseGitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private File localRepoFile;

    private Git localGit;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(3, 2, 0, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");

        localRepoFile = Files.createTempDirectory("repo").toFile();
        localGit = Git.init().setDirectory(localRepoFile).call();

        // create commits
        // the following 2 lines prepare the generation of the structural test oracle
        var testJsonFilePath = Paths.get(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test.json");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath);
        localGit.commit().setMessage("add test.json").setAuthor("test", "test@test.com").call();
        var testJsonFilePath2 = Paths.get(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test2.json");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath2);
        localGit.commit().setMessage("add test2.json").setAuthor("test", "test@test.com").call();
        var testJsonFilePath3 = Paths.get(localRepoFile.getPath(), "test", programmingExercise.getPackageFolderName(), "test3.json");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath3);
        localGit.commit().setMessage("add test3.json").setAuthor("test", "test@test.com").call();

        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoFile.toPath(), null);
        doReturn(repository).when(gitService).getOrCheckoutRepository(any(VcsRepositoryUrl.class), anyString(), anyBoolean());
        doNothing().when(gitService).fetchAll(any());
        var objectId = localGit.reflog().call().iterator().next().getNewId();
        doReturn(objectId).when(gitService).getLastCommitHash(any());
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).pullIgnoreConflicts(any());
        doNothing().when(gitService).commitAndPush(any(), anyString(), any());
    }

    @AfterEach
    void tearDown() throws IOException {
        database.resetDatabase();
        if (localRepoFile != null && localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepoFile);
        }
        if (localGit != null) {
            localGit.close();
        }
    }

    @Test
    @WithMockUser(username = "student3")
    public void testRepositoryMethods() {
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithAuxiliaryRepositoriesElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithSubmissionPolicyElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationLatestResultElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationSubmissionsAndResultsElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class,
                () -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationWithResultsElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits() throws Exception {
        File originRepoFile = Files.createTempDirectory("repoOrigin").toFile();
        Git remoteGit = Git.init().setDirectory(originRepoFile).call();
        StoredConfig config = localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", originRepoFile.getAbsolutePath());
        config.save();
        localGit.push().call();
        assertThat(getAllCommits(localGit).size()).isEqualTo(3);
        assertThat(getAllCommits(remoteGit).size()).isEqualTo(3);

        final var path = ProgrammingExerciseResource.Endpoints.ROOT
                + ProgrammingExerciseResource.Endpoints.COMBINE_COMMITS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(path, Void.class, HttpStatus.OK);
        assertThat(getAllCommits(localGit).size()).isEqualTo(1);
        assertThat(getAllCommits(remoteGit).size()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits_invalidId_notFound() throws Exception {
        programmingExercise.setId(20L);
        final var path = ProgrammingExerciseResource.Endpoints.ROOT
                + ProgrammingExerciseResource.Endpoints.COMBINE_COMMITS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(path, Void.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testCombineTemplateRepositoryCommits_instructorNotInCourse_forbidden() throws Exception {
        database.addInstructor("other-instructors", "instructoralt");
        final var path = ProgrammingExerciseResource.Endpoints.ROOT
                + ProgrammingExerciseResource.Endpoints.COMBINE_COMMITS.replace("{exerciseId}", String.valueOf(programmingExercise.getId()));
        request.put(path, Void.class, HttpStatus.FORBIDDEN);
    }

    public List<RevCommit> getAllCommits(Git gitRepo) throws Exception {
        return StreamSupport.stream(gitRepo.log().call().spliterator(), false).collect(Collectors.toList());
    }
}
