package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.repository.DataExportRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.DataExportService;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;

@ExtendWith(MockitoExtension.class)
class DataExportResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexport";

    private static final String FILE_FORMAT_TXT = ".txt";

    private static final String FILE_FORMAT_PDF = ".pdf";

    private static final String FILE_FORMAT_ZIP = ".zip";

    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    @Autowired
    private DataExportRepository dataExportRepository;

    @SpyBean
    private DataExportService dataExportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @MockBean
    private ApollonConversionService apollonConversionService;

    private static final String TEST_DATA_EXPORT_BASE_FILE_PATH = "src/test/resources/test-data/data-export/data-export.zip";

    private static final String FILE_FORMAT_CSV = ".csv";

    @BeforeEach
    void initTestCase() throws IOException {
        database.addUsers(TEST_PREFIX, 5, 4, 1, 1);
        database.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 5, 4, 1, 1);
        // we cannot directly return the input stream using mockito because then the stream is closed when the method is invoked more than once
        var byteArray = new ClassPathResource("test-data/data-export/apollon_conversion.pdf").getInputStream().readAllBytes();
        when(apollonConversionService.convertModel(anyString())).thenReturn(new ByteArrayInputStream(byteArray));

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_containsCorrectCourseContent() throws Exception {
        prepareCourseDataForDataExportCreation();
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var dataExport = request.putWithResponseBody("/api/" + userForExport.getId() + "/data-export", null, DataExport.class, HttpStatus.OK);
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
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "short");
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("FileUpload2"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling0"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling3")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("Text1"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Programming")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("quiz"));
        getExerciseDirectoryPaths(courseDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, true));

    }

    private void prepareCourseDataForDataExportCreation() throws Exception {
        var userLogin = TEST_PREFIX + "student1";
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        Course course1 = database.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 4, 2, 1, 1, false, 1, validModel);
        database.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course1, TEST_PREFIX + "student1");
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        var programmingExercise = database.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().minusSeconds(1));
        var participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, userLogin,
                programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        var submission = database.createProgrammingSubmission(participation, false, "abc");
        var submission2 = database.createProgrammingSubmission(participation, false, "def");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 2.0, true, ZonedDateTime.now().minusMinutes(1));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        database.addFeedbackToResult(feedback, submission.getFirstResult());
        database.addSubmission(participation, submission);
        database.addSubmission(participation, submission2);
        var exercises = exerciseRepository.findAllExercisesByCourseId(course1.getId()).stream().filter(exercise -> exercise instanceof ModelingExercise).toList();
        database.createPlagiarismCaseForUserForExercise(programmingExercise, database.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);
        database.createPlagiarismCaseForUserForExercise(exercises.get(0), database.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.POINT_DEDUCTION);
        database.createPlagiarismCaseForUserForExercise(exercises.get(1), database.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.WARNING);
        database.addResultToSubmission(submission2, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));

        // add communication and messaging data
        database.addMessageWithReplyAndReactionInGroupChatOfCourseForUser(userLogin, course1, "group chat");
        database.addMessageInChannelOfCourseForUser(userLogin, course1, "channel");
        database.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(userLogin, course1, "one-to-one-chat");

        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), anyString(), anyBoolean());
    }

    private Exam prepareExamDataForDataExportCreation(String courseShortName) throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var course = database.createCourseWithCustomStudentUserGroupWithExamAndExerciseGroupAndExercises(userForExport, TEST_PREFIX + "student", courseShortName, true, true);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        var exam = course.getExams().iterator().next();
        exam = examRepository.findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(exam.getId()).get();
        var studentExam = database.addStudentExamWithUser(exam, userForExport);
        studentExam = database.addExercisesWithParticipationsAndSubmissionsToStudentExam(exam, studentExam, validModel,
                programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        studentExam = studentExamRepository.findWithExercisesParticipationsSubmissionsResultsAndFeedbacksByUserIdAndExamId(userForExport.getId(), exam.getId()).get();

        var submission = studentExam.getExercises().get(0).getStudentParticipations().iterator().next().getSubmissions().iterator().next();
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        database.addFeedbackToResult(feedback, submission.getFirstResult());
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(any(), anyString(), anyBoolean());
        return exam;
    }

    private void prepareExamDataWithResultPublicationDateInTheFuture() throws Exception {
        var exam = prepareExamDataForDataExportCreation("examNoResults");
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        examRepository.save(exam);
    }

    private void assertNoResultsFile(Path exerciseDirPath) {
        assertThat(exerciseDirPath).isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result"));
    }

    private void assertCorrectContentForExercise(Path exerciseDirPath, boolean courseExercise) {
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
            // modeling exercises in the course have plagiarism cases
            if (courseExercise) {
                assertThat(exerciseDirPath)
                        .isDirectoryContaining(path -> path.getFileName().toString().contains("plagiarism_case") && path.getFileName().toString().endsWith(FILE_FORMAT_CSV));
            }
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

    private Path getCourseOrExamDirectoryPath(Path rootPath, String shortName) throws IOException {
        try (var files = Files.list(rootPath).filter(Files::isDirectory).filter(path -> path.getFileName().toString().contains(shortName))) {
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
    void testDataExportCreationSuccess_containsCorrectExamContent() throws Exception {
        prepareExamDataForDataExportCreation("shortExam");
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var dataExport = request.putWithResponseBody("/api/" + userForExport.getId() + "/data-export", null, DataExport.class, HttpStatus.OK);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExport.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getRequestDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationDate()).isNotNull();
        // extract zip file and check content
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "shortExam");
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, false));

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resultsPublicationDateInTheFuture_noResultsLeaked() throws Exception {
        prepareExamDataWithResultPublicationDateInTheFuture();
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var dataExport = request.putWithResponseBody("/api/" + userForExport.getId() + "/data-export", null, DataExport.class, HttpStatus.OK);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "examNoResults");
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(this::assertNoResultsFile);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDownloadSuccess() throws Exception {
        var userForExport = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        // create an export
        var dataExport = prepareDataExportForDownload();
        dataExport.setUser(userForExport);
        dataExport = dataExportRepository.save(dataExport);
        var dataExportFile = request.getFile("/api/" + userForExport.getId() + "/data-export/" + dataExport.getId(), HttpStatus.OK, new LinkedMultiValueMap<>());
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
        var user1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").get();
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/" + user1.getId() + "/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntBelongToLoggedInUser_forbidden() throws Exception {
        var user2 = userRepository.findOneByLogin(TEST_PREFIX + "student2").get();
        var dataExport = new DataExport();
        dataExport.setDataExportState(DataExportState.EMAIL_SENT);
        dataExport.setUser(user2);
        dataExport = dataExportRepository.save(dataExport);
        request.get("/api/" + user2.getId() + "/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportErrorDuringCreation_internalServerError() throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        when(dataExportService.requestDataExport(userForExport)).thenThrow(new RuntimeException("Error!"));
        request.putWithResponseBody("/api/" + userForExport.getId() + "/data-export", null, DataExport.class, HttpStatus.INTERNAL_SERVER_ERROR);

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
        request.get("/api/" + userForExport.getId() + "/data-export/" + dataExport.getId(), HttpStatus.INTERNAL_SERVER_ERROR, Resource.class);

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
        request.get("/api/" + userForExport.getId() + "/data-export/" + dataExport.getId(), HttpStatus.FORBIDDEN, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportIdNotExistent_notFound() throws Exception {
        var userForExport = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        request.get("/api/" + userForExport.getId() + "/data-export/999999", HttpStatus.NOT_FOUND, Resource.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserIdNotExistent_notFound() throws Exception {
        request.get("/api/" + 999999999 + "/data-export/999999", HttpStatus.NOT_FOUND, Resource.class);

    }
}
