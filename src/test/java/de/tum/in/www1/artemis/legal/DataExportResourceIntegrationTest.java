package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.apollon.ApollonRequestMockProvider;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DataExport;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;

class DataExportResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexport";

    private static final String FILE_FORMAT_TXT = ".txt";

    private static final String FILE_FORMAT_PDF = ".pdf";

    private static final String FILE_FORMAT_ZIP = ".zip";

    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ApollonRequestMockProvider apollonRequestMockProvider;

    @Autowired
    @Qualifier("apollonRestTemplate")
    RestTemplate restTemplate;

    @Value("${artemis.apollon.conversion-service-url}")
    private String apollonConversionUrl;

    @Autowired
    private ApollonConversionService apollonConversionService;

    private static final String TEST_DATA_EXPORT_BASE_FILE_PATH = "src/test/resources/test-data/data-export/data-export.zip";

    private static final String FILE_FORMAT_CSV = ".csv";

    @BeforeEach
    void initTestCase() throws IOException {
        userUtilService.addUsers(TEST_PREFIX, 2, 4, 1, 1);
        userUtilService.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 2, 4, 1, 1);

        apollonConversionService.setRestTemplate(restTemplate);
        ReflectionTestUtils.setField(apollonConversionService, "apollonConversionUrl", apollonConversionUrl);

        apollonRequestMockProvider.enableMockingOfRequests();

        // mock apollon conversion twice
        mockApollonConversion();
        mockApollonConversion();
    }

    void mockApollonConversion() throws IOException {
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getInputStream()).thenReturn(new ClassPathResource("test-data/data-export/apollon_conversion.pdf").getInputStream());
        apollonRequestMockProvider.mockConvertModel(true, mockResource);
    }

    @AfterEach
    void tearDown() throws Exception {
        apollonRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_containsCorrectContent() throws Exception {
        prepareTestDataForDataExportCreation();
        var dataExport = request.putWithResponseBody("/api/data-export", null, DataExport.class, HttpStatus.OK);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExport.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getRequestDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationDate()).isNotNull();
        // extract zip file and check content
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        Predicate<Path> generalUserInformationCsv = path -> "general_user_information.csv".equals(path.getFileName().toString());
        Predicate<Path> courseDir = path -> path.getFileName().toString().startsWith("course_short");
        assertThat(extractedZipDirPath).isDirectoryContaining(generalUserInformationCsv).isDirectoryContaining(courseDir);
        var courseDirPath = getCourseDirectoryPath(extractedZipDirPath);
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("FileUpload2"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling0"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling3")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("Text1"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Programming")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("quiz"));
        getExerciseDirectoryPaths(courseDirPath).forEach(this::assertCorrectContentForExercise);

    }

    private void prepareTestDataForDataExportCreation() throws Exception {
        var userLogin = TEST_PREFIX + "student1";
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        Course course1 = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 4, 2, 1, 1, false, 1, validModel);
        quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course1, TEST_PREFIX + "student1");
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        var programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course1, false);
        var participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, userLogin,
                programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, false, "abc");
        var submission2 = programmingExerciseUtilService.createProgrammingSubmission(participation, false, "def");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 2.0, true, ZonedDateTime.now().minusMinutes(1));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        participationUtilService.addFeedbackToResult(feedback, submission.getFirstResult());
        participationUtilService.addSubmission(participation, submission);
        participationUtilService.addSubmission(participation, submission2);
        participationUtilService.addResultToSubmission(submission2, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));

        // add communication and messaging data
        conversationUtilService.addMessageWithReplyAndReactionInGroupChatOfCourseForUser(userLogin, course1, "group chat");
        conversationUtilService.addMessageInChannelOfCourseForUser(userLogin, course1, "channel");
        conversationUtilService.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(userLogin, course1, "one-to-one-chat");

        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), anyString(), anyBoolean());
    }

    private void assertCorrectContentForExercise(Path exerciseDirPath) {
        // Predicate<Path> participationFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("participation");
        Predicate<Path> resultsFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result");
        Predicate<Path> submissionFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("submission");
        assertThat(exerciseDirPath).isDirectoryContaining(submissionFile);
        // quizzes do not have a result file
        if (!exerciseDirPath.toString().contains("quiz")) {
            assertThat(exerciseDirPath).isDirectoryContaining(resultsFile);
        }
        if (exerciseDirPath.toString().contains("Programming")) {
            // zip file of the repository
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith(FILE_FORMAT_ZIP));
        }
        else if (exerciseDirPath.toString().contains("Modeling")) {
            // model as pdf file
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith(FILE_FORMAT_PDF));
        }
        else if (exerciseDirPath.toString().contains("Text")) {
            // submission text txt file
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("_text" + FILE_FORMAT_TXT));
        }
        else if (exerciseDirPath.toString().contains("quiz")) {
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("short_answer_questions_answers" + FILE_FORMAT_TXT))
                    .isDirectoryContaining(path -> path.getFileName().toString().endsWith("multiple_choice_questions_answers" + FILE_FORMAT_TXT))
                    .isDirectoryContaining(path -> path.getFileName().toString().contains("dragAndDropQuestion") && path.getFileName().toString().endsWith(FILE_FORMAT_PDF));
        }

    }

    private Path getCourseDirectoryPath(Path rootPath) throws IOException {
        try (var files = Files.list(rootPath).filter(Files::isDirectory)) {
            return files.findFirst().get();
        }
    }

    private List<Path> getExerciseDirectoryPaths(Path coursePath) throws IOException {
        try (var files = Files.list(coursePath).filter(Files::isDirectory)) {
            return files.toList();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownloadSuccess() throws Exception {
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        // create an export
        var dataExport = prepareDataExportForDownload();
        dataExport.setUser(userForExport);
        dataExport = dataExportRepository.save(dataExport);
        var dataExportFile = request.getFile("/api/data-export/" + dataExport.getId(), HttpStatus.OK, new LinkedMultiValueMap<>());
        var dataExportAfterDownload = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFile).isNotNull();
        assertThat(dataExportAfterDownload.getDataExportState()).isEqualTo(DataExportState.DOWNLOADED);
        assertThat(dataExportAfterDownload.getDownloadDate()).isNotNull();
        restoreTestDataInitState(dataExport);

    }

    private DataExport prepareDataExportForDownload() throws IOException {
        var dataExport = new DataExport();

        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setRequestDate(ZonedDateTime.now().minusDays(2));
        dataExport.setCreationDate(ZonedDateTime.now().minusDays(1));
        // rename file to avoid duplicates in the temp directory
        var newFilePath = TEST_DATA_EXPORT_BASE_FILE_PATH + ZonedDateTime.now().toEpochSecond();
        Files.move(Path.of(TEST_DATA_EXPORT_BASE_FILE_PATH), Path.of(newFilePath));
        dataExport.setFilePath(newFilePath);
        return dataExportRepository.save(dataExport);
    }

    private void restoreTestDataInitState(DataExport dataExport) throws IOException {
        // undo file renaming
        Files.move(Path.of(dataExport.getFilePath()), Path.of(TEST_DATA_EXPORT_BASE_FILE_PATH));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntBelongToUser_forbidden() throws Exception {
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownload_fileDoesntExist_internalServerError() throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        DataExport dataExport = new DataExport();
        dataExport.setUser(userForExport);
        dataExport.setFilePath("not-existent");
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-export/" + dataExport.getId(), HttpStatus.INTERNAL_SERVER_ERROR, Resource.class);

    }

    @ParameterizedTest
    @EnumSource(value = DataExportState.class, names = { "REQUESTED", "IN_CREATION", "DELETED", "DOWNLOADED_DELETED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExport_notYetFullyCreatedOrDeleted_accessForbidden(DataExportState state) throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        DataExport dataExport = new DataExport();
        dataExport.setUser(userForExport);
        dataExport.setFilePath("not-existent");
        dataExport.setDataExportState(state);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportIdNotExistent_notFound() throws Exception {
        request.get("/api/data-export/999999", HttpStatus.NOT_FOUND, Resource.class);

    }
}
