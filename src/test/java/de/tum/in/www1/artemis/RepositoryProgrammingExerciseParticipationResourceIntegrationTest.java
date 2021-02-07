package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
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
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.util.*;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

public class RepositoryProgrammingExerciseParticipationResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final String studentRepoBaseUrl = "/api/repository/";

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    @Autowired
    ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    @Autowired
    ProgrammingExerciseService programmingExerciseService;

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    private final String newLocalFileName = "newFileName";

    private final String newLocalFolderName = "newFolderName";

    LocalRepository studentRepository = new LocalRepository();

    LocalRepository templateRepository = new LocalRepository();

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

    ListAppender<ILoggingEvent> listAppender;

    Logger logger;

    Path studentFilePath;

    File studentFile;

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(1, 1, 1);
        database.addCourseWithOneProgrammingExerciseAndTestCases();

        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);

        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");

        // add file to the repository folder
        studentFilePath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName);
        studentFile = Files.createFile(studentFilePath).toFile();

        // write content to the created file
        FileUtils.write(studentFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the repository folder
        Path folderPath = Paths.get(studentRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(folderPath).toFile();

        var localRepoUrl = new GitUtilService.MockFileRepositoryUrl(studentRepository.localRepoFile);
        database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1", localRepoUrl.getURL());
        participation = studentParticipationRepository.findAll().get(0);
        programmingExercise.setTestRepositoryUrl(localRepoUrl.toString());

        // Create template repo
        templateRepository = new LocalRepository();
        templateRepository.configureRepos("templateLocalRepo", "templateOriginRepo");

        // add file to the template repo folder
        var templateFilePath = Paths.get(templateRepository.localRepoFile + "/" + currentLocalFileName);
        var templateFile = Files.createFile(templateFilePath).toFile();

        // write content to the created file
        FileUtils.write(templateFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the template repo folder
        Path templateFolderPath = Paths.get(templateRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(templateFolderPath).toFile();

        programmingExercise = database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl(), true);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(((ProgrammingExerciseParticipation) participation).getVcsRepositoryUrl(), true);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(((ProgrammingExerciseParticipation) participation).getVcsRepositoryUrl(), false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository((ProgrammingExerciseParticipation) participation);

        logs.add(buildLogEntry);
        logs.add(largeBuildLogEntry);

        // Following setup is to check log messages see: https://stackoverflow.com/a/51812144
        // Get Logback Logger
        logger = (Logger) LoggerFactory.getLogger(ProgrammingExerciseParticipationService.class);

        // Create and start a ListAppender
        listAppender = new ListAppender<>();
        listAppender.start();

        // Add the appender to the logger, the addAppender is outdated now
        logger.addAppender(listAppender);
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
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetFilesWithContent() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content", HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + key))).isTrue();
        }
        assertThat(files.get(currentLocalFileName).equals(currentLocalFileContent));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetFilesWithInfoAboutChange_noChange() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + key))).isTrue();
            assertThat(files.get(key)).isFalse();
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetFilesWithInfoAboutChange_withChange() throws Exception {
        FileUtils.write(studentFile, "newContent123", Charset.defaultCharset());

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + key))).isTrue();
            assertThat(files.get(key)).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetFilesWithInfoAboutChange_withNewFile() throws Exception {
        FileUtils.write(studentFile, "newContent123", Charset.defaultCharset());

        Path newPath = Paths.get(studentRepository.localRepoFile + "/newFile");
        var file2 = Files.createFile(newPath).toFile();
        // write content to the created file
        FileUtils.write(file2, currentLocalFileContent + "test1", Charset.defaultCharset());

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + key))).isTrue();
            assertThat(files.get(key)).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetFiles_solutionParticipation() throws Exception {
        // Create template repo
        var solutionRepository = new LocalRepository();
        solutionRepository.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        // add file to the template repo folder
        var solutionFilePath = Paths.get(solutionRepository.localRepoFile + "/" + currentLocalFileName);
        var solutionFile = Files.createFile(solutionFilePath).toFile();

        // write content to the created file
        FileUtils.write(solutionFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the template repo folder
        Path solutionFolderPath = Paths.get(solutionRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(solutionFolderPath).toFile();

        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(programmingExercise.getSolutionParticipation().getVcsRepositoryUrl(), true);

        var files = request.getMap(studentRepoBaseUrl + programmingExercise.getSolutionParticipation().getId() + "/files", HttpStatus.OK, String.class, FileType.class);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Paths.get(solutionRepository.localRepoFile + "/" + key))).isTrue();
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

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSaveFiles() throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), HttpStatus.OK);
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("updatedFileContent");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSaveFilesAndCommit() throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");

        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions("updatedFileContent"), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus.toString()).isEqualTo("CLEAN");

        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("updatedFileContent");

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
        var remoteRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.originRepoFile.toPath(), null);

        // Create file in the remote repository
        Path filePath = Paths.get(studentRepository.originRepoFile + "/" + fileName);
        Files.createFile(filePath).toFile();

        // Check if the file exists in the remote repository and that it doesn't yet exists in the local repository
        assertThat(Files.exists(Paths.get(studentRepository.originRepoFile + "/" + fileName))).isTrue();
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + fileName))).isFalse();

        // Stage all changes and make a second commit in the remote repository
        gitService.stageAllChanges(remoteRepository);
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
        var localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null);
        var remoteRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.originRepoFile.toPath(), null);

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
        FileUtils.write(localFile, "local", Charset.defaultCharset());
        gitService.stageAllChanges(localRepo);
        studentRepository.localGit.commit().setMessage("local").call();

        // Create file in the remote repository and commit it
        Path remoteFilePath = Paths.get(studentRepository.originRepoFile + "/" + fileName);
        var remoteFile = Files.createFile(remoteFilePath).toFile();
        // write content to the created file
        FileUtils.write(remoteFile, "remote", Charset.defaultCharset());
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
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC);
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
        assertThat(receivedLogs).hasSize(2);
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

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStashChanges() throws Exception {
        // Make initial commit and save files afterwards
        initialCommitAndSaveFiles(HttpStatus.OK);
        Repository localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null);

        // Stash changes
        gitService.stashChanges(localRepo);
        // Local repo has no unsubmitted changes
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("initial commit");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        // Try to stash changes
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage())
                .isEqualTo("Cannot stash student repository for participation " + participation.getId() + " because the repository was not copied yet!");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_dueDatePassed() throws Exception {
        // Make initial commit and save files afterwards
        initialCommitAndSaveFiles(HttpStatus.OK);
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));

        // Stash changes using service
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("initial commit");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_throwError() {
        // Try to stash changes, but it will throw error as the HEAD is not initialized in the remote repo (this is done with the initial commit)
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check for canAccessParticipation(participation)
        var response = programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation);
        assertThat(response).isTrue();

        var responseSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getSolutionParticipation());
        assertThat(responseSolution).isTrue();

        var responseTemplate = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getTemplateParticipation());
        assertThat(responseTemplate).isTrue();

        var responseOther = programmingExerciseParticipationService.canAccessParticipation(null);
        assertThat(responseOther).isFalse();

        // Check for canAccessParticipation(participation, principal)
        final var username = "instructor1";
        final Principal principal = () -> username;

        response = programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation, principal);
        assertThat(response).isFalse();

        responseSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getSolutionParticipation(), principal);
        assertThat(responseSolution).isTrue();

        responseSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getTemplateParticipation(), principal);
        assertThat(responseSolution).isTrue();

        responseSolution = programmingExerciseParticipationService.canAccessParticipation(null, principal);
        assertThat(responseSolution).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCanAccessParticipation_asStudent() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check for canAccessParticipation(participation)
        var response = programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation);
        assertThat(response).isTrue();

        var responseSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getSolutionParticipation());
        assertThat(responseSolution).isFalse();

        var responseTemplate = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getTemplateParticipation());
        assertThat(responseTemplate).isFalse();

        var responseOther = programmingExerciseParticipationService.canAccessParticipation(null);
        assertThat(responseOther).isFalse();

        // Check for canAccessParticipation(participation, principal)
        final var username = "student1";
        final Principal principal = () -> username;
        response = programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation, principal);
        assertThat(response).isTrue();

        responseSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getSolutionParticipation(), principal);
        assertThat(responseSolution).isFalse();

        responseSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getTemplateParticipation(), principal);
        assertThat(responseSolution).isFalse();

        responseSolution = programmingExerciseParticipationService.canAccessParticipation(null, principal);
        assertThat(responseSolution).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testFindStudentParticipation() {
        var response = programmingExerciseParticipationService.findStudentParticipation(participation.getId());
        assertThat(response.isPresent()).isTrue();
        assertThat(response.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnlockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            ((ProgrammingExercise) participation.getExercise()).setBuildAndTestStudentSubmissionsAfterDueDate(null);
            return null;
        }).when(versionControlService).configureRepository(programmingExercise, ((ProgrammingExerciseParticipation) participation).getVcsRepositoryUrl(),
                participation.getStudents(), true);

        programmingExerciseParticipationService.unlockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);

        assertThat(((ProgrammingExercise) participation.getExercise()).getBuildAndTestStudentSubmissionsAfterDueDate()).isNull();
        assertThat(programmingExerciseService.isParticipationRepositoryLocked((ProgrammingExerciseStudentParticipation) participation)).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnlockStudentRepository_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        programmingExerciseParticipationService.unlockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage())
                .isEqualTo("Cannot unlock student repository for participation " + participation.getId() + " because the repository was not copied yet!");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testLockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            participation.getExercise().setDueDate(ZonedDateTime.now().minusHours(1));
            return null;
        }).when(versionControlService).setRepositoryPermissionsToReadOnly(((ProgrammingExerciseParticipation) participation).getVcsRepositoryUrl(),
                programmingExercise.getProjectKey(), participation.getStudents());

        programmingExerciseParticipationService.lockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);
        assertThat(programmingExerciseService.isParticipationRepositoryLocked((ProgrammingExerciseStudentParticipation) participation)).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testLockStudentRepository_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        programmingExerciseParticipationService.lockStudentRepository(programmingExercise, (ProgrammingExerciseStudentParticipation) participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage())
                .isEqualTo("Cannot lock student repository for participation " + participation.getId() + " because the repository was not copied yet!");
    }

    private List<FileSubmission> getFileSubmissions(String fileContent) {
        List<FileSubmission> fileSubmissions = new ArrayList();
        FileSubmission fileSubmission = new FileSubmission();
        fileSubmission.setFileName(currentLocalFileName);
        fileSubmission.setFileContent(fileContent);
        fileSubmissions.add(fileSubmission);
        return fileSubmissions;
    }

    private void initialCommitAndSaveFiles(HttpStatus expectedStatus) throws Exception {
        assertThat(Files.exists(Paths.get(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        // Do initial commit
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions("initial commit"), expectedStatus);
        // Check repo
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("initial commit");

        // Save file, without commit
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), expectedStatus);
        // Check repo
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("updatedFileContent");
    }
}
