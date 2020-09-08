package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.*;

public class ParticipationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    DatabaseUtilService database;

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    LocalRepository localRepo = new LocalRepository();

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 0, 0);
        database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "student1")
    public void performEmptySetupCommit_withNullExercise() throws Exception {
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");
        // add file to the repository folder
        Path filePath = Paths.get(localRepo.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();
        // write content to the created file
        FileUtils.write(file, currentLocalFileContent);

        // add folder to the repository folder
        filePath = Paths.get(localRepo.localRepoFile + "/" + currentLocalFolderName);
        var folder = Files.createDirectory(filePath).toFile();
        var localRepoUrl = new GitUtilService.MockFileRepositoryUrl(localRepo.localRepoFile);

        // create a participation
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1",
                localRepoUrl.getURL());
        assertThat(programmingExercise).as("Exercise was correctly set").isEqualTo(participation.getProgrammingExercise());

        // mock return of git path
        doReturn(gitService.getRepositoryByLocalPath(localRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(participation.getRepositoryUrlAsUrl(), true);

        doReturn(gitService.getRepositoryByLocalPath(localRepo.localRepoFile.toPath())).when(gitService).getOrCheckoutRepository(participation.getRepositoryUrlAsUrl(), false);

        // test performEmptyCommit() with empty exercise
        participation.setProgrammingExercise(null);
        participationService.performEmptyCommit(participation);

        // git local repo should have been deleted
        Repository repo = gitService.getRepositoryByLocalPath(localRepo.localRepoFile.toPath());
        assertThat(repo).as("local repository has been deleted").isNull();

        Repository originRepo = gitService.getRepositoryByLocalPath(localRepo.originRepoFile.toPath());
        assertThat(originRepo).as("origin repository has not been deleted").isNotNull();
    }
}
