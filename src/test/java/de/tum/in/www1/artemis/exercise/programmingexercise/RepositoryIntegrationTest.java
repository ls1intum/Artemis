package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.util.RequestUtilService.parameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Repository;
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
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlRepositoryPermission;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.GitUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.TestConstants;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

class RepositoryIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "repositoryintegration";

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

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private ProgrammingExercise programmingExercise;

    private final String currentLocalFileName = "currentFileName";

    private final String currentLocalFileContent = "testContent";

    private final String currentLocalFolderName = "currentFolderName";

    private final LocalRepository studentRepository = new LocalRepository(defaultBranch);

    private LocalRepository templateRepository;

    private LocalRepository tempRepository;

    private final List<BuildLogEntry> logs = new ArrayList<>();

    private final BuildLogEntry buildLogEntry = new BuildLogEntry(ZonedDateTime.now(), "Checkout to revision e65aa77cc0380aeb9567ccceb78aca416d86085b has failed.");

    private final BuildLogEntry largeBuildLogEntry = new BuildLogEntry(ZonedDateTime.now(),
            "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin:3.1.1:checkstyle (default-cli)"
                    + "on project testPluginSCA-Tests: An error has occurred in Checkstyle report generation. Failed during checkstyle"
                    + "configuration: Exception was thrown while processing C:\\Users\\Stefan\\jenkins-home\\xml-data\\build-dir\\STCTES"
                    + "TPLUGINSCA-SOLUTION-JOB1\\assignment\\src\\www\\testPluginSCA\\BubbleSort.java: MismatchedTokenException occurred"
                    + "while parsing file C:\\Users\\Stefan\\gitlab-home\\xml-data\\build-dir\\STCTESTPLUGINSCA-SOLUTION-JOB1\\assignment\\"
                    + "src\\www\\testPluginSCA\\BubbleSort.java. expecting EOF, found '}' -> [Help 1]");

    private ProgrammingExerciseStudentParticipation participation;

    private ListAppender<ILoggingEvent> listAppender;

    private Path studentFilePath;

    private File studentFile;

    private Course course;

    @BeforeEach
    void setup() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);

        studentRepository.configureRepos("studentLocalRepo", "studentOriginRepo");

        // add file to the repository folder
        studentFilePath = Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName);
        studentFile = Files.createFile(studentFilePath).toFile();

        // write content to the created file
        FileUtils.write(studentFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the repository folder
        Path folderPath = Path.of(studentRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(folderPath);

        var localRepoUri = new GitUtilService.MockFileRepositoryUri(studentRepository.localRepoFile);
        participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, TEST_PREFIX + "student1", localRepoUri.getURI());
        programmingExercise.setTestRepositoryUri(localRepoUri.toString());

        // Create template repo
        templateRepository = new LocalRepository(defaultBranch);
        templateRepository.configureRepos("templateLocalRepo", "templateOriginRepo");

        // add file to the template repo folder
        var templateFilePath = Path.of(templateRepository.localRepoFile + "/" + currentLocalFileName);
        var templateFile = Files.createFile(templateFilePath).toFile();

        // write content to the created file
        FileUtils.write(templateFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the template repo folder
        Path templateFolderPath = Path.of(templateRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(templateFolderPath);

        programmingExercise = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(programmingExercise.getTemplateParticipation().getVcsRepositoryUri()), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), eq(true), any());
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(participation.getVcsRepositoryUri()), eq(false), any());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(templateRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(programmingExercise.getTemplateParticipation().getVcsRepositoryUri(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUri(), true);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(participation.getVcsRepositoryUri(), false);

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null)).when(gitService).getOrCheckoutRepository(participation);

        gitlabRequestMockProvider.enableMockingOfRequests();
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
        reset(gitService);
        studentRepository.resetLocalRepo();
        templateRepository.resetLocalRepo();
        if (tempRepository != null) {
            tempRepository.resetLocalRepo();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFiles() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files", HttpStatus.OK, String.class, FileType.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
        }
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
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
        }
        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesAtCommitInstructorNotInCourseForbidden() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.localGit);
        courseUtilService.updateCourseGroups("abc", course, "");
        request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content/" + commitHash, HttpStatus.FORBIDDEN, String.class, String.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesAtCommitTutorNotInCourseForbidden() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.localGit);
        courseUtilService.updateCourseGroups("abc", course, "");
        request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content/" + commitHash, HttpStatus.FORBIDDEN, String.class, String.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetFilesAtCommitEditorNotInCourseForbidden() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.localGit);
        courseUtilService.updateCourseGroups("abc", course, "");
        request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content/" + commitHash, HttpStatus.FORBIDDEN, String.class, String.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesWithContentAtCommit() throws Exception {
        prepareRepository();
        String commitHash = getCommitHash(studentRepository.localGit);
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-content/" + commitHash, HttpStatus.OK, String.class, String.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
        }
        assertThat(files).containsEntry(currentLocalFileName, currentLocalFileContent);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFilesWithContentAtCommitParticipationNotFound() throws Exception {
        request.getMap(studentRepoBaseUrl + UUID.randomUUID().getLeastSignificantBits() + "/files-content/" + "abc", HttpStatus.NOT_FOUND, String.class, String.class);
    }

    private void prepareRepository() throws GitAPIException, IOException {
        // files are already created in beforeEach
        // first commit
        studentRepository.localGit.add().addFilepattern(".").call();
        GitService.commit(studentRepository.localGit).setMessage("my commit 1").call();
        // second commit
        Files.createFile(Path.of(studentRepository.localRepoFile + "/" + "dummy.txt"));
        studentRepository.localGit.add().addFilepattern(".").call();
        GitService.commit(studentRepository.localGit).setMessage("my commit 2").call();
    }

    @NotNull
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_noChange() throws Exception {
        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
            assertThat(files.get(key)).isFalse();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFilesWithInfoAboutChange_withChange() throws Exception {
        FileUtils.write(studentFile, "newContent123", Charset.defaultCharset());

        var files = request.getMap(studentRepoBaseUrl + participation.getId() + "/files-change", HttpStatus.OK, String.class, Boolean.class);
        assertThat(files).isNotEmpty();

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
            assertThat(files.get(key)).isTrue();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
            assertThat(files.get(key)).isTrue();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFiles_solutionParticipation() throws Exception {
        // Create template repo
        tempRepository = new LocalRepository(defaultBranch);
        tempRepository.configureRepos("solutionLocalRepo", "solutionOriginRepo");

        // add file to the template repo folder
        var solutionFilePath = Path.of(tempRepository.localRepoFile + "/" + currentLocalFileName);
        var solutionFile = Files.createFile(solutionFilePath).toFile();

        // write content to the created file
        FileUtils.write(solutionFile, currentLocalFileContent, Charset.defaultCharset());

        // add folder to the template repo folder
        Path solutionFolderPath = Path.of(tempRepository.localRepoFile + "/" + currentLocalFolderName);
        Files.createDirectory(solutionFolderPath);

        programmingExercise = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(tempRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(eq(programmingExercise.getSolutionParticipation().getVcsRepositoryUri()), eq(true), any());

        var files = request.getMap(studentRepoBaseUrl + programmingExercise.getSolutionParticipation().getId() + "/files", HttpStatus.OK, String.class, FileType.class);

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(tempRepository.localRepoFile + "/" + key)).exists();
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
        var textPlagiarismResult = textExerciseUtilService.createTextPlagiarismResultForExercise(programmingExercise);
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

        // Check if all files exist
        for (String key : files.keySet()) {
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
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
            assertThat(Path.of(studentRepository.localRepoFile + "/" + key)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFile_shouldThrowException() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);

        doReturn(Optional.empty()).when(gitService).getFileByName(any(Repository.class), eq(currentLocalFileName));
        var file = request.get(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.NOT_FOUND, byte[].class, params);
        assertThat(file).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", "newFile");
        assertThat(Path.of(studentRepository.localRepoFile + "/newFile")).doesNotExist();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Path.of(studentRepository.localRepoFile + "/newFile")).isRegularFile();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateFolder() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("folder", "newFolder");
        assertThat(Path.of(studentRepository.localRepoFile + "/newFolder")).doesNotExist();
        request.postWithoutResponseBody(studentRepoBaseUrl + participation.getId() + "/folder", HttpStatus.OK, params);
        assertThat(Path.of(studentRepository.localRepoFile + "/newFolder")).isDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenameFile() throws Exception {
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).exists();
        String newLocalFileName = "newFileName";
        assertThat(Path.of(studentRepository.localRepoFile + "/" + newLocalFileName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFileName, newLocalFileName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).doesNotExist();
        assertThat(Path.of(studentRepository.localRepoFile + "/" + newLocalFileName)).exists();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRenameFolder() throws Exception {
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFolderName)).exists();
        String newLocalFolderName = "newFolderName";
        assertThat(Path.of(studentRepository.localRepoFile + "/" + newLocalFolderName)).doesNotExist();
        FileMove fileMove = new FileMove(currentLocalFolderName, newLocalFolderName);
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/rename-file", fileMove, HttpStatus.OK, null);
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFolderName)).doesNotExist();
        assertThat(Path.of(studentRepository.localRepoFile + "/" + newLocalFolderName)).exists();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteFile() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("file", currentLocalFileName);
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).exists();
        request.delete(studentRepoBaseUrl + participation.getId() + "/file", HttpStatus.OK, params);
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).doesNotExist();
    }

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
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveFiles() throws Exception {
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).exists();
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), HttpStatus.OK);
        assertThat(studentFilePath).hasContent("updatedFileContent");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSaveFilesAndCommit() throws Exception {
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).exists();

        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");

        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions("updatedFileContent"), HttpStatus.OK);

        var receivedStatusAfterCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusAfterCommit.repositoryStatus()).hasToString("CLEAN");

        assertThat(studentFilePath).hasContent("updatedFileContent");

        var testRepoCommits = studentRepository.getAllLocalCommits();
        assertThat(testRepoCommits).hasSize(1);
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "student1").getName()).isEqualTo(testRepoCommits.get(0).getAuthorIdent().getName());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveFilesAfterDueDateAsInstructor() throws Exception {
        // Instructors should be able to push to their personal assignment repository after the due date of the exercise has passed.
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));

        // Create assignment repository and participation for the instructor.
        tempRepository = new LocalRepository(defaultBranch);
        tempRepository.configureRepos("localInstructorAssignmentRepo", "remoteInstructorAssignmentRepo");
        var instructorAssignmentRepoUri = new GitUtilService.MockFileRepositoryUri(tempRepository.localRepoFile);
        ProgrammingExerciseStudentParticipation instructorAssignmentParticipation = participationUtilService
                .addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, TEST_PREFIX + "instructor1", instructorAssignmentRepoUri.getURI());
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfStudentParticipation(instructorAssignmentParticipation);
        doReturn(gitService.getExistingCheckedOutRepositoryByLocalPath(tempRepository.localRepoFile.toPath(), null)).when(gitService)
                .getOrCheckoutRepository(instructorAssignmentParticipation.getVcsRepositoryUri(), true, defaultBranch);

        request.put(studentRepoBaseUrl + instructorAssignmentParticipation.getId() + "/files?commit=true", List.of(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testUpdateParticipationFiles_cannotAccessParticipation() throws Exception {
        // student2 should not have access to student1's participation.
        request.put(studentRepoBaseUrl + participation.getId() + "/files", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testPullChanges() throws Exception {
        String fileName = "remoteFile";

        // Create a commit for the local and the remote repository
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.OK, null);
        try (var remoteRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.originRepoFile.toPath(), null)) {

            // Create file in the remote repository
            Path filePath = studentRepository.originRepoFile.toPath().resolve(fileName);
            Files.createFile(filePath);

            // Check if the file exists in the remote repository and that it doesn't yet exist in the local repository
            assertThat(studentRepository.originRepoFile.toPath().resolve(fileName)).exists();
            assertThat(studentRepository.localRepoFile.toPath().resolve(fileName)).doesNotExist();

            // Stage all changes and make a second commit in the remote repository
            gitService.stageAllChanges(remoteRepository);
            GitService.commit(studentRepository.originGit).setMessage("TestCommit").setAllowEmpty(true).setCommitter("testname", "test@email").call();

            // Checks if the current commit is not equal on the local and the remote repository
            assertThat(studentRepository.getAllLocalCommits().get(0)).isNotEqualTo(studentRepository.getAllOriginCommits().get(0));

            // Execute the Rest call
            request.get(studentRepoBaseUrl + participation.getId() + "/pull", HttpStatus.OK, Void.class);

            // Check if the current commit is the same on the local and the remote repository and if the file exists on the local repository
            assertThat(studentRepository.getAllLocalCommits().get(0)).isEqualTo(studentRepository.getAllOriginCommits().get(0));
            assertThat(studentRepository.localRepoFile.toPath().resolve(fileName)).exists();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testResetToLastCommit() throws Exception {
        String fileName = "testFile";
        try (var localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null);
                var remoteRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.originRepoFile.toPath(), null)) {

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
            GitService.commit(studentRepository.localGit).setMessage("local").call();

            // Create file in the remote repository and commit it
            Path remoteFilePath = Path.of(studentRepository.originRepoFile + "/" + fileName);
            var remoteFile = Files.createFile(remoteFilePath).toFile();
            // write content to the created file
            FileUtils.write(remoteFile, "remote", Charset.defaultCharset());
            gitService.stageAllChanges(remoteRepo);
            GitService.commit(studentRepository.originGit).setMessage("remote").call();

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
    }

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
        var receivedLogs = request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, List.class);
        assertThat(receivedLogs).isNotNull().isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsWithSubmissionBuildSuccessful() throws Exception {
        programmingExerciseUtilService.createProgrammingSubmission(participation, false);
        request.get(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsWithManualResult() throws Exception {
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, true);
        var buildLogEntries = buildLogEntryService.saveBuildLogs(logs, submission);
        submission.setBuildLogEntries(buildLogEntries);
        participationUtilService.addResultToSubmission(submission, AssessmentType.SEMI_AUTOMATIC);
        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
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
        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
        assertThat(receivedLogs).hasSize(2);
        assertLogsContent(receivedLogs);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetBuildLogs_cannotAccessParticipation() throws Exception {
        // student2 should not have access to student1's participation.
        request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, BuildLogEntry.class);
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

        var receivedLogs = request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.OK, BuildLogEntry.class);
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogsFromDatabaseForSpecificResults_otherParticipation() throws Exception {
        var result = participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "tutor1");
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(result, (StudentParticipation) result.getParticipation(), "xyz");

        request.getList(studentRepoBaseUrl + participation.getId() + "/buildlogs", HttpStatus.FORBIDDEN, BuildLogEntry.class, parameters(Map.of("resultId", result.getId())));
    }

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCommitChangesNotAllowedForLockedParticipation() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        this.programmingExerciseStudentParticipationRepository.updateLockedById(participation.getId(), true);

        // Committing is not allowed
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/commit", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
    }

    private void assertUnchangedRepositoryStatusForForbiddenReset() throws Exception {
        // Reset the repo is not allowed
        var receivedStatusBeforeCommit = request.get(studentRepoBaseUrl + participation.getId(), HttpStatus.OK, RepositoryStatusDTO.class);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
        request.postWithoutLocation(studentRepoBaseUrl + participation.getId() + "/reset", null, HttpStatus.FORBIDDEN, null);
        assertThat(receivedStatusBeforeCommit.repositoryStatus()).hasToString("UNCOMMITTED_CHANGES");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testResetNotAllowedForLockedParticipation() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise);
        this.programmingExerciseStudentParticipationRepository.updateLockedById(participation.getId(), true);

        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testResetNotAllowedBeforeDueDate() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);

        assertUnchangedRepositoryStatusForForbiddenReset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testResetNotAllowedForExamBeforeDueDate() throws Exception {
        programmingExercise = createProgrammingExerciseForExam();
        // A tutor is not allowed to reset the repository during the exam time
        assertUnchangedRepositoryStatusForForbiddenReset();
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
    void testStashChanges() throws Exception {
        // Make initial commit and save files afterwards
        initialCommitAndSaveFiles(HttpStatus.OK);
        Repository localRepo = gitService.getExistingCheckedOutRepositoryByLocalPath(studentRepository.localRepoFile.toPath(), null);

        // Stash changes
        gitService.stashChanges(localRepo);
        // Local repo has no unsubmitted changes
        assertThat(studentFilePath).hasContent("initial commit");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_dueDatePassed() throws Exception {
        // Make initial commit and save files afterwards
        initialCommitAndSaveFiles(HttpStatus.OK);
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));

        // Stash changes using service
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, participation);
        assertThat(studentFilePath).hasContent("initial commit");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStashChangesInStudentRepositoryAfterDueDateHasPassed_throwError() {
        // Try to stash changes, but it will throw error as the HEAD is not initialized in the remote repo (this is done with the initial commit)
        programmingExerciseParticipationService.stashChangesInStudentRepositoryAfterDueDateHasPassed(programmingExercise, participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getLevel()).isEqualTo(Level.ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFindStudentParticipation() {
        var response = studentParticipationRepository.findById(participation.getId());
        assertThat(response).isPresent();
        assertThat(response.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUnlockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            ((ProgrammingExercise) participation.getExercise()).setBuildAndTestStudentSubmissionsAfterDueDate(null);
            return null;
        }).when(versionControlService).addMemberToRepository(participation.getVcsRepositoryUri(), participation.getStudent().orElseThrow(),
                VersionControlRepositoryPermission.REPO_WRITE);

        programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation(participation);

        assertThat(((ProgrammingExercise) participation.getExercise()).getBuildAndTestStudentSubmissionsAfterDueDate()).isNull();
        assertThat(participation.isLocked()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUnlockStudentRepository_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        programmingExerciseParticipationService.unlockStudentRepositoryAndParticipation(participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage()).startsWith("Cannot unlock student repository for participation ");
        assertThat(logsList.get(0).getArgumentArray()).containsExactly(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLockStudentRepository() {
        doAnswer((Answer<Void>) invocation -> {
            participation.getExercise().setDueDate(ZonedDateTime.now().minusHours(1));
            return null;
        }).when(versionControlService).setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUri(), programmingExercise.getProjectKey(), participation.getStudents());

        programmingExerciseParticipationService.lockStudentRepositoryAndParticipation(programmingExercise, participation);
        assertThat(participation.isLocked()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testLockStudentRepository_beforeStateRepoConfigured() {
        participation.setInitializationState(InitializationState.REPO_COPIED);
        programmingExerciseParticipationService.lockStudentRepositoryAndParticipation(programmingExercise, participation);

        // Check the logs
        List<ILoggingEvent> logsList = listAppender.list;
        assertThat(logsList.get(0).getMessage()).startsWith("Cannot lock student repository for participation ");
        assertThat(logsList.get(0).getArgumentArray()).containsExactly(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
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
        assertThat(Path.of(studentRepository.localRepoFile + "/" + currentLocalFileName)).exists();
        // Do initial commit
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=true", getFileSubmissions("initial commit"), expectedStatus);
        // Check repo
        assertThat(studentFilePath).hasContent("initial commit");

        // Save file, without commit
        request.put(studentRepoBaseUrl + participation.getId() + "/files?commit=false", getFileSubmissions("updatedFileContent"), expectedStatus);
        // Check repo
        assertThat(studentFilePath).hasContent("updatedFileContent");
    }
}
