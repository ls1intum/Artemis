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
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;

class DataExportCreationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "dataexportcreation";

    private static final String FILE_FORMAT_TXT = ".txt";

    private static final String FILE_FORMAT_PDF = ".pdf";

    private static final String FILE_FORMAT_ZIP = ".zip";

    private static final String FILE_FORMAT_CSV = ".csv";

    @Value("${artemis.repo-download-clone-path}")
    private Path repoDownloadClonePath;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @SpyBean
    private ExerciseRepository exerciseRepository;

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataExportCreationService dataExportCreationService;

    @MockBean
    private ApollonConversionService apollonConversionService;

    @SpyBean
    private CourseRepository courseRepository;

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
        boolean assessmentDueDateInTheFuture = false;
        prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture);
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
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
        getExerciseDirectoryPaths(courseDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, true, assessmentDueDateInTheFuture));

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntLeakResultsIfAssessmentDueDateInTheFuture() throws Exception {
        boolean assessmentDueDateInTheFuture = true;
        prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture);
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "future");
        getExerciseDirectoryPaths(courseDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, true, assessmentDueDateInTheFuture));
    }

    private void prepareCourseDataForDataExportCreation(boolean assessmentDueDateInTheFuture) throws Exception {
        var userLogin = TEST_PREFIX + "student1";
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        Course course1;
        if (assessmentDueDateInTheFuture) {
            course1 = database.addCourseWithExercisesAndSubmissionsWithAssessmentDueDatesInTheFuture("future", TEST_PREFIX, "", 4, 2, 1, 1, false, 1, validModel);
        }
        else {
            course1 = database.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 4, 2, 1, 1, false, 1, validModel);
        }
        database.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course1, TEST_PREFIX + "student1", assessmentDueDateInTheFuture);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        ProgrammingExercise programmingExercise;
        if (assessmentDueDateInTheFuture) {
            programmingExercise = database.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().plusMinutes(1));
        }
        else {
            programmingExercise = database.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().minusMinutes(1));
        }
        var participation = database.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, userLogin,
                programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        var submission = database.createProgrammingSubmission(participation, false, "abc");
        var submission2 = database.createProgrammingSubmission(participation, false, "def");
        database.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 2.0, true, ZonedDateTime.now().minusMinutes(1));
        database.addResultToSubmission(submission2, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        database.addFeedbackToResult(feedback, submission.getFirstResult());
        database.addSubmission(participation, submission);
        database.addSubmission(participation, submission2);
        var exercises = exerciseRepository.findAllExercisesByCourseId(course1.getId()).stream().filter(exercise -> exercise instanceof ModelingExercise).toList();
        createPlagiarismData(userLogin, programmingExercise, exercises);
        createCommunicationData(userLogin, course1);

        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), anyString(), anyBoolean());
    }

    private void createCommunicationData(String userLogin, Course course1) {
        database.addMessageWithReplyAndReactionInGroupChatOfCourseForUser(userLogin, course1, "group chat");
        database.addMessageInChannelOfCourseForUser(userLogin, course1, "channel");
        database.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(userLogin, course1, "one-to-one-chat");
    }

    private void createPlagiarismData(String userLogin, ProgrammingExercise programmingExercise, List<Exercise> exercises) {
        database.createPlagiarismCaseForUserForExercise(programmingExercise, database.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);
        database.createPlagiarismCaseForUserForExercise(exercises.get(0), database.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.POINT_DEDUCTION);
        database.createPlagiarismCaseForUserForExercise(exercises.get(1), database.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.WARNING);
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
        database.addExercisesWithParticipationsAndSubmissionsToStudentExam(exam, studentExam, validModel, programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        Set<StudentExam> studentExams = studentExamRepository.findAllWithExercisesParticipationsSubmissionsResultsAndFeedbacksByUserIdAndExamId(userForExport.getId(),
                exam.getId());
        var submission = studentExams.iterator().next().getExercises().get(0).getStudentParticipations().iterator().next().getSubmissions().iterator().next();
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
        var exam = prepareExamDataForDataExportCreation("noResultsExam");
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        examRepository.save(exam);
    }

    private void assertNoResultsFile(Path exerciseDirPath) {
        assertThat(exerciseDirPath).isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result"));
    }

    private void assertCorrectContentForExercise(Path exerciseDirPath, boolean courseExercise, boolean assessmentDueDateInTheFuture) {
        Predicate<Path> resultsFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result");
        Predicate<Path> submissionFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("submission");
        assertThat(exerciseDirPath).isDirectoryContaining(submissionFile);
        if (assessmentDueDateInTheFuture) {
            assertThat(exerciseDirPath).isDirectoryNotContaining(resultsFile);
        }
        // quizzes do not have a result file
        if (!exerciseDirPath.toString().contains("quiz") && !assessmentDueDateInTheFuture) {
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
        prepareExamDataForDataExportCreation("exam");
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getRequestDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationDate()).isNotNull();
        // extract zip file and check content
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "exam");
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, false, false));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resultsPublicationDateInTheFuture_noResultsLeaked() throws Exception {
        prepareExamDataWithResultPublicationDateInTheFuture();
        DataExport dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "noResultsExam");
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(this::assertNoResultsFile);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationError_handlesErrorAndInformsUser() {
        var dataExport = initDataExport();
        doThrow(new RuntimeException("error")).when(courseRepository).getAllCoursesWithExamsUserIsMemberOf(anyBoolean(), anySet());
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.FAILED);
        verify(singleUserNotificationService).notifyUserAboutDataExportFailure(any(DataExport.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportSuccess_informsUser() {
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        verify(singleUserNotificationService).notifyUserAboutDataExportCreation(any(DataExport.class));
    }

    private DataExport initDataExport() {
        DataExport dataExport = new DataExport();
        dataExport.setUser(userRepository.findOneByLogin(TEST_PREFIX + "student1").get());
        dataExport.setDataExportState(DataExportState.REQUESTED);
        dataExport.setRequestDate(ZonedDateTime.now());
        dataExport.setFilePath("path");
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;
    }
}
