package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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
import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

public class RepositoryIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private final String studentRepoBaseUrl = "/api/repository/";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    private final LocalRepository studentRepository = new LocalRepository();

    private final List<BuildLogEntry> logs = new ArrayList<>();

    private final BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Checkout to revision e65aa77cc0380aeb9567ccceb78aca416d86085b has failed.");

    private final BuildLogEntry largeBuildLogEntry = new BuildLogEntry(ZonedDateTime.now(),
            "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin:3.1.1:checkstyle (default-cli)"
                    + "on project testPluginSCA-Tests: An error has occurred in Checkstyle report generation. Failed during checkstyle"
                    + "configuration: Exception was thrown while processing C:\\Users\\Stefan\\bamboo-home\\xml-data\\build-dir\\STCTES"
                    + "TPLUGINSCA-SOLUTION-JOB1\\assignment\\src\\www\\testPluginSCA\\BubbleSort.java: MismatchedTokenException occurred"
                    + "while parsing file C:\\Users\\Stefan\\bamboo-home\\xml-data\\build-dir\\STCTESTPLUGINSCA-SOLUTION-JOB1\\assignment\\"
                    + "src\\www\\testPluginSCA\\BubbleSort.java. expecting EOF, found '}' -> [Help 1]");

    private ProgrammingExerciseStudentParticipation participation;

    private ListAppender<ILoggingEvent> listAppender;

    private Path studentFilePath;

    private File studentFile;

    @BeforeEach
    public void setup() throws Exception {
        database.addUsers(1, 1, 1, 1);
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
        participation = (ProgrammingExerciseStudentParticipation) studentParticipationRepository.findAll().get(0);
        programmingExercise.setTestRepositoryUrl(localRepoUrl.toString());

        // Create template repo
        LocalRepository templateRepository = new LocalRepository();
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
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl()), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), eq(false), any());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(programmingExercise.getTemplateParticipation().getVcsRepositoryUrl(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUrl(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUrl(), false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(participation);

        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch("master", participation.getVcsRepositoryUrl());
        bitbucketRequestMockProvider.mockDefaultBranch("master", programmingExercise.getVcsTemplateRepositoryUrl());

        logs.add(buildLogEntry);
        logs.add(largeBuildLogEntry);

        // Following setup is to check log messages see: https://stackoverflow.com/a/51812144
        // Get Logback Logger
        Logger logger = (Logger) LoggerFactory.getLogger(ProgrammingExerciseParticipationService.class);

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
        assertThat(files.get(currentLocalFileName)).isEqualTo(currentLocalFileContent);
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
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(programmingExercise.getSolutionParticipation().getVcsRepositoryUrl()), eq(true), any());

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
        String newLocalFileName = "newFileName";
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
        String newLocalFolderName = "newFolderName";
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
        doReturn(logs).when(continuousIntegrationService).getLatestBuildLogs(submission);
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC);
        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).isNotNull();
        assertThat(receivedLogs).hasSize(2);
        assertThat(receivedLogs.get(0).getTime()).isEqualTo(logs.get(0).getTime());
        // due to timezone assertThat isEqualTo issues, we compare those directly first and ignore them afterwards
        assertThat(receivedLogs).usingElementComparatorIgnoringFields("time", "id").isEqualTo(logs);
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

    private void setBuildAndTestForProgrammingExercise() {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);
    }

    private void setManualAssessmentForProgrammingExercise() {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);
    }

    private void assertUnchangedRepositoryStatusForForbiddenCommit() throws Exception {
        // Committing is not allowed
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCommitChangesNotAllowedForBuildAndTestAfterDueDate() throws Exception {
        setBuildAndTestForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenCommit();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCommitChangesNotAllowedForManuallyAssessedAfterDueDate() throws Exception {
        setManualAssessmentForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenCommit();
    }

    private void assertUnchangedRepositoryStatusForForbiddenReset() throws Exception {
        // Reset the repo is not allowed
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus.toString()).isEqualTo("UNCOMMITTED_CHANGES");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testResetNotAllowedForBuildAndTestAfterDueDate() throws Exception {
        setBuildAndTestForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testResetNotAllowedForManuallyAssessedAfterDueDate() throws Exception {
        setManualAssessmentForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testResetNotAllowedBeforeDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testResetNotAllowedForExamBeforeDueDate() throws Exception {
        // Create an exam programming exercise
        programmingExercise = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        programmingExerciseRepository.save(programmingExercise);
        participation.setExercise(programmingExercise);
        studentParticipationRepository.save(participation);
        // Create an exam which has already started
        Exam exam = examRepository.findByIdElseThrow(programmingExercise.getExerciseGroup().getExam().getId());
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);
        var studentExam = database.addStudentExam(exam);
        studentExam.setWorkingTime(7200); // 2 hours
        studentExam.setUser(participation.getStudent().get());
        studentExam.addExercise(programmingExercise);
        studentExamRepository.save(studentExam);
        // A tutor is not allowed to reset the repository during the exam time
        assertUnchangedRepositoryStatusForForbiddenReset();
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
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage()).startsWith("Cannot stash student repository for participation ");
        assertThat(logsList.get(0).getArgumentArray()).containsExactly(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_dueDatePassed() throws Exception {
        // Make initial commit and save files afterwards
        initialCommitAndSaveFiles(HttpStatus.OK);
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));

        // Stash changes using service
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, participation);
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("initial commit");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_throwError() {
        // Try to stash changes, but it will throw error as the HEAD is not initialized in the remote repo (this is done with the initial commit)
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, participation);

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

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    void checkCanAccessParticipation(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation, boolean shouldBeAllowed,
            boolean shouldBeAllowedTemplateSolution) {
        var isAllowed = programmingExerciseParticipationService.canAccessParticipation(participation);
        assertThat(isAllowed).isEqualTo(shouldBeAllowed);

        var isAllowedSolution = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getSolutionParticipation());
        assertThat(isAllowedSolution).isEqualTo(shouldBeAllowedTemplateSolution);

        var isAllowedTemplate = programmingExerciseParticipationService.canAccessParticipation(programmingExercise.getTemplateParticipation());
        assertThat(isAllowedTemplate).isEqualTo(shouldBeAllowedTemplateSolution);

        var responseOther = programmingExerciseParticipationService.canAccessParticipation(null);
        assertThat(responseOther).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor_edgeCase_exercise_null() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with exercise null
        participation.setExercise(null);
        programmingExercise.getSolutionParticipation().setExercise(null);
        programmingExercise.getTemplateParticipation().setExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);

        // Check with exercise and programmingExercise null (and set everything again)
        participation.setExercise(null);
        programmingExercise.getSolutionParticipation().setExercise(null);
        programmingExercise.getTemplateParticipation().setExercise(null);
        // Note that in the current implementation, setProgrammingExercise is equivalent to setExercise only for the ProgrammingExerciseStudentParticipation
        participation.setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor_edgeCase_programmingExercise_null() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with programmingExercise only null
        participation.setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCanAccessParticipation_asStudent() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testCanAccessParticipation_asTutor() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testCanAccessParticipation_asEditor() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        checkCanAccessParticipation(programmingExercise, participation, true, true);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testFindStudentParticipation() {
        var response = studentParticipationRepository.findById(participation.getId());
        assertThat(response.isPresent()).isTrue();
        assertThat(response.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnlockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            ((ProgrammingExercise) participation.getExercise()).setBuildAndTestStudentSubmissionsAfterDueDate(null);
            return null;
        }).when(versionControlService).configureRepository(programmingExercise, participation.getVcsRepositoryUrl(), participation.getStudents(), true);

        programmingExerciseParticipationService.unlockStudentRepository(programmingExercise, participation);

        assertThat(((ProgrammingExercise) participation.getExercise()).getBuildAndTestStudentSubmissionsAfterDueDate()).isNull();
        assertThat(participation.isLocked()).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnlockStudentRepository_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        programmingExerciseParticipationService.unlockStudentRepository(programmingExercise, participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage()).startsWith("Cannot unlock student repository for participation ");
        assertThat(logsList.get(0).getArgumentArray()).containsExactly(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testLockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            participation.getExercise().setDueDate(ZonedDateTime.now().minusHours(1));
            return null;
        }).when(versionControlService).setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), participation.getStudents());

        programmingExerciseParticipationService.lockStudentRepository(programmingExercise, participation);
        assertThat(participation.isLocked()).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testLockStudentRepository_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        programmingExerciseParticipationService.lockStudentRepository(programmingExercise, participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage()).startsWith("Cannot lock student repository for participation ");
        assertThat(logsList.get(0).getArgumentArray()).containsExactly(participation.getId());
    }

    private List<FileSubmission> getFileSubmissions(String fileContent) {
        List<FileSubmission> fileSubmissions = new ArrayList<>();
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
