package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.util.RequestUtilService.parameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.TestConstants;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.dto.FileMove;
import de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTO;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.web.repository.FileSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class RepositoryIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(RepositoryIntegrationTest.class);

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateParticipationRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    @LocalServerPort
    private int port;

    private static final String TEST_PREFIX = "repositoryintegration";

    private final String studentRepoBaseUrl = "/api/programming/repository/";

    private final String participationsBaseUrl = "/api/programming/participations/";

    private final String filesContentBaseUrl = "/api/programming/repository-files-content/";

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final byte[] currentLocalBinaryFileContent = { (byte) 0b10101010, (byte) 0b11001100, (byte) 0b11110000 };

    private final String currentLocalFolderName = "currentFolderName";

    private LocalRepository studentRepository;

    private LocalRepository templateRepository;

    private LocalRepository solutionRepository;

    private LocalRepository testsRepository;

    private final List<BuildLogEntry> logs = new ArrayList<>();

    private final BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Checkout to revision e65aa77cc0380aeb9567ccceb78aca416d86085b has failed.");

    private final BuildLogEntry largeBuildLogEntry = new BuildLogEntry(ZonedDateTime.now(),
            "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin:3.1.1:checkstyle (default-cli)"
                    + "on project testPluginSCA-Tests: An error has occurred in Checkstyle report generation. Failed during checkstyle"
                    + "configuration: Exception was thrown while processing C:\\Users\\Stefan\\jenkins-home\\xml-data\\build-dir\\STCTES"
                    + "TPLUGINSCA-SOLUTION-JOB1\\assignment\\src\\www\\testPluginSCA\\BubbleSort.java: MismatchedTokenException occurred"
                    + "while parsing file C:\\Users\\Stefan\\home\\xml-data\\build-dir\\STCTESTPLUGINSCA-SOLUTION-JOB1\\assignment\\"
                    + "src\\www\\testPluginSCA\\BubbleSort.java. expecting EOF, found '}' -> [Help 1]");

    private ProgrammingExerciseStudentParticipation participation;

    private Path studentFilePath;

    private String projectKey;

    private String studentLogin;

    private Course course;

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        // LocalVC helper needs the running server port for URI construction
        localVCLocalCITestService.setPort(port);

        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        projectKey = programmingExercise.getProjectKey().toUpperCase();
        deleteExistingProject(projectKey);
        studentLogin = TEST_PREFIX + "student1";

        initializeInstructorRepositories();
        initializeStudentParticipation();

        logs.add(buildLogEntry);
        logs.add(largeBuildLogEntry);

        // Following setup is to check log messages see: https://stackoverflow.com/a/51812144
        // Get Logback Logger
        Logger logger = (Logger) LoggerFactory.getLogger(ProgrammingExerciseParticipationService.class);

        // Create and start a ListAppender
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();

        // Add the appender to the logger, the addAppender is outdated now
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() throws IOException {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
        if (studentRepository != null) {
            studentRepository.resetLocalRepo();
        }
        if (templateRepository != null) {
            templateRepository.resetLocalRepo();
        }
        if (solutionRepository != null) {
            solutionRepository.resetLocalRepo();
        }
        if (testsRepository != null) {
            testsRepository.resetLocalRepo();
        }
        deleteDirectoryIfExists(Path.of("local/server-integration-test/repos"));
        deleteDirectoryIfExists(Path.of("local/server-integration-test/repos-download"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFiles() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        synchronizeWithRemote(studentRepository);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFilesWithFileAndDirectoryFilter() throws Exception {
        // This case tests the FileAndDirectoryFilter inner class in GitRepositoryExportService
        // by calling the getFiles endpoint which internally uses the filter
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        validateFilesExcludeGitAndFolders(files, false);

        synchronizeWithRemote(studentRepository);

        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithContentAndFileAndDirectoryFilter() throws Exception {
        // This case tests the FileAndDirectoryFilter inner class in GitRepositoryExportService
        // by calling the files-content endpoint which internally uses the filter
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content", HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        validateFilesExcludeGitAndFolders(files, true);

        synchronizeWithRemote(studentRepository);

        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFileBeforeExamExerciseStartForbidden() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        Exam exam = programmingExercise.getExerciseGroup().getExam();
        examRepository.save(exam);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.FORBIDDEN, byte[].class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFileExerciseStartForbidden() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setStartDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.FORBIDDEN, byte[].class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithContent() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content", HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithOmitBinaries() throws Exception {
        var queryParams = "?omitBinaries=true";
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content" + queryParams, HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }

        assertThat(files.keySet()).noneMatch(file -> file.endsWith(".jar"));

        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesAtCommitInstructorNotInCourseForbidden() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.workingCopyGitRepo);
        courseUtilService.updateCourseGroups("abc", course, "");
        request.getMap(filesContentBaseUrl + commitHash + "?participationId=" + participation.getId(), HttpStatus.FORBIDDEN, String.class, String.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesAtCommitTutorNotInCourseForbidden() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.workingCopyGitRepo);
        courseUtilService.updateCourseGroups("abc", course, "");
        request.getMap(filesContentBaseUrl + commitHash + "?participationId=" + participation.getId(), HttpStatus.FORBIDDEN, String.class, String.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetFilesAtCommitEditorNotInCourseForbidden() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.workingCopyGitRepo);
        courseUtilService.updateCourseGroups("abc", course, "");
        request.getMap(filesContentBaseUrl + commitHash + "?participationId=" + participation.getId(), HttpStatus.FORBIDDEN, String.class, String.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesWithContentAtCommit() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.workingCopyGitRepo);
        var files = request.getMap(filesContentBaseUrl + commitHash + "?participationId=" + participation.getId(), HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesWithContentAtCommitParticipationNotFound() throws Exception {
        request.getMap(filesContentBaseUrl + "abc?participationId=" + UUID.randomUUID().getLeastSignificantBits(), HttpStatus.NOT_FOUND, String.class, String.class);
    }

    private void prepareRepository() throws GitAPIException, IOException {
        // files are already created in beforeEach
        // first commit
        studentRepository.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(studentRepository.workingCopyGitRepo).setMessage("my commit 1").call();
        // second commit
        Files.createFile(Path.of(studentRepository.workingCopyGitRepoFile + "/dummy.txt"));
        studentRepository.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(studentRepository.workingCopyGitRepo).setMessage("my commit 2").call();
    }

    @NonNull
    private String getCommitHash(Git repo) throws GitAPIException {
        AtomicReference<String> commitHash = new AtomicReference<>();
        repo.log().call().forEach(revCommit -> {
            if ("my commit 1".equals(revCommit.getFullMessage())) {
                commitHash.set(revCommit.getId().getName());
            }
        });
        return commitHash.get();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithContent_shouldNotThrowException() throws Exception {
        MockedStatic<FileUtils> mockedFileUtils = mockStatic(FileUtils.class);
        mockedFileUtils.when(() -> FileUtils.readFileToString(any(File.class), eq(StandardCharsets.UTF_8))).thenThrow(IOException.class);

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content", HttpStatus.OK, String.class, String.class);
        assertThat(files).isEmpty();
        mockedFileUtils.close();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_noChange() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        synchronizeWithRemote(studentRepository);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
            assertThat(files.get(key)).isFalse();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_withChange() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("newContent123"), HttpStatus.OK);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        synchronizeWithRemote(studentRepository);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
            if (currentLocalFileName.equals(key)) {
                assertThat(files.get(key)).isTrue();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_withNewFile() throws Exception {
        var newSubmission = new FileSubmission();
        String newFileName = "newFile";
        newSubmission.setFileName(newFileName);
        newSubmission.setFileContent(currentLocalFileContent + "test1");
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", List.of(newSubmission), HttpStatus.OK);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        synchronizeWithRemote(studentRepository);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
            if (newFileName.equals(key)) {
                assertThat(files.get(key)).isTrue();
            }

        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFiles_solutionParticipation() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + programmingExercise.getSolutionParticipation().getId() + "/files", HttpStatus.OK, String.class, FileType.class);

        synchronizeWithRemote(solutionRepository);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(solutionRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetFilesAsDifferentStudentForbidden() throws Exception {
        request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.FORBIDDEN, String.class, FileType.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetFileAsDifferentStudentForbidden() throws Exception {
        programmingExerciseRepository.save(programmingExercise);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.FORBIDDEN, byte[].class, params);
    }

    private void addPlagiarismCaseToProgrammingExercise(String studentLoginWithoutPost, String studentLoginWithPost) {
        var textPlagiarismResult = textExerciseUtilService.createPlagiarismResultForExercise(programmingExercise);
        var plagiarismComparison = new PlagiarismComparison();
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatus(PlagiarismStatus.CONFIRMED);

        var plagiarismSubmissionA = new PlagiarismSubmission();
        plagiarismSubmissionA.setStudentLogin(studentLoginWithoutPost);
        plagiarismSubmissionA.setSubmissionId(participation.getId());

        var plagiarismSubmissionB = new PlagiarismSubmission();
        plagiarismSubmissionB.setStudentLogin(studentLoginWithPost);
        plagiarismSubmissionB.setSubmissionId(participation.getId() + 1);

        plagiarismComparison.setSubmissionA(plagiarismSubmissionA);
        plagiarismComparison.setSubmissionB(plagiarismSubmissionB);

        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(programmingExercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

        Post post = new Post();
        post.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        postRepository.save(post);

        plagiarismSubmissionB.setPlagiarismCase(plagiarismCase);
        plagiarismComparisonRepository.save(plagiarismComparison);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetFilesAsDifferentStudentWithRelevantPlagiarismCase() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student1", TEST_PREFIX + "student2");

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-plagiarism-view", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        synchronizeWithRemote(studentRepository);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetFileAsDifferentStudentWithRelevantPlagiarismCase() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student1", TEST_PREFIX + "student2");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file-plagiarism-view", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testCannotGetFileAsDifferentStudentWithRelevantPlagiarismCaseBeforeExerciseDueDate() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student1", TEST_PREFIX + "student2");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        request.get(studentRepoBaseUrl + participation.getId() + "/file-plagiarism-view", HttpStatus.FORBIDDEN, byte[].class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCanGetFileAsInstructorWithRelevantPlagiarismCaseBeforeExerciseDueDate() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student1", TEST_PREFIX + "student2");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file-plagiarism-view", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFileAsInstructorWithRelevantPlagiarismCaseAfterExam() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(4));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student2", TEST_PREFIX + "student1");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file-plagiarism-view", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFileWithRelevantPlagiarismCaseAfterExam() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(4));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student2", TEST_PREFIX + "student1");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file-plagiarism-view", HttpStatus.OK, byte[].class, params);
        assertThat(file).isNotEmpty();
        assertThat(new String(file)).isEqualTo(currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFilesWithRelevantPlagiarismCaseAfterExam_forbidden() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(3));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        // student1 is NOT notified yet.
        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student1", TEST_PREFIX + "student2");

        request.getMap(studentRepoBaseUrl + participation.getId() + "/files-plagiarism-view", HttpStatus.FORBIDDEN, String.class, FileType.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFilesWithRelevantPlagiarismCaseAfterExam() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        Exam exam = programmingExercise.getExerciseGroup().getExam();

        // The calculated exam end date (startDate of exam + workingTime of studentExam (7200 seconds))
        // should be in the past for this test.
        exam.setStartDate(ZonedDateTime.now().minusHours(4));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        // student1 is notified.
        addPlagiarismCaseToProgrammingExercise(TEST_PREFIX + "student2", TEST_PREFIX + "student1");

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-plagiarism-view", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFile_shouldThrowException() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "does-not-exist.txt");
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.NOT_FOUND, byte[].class, params);
        assertThat(file).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "newFile");
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).containsKey("newFile");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateFolder() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/folder", HttpStatus.OK, params);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).containsEntry("newFolder", FileType.FOLDER);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenameFile() throws Exception {
        String newLocalFileName = "newFileName";
        FileMove fileMove = new FileMove(currentLocalFileName, newLocalFileName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFileName).containsKey(newLocalFileName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenameFolder() throws Exception {
        String newLocalFolderName = "newFolderName";
        FileMove fileMove = new FileMove(currentLocalFolderName, newLocalFolderName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFolderName).containsEntry(newLocalFolderName, FileType.FOLDER);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        request.delete(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).doesNotContainKey(currentLocalFileName);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCommitChanges() throws Exception {
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");
        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getName()).isEqualTo(testRepoCommits.getFirst().getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveFiles() throws Exception {
        assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), HttpStatus.OK);
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var updatedFile = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(new String(updatedFile, StandardCharsets.UTF_8)).isEqualTo("updatedFileContent");
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveFilesAndCommit() throws Exception {
        assertThat(Path.of(studentRepository.workingCopyGitRepoFile + "/" + currentLocalFileName)).exists();

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions("updatedFileContent"), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        var updatedFile = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, byte[].class, params);
        assertThat(new String(updatedFile, StandardCharsets.UTF_8)).isEqualTo("updatedFileContent");

        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getName()).isEqualTo(testRepoCommits.getFirst().getAuthorIdent().getName());
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesAfterDueDateAsInstructor() throws Exception {
        // Instructors should be able to push to their personal assignment repository after the due date of the exercise has passed.
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseStudentParticipation instructorAssignmentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "instructor1");
        request.put(studentRepoBaseUrl + instructorAssignmentParticipation.getId() + "/files?commit=true", List.of(), HttpStatus.OK);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testUpdateParticipationFiles_cannotAccessParticipation() throws Exception {
        // student2 should not have access to student1's participation.
        request.put(studentRepoBaseUrl + participation.getId() + "/files", List.of(), HttpStatus.FORBIDDEN);
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPullChanges() throws Exception {
        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(participation.getRepositoryUri());
        Path remoteClonePath = Files.createTempDirectory("repositoryintegration-remote-clone");
        try (Repository remoteRepository = gitService.getOrCheckoutRepositoryWithLocalPath(repositoryUri, remoteClonePath, true, true);
                Git remoteGit = Git.wrap(remoteRepository)) {

            // Create file in the remote repository clone
            Path filePath = remoteRepository.getWorkTree().toPath().resolve(fileName);
            Files.createFile(filePath);

            // Check if the file exists in the remote repository clone and that it doesn't yet exist in the local repository
            assertThat(filePath).exists();
            assertThat(studentRepository.workingCopyGitRepoFile.toPath().resolve(fileName)).doesNotExist();

            // Stage all changes, make a second commit and push it to origin from the remote clone
            remoteGit.add().setUpdate(true).addFilepattern(".").call();
            remoteGit.add().addFilepattern(".").call();
            GitService.commit(remoteGit).setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();
            remoteGit.push().setRemote("origin").call();

            // Checks if the current commit is not equal on the local and the remote repository
            assertThat(studentRepository.getAllLocalCommits().getFirst()).isNotEqualTo(studentRepository.getAllOriginCommits().getFirst());

            // Execute the Rest call
            request.get(studentRepoBaseUrl + participation.getId() + "/pull", HttpStatus.OK, Void.class);

            // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
            assertThat(studentRepository.getAllLocalCommits().getFirst()).isEqualTo(studentRepository.getAllOriginCommits().getFirst());
            assertThat(studentRepository.workingCopyGitRepoFile.toPath().resolve(fileName)).exists();
        }
        finally {
            FileUtils.deleteQuietly(remoteClonePath.toFile());
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testResetToLastCommit() throws Exception {
        String fileName = "testFile";
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(participation.getRepositoryUri());
        Path remoteClonePath = Files.createTempDirectory("repositoryintegration-reset-remote");
        try (Repository remoteRepository = gitService.getOrCheckoutRepositoryWithLocalPath(repositoryUri, remoteClonePath, true, true);
                Git remoteGit = Git.wrap(remoteRepository)) {

            // Check status of git before the commit
            var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

            // Create a commit for the local and the remote repository
            request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);

            // Check status of git after the commit
            var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

            // Create file in the local repository and commit it
            Path localFilePath = studentRepository.workingCopyGitRepoFile.toPath().resolve(fileName);
            var localFile = Files.createFile(localFilePath).toFile();
            // write content to the created file
            FileUtils.write(localFile, "local", Charset.defaultCharset());
            studentRepository.workingCopyGitRepo.add().setUpdate(true).addFilepattern(".").call();
            studentRepository.workingCopyGitRepo.add().addFilepattern(".").call();
            GitService.commit(studentRepository.workingCopyGitRepo).setMessage("local").call();

            // Create file in the remote repository clone and commit it
            Path remoteFilePath = remoteRepository.getWorkTree().toPath().resolve(fileName);
            var remoteFile = Files.createFile(remoteFilePath).toFile();
            // write content to the created file
            FileUtils.write(remoteFile, "remote", Charset.defaultCharset());
            remoteGit.add().setUpdate(true).addFilepattern(".").call();
            remoteGit.add().addFilepattern(".").call();
            GitService.commit(remoteGit).setMessage("remote").call();
            remoteGit.push().setRemote("origin").call();

            // Merge the two and a conflict will occur
            studentRepository.workingCopyGitRepo.fetch().setRemote("origin").call();
            List<Ref> refs = studentRepository.workingCopyGitRepo.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            var result = studentRepository.workingCopyGitRepo.merge().include(refs.getFirst().getObjectId()).setStrategy(MergeStrategy.RESOLVE).call();
            var status = studentRepository.workingCopyGitRepo.status().call();
            assertThat(status.getConflicting()).isNotEmpty();
            assertThat(result.getMergeStatus()).isEqualTo(MergeResult.MergeStatus.CONFLICTING);

            // Execute the reset Rest call
            request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.OK, null);

            // Check the git status after the reset
            status = studentRepository.workingCopyGitRepo.status().call();
            assertThat(status.getConflicting()).isEmpty();
            assertThat(studentRepository.getAllLocalCommits().getFirst()).isEqualTo(studentRepository.getAllOriginCommits().getFirst());
            var receivedStatusAfterReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
            assertThat(receivedStatusAfterReset.repositoryStatus()).hasToString("CLEAN");
        }
        finally {
            FileUtils.deleteQuietly(remoteClonePath.toFile());
        }
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsNoSubmission() throws Exception {
        var receivedLogs = request.get(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull().isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsWithSubmissionBuildSuccessful() throws Exception {
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);
        var buildLogs = request.get(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(buildLogs).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsWithManualResult() throws Exception {
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, true);
        var buildLogEntries = buildLogEntryService.saveBuildLogs(logs, submission);
        submission.setBuildLogEntries(buildLogEntries);
        participationUtilService.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC);
        var receivedLogs = request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).hasSize(2);
        assertLogsContent(receivedLogs);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogs() throws Exception {
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, true);
        var buildLogEntries = buildLogEntryService.saveBuildLogs(logs, submission);
        submission.setBuildLogEntries(buildLogEntries);
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC);
        var receivedLogs = request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).hasSize(2);
        assertLogsContent(receivedLogs);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetBuildLogs_cannotAccessParticipation() throws Exception {
        // student2 should not have access to student1's participation.
        request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, BuildLogEntry.class);
    }

    private void assertLogsContent(List<BuildLogEntry> receivedLogs) {
        for (int i = 0; i < receivedLogs.size(); i++) {
            assertThat(receivedLogs.get(i).getLog()).isEqualTo(logs.get(i).getLog());
            // When serializing and deserializing the logs, the time of each BuildLogEntry is converted to UTC.
            // Convert the time in the logs set up above to UTC and round it to milliseconds for comparison.
            ZonedDateTime expectedTime = ZonedDateTime.ofInstant(logs.get(i).getTime().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC"));
            ZonedDateTime actualTime = receivedLogs.get(i).getTime().truncatedTo(ChronoUnit.MILLIS);
            assertThat(actualTime).isCloseTo(expectedTime, within(1, ChronoUnit.MILLIS));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
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
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");

        var receivedLogs = request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).hasSize(3).isEqualTo(buildLogEntries);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
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
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission1, TEST_PREFIX + "student1");
        var result1 = participationUtilService.addResultToSubmission(submission1, AssessmentType.AUTOMATIC).getFirstResult();

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
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission2, TEST_PREFIX + "student1");
        var result2 = participationUtilService.addResultToSubmission(submission2, AssessmentType.AUTOMATIC).getFirstResult();

        // Specify to use result1
        var receivedLogs1 = request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class,
                parameters(Map.of("resultId", result1.getId())));
        assertThat(receivedLogs1).isEqualTo(submission1Logs);

        // Specify to use result2
        var receivedLogs2 = request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class,
                parameters(Map.of("resultId", result2.getId())));
        assertThat(receivedLogs2).isEqualTo(submission2Logs);

        // Without parameters, the latest submission must be used
        var receivedLogsLatest = request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogsLatest).isEqualTo(submission2Logs);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsFromDatabaseForSpecificResults_otherParticipation() throws Exception {
        var result = participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "tutor1");
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(result, (StudentParticipation) result.getSubmission().getParticipation(), "xyz");

        request.getList(participationsBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, BuildLogEntry.class, parameters(Map.of("resultId", result.getId())));
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCommitChangesAllowedForPracticeModeAfterDueDate() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.MANUAL);
        programmingExerciseRepository.save(programmingExercise);

        participation.setPracticeMode(true);
        studentParticipationRepository.save(participation);

        testCommitChanges();
    }

    private void assertUnchangedRepositoryStatusForForbiddenReset(boolean ensureUncommittedChanges) throws Exception {
        if (ensureUncommittedChanges) {
            userUtilService.changeUser(TEST_PREFIX + "student1");
            request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), HttpStatus.OK);
            userUtilService.changeUser(TEST_PREFIX + "tutor1");
        }

        var receivedStatusBeforeReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.FORBIDDEN, null);
        var receivedStatusAfterReset = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterReset.repositoryStatus()).hasToString(receivedStatusBeforeReset.repositoryStatus().name());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testResetNotAllowedBeforeDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        assertUnchangedRepositoryStatusForForbiddenReset(true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testResetNotAllowedForExamBeforeDueDate() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        // A tutor is not allowed to reset the repository during the exam time
        assertUnchangedRepositoryStatusForForbiddenReset(false);
    }

    private ProgrammingExercise createProgrammingExerciseForExam() {
        // Create an exam programming exercise
        var programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        programmingExerciseRepository.save(programmingExercise);
        participation.setExercise(programmingExercise);
        studentParticipationRepository.save(participation);
        // Create an exam which has already started
        Exam exam = examRepository.findByIdElseThrow(programmingExercise.getExerciseGroup().getExam().getId());
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);
        var studentExam = examUtilService.addStudentExam(exam);
        studentExam.setWorkingTime(7200); // 2 hours
        studentExam.setUser(participation.getStudent().orElseThrow());
        studentExam.addExercise(programmingExercise);
        studentExamRepository.save(studentExam);
        return programmingExercise;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFindStudentParticipation() {
        var response = studentParticipationRepository.findById(participation.getId());
        assertThat(response).isPresent();
        assertThat(response.get().getId()).isEqualTo(participation.getId());
    }

    private void initializeInstructorRepositories() throws Exception {
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        var templateSlug = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        templateRepository = createRepositoryForSlug(templateSlug);
        updateTemplateParticipationUri(templateSlug);

        var solutionSlug = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
        solutionRepository = createRepositoryForSlug(solutionSlug);
        updateSolutionParticipationUri(solutionSlug);

        var testsSlug = programmingExercise.generateRepositoryName(RepositoryType.TESTS);
        testsRepository = createRepositoryForSlug(testsSlug);
        programmingExercise.setTestRepositoryUri(buildLocalVcUri(testsSlug));
        programmingExerciseRepository.save(programmingExercise);
    }

    private void initializeStudentParticipation() throws Exception {
        var studentSlug = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        studentRepository = createRepositoryForSlug(studentSlug);
        studentFilePath = studentRepository.workingCopyGitRepoFile.toPath().resolve(currentLocalFileName);
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, studentLogin);
    }

    private LocalRepository createRepositoryForSlug(String repositorySlug) throws Exception {
        var repo = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug));
        seedRepositoryWithDefaultContent(repo);
        return repo;
    }

    private void seedRepositoryWithDefaultContent(LocalRepository repository) throws Exception {
        Path workingDir = repository.workingCopyGitRepoFile.toPath();
        Files.createDirectories(workingDir);

        FileUtils.writeStringToFile(workingDir.resolve(currentLocalFileName).toFile(), currentLocalFileContent, StandardCharsets.UTF_8);
        FileUtils.writeByteArrayToFile(workingDir.resolve(currentLocalFileName + ".jar").toFile(), currentLocalBinaryFileContent);
        Path folderPath = workingDir.resolve(currentLocalFolderName);
        Files.createDirectories(folderPath);
        FileUtils.writeStringToFile(folderPath.resolve(".keep").toFile(), "", java.nio.charset.StandardCharsets.UTF_8);

        repository.workingCopyGitRepo.add().addFilepattern(".").call();
        GitService.commit(repository.workingCopyGitRepo).setMessage("Initial commit for " + currentLocalFileName).call();
        repository.workingCopyGitRepo.push().setRemote("origin").call();
    }

    private void updateTemplateParticipationUri(String repositorySlug) {
        var templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(buildLocalVcUri(repositorySlug));
        templateParticipationRepository.save(templateParticipation);
    }

    private void updateSolutionParticipationUri(String repositorySlug) {
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        solutionParticipation.setRepositoryUri(buildLocalVcUri(repositorySlug));
        solutionParticipationRepository.save(solutionParticipation);
    }

    private String buildLocalVcUri(String repositorySlug) {
        return localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repositorySlug);
    }

    private void synchronizeWithRemote(LocalRepository repository) throws Exception {
        repository.workingCopyGitRepo.fetch().setRemote("origin").call();
        repository.workingCopyGitRepo.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/" + defaultBranch).call();
    }

    private void deleteExistingProject(String normalizedProjectKey) {
        try {
            versionControlService.deleteProject(normalizedProjectKey);
        }
        catch (Exception ex) {
            log.warn("Failed to delete LocalVC project {} before test execution", normalizedProjectKey, ex);
        }
    }

    private void deleteDirectoryIfExists(Path path) throws IOException {
        RepositoryExportTestUtil.safeDeleteDirectory(path);
    }

    private List<FileSubmission> getFileSubmissions(String fileContent) {
        List<FileSubmission> fileSubmissions = new ArrayList<>();
        FileSubmission fileSubmission = new FileSubmission();
        fileSubmission.setFileName(currentLocalFileName);
        fileSubmission.setFileContent(fileContent);
        fileSubmissions.add(fileSubmission);
        return fileSubmissions;
    }

    private void validateFilesExcludeGitAndFolders(Map<String, ?> files, boolean excludeFolders) {
        // Ensure we only exclude the actual .git directory (not files like .gitignore)
        assertThat(files.keySet()).noneMatch(path -> path.equals(".git") || path.startsWith(".git/") || path.startsWith(".git\\") || path.contains("/.git/")
                || path.contains("\\.git\\") || path.endsWith("/.git") || path.endsWith("\\.git"));

        if (excludeFolders) {
            assertThat(files.keySet()).doesNotContain(currentLocalFolderName);
        }
    }
}
