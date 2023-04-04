package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.util.GitUtilService;

class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localvclocalciintegration";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private GitUtilService gitUtilService;

    private ProgrammingExercise programmingExercise;

    private Path templateRepositoryFolder;

    private Git templateGit;

    private Path testsRepositoryFolder;

    private Git testsGit;

    private Path remoteAssignmentRepositoryFolder;

    private Git remoteAssignmentGit;

    private String assignmentRepoName;

    private Path localAssignmentRepositoryFolder;

    private Git localAssignmentGit;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        String studentLogin = TEST_PREFIX + "student1";

        Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setTestRepositoryUrl(
                localVCSBaseUrl + "/git/" + programmingExercise.getProjectKey().toUpperCase() + "/" + programmingExercise.getProjectKey().toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).get();
        ProgrammingExerciseStudentParticipation participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        final String repoName = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        participation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", programmingExercise.getProjectKey(), repoName));
        participation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(participation);

        // Create template and tests repository
        final String templateRepoName = programmingExercise.getProjectKey().toLowerCase() + "-exercise";
        templateRepositoryFolder = createRepositoryFolderInTempDirectory(programmingExercise.getProjectKey(), templateRepoName);
        ClassPathResource templateResource = new ClassPathResource("test-data/java-templates/exercise");
        templateGit = createGitRepository(templateRepositoryFolder, templateResource.getFile());
        final String testsRepoName = programmingExercise.getProjectKey().toLowerCase() + "-tests";
        testsRepositoryFolder = createRepositoryFolderInTempDirectory(programmingExercise.getProjectKey(), testsRepoName);
        ClassPathResource testsResource = new ClassPathResource("test-data/java-templates/tests");
        testsGit = createGitRepository(testsRepositoryFolder, testsResource.getFile());

        // Create remote assignment repository
        assignmentRepoName = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        remoteAssignmentRepositoryFolder = createRepositoryFolderInTempDirectory(programmingExercise.getProjectKey(), assignmentRepoName);
        ClassPathResource assignmentResource = new ClassPathResource("test-data/java-templates/exercise");
        remoteAssignmentGit = createGitRepository(remoteAssignmentRepositoryFolder, assignmentResource.getFile());

        // Clone the remote assignment repository into a local folder.
        localAssignmentRepositoryFolder = Files.createTempDirectory("localAssignment");
        localAssignmentGit = Git.cloneRepository().setURI(remoteAssignmentRepositoryFolder.toString()).setDirectory(localAssignmentRepositoryFolder.toFile()).call();
    }

    private Path createRepositoryFolderInTempDirectory(String projectKey, String repositorySlug) {
        String tempDir = System.getProperty("java.io.tmpdir");

        Path projectFolder = Paths.get(tempDir, projectKey);

        // Create the project folder if it does not exist.
        try {
            if (!Files.exists(projectFolder)) {
                Files.createDirectories(projectFolder);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Create the repository folder.
        Path repositoryFolder = projectFolder.resolve(repositorySlug + ".git");
        try {
            Files.createDirectories(repositoryFolder);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return repositoryFolder;
    }

    private Git createGitRepository(Path repositoryFolder, File resourcesFolder) throws IOException, GitAPIException, URISyntaxException {

        // Initialize bare Git repository in the repository folder.
        Git remoteGit = Git.init().setDirectory(repositoryFolder.toFile()).setBare(true).call();
        modifyDefaultBranch(remoteGit);

        // Initialize a non-bare Git repository in a temporary folder.
        Path tempDirectory = Files.createTempDirectory("temp");
        Git localGit = Git.init().setDirectory(tempDirectory.toFile()).call();
        modifyDefaultBranch(localGit);

        // Copy the files from "test/resources/test-data/java-templates/..." to the temporary directory.
        FileUtils.copyDirectory(resourcesFolder, tempDirectory.toFile());
        // Add all files to the Git repository.
        localGit.add().addFilepattern(".").call();
        // Commit the files.
        localGit.commit().setMessage("Initial commit").call();

        // Set the remote to the bare repository.
        localGit.remoteAdd().setName("origin").setUri(new URIish(repositoryFolder.toString())).call();

        // Push the files to the bare repository.
        localGit.push().setRemote("origin").call();

        localGit.close();
        FileUtils.deleteDirectory(tempDirectory.toFile());

        return remoteGit;
    }

    private void modifyDefaultBranch(Git gitHandle) throws IOException {
        Repository repository = gitHandle.getRepository();
        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);
    }

    private String constructLocalVCUrl(String username, String projectKey, String repositorySlug) {
        return "http://" + username + ":" + USER_PASSWORD + "@localhost:" + port + "/git/" + projectKey.toUpperCase() + "/" + repositorySlug + ".git";
    }

    @AfterEach
    void removeRepositories() throws IOException {
        if (templateGit != null) {
            templateGit.close();
        }
        if (testsGit != null) {
            testsGit.close();
        }
        if (remoteAssignmentGit != null) {
            remoteAssignmentGit.close();
        }
        if (localAssignmentGit != null) {
            localAssignmentGit.close();
        }
        if (templateRepositoryFolder != null && Files.exists(templateRepositoryFolder)) {
            FileUtils.deleteDirectory(templateRepositoryFolder.toFile());
        }
        if (testsRepositoryFolder != null && Files.exists(testsRepositoryFolder)) {
            FileUtils.deleteDirectory(testsRepositoryFolder.toFile());
        }
        if (remoteAssignmentRepositoryFolder != null && Files.exists(remoteAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteAssignmentRepositoryFolder.toFile());
        }
        if (localAssignmentRepositoryFolder != null && Files.exists(localAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(localAssignmentRepositoryFolder.toFile());
        }
    }

    @Test
    public void testPushAndReceiveResult_allTestCasesFail() throws Exception {
        // Create a file and push the changes to the remote assignment repository, this time via the Artemis Git servlet.
        Path testJsonFilePath = Path.of(localAssignmentRepositoryFolder.toString(), "src", programmingExercise.getPackageFolderName(), "test.txt");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath);
        localAssignmentGit.add().addFilepattern(".").call();
        RevCommit commit = localAssignmentGit.commit().setMessage("Add test.txt").call();
        String commitHash = commit.getId().getName();
        // localVCLocalCITestConfig.setAssignmentRepoCommitHashSupplier(() -> commitHash);
        localAssignmentGit.push().setRemote(constructLocalVCUrl(TEST_PREFIX + "student1", programmingExercise.getProjectKey(), assignmentRepoName)).call();
    }

    @Test
    public void testPushAndReceiveResult_someTestCasesFail() {
        // Prepare programming exercise with template repository, tests repository, and assignment repository for a student.

        // Clone the assignment repository.

        // Make changes to the assignment repository.

        // Push the changes to the assignment repository.

        // Check that the new result was successfully created.
    }
}
