package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

public class RepositoryProgrammingExerciseParticipationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final String studentRepoBaseUrl = "/api/repository/";

    @Autowired
    private DatabaseUtilService database;

    @Autowired
    private RequestUtilService request;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    private ProgrammingExercise programmingExercise;

    private String currentLocalFileName = "currentFileName";

    private String currentLocalFileContent = "testContent";

    private String currentLocalFolderName = "currentFolderName";

    private String newLocalFileName = "newFileName";

    private String newLocalFolderName = "newFolderName";

    LocalRepository studentRepository = new LocalRepository();

    List<BuildLogEntry> logs = new ArrayList<>();

    BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Checkout to revision e65aa77cc0380aeb9567ccceb78aca416d86085b has failed.");

    BuildLogEntry largeBuildLogEntry = new BuildLogEntry(ZonedDateTime.now(),
            "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin:3.1.1:checkstyle (default-cli)"
                    + "on project testPluginSCA-Tests: An error has occurred in Checkstyle report generation. Failed during checkstyle"
                    + "configuration: Exception was thrown while processing C:\\Users\\Stefan\\bamboo-home\\xml-data\\build-dir\\STCTES"
                    + "TPLUGINSCA-SOLUTION-JOB1\\assignment\\src\\www\\testPluginSCA\\BubbleSort.java: MismatchedTokenException occurred"
                    + "while parsing file C:\\Users\\Stefan\\bamboo-home\\xml-data\\build-dir\\STCTESTPLUGINSCA-SOLUTION-JOB1\\assignment\\"
                    + "src\\www\\testPluginSCA\\BubbleSort.java. expecting EOF, found '}' -> [Help 1]");

    StudentParticipation participation;

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(1, 0, 0);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);

        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");

        // add file to the repository folder
        Path filePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName);
        var file = Files.createFile(filePath).toFile();

        // write content to the created file
        FileUtils.write(file, currentLocalFileContent);

        // add folder to the repository folder
        filePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName);
        var folder = Files.createDirectory(filePath).toFile();

        var localRepoUrl = new GitUtilService.MockFileRepositoryUrl(studentRepository.localRepoFile);
        database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1", localRepoUrl.getURL());
        participation = studentParticipationRepository.findAll().get(0);
        programmingExercise.setTestRepositoryUrl(localRepoUrl.toString());
        doReturn(gitService.getRepositoryByLocalPath(studentRepository.localRepoFile.toPath())).when(gitService)
                .getOrCheckoutRepository(((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl(), true);

        doReturn(gitService.getRepositoryByLocalPath(studentRepository.localRepoFile.toPath())).when(gitService)
                .getOrCheckoutRepository(((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl(), false);

        logs.add(buildLogEntry);
        logs.add(largeBuildLogEntry);
    }

    @AfterEach
    public void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        studentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetFiles() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + key))).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "newFile");
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/newFile"))).isFalse();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.isRegularFile(Paths.get(studentRepository.localRepoFile + "/newFile"))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCreateFolder() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/newFolder"))).isFalse();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Files.isDirectory(Paths.get(studentRepository.localRepoFile + "/newFolder"))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testRenameFile() throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFileName))).isFalse();
        FileMove fileMove = new FileMove();
        fileMove.setCurrentFilePath(currentLocalFileName);
        fileMove.setNewFilename(newLocalFileName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isFalse();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFileName))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testRenameFolder() throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFolderName))).isFalse();
        FileMove fileMove = new FileMove();
        fileMove.setCurrentFilePath(currentLocalFolderName);
        fileMove.setNewFilename(newLocalFolderName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName))).isFalse();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + newLocalFolderName))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testDeleteFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        request.delete(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCommitChanges() throws Exception {
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");
        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits.size() == 1).isTrue();
        assertThat(database.getUserByLogin("student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    private List<FileSubmission> getFileSubmissions() {
        List<FileSubmission> fileSubmissions = new ArrayList();
        FileSubmission fileSubmission = new FileSubmission();
        fileSubmission.setFileName(currentLocalFileName);
        fileSubmission.setFileContent("updatedFileContent");
        fileSubmissions.add(fileSubmission);
        return fileSubmissions;
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSaveFiles() throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions(), HttpStatus.OK);

        Path filePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName);
        assertThat(FileUtils.readFileToString(filePath.toFile())).isEqualTo("updatedFileContent");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSaveFilesAndCommit() throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions(), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");

        Path filePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName);
        assertThat(FileUtils.readFileToString(filePath.toFile())).isEqualTo("updatedFileContent");

        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits.size() == 1).isTrue();
        assertThat(database.getUserByLogin("student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testPullChanges() throws Exception {
        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var remote = gitService.getRepositoryByLocalPath(studentRepository.originRepoFile.toPath());

        // Create file in the remote repository
        Path filePath = Paths.get(studentRepository.originRepoFile + "/" + fileName);
        Files.createFile(filePath).toFile();

        // Check if the file exists in the remote repository and that it doesn't yet exists in the local repository
        assertThat(Files.exists(Paths.get(studentRepository.originRepoFile + "/" + fileName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isFalse();

        // Stage all changes and make a second commit in the remote repository
        gitService.stageAllChanges(remote);
        studentRepository.originGit.commit().setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

        // Checks if the current commit is not equal on the local and the remote repository
        assertThat(studentRepository.getAllLocalCommits().get(0)).isNotEqualTo(studentRepository.getAllOriginCommits().get(0));

        // Execute the Rest call
        request.get(studentRepoBaseUrl + participation.getId() + "/pull", HttpStatus.OK, Void.class);

        // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
        assertThat(studentRepository.getAllLocalCommits().get(0)).isEqualTo(studentRepository.getAllOriginCommits().get(0));
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isTrue();

    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testResetToLastCommit() throws Exception {
        String fileName = "testFile";
        var localRepo = gitService.getRepositoryByLocalPath(studentRepository.localRepoFile.toPath());
        var remoteRepo = gitService.getRepositoryByLocalPath(studentRepository.originRepoFile.toPath());

        // Check status of git before the commit
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);

        // Check status of git after the commit
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");

        // Create file in the local repository and commit it
        Path localFilePath = Paths.get(studentRepository.localRepoFile + "/" + fileName);
        var localFile = Files.createFile(localFilePath).toFile();
        // write content to the created file
        FileUtils.write(localFile, "local");
        gitService.stageAllChanges(localRepo);
        studentRepository.localGit.commit().setMessage("local").call();

        // Create file in the remote repository and commit it
        Path remoteFilePath = Paths.get(studentRepository.originRepoFile + "/" + fileName);
        var remoteFile = Files.createFile(remoteFilePath).toFile();
        // write content to the created file
        FileUtils.write(remoteFile, "remote");
        gitService.stageAllChanges(remoteRepo);
        studentRepository.originGit.commit().setMessage("remote").call();

        // Merge the two and a conflict will occur
        studentRepository.localGit.fetch().setRemote("origin").call();
        List<Ref> refs = studentRepository.localGit.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        var result = studentRepository.localGit.merge().include(refs.get(0).getObjectId()).setStrategy(MergeStrategy.RESOLVE).call();
        var status = studentRepository.localGit.status().call();
        assertThat(status.getConflicting().size() > 0).isTrue();
        assertThat(MergeResult.MergeStatus.CONFLICTING).isEqualTo(result.getMergeStatus());

        // Execute the reset Rest call
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.OK, null);

        // Check the git status after the reset
        status = studentRepository.localGit.status().call();
        assertThat(status.getConflicting().size() == 0).isTrue();
        assertThat(studentRepository.getAllLocalCommits().get(0)).isEqualTo(studentRepository.getAllOriginCommits().get(0));
        var receivedStatusAfterReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterReset.repositoryStatus.toString()).isEqualTo("CLEAN");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetStatus() throws Exception {
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);

        // The current status is "uncommited changes", since we added files and folders, but we didn't commit yet
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        // Perform a commit to check if the status changes
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);

        // Check if the status of git is "clean" after the commit
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testBuildLogsNoSubmission() throws Exception {
        var receivedLogs = request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSize(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testBuildLogsWithSubmissionBuildSuccessful() throws Exception {
        database.createProgrammingSubmission(participation, false);
        request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testBuildLogsWithManualResult() throws Exception {
        var submission = database.createProgrammingSubmission(participation, true);
        database.addResultToSubmission(submission, AssessmentType.MANUAL);
        var receivedLogs = request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSize(0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testBuildLogs() throws Exception {
        var submission = database.createProgrammingSubmission(participation, true);

        doReturn(logs).when(continuousIntegrationService).getLatestBuildLogs(submission);

        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC);
        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSize(1);
        assertThat(receivedLogs.get(0).getTime()).isEqualTo(logs.get(0).getTime());
        // due to timezone assertThat isEqualTo issues, we compare those directly first and ignore them afterwards
        assertThat(receivedLogs).usingElementComparatorIgnoringFields("time", "id").isEqualTo(logs);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testBuildLogsFromDatabase() throws Exception {
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

        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSize(3);
        assertThat(receivedLogs).isEqualTo(buildLogEntries);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCommitChangesAllowedForAutomaticallyAssessedAfterDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        testCommitChanges();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCommitChangesNotAllowedForBuildAndTestAfterDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCommitChangesNotAllowedForManuallyAssessedAfterDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
    }
}
