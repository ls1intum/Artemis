package de.tum.in.www1.artemis.localvcci;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

public class LocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    LocalVCLocalCITestService localVCLocalCITestService;

    protected File tempRemoteRepoFolder;

    protected File tempLocalRepoFolder;

    protected Repository repository;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @AfterEach
    public void tearDown() throws IOException {
        repository.close();
        Files.delete(tempRemoteRepoFolder.toPath());
        Files.delete(tempLocalRepoFolder.toPath());
    }

    @Test
    public void testBuildAndTestSubmission() throws IOException, GitAPIException {
        Course course = database.addEmptyCourse();
        // Create a Java programming exercise.
        ProgrammingExercise programmingExercise = database.addProgrammingExerciseToCourse(course, false);
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);

        // Create a temporary JGit repository
        File repoDir = tempFolder.newFolder("remote-repo.git");
        Path repoPath = repoDir.toPath();

        // Initialize the repository
        Git git = Git.init().setDirectory(repoDir).setBare(true).call();

        // Copy exercise template files to the repository
        copyTestFilesToRepo(repoPath);

        // Commit and push the test files
        commitAndPushFiles(repoPath);

        // Prepare a Repository that has just received a push.

        // Create a test repository containing Java tests.

        // Call processNewPush.

        // Check that the new result was successfully created.

        git.close();
    }

    private void copyTestFilesToRepo(Path repoPath) throws IOException {
        Path testFilesPath = Paths.get("src", "test", "resources", "test-data", "java-templates", "exercise");

        Files.walk(testFilesPath).forEach(source -> {
            Path destination = repoPath.resolve(testFilesPath.relativize(source));
            try {
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (IOException e) {
                throw new RuntimeException("Error copying test files to the repository", e);
            }
        });
    }

    private void commitAndPushFiles(Path repoPath) {
        try (FileRepository repository = new FileRepository(repoPath.toFile())) {
            Git git = new Git(repository);

            // Add all files to the index
            git.add().addFilepattern(".").call();

            // Commit
            git.commit().setMessage("Add test files").call();
        }
        catch (IOException | GitAPIException e) {
            throw new RuntimeException("Error committing and pushing test files", e);
        }
    }
}
