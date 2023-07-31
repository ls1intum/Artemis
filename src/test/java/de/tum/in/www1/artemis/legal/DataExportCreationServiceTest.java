package de.tum.in.www1.artemis.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.apollon.ApollonRequestMockProvider;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DataExportState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.post.ConversationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.service.DataExportCreationService;
import de.tum.in.www1.artemis.service.connectors.apollon.ApollonConversionService;
import de.tum.in.www1.artemis.user.UserUtilService;
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
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private DataExportCreationService dataExportCreationService;

    @Autowired
    private ApollonRequestMockProvider apollonRequestMockProvider;

    @Autowired
    @Qualifier("apollonRestTemplate")
    private RestTemplate restTemplate;

    @Autowired
    private ApollonConversionService apollonConversionService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @BeforeEach
    void initTestCase() throws IOException {
        userUtilService.addUsers(TEST_PREFIX, 2, 5, 0, 1);
        userUtilService.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 2, 5, 0, 1);

        apollonConversionService.setRestTemplate(restTemplate);

        apollonRequestMockProvider.enableMockingOfRequests();

        // mock apollon conversion 8 times, because the last test includes 8 modeling exercises, because each test adds modeling exercises
        for (int i = 0; i < 8; i++) {
            mockApollonConversion();
        }
    }

    private void mockApollonConversion() throws IOException {
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
    void testDataExportCreationSuccess_containsCorrectCourseContent() throws Exception {
        boolean assessmentDueDateInTheFuture = false;
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, "short");
        createCommunicationData(TEST_PREFIX + "student1", course);
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getCreatedDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationFinishedDate()).isNotNull();
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
        assertCommunicationDataCsvFile(courseDirPath);
        getExerciseDirectoryPaths(courseDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, true, assessmentDueDateInTheFuture));

    }

    private void assertCommunicationDataCsvFile(Path courseDirPath) {
        assertThat(courseDirPath).isDirectoryContaining(path -> "messages_posts_reactions.csv".equals(path.getFileName().toString()));
    }

    private Course prepareCourseDataForDataExportCreation(boolean assessmentDueDateInTheFuture, String courseShortName) throws Exception {
        var userLogin = TEST_PREFIX + "student1";
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        Course course1;
        if (assessmentDueDateInTheFuture) {
            course1 = courseUtilService.addCourseWithExercisesAndSubmissionsWithAssessmentDueDatesInTheFuture(courseShortName, TEST_PREFIX, "", 4, 2, 1, 1, true, 1, validModel);
        }
        else {
            course1 = courseUtilService.addCourseWithExercisesAndSubmissions(TEST_PREFIX, "", 4, 2, 1, 1, true, 1, validModel);
        }
        quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course1, TEST_PREFIX + "student1", assessmentDueDateInTheFuture);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        ProgrammingExercise programmingExercise;
        if (assessmentDueDateInTheFuture) {
            programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().plusMinutes(1));
        }
        else {
            programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().minusMinutes(1));
        }
        var participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, userLogin,
                programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, false, "abc");
        var submission2 = programmingExerciseUtilService.createProgrammingSubmission(participation, false, "def");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 2.0, true, ZonedDateTime.now().minusMinutes(1));
        participationUtilService.addResultToSubmission(submission2, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        participationUtilService.addFeedbackToResult(feedback, submission.getFirstResult());
        participationUtilService.addSubmission(participation, submission);
        participationUtilService.addSubmission(participation, submission2);
        var modelingExercises = exerciseRepository.findAllExercisesByCourseId(course1.getId()).stream().filter(exercise -> exercise instanceof ModelingExercise).toList();
        createPlagiarismData(userLogin, programmingExercise, modelingExercises);
        // Mock student repo
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(eq(participation.getVcsRepositoryUrl()), anyString(), anyBoolean());
        return course1;
    }

    private void createCommunicationData(String userLogin, Course course1) {
        conversationUtilService.addMessageWithReplyAndReactionInGroupChatOfCourseForUser(userLogin, course1, "group chat");
        conversationUtilService.addMessageInChannelOfCourseForUser(userLogin, course1, "channel");
        conversationUtilService.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(userLogin, course1, "one-to-one-chat");
    }

    private void createPlagiarismData(String userLogin, ProgrammingExercise programmingExercise, List<Exercise> exercises) {
        exerciseUtilService.createPlagiarismCaseForUserForExercise(programmingExercise, userUtilService.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);
        exerciseUtilService.createPlagiarismCaseForUserForExercise(exercises.get(0), userUtilService.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.POINT_DEDUCTION);
        exerciseUtilService.createPlagiarismCaseForUserForExercise(exercises.get(1), userUtilService.getUserByLogin(userLogin), TEST_PREFIX, PlagiarismVerdict.WARNING);
    }

    private Exam prepareExamDataForDataExportCreation(String courseShortName) throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        var userForExport = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = courseUtilService.createCourseWithCustomStudentUserGroupWithExamAndExerciseGroupAndExercises(userForExport, TEST_PREFIX + "student", courseShortName, true,
                true);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        var exam = course.getExams().iterator().next();
        exam = examRepository.findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(exam.getId()).get();
        var studentExam = examUtilService.addStudentExamWithUser(exam, userForExport);
        examUtilService.addExercisesWithParticipationsAndSubmissionsToStudentExam(exam, studentExam, validModel, programmingExerciseTestService.studentRepo.localRepoFile.toURI());
        Set<StudentExam> studentExams = studentExamRepository.findAllWithExercisesParticipationsSubmissionsResultsAndFeedbacksByUserId(userForExport.getId());
        var submission = studentExams.iterator().next().getExercises().get(0).getStudentParticipations().iterator().next().getSubmissions().iterator().next();
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        participationUtilService.addFeedbackToResult(feedback, submission.getFirstResult());
        Repository studentRepository = gitService.getExistingCheckedOutRepositoryByLocalPath(programmingExerciseTestService.studentRepo.localRepoFile.toPath(), null);
        doReturn(studentRepository).when(gitService).getOrCheckoutRepository(any(), anyString(), anyBoolean());
        return exam;
    }

    private Exam prepareExamDataWithResultPublicationDateInTheFuture() throws Exception {
        var exam = prepareExamDataForDataExportCreation("examNoResults");
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        return examRepository.save(exam);
    }

    private void addOnlyReactionToPostInCourse(Course course) {
        // add a reaction in a course to a post where no other communication data exists
        var loginUser2 = TEST_PREFIX + "student2";
        conversationUtilService.addMessageInChannelOfCourseForUser(loginUser2, course, "student 2 message");
        var posts = postRepository.findPostsByAuthorIdAndCourseId(userUtilService.getUserByLogin(loginUser2).getId(), course.getId());
        conversationUtilService.addReactionForUserToPost(TEST_PREFIX + "student1", posts.get(0));
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
            // programming course exercise has a plagiarism case
            if (courseExercise) {
                assertThat(exerciseDirPath)
                        .isDirectoryContaining(path -> path.getFileName().toString().contains("plagiarism_case") && path.getFileName().toString().endsWith(FILE_FORMAT_CSV));
            }
        }
        if (exerciseDirPath.toString().contains("Modeling")) {
            // model as pdf file
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith(FILE_FORMAT_PDF));
            // modeling exercises in the course have plagiarism cases
            if (courseExercise) {
                assertThat(exerciseDirPath)
                        .isDirectoryContaining(path -> path.getFileName().toString().contains("plagiarism_case") && path.getFileName().toString().endsWith(FILE_FORMAT_CSV));
            }
        }
        if (exerciseDirPath.toString().contains("Text")) {
            // submission text txt file
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("_text" + FILE_FORMAT_TXT));
        }
        if (exerciseDirPath.toString().contains("quiz")) {
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("short_answer_questions_answers" + FILE_FORMAT_TXT))
                    .isDirectoryContaining(path -> path.getFileName().toString().endsWith("multiple_choice_questions_answers" + FILE_FORMAT_TXT))
                    .isDirectoryContaining(path -> path.getFileName().toString().contains("dragAndDropQuestion") && path.getFileName().toString().endsWith(FILE_FORMAT_PDF));
        }
        boolean notQuizOrProgramming = !exerciseDirPath.toString().contains("quiz") && !exerciseDirPath.toString().contains("Programming");
        if (notQuizOrProgramming && courseExercise && !assessmentDueDateInTheFuture) {
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().contains("complaint"));
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
        var exam = prepareExamDataForDataExportCreation("exam");
        addOnlyAnswerPostReactionInCourse(exam.getCourse());
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getCreatedBy()).isNotNull();
        assertThat(dataExportFromDb.getCreationFinishedDate()).isNotNull();
        // extract zip file and check content
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "exam");
        assertCommunicationDataCsvFile(courseDirPath);
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, false, false));

    }

    private void addOnlyAnswerPostReactionInCourse(Course course) {
        var loginUser2 = TEST_PREFIX + "student2";
        conversationUtilService.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(loginUser2, course, "student 2 message");
        var answerPosts = answerPostRepository.findAnswerPostsByAuthorId(userUtilService.getUserByLogin(loginUser2).getId());
        conversationUtilService.addReactionForUserToAnswerPost(TEST_PREFIX + "student1", answerPosts.get(0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resultsPublicationDateInTheFuture_noResultsLeaked() throws Exception {
        var exam = prepareExamDataWithResultPublicationDateInTheFuture();
        addOnlyReactionToPostInCourse(exam.getCourse());
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "examNoResults");
        assertCommunicationDataCsvFile(courseDirPath);
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(this::assertNoResultsFile);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportDoesntLeakResultsIfAssessmentDueDateInTheFuture() throws Exception {
        boolean assessmentDueDateInTheFuture = true;
        var courseShortName = "future";
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, courseShortName);
        addOnlyAnswerPostInCourse(course);
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, courseShortName);
        assertCommunicationDataCsvFile(courseDirPath);
        getExerciseDirectoryPaths(courseDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, true, assessmentDueDateInTheFuture));
    }

    private void addOnlyAnswerPostInCourse(Course course) {
        var loginUser2 = TEST_PREFIX + "student2";
        conversationUtilService.addMessageInChannelOfCourseForUser(loginUser2, course, "message student2");
        var posts = postRepository.findPostsByAuthorIdAndCourseId(userUtilService.getUserByLogin(loginUser2).getId(), course.getId());
        conversationUtilService.addThreadReplyWithReactionForUserToPost(TEST_PREFIX + "student1", posts.get(0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationError_handlesErrorAndInformsUserAndAdmin() {
        var dataExport = initDataExport();
        Exception exception = new RuntimeException("error");
        doThrow(exception).when(fileService).scheduleForDirectoryDeletion(any(Path.class), anyLong());
        doNothing().when(mailService).sendDataExportFailedEmailToAdmin(any(), any(), any());
        doNothing().when(singleUserNotificationService).notifyUserAboutDataExportCreation(any(DataExport.class));
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.FAILED);
        verify(singleUserNotificationService).notifyUserAboutDataExportFailure(any(DataExport.class));
        verify(mailService).sendDataExportFailedEmailToAdmin(any(User.class), eq(dataExportFromDb), eq(exception));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportSuccess_informsUser() {
        var dataExport = initDataExport();
        doNothing().when(singleUserNotificationService).notifyUserAboutDataExportCreation(any(DataExport.class));
        dataExportCreationService.createDataExport(dataExport);
        verify(singleUserNotificationService).notifyUserAboutDataExportCreation(any(DataExport.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportContainsDataAboutCourseStudentUnenrolled() throws Exception {
        boolean assessmentDueDateInTheFuture = true;
        var courseShortName = "unenrolled";
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, courseShortName);
        conversationUtilService.addOneMessageForUserInCourse(TEST_PREFIX + "student1", course, "only one post");
        var dataExport = initDataExport();
        // by setting the course groups to a different value we simulate unenrollment because the user is no longer part of the user group and hence, the course.
        courseUtilService.updateCourseGroups("abc", course, "");
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Path extractedZipDirPath = Path.of(dataExportFromDb.getFilePath().substring(0, dataExportFromDb.getFilePath().length() - 4));
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, courseShortName);
        getExerciseDirectoryPaths(courseDirPath).forEach(exercise -> assertCorrectContentForExercise(exercise, true, assessmentDueDateInTheFuture));
    }

    private DataExport initDataExport() {
        DataExport dataExport = new DataExport();
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setDataExportState(DataExportState.REQUESTED);
        dataExport.setFilePath("path");
        dataExport = dataExportRepository.save(dataExport);
        return dataExport;
    }
}
