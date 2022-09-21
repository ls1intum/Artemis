package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.util.RequestUtilService.parameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.MockedStatic;
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
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

class RepositoryIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostRepository postRepository;

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    private final LocalRepository studentRepository = new LocalRepository(defaultBranch);

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
    void setup() throws Exception {
        database.addUsers(2, 1, 1, 1);
        var course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).get();

        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");

        // add file to the repository folder
        studentFilePath = Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName);
        studentFile = Files.createFile(studentFilePath).toFile();

        // write content to the created file
        FileUtils.write(studentFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the repository folder
        Path folderPath = Path.of(studentRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(folderPath).toFile();

        var localRepoUrl = new GitUtilService.MockFileRepositoryUrl(studentRepository.localRepoFile);
        participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, "student1", localRepoUrl.getURI());
        programmingExercise.setTestRepositoryUrl(localRepoUrl.toString());

        // Create template repo
        LocalRepository templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("templateLocalRepo", "templateOriginRepo");

        // add file to the template repo folder
        var templateFilePath = Path.of(templateRepository.localRepoFile + "/" + currentLocalFileName);
        var templateFile = Files.createFile(templateFilePath).toFile();

        // write content to the created file
        FileUtils.write(templateFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the template repo folder
        Path templateFolderPath = Path.of(templateRepository.localRepoFile + "/" + currentLocalFolderName);
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
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfStudentParticipation(participation);
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);

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
    void tearDown() throws IOException {
        database.resetDatabase();
        reset(gitService);
        studentRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFiles() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + key))).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFilesWithContent() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content", HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + key))).isTrue();
        }
        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFilesWithContent_shouldNotThrowException() throws Exception {
        Map<de.tum.in.www1.artemis.domain.File, FileType> mockedFiles = new HashMap<>();
        mockedFiles.put(mock(de.tum.in.www1.artemis.domain.File.class), FileType.FILE);
        doReturn(mockedFiles).when(gitService).listFilesAndFolders(any(Repository.class));

        MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class);
        mockedFileUtils.when(() -> FileUtils.readFileToString(any(File.class), eq(StandardCharsets.UTF_8))).thenThrow(IOException.class);

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content", HttpStatus.OK, String.class, String.class);
        assertThat(files).isEmpty();
        mockedFileUtils.close();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_noChange() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + key))).isTrue();
            assertThat(files.get(key)).isFalse();
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_withChange() throws Exception {
        FileUtils.write(studentFile, "newContent123", Charset.defaultCharset());

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + key))).isTrue();
            assertThat(files.get(key)).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_withNewFile() throws Exception {
        FileUtils.write(studentFile, "newContent123", Charset.defaultCharset());

        Path newPath = Path.of(studentRepository.localRepoFile + "/newFile");
        var file2 = Files.createFile(newPath).toFile();
        // write content to the created file
        FileUtils.write(file2, currentLocalFileContent + "test1", Charset.defaultCharset());

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + key))).isTrue();
            assertThat(files.get(key)).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetFiles_solutionParticipation() throws Exception {
        // Create template repo
        var solutionRepository = new LocalRepository(defaultBranch);
        solutionRepository.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        // add file to the template repo folder
        var solutionFilePath = Path.of(solutionRepository.localRepoFile + "/" + currentLocalFileName);
        var solutionFile = Files.createFile(solutionFilePath).toFile();

        // write content to the created file
        FileUtils.write(solutionFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the template repo folder
        Path solutionFolderPath = Path.of(solutionRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(solutionFolderPath).toFile();

        programmingExercise = database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(solutionRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(programmingExercise.getSolutionParticipation().getVcsRepositoryUrl()), eq(true), any());

        var files = request.getMap(studentRepoBaseUrl + programmingExercise.getSolutionParticipation().getId() + "/files", HttpStatus.OK, String.class, FileType.class);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Files.exists(Path.of(solutionRepository.localRepoFile + "/" + key))).isTrue();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testGetFilesAsDifferentStudentForbidden() throws Exception {
        request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.FORBIDDEN, String.class, FileType.class);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testGetFileAsDifferentStudentForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.FORBIDDEN, byte[].class, params);
    }

    private void addPlagiarismCaseToProgrammingExercise(String studentLoginWithoutPost, String studentLoginWithPost) {
        var textPlagiarismResult = database.createTextPlagiarismResultForExercise(programmingExercise);
        var plagiarismComparison = new PlagiarismComparison<TextSubmissionElement>();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatus(PlagiarismStatus.CONFIRMED);

        var plagiarismSubmissionA = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA.setStudentLogin(studentLoginWithoutPost);
        plagiarismSubmissionA.setSubmissionId(participation.getId());

        var plagiarismSubmissionB = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB.setStudentLogin(studentLoginWithPost);
        plagiarismSubmissionB.setSubmissionId(participation.getId() + 1);

        plagiarismComparison.setSubmissionA(plagiarismSubmissionA);
        plagiarismComparison.setSubmissionB(plagiarismSubmissionB);

        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(programmingExercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        Post post = new Post();
        post.setAuthor(database.getUserByLogin("instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        postRepository.save(post);

        plagiarismSubmissionB.setPlagiarismCase(plagiarismCase);
        plagiarismComparisonRepository.save(plagiarismComparison);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFilesAsDifferentStudentWithRelevantPlagiarismCase() throws Exception {
        addPlagiarismCaseToProgrammingExercise("student1", "student2");

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    void testGetFileAsDifferentStudentWithRelevantPlagiarismCase() throws Exception {
        programmingExerciseRepository.save(programmingExercise);

        addPlagiarismCaseToProgrammingExercise("student1", "student2");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFileWithRelevantPlagiarismCaseAfterExam() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(3));
        examRepository.save(exam);

        addPlagiarismCaseToProgrammingExercise("student2", "student1");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFilesWithRelevantPlagiarismCaseAfterExam_forbidden() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(3));
        examRepository.save(exam);

        // student1 is NOT notified yet.
        addPlagiarismCaseToProgrammingExercise("student1", "student2");

        request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.FORBIDDEN, String.class, FileType.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFilesWithRelevantPlagiarismCaseAfterExam() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(3));
        examRepository.save(exam);

        // student1 is notified.
        addPlagiarismCaseToProgrammingExercise("student2", "student1");

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetFile_shouldThrowException() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);

        doReturn(Optional.empty()).when(gitService).getFileByName(any(Repository.class), eq(currentLocalFileName));
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.NOT_FOUND, byte[].class, params);
        assertThat(file).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "newFile");
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/newFile"))).isFalse();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.isRegularFile(Path.of(studentRepository.localRepoFile + "/newFile"))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCreateFolder() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/newFolder"))).isFalse();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Files.isDirectory(Path.of(studentRepository.localRepoFile + "/newFolder"))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testRenameFile() throws Exception {
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        String newLocalFileName = "newFileName";
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + newLocalFileName))).isFalse();
        FileMove fileMove = new FileMove(currentLocalFileName, newLocalFileName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isFalse();
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + newLocalFileName))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testRenameFolder() throws Exception {
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFolderName))).isTrue();
        String newLocalFolderName = "newFolderName";
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + newLocalFolderName))).isFalse();
        FileMove fileMove = new FileMove(currentLocalFolderName, newLocalFolderName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFolderName))).isFalse();
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + newLocalFolderName))).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testDeleteFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        request.delete(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isFalse();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCommitChanges() throws Exception {
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(database.getUserByLogin("student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSaveFiles() throws Exception {
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), HttpStatus.OK);
        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("updatedFileContent");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testSaveFilesAndCommit() throws Exception {
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions("updatedFileContent"), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        assertThat(FileUtils.readFileToString(studentFilePath.toFile(), Charset.defaultCharset())).isEqualTo("updatedFileContent");

        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(database.getUserByLogin("student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // git file locking issues
    @WithMockUser(username = "student1", roles = "USER")
    void testPullChanges() throws Exception {
        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var remoteRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.originRepoFile.toPath(), null);

        // Create file in the remote repository
        Path filePath = Path.of(studentRepository.originRepoFile.toString()).resolve(fileName);
        Files.createFile(filePath);

        // Check if the file exists in the remote repository and that it doesn't yet exist in the local repository
        assertThat(Files.exists(Path.of(studentRepository.originRepoFile.toString()).resolve(fileName))).isTrue();
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile.toString()).resolve(fileName))).isFalse();

        // Stage all changes and make a second commit in the remote repository
        gitService.stageAllChanges(remoteRepository);
        studentRepository.originGit.commit().setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

        // Checks if the current commit is not equal on the local and the remote repository
        assertThat(studentRepository.getAllLocalCommits().get(0)).isNotEqualTo(studentRepository.getAllOriginCommits().get(0));

        // Execute the Rest call
        request.get(studentRepoBaseUrl + participation.getId() + "/pull", HttpStatus.OK, Void.class);

        // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
        assertThat(studentRepository.getAllLocalCommits().get(0)).isEqualTo(studentRepository.getAllOriginCommits().get(0));
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile.toString()).resolve(fileName))).isTrue();

    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // git file locking issues
    @WithMockUser(username = "student1", roles = "USER")
    void testResetToLastCommit() throws Exception {
        String fileName = "testFile";
        var localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null);
        var remoteRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.originRepoFile.toPath(), null);

        // Check status of git before the commit
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);

        // Check status of git after the commit
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        // Create file in the local repository and commit it
        Path localFilePath = Path.of(studentRepository.localRepoFile + "/" + fileName);
        var localFile = Files.createFile(localFilePath).toFile();
        // write content to the created file
        FileUtils.write(localFile, "local", Charset.defaultCharset());
        gitService.stageAllChanges(localRepo);
        studentRepository.localGit.commit().setMessage("local").call();

        // Create file in the remote repository and commit it
        Path remoteFilePath = Path.of(studentRepository.originRepoFile + "/" + fileName);
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
        assertThat(status.getConflicting()).isNotEmpty();
        assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);

        // Execute the reset Rest call
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.OK, null);

        // Check the git status after the reset
        status = studentRepository.localGit.status().call();
        assertThat(status.getConflicting()).isEmpty();
        assertThat(studentRepository.getAllLocalCommits().get(0)).isEqualTo(studentRepository.getAllOriginCommits().get(0));
        var receivedStatusAfterReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterReset.repositoryStatus()).hasToString("CLEAN");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testGetStatus() throws Exception {
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);

        // The current status is "uncommited changes", since we added files and folders, but we didn't commit yet
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        // Perform a commit to check if the status changes
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);

        // Check if the status of git is "clean" after the commit
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogsNoSubmission() throws Exception {
        var receivedLogs = request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull().isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogsWithSubmissionBuildSuccessful() throws Exception {
        database.createProgrammingSubmission(participation, false);
        request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogsWithManualResult() throws Exception {
        var submission = database.createProgrammingSubmission(participation, true);
        doReturn(logs).when(continuousIntegrationService).getLatestBuildLogs(submission);
        database.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC);
        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).hasSize(2);
        assertThat(receivedLogs.get(0).getTime()).isEqualTo(logs.get(0).getTime());
        // due to timezone assertThat isEqualTo issues, we compare those directly first and ignore them afterwards
        assertThat(receivedLogs).usingElementComparatorIgnoringFields("time", "id").isEqualTo(logs);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogs() throws Exception {
        var submission = database.createProgrammingSubmission(participation, true);

        doReturn(logs).when(continuousIntegrationService).getLatestBuildLogs(submission);

        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC);
        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).hasSize(2);
        assertThat(receivedLogs.get(0).getTime()).isEqualTo(logs.get(0).getTime());
        // due to timezone assertThat isEqualTo issues, we compare those directly first and ignore them afterwards
        assertThat(receivedLogs).usingElementComparatorIgnoringFields("time", "id").isEqualTo(logs);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogsFromDatabase() throws Exception {
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
        assertThat(receivedLogs).hasSize(3).isEqualTo(buildLogEntries);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogsFromDatabaseForSpecificResults() throws Exception {
        // FIRST SUBMISSION
        var submission1 = new ProgrammingSubmission();
        submission1.setSubmissionDate(ZonedDateTime.now().minusMinutes(4));
        submission1.setSubmitted(true);
        submission1.setCommitHash("A");
        submission1.setType(SubmissionType.MANUAL);
        submission1.setBuildFailed(true);

        var submission1Logs = new ArrayList<BuildLogEntry>();
        submission1Logs.add(new BuildLogEntry(ZonedDateTime.now(), "Submission 1 - Log 1", submission1));
        submission1Logs.add(new BuildLogEntry(ZonedDateTime.now(), "Submission 1 - Log 2", submission1));

        submission1.setBuildLogEntries(submission1Logs);
        database.addProgrammingSubmission(programmingExercise, submission1, "student1");
        var result1 = database.addResultToSubmission(submission1, AssessmentType.AUTOMATIC).getFirstResult();

        // SECOND SUBMISSION
        var submission2 = new ProgrammingSubmission();
        submission2.setSubmissionDate(ZonedDateTime.now().minusMinutes(2));
        submission2.setSubmitted(true);
        submission2.setCommitHash("B");
        submission2.setType(SubmissionType.MANUAL);
        submission2.setBuildFailed(true);

        var submission2Logs = new ArrayList<BuildLogEntry>();
        submission2Logs.add(new BuildLogEntry(ZonedDateTime.now(), "Submission 2 - Log 1", submission2));
        submission2Logs.add(new BuildLogEntry(ZonedDateTime.now(), "Submission 2 - Log 2", submission2));

        submission2.setBuildLogEntries(submission2Logs);
        database.addProgrammingSubmission(programmingExercise, submission2, "student1");
        var result2 = database.addResultToSubmission(submission2, AssessmentType.AUTOMATIC).getFirstResult();

        // Specify to use result1
        var receivedLogs1 = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class,
                parameters(Map.of("resultId", result1.getId())));
        assertThat(receivedLogs1).isEqualTo(submission1Logs);

        // Specify to use result2
        var receivedLogs2 = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class,
                parameters(Map.of("resultId", result2.getId())));
        assertThat(receivedLogs2).isEqualTo(submission2Logs);

        // Without parameters, the latest submission must be used
        var receivedLogsLatest = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogsLatest).isEqualTo(submission2Logs);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testBuildLogsFromDatabaseForSpecificResults_otherParticipation() throws Exception {
        var result = database.addProgrammingParticipationWithResultForExercise(programmingExercise, "tutor1");
        database.addProgrammingSubmissionToResultAndParticipation(result, (StudentParticipation) result.getParticipation(), "xyz");

        request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, BuildLogEntry.class, parameters(Map.of("resultId", result.getId())));
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCommitChangesAllowedForAutomaticallyAssessedAfterDueDate() throws Exception {
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
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCommitChangesNotAllowedForBuildAndTestAfterDueDate() throws Exception {
        setBuildAndTestForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenCommit();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testCommitChangesNotAllowedForManuallyAssessedAfterDueDate() throws Exception {
        setManualAssessmentForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenCommit();
    }

    private void assertUnchangedRepositoryStatusForForbiddenReset() throws Exception {
        // Reset the repo is not allowed
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testResetNotAllowedForBuildAndTestAfterDueDate() throws Exception {
        setBuildAndTestForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testResetNotAllowedForManuallyAssessedAfterDueDate() throws Exception {
        setManualAssessmentForProgrammingExercise();
        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testResetNotAllowedBeforeDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testResetNotAllowedForExamBeforeDueDate() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        // A tutor is not allowed to reset the repository during the exam time
        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    private ProgrammingExercise createProgrammingExerciseForExam() {
        // Create an exam programming exercise
        var programmingExercise = database.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
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
        return programmingExercise;
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCanAccessParticipation_asInstructor_edgeCase_programmingExercise_unknownId() {
        // Set solution and template participation
        database.addSolutionParticipationForProgrammingExercise(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);

        // Check with programmingExercise null and a non-existent participation id
        participation.setProgrammingExercise(null);
        participation.setId(123456L);
        programmingExercise.getSolutionParticipation().setProgrammingExercise(null);
        programmingExercise.getSolutionParticipation().setId(123456L);
        programmingExercise.getTemplateParticipation().setProgrammingExercise(null);
        programmingExercise.getTemplateParticipation().setId(123456L);

        checkCanAccessParticipation(programmingExercise, participation, false, false);
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
        assertThat(response).isPresent();
        assertThat(response.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testUnlockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            ((ProgrammingExercise) participation.getExercise()).setBuildAndTestStudentSubmissionsAfterDueDate(null);
            return null;
        }).when(versionControlService).configureRepository(programmingExercise, participation, true);

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

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetSolutionFileNames() throws Exception {
        var fileNames = request.get(studentRepoBaseUrl + participation.getId() + "/file-names", HttpStatus.OK, String[].class);
        assertThat(fileNames).containsExactly("currentFileName");
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
        assertThat(Files.exists(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName))).isTrue();
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
