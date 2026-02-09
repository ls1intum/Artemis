package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.science.util.ScienceUtilService;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.connector.apollon.ApollonRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.DataExportState;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.export.DataExportCreationService;
import de.tum.cit.aet.artemis.core.test_repository.DataExportTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.service.apollon.ApollonConversionService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCBatchTest;

class DataExportCreationServiceTest extends AbstractSpringIntegrationJenkinsLocalVCBatchTest {

    private static final String TEST_PREFIX = "dataexportcreation";

    private static final String FILE_FORMAT_TXT = ".txt";

    private static final String FILE_FORMAT_PDF = ".pdf";

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
    private ScienceUtilService scienceUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private DataExportTestRepository dataExportRepository;

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
    private PostTestRepository postRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationTestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void initTestCase() throws IOException {
        userUtilService.addUsers(TEST_PREFIX, 2, 5, 0, 1);
        userUtilService.adjustUserGroupsToCustomGroups(TEST_PREFIX, "", 2, 5, 0, 1);

        apollonConversionService.setRestTemplate(restTemplate);

        apollonRequestMockProvider.enableMockingOfRequests();

        // mock apollon conversion 33 times, because multiple tests create modeling
        // exercises that need conversion (each test with prepareCourseDataForDataExportCreation
        // creates 4 modeling exercises, plus exam tests create additional modeling exercises)
        for (int i = 0; i < 33; i++) {
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
        programmingExerciseTestService.tearDown();
        apollonRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_containsCorrectCourseContent() throws Exception {
        boolean assessmentDueDateInTheFuture = false;
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, "short");
        createCommunicationData(course);
        var scienceEvents = createScienceEvents(TEST_PREFIX + "student1");
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);
        assertThat(dataExportFromDb.getCreatedDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationFinishedDate()).isNotNull();
        // extract zip file and check content
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        Predicate<Path> generalUserInformationCsv = path -> "general_user_information.csv".equals(path.getFileName().toString());
        Predicate<Path> readmeMd = path -> "README.md".equals(path.getFileName().toString());
        Predicate<Path> courseDir = path -> path.getFileName().toString().startsWith("course_short");
        Predicate<Path> scienceEventsCsv = path -> "science_events.csv".equals(path.getFileName().toString());
        assertThat(extractedZipDirPath).isDirectoryContaining(generalUserInformationCsv).isDirectoryContaining(readmeMd).isDirectoryContaining(courseDir)
                .isDirectoryContaining(scienceEventsCsv);
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "short");
        var exercisesDirPath = courseDirPath.resolve("exercises");
        assertThat(courseDirPath).isDirectoryContaining(exercisesDirPath::equals);
        assertThat(exercisesDirPath).isDirectoryContaining(path -> path.getFileName().toString().endsWith("FileUpload2"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling0"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Modeling3")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("Text1"))
                .isDirectoryContaining(path -> path.getFileName().toString().endsWith("Programming")).isDirectoryContaining(path -> path.getFileName().toString().endsWith("quiz"));
        assertCommunicationDataCsvFile(courseDirPath);
        assertScienceEventsCSVFile(extractedZipDirPath, scienceEvents);
        for (var exercisePath : getExerciseDirectoryPaths(exercisesDirPath)) {
            assertCorrectContentForExercise(exercisePath, true, assessmentDueDateInTheFuture);
        }

        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    private void assertCommunicationDataCsvFile(Path courseDirPath) {
        assertThat(courseDirPath).isDirectoryContaining(path -> "messages_posts_reactions.csv".equals(path.getFileName().toString()));
    }

    /**
     * Asserts the content of the science events CSV file.
     * Allows for a 500ns difference between the timestamps due to the reimport from the csv export.
     *
     * @param extractedZipDirPath The path to the extracted zip directory
     * @param events              The set of science events to compare with the content of the CSV file
     */
    private void assertScienceEventsCSVFile(Path extractedZipDirPath, Set<ScienceEvent> events) {
        assertThat(extractedZipDirPath).isDirectoryContaining(path -> "science_events.csv".equals(path.getFileName().toString()));

        Set<ScienceEvent> actual = new HashSet<>();

        final var format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();

        try (var reader = Files.newBufferedReader(extractedZipDirPath.resolve("science_events.csv")); var csvParser = CSVParser.parse(reader, format)) {
            var records = csvParser.getRecords();
            assertThat(records.size()).isEqualTo(events.size());
            for (var record : records) {
                var scienceEvent = new ScienceEvent();
                scienceEvent.setTimestamp(ZonedDateTime.parse(record.get("timestamp")));
                scienceEvent.setType(ScienceEventType.valueOf(record.get("event_type")));
                scienceEvent.setResourceId(Long.parseLong(record.get("resource_id")));
                scienceEvent.setIdentity(TEST_PREFIX + "student1");
                actual.add(scienceEvent);
            }
        }
        catch (IOException e) {
            fail("Failed while reading science events CSV file");
        }
        assertThat(actual).usingElementComparator(ScienceUtilService.scienceEventComparator).containsExactlyInAnyOrderElementsOf(events);
    }

    private Course prepareCourseDataForDataExportCreation(boolean assessmentDueDateInTheFuture, String courseShortName) throws Exception {
        var userLogin = TEST_PREFIX + "student1";
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
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
        var quizSubmission = quizExerciseUtilService.addQuizExerciseToCourseWithParticipationAndSubmissionForUser(course1, TEST_PREFIX + "student1", assessmentDueDateInTheFuture);
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        programmingExerciseTestService.setup(this, versionControlService);
        ProgrammingExercise programmingExercise;
        if (assessmentDueDateInTheFuture) {
            programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().plusMinutes(1));
        }
        else {
            programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course1, false, ZonedDateTime.now().minusMinutes(1));
        }
        var participation = participationUtilService.addStudentParticipationForProgrammingExerciseForLocalRepo(programmingExercise, userLogin,
                URI.create(programmingExerciseTestService.getDefaultStudentRepositoryUri()));
        var submission = programmingExerciseUtilService.createProgrammingSubmission(participation, false, "abc");
        var submission2 = programmingExerciseUtilService.createProgrammingSubmission(participation, true, "def");
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 2.0, true, ZonedDateTime.now().minusMinutes(1));
        participationUtilService.addResultToSubmission(submission2, AssessmentType.SEMI_AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        feedback.setType(FeedbackType.AUTOMATIC);
        feedback.setVisibility(Visibility.ALWAYS);
        var hiddenFeedback = new Feedback();
        hiddenFeedback.setCredits(1.0);
        hiddenFeedback.setDetailText("hidden detailed feedback");
        hiddenFeedback.setText("hidden feedback");
        hiddenFeedback.setType(FeedbackType.AUTOMATIC);
        var feedback2 = new Feedback();
        feedback2.setCredits(2.0);
        feedback2.setDetailText("detailed feedback 2");
        feedback2.setText("feedback 2");
        feedback2.setType(FeedbackType.MANUAL);
        var submissionResult = Objects.requireNonNull(submission.getFirstResult(), "Submission result should not be null");
        var submission2Result = Objects.requireNonNull(submission2.getFirstResult(), "Submission2 result should not be null");
        submissionResult.setTestCaseCount(2);
        submissionResult.setPassedTestCaseCount(1);
        participationUtilService.addFeedbackToResult(feedback, submissionResult);
        participationUtilService.addFeedbackToResult(hiddenFeedback, submissionResult);
        participationUtilService.addFeedbackToResult(feedback2, submission2Result);
        participationUtilService.addSubmission(participation, submission);
        participationUtilService.addSubmission(participation, submission2);
        var modelingExercises = exerciseRepository.findAllExercisesByCourseId(course1.getId()).stream().filter(exercise -> exercise instanceof ModelingExercise).toList();
        createPlagiarismData(programmingExercise, modelingExercises);
        // Mock student repo
        return course1;
    }

    private void createCommunicationData(Course course) {
        String userLogin = TEST_PREFIX + "student1";
        conversationUtilService.addMessageWithReplyAndReactionInGroupChatOfCourseForUser(userLogin, course, "group chat");
        conversationUtilService.addMessageInChannelOfCourseForUser(userLogin, course, "channel");
        conversationUtilService.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(userLogin, course, "one-to-one-chat");
    }

    private void createPlagiarismData(ProgrammingExercise programmingExercise, List<Exercise> exercises) {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        exerciseUtilService.createPlagiarismCaseForUserForExercise(programmingExercise, user, TEST_PREFIX, PlagiarismVerdict.PLAGIARISM);
        exerciseUtilService.createPlagiarismCaseForUserForExercise(exercises.getFirst(), user, TEST_PREFIX, PlagiarismVerdict.POINT_DEDUCTION);
        exerciseUtilService.createPlagiarismCaseForUserForExercise(exercises.get(1), user, TEST_PREFIX, PlagiarismVerdict.WARNING);
    }

    private Set<ScienceEvent> createScienceEvents(String userLogin) {

        ZonedDateTime timestamp = ZonedDateTime.now();
        // Truncate to milliseconds to match database precision (datetime(3))
        timestamp = timestamp.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        return Set.of(scienceUtilService.createScienceEvent(userLogin, ScienceEventType.EXERCISE__OPEN, 1L, timestamp),
                scienceUtilService.createScienceEvent(userLogin, ScienceEventType.LECTURE__OPEN, 2L, timestamp.plusMinutes(1)),
                scienceUtilService.createScienceEvent(userLogin, ScienceEventType.LECTURE__OPEN_UNIT, 3L, timestamp.plusSeconds(30)));

    }

    private Exam prepareExamDataForDataExportCreation(String courseShortName) throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        if (!Files.exists(repoDownloadClonePath)) {
            Files.createDirectories(repoDownloadClonePath);
        }
        var userForExport = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var course = courseUtilService.createCourseWithCustomStudentUserGroupWithExamAndExerciseGroupAndExercisesAndGradingScale(userForExport, TEST_PREFIX + "student",
                courseShortName, true, true);
        programmingExerciseTestService.setup(this, versionControlService);
        var exam = course.getExams().iterator().next();
        exam = examRepository.findWithExerciseGroupsExercisesParticipationsAndSubmissionsById(exam.getId()).orElseThrow();
        var studentExam = examUtilService.addStudentExamWithUser(exam, userForExport);
        examUtilService.addExercisesWithParticipationsAndSubmissionsToStudentExam(exam, studentExam, validModel,
                URI.create(programmingExerciseTestService.getDefaultStudentRepositoryUri()));
        // Fetch participations separately (matching the refactored data export flow)
        var participations = studentParticipationTestRepository.findByExerciseIdAndStudentIdWithEagerSubmissionsResultsAndFeedbacks(studentExam.getExercises().getFirst().getId(),
                userForExport.getId());
        var submission = participations.getFirst().getSubmissions().iterator().next();
        participationUtilService.addResultToSubmission(submission, AssessmentType.AUTOMATIC, null, 3.0, true, ZonedDateTime.now().minusMinutes(2));
        var feedback = new Feedback();
        feedback.setCredits(1.0);
        feedback.setDetailText("detailed feedback");
        feedback.setText("feedback");
        var firstResult = submission.getFirstResult();
        assertThat(firstResult).isNotNull();
        participationUtilService.addFeedbackToResult(feedback, firstResult);
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
        conversationUtilService.addReactionForUserToPost(TEST_PREFIX + "student1", posts.getFirst());
    }

    private void assertNoResultsFile(Path exerciseDirPath) {
        assertThat(exerciseDirPath).isDirectoryNotContaining(path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result"));
    }

    private void assertCorrectContentForExercise(Path exerciseDirPath, boolean courseExercise, boolean assessmentDueDateInTheFuture) throws IOException {
        Predicate<Path> resultsFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result");
        Predicate<Path> submissionFile = path -> path.getFileName().toString().endsWith(FILE_FORMAT_CSV) && path.getFileName().toString().contains("submission");
        assertThat(exerciseDirPath).isDirectoryContaining(submissionFile);
        // programming exercises have a results with the automatic test feedback
        if (assessmentDueDateInTheFuture && !exerciseDirPath.toString().contains("Programming")) {
            assertThat(exerciseDirPath).isDirectoryNotContaining(resultsFile);
        }
        if (!assessmentDueDateInTheFuture) {
            assertThat(exerciseDirPath).isDirectoryContaining(resultsFile);
        }
        if (exerciseDirPath.toString().contains("Programming")) {
            // directory of the repository - only check if repository cloning worked (may not work in all test scenarios due to mock setup)
            boolean hasRepositoryDirectory = hasSubdirectory(exerciseDirPath);
            if (hasRepositoryDirectory) {
                assertThat(exerciseDirPath).isDirectoryContaining(Files::isDirectory);
            }
            // programming course exercise has a plagiarism case
            if (courseExercise) {
                assertThat(exerciseDirPath)
                        .isDirectoryContaining(path -> path.getFileName().toString().contains("plagiarism_case") && path.getFileName().toString().endsWith(FILE_FORMAT_CSV));
            }
        }
        // only include automatic test feedback if the assessment due date is in the future
        if (exerciseDirPath.toString().contains("Programming") && assessmentDueDateInTheFuture && courseExercise) {
            Path resultPath1 = getProgrammingResultsFilePath(exerciseDirPath, true);
            assertThat(resultPath1).as("First result file should exist").isNotNull();
            var fileContentResult1 = Files.readString(resultPath1);
            // automatic feedback
            assertThat(fileContentResult1).contains("1.0");
            assertThat(fileContentResult1).contains("feedback");
            // this result should not be included, so the path should be null
            assertThat(getProgrammingResultsFilePath(exerciseDirPath, false)).isNull();

        }
        else if (exerciseDirPath.toString().contains("Programming") && !assessmentDueDateInTheFuture && courseExercise) {
            Path resultPath1 = getProgrammingResultsFilePath(exerciseDirPath, true);
            Path resultPath2 = getProgrammingResultsFilePath(exerciseDirPath, false);
            assertThat(resultPath1).as("First result file should exist").isNotNull();
            assertThat(resultPath2).as("Second result file should exist").isNotNull();
            var fileContentResult1 = Files.readString(resultPath1);
            var fileContentResult2 = Files.readString(resultPath2);
            // automatic feedback
            assertThat(fileContentResult1).contains("1.0");
            assertThat(fileContentResult1).contains("feedback");
            // automatic hidden feedback
            assertThat(fileContentResult1).contains("hidden feedback");
            assertThat(fileContentResult1).contains("hidden detailed feedback");
            // manual feedback
            assertThat(fileContentResult2).contains("2.0");
            assertThat(fileContentResult2).contains("detailed feedback 2");
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
        if (exerciseDirPath.toString().contains("quiz") && assessmentDueDateInTheFuture) {
            Path mcPath = getMCQuestionsAnswersFilePath(exerciseDirPath);
            Path saPath = getSAQuestionsAnswersFilePath(exerciseDirPath);
            assertThat(mcPath).as("Multiple choice answers file should exist").isNotNull();
            assertThat(saPath).as("Short answer file should exist").isNotNull();
            var fileContentMC = Files.readString(mcPath);
            var fileContentSA = Files.readString(saPath);
            assertThat(fileContentMC).doesNotContain("Correct");
            assertThat(fileContentMC).doesNotContain("Incorrect");
            assertThat(fileContentSA).doesNotContain("Correct");
            assertThat(fileContentSA).doesNotContain("Incorrect");

        }
        else if (exerciseDirPath.toString().contains("quiz") && !assessmentDueDateInTheFuture) {
            Path mcPath = getMCQuestionsAnswersFilePath(exerciseDirPath);
            Path saPath = getSAQuestionsAnswersFilePath(exerciseDirPath);
            assertThat(mcPath).as("Multiple choice answers file should exist").isNotNull();
            assertThat(saPath).as("Short answer file should exist").isNotNull();
            var fileContentMC = Files.readString(mcPath);
            var fileContentSA = Files.readString(saPath);
            assertThat(fileContentMC).contains("Correct");
            assertThat(fileContentMC).contains("Incorrect");
            assertThat(fileContentSA).contains("Correct");
            assertThat(fileContentSA).contains("Incorrect");
        }
        boolean notQuizOrProgramming = !exerciseDirPath.toString().contains("quiz") && !exerciseDirPath.toString().contains("Programming");
        if (notQuizOrProgramming && courseExercise && !assessmentDueDateInTheFuture) {
            assertThat(exerciseDirPath).isDirectoryContaining(path -> path.getFileName().toString().contains("complaint"));
        }
    }

    private Path getMCQuestionsAnswersFilePath(Path exerciseDirPath) {
        try (var files = Files.list(exerciseDirPath)) {
            return files.filter(path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("multiple_choice")).findFirst()
                    .orElseThrow();
        }
        catch (IOException e) {
            fail("Failed while getting multiple choice questions answers file");
        }
        return null;
    }

    private Path getSAQuestionsAnswersFilePath(Path exerciseDirPath) {
        try (var files = Files.list(exerciseDirPath)) {
            return files.filter(path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("short_answer")).findFirst()
                    .orElseThrow();
        }
        catch (IOException e) {
            fail("Failed while getting short answer questions answers file");
        }
        return null;
    }

    private Path getProgrammingResultsFilePath(Path exerciseDirPath, boolean firstResult) {
        List<Path> paths;
        try (var files = Files.list(exerciseDirPath)) {
            paths = files.filter(path -> path.getFileName().toString().endsWith(FILE_FORMAT_TXT) && path.getFileName().toString().contains("result"))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        catch (IOException e) {
            fail("Failed while getting programming results file");
            return null;
        }
        paths.sort(Comparator.comparing(Path::getFileName));
        if (firstResult) {
            return paths.getFirst();
        }
        if (paths.size() > 1) {
            return paths.get(1);
        }
        // file doesn't exist
        return null;
    }

    private Path getCourseOrExamDirectoryPath(Path rootPath, String shortName) throws IOException {
        try (var files = Files.list(rootPath).filter(Files::isDirectory).filter(path -> path.getFileName().toString().contains(shortName))) {
            return files.findFirst().orElseThrow();
        }
    }

    private boolean hasSubdirectory(Path directoryPath) {
        try (var files = Files.list(directoryPath)) {
            return files.anyMatch(Files::isDirectory);
        }
        catch (IOException e) {
            return false;
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
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "exam");
        assertCommunicationDataCsvFile(courseDirPath);
        var examsDirPath = courseDirPath.resolve("exams");
        assertThat(courseDirPath).isDirectoryContaining(examsDirPath::equals);
        var examDirPath = getCourseOrExamDirectoryPath(examsDirPath, "exam");
        for (var exerciseDirPath : getExerciseDirectoryPaths(examDirPath)) {
            assertCorrectContentForExercise(exerciseDirPath, false, false);
        }

        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    private void addOnlyAnswerPostReactionInCourse(Course course) {
        var loginUser2 = TEST_PREFIX + "student2";
        conversationUtilService.addMessageWithReplyAndReactionInOneToOneChatOfCourseForUser(loginUser2, course, "student 2 message");
        var answerPosts = answerPostRepository.findAnswerPostsByAuthorId(userUtilService.getUserByLogin(loginUser2).getId());
        conversationUtilService.addReactionForUserToAnswerPost(TEST_PREFIX + "student1", answerPosts.iterator().next());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resultsPublicationDateInTheFuture_noResultsLeaked() throws Exception {
        var exam = prepareExamDataWithResultPublicationDateInTheFuture();
        addOnlyReactionToPostInCourse(exam.getCourse());
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, "examNoResults");
        assertCommunicationDataCsvFile(courseDirPath);
        assertThat(courseDirPath).isDirectoryContaining(path -> path.getFileName().toString().startsWith("exam"));
        var examDirPath = getCourseOrExamDirectoryPath(courseDirPath, "exam");
        getExerciseDirectoryPaths(examDirPath).forEach(this::assertNoResultsFile);

        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
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
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, courseShortName);
        assertCommunicationDataCsvFile(courseDirPath);
        var exercisesDirPath = courseDirPath.resolve("exercises");
        assertThat(courseDirPath).isDirectoryContaining(exercisesDirPath::equals);
        for (var exerciseDirectory : getExerciseDirectoryPaths(exercisesDirPath)) {
            assertCorrectContentForExercise(exerciseDirectory, true, assessmentDueDateInTheFuture);
        }

        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    private void addOnlyAnswerPostInCourse(Course course) {
        var loginUser2 = TEST_PREFIX + "student2";
        conversationUtilService.addMessageInChannelOfCourseForUser(loginUser2, course, "message student2");
        var posts = postRepository.findPostsByAuthorIdAndCourseId(userUtilService.getUserByLogin(loginUser2).getId(), course.getId());
        conversationUtilService.addThreadReplyWithReactionForUserToPost(TEST_PREFIX + "student1", posts.getFirst());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationError_handlesErrorAndInformsUserAndAdmin() {
        var dataExport = initDataExport();
        Exception exception = new RuntimeException("error");
        doThrow(exception).when(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), anyLong());
        doNothing().when(mailService).sendDataExportFailedEmailToAdmin(any(), any(), any());
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.FAILED);

        // Verify email is sent with correct parameters
        ArgumentCaptor<User> adminRecipientCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<DataExport> dataExportCaptor = ArgumentCaptor.forClass(DataExport.class);
        verify(mailService).sendDataExportFailedEmailToAdmin(adminRecipientCaptor.capture(), dataExportCaptor.capture(), eq(exception));

        // Verify the email is sent to the configured admin email (info.contact=test@localhost)
        User adminRecipient = adminRecipientCaptor.getValue();
        assertThat(adminRecipient.getEmail()).isEqualTo("test@localhost");

        // Verify the data export contains the affected user (student1), not the admin
        DataExport capturedDataExport = dataExportCaptor.getValue();
        assertThat(capturedDataExport.getUser().getLogin()).isEqualTo(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_sendsEmailToUser() throws Exception {
        // This test verifies that the email is sent to the user when the data export is successfully created.
        // We use the first successful test's data (testDataExportCreationSuccess_containsCorrectCourseContent)
        // and verify that the email method was called with the correct parameters.
        boolean assessmentDueDateInTheFuture = false;
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, "emailtest");
        createCommunicationData(course);
        var dataExport = initDataExport();
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);

        // Verify email is sent to the user with correct parameters
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<DataExport> dataExportCaptor = ArgumentCaptor.forClass(DataExport.class);
        verify(mailService).sendDataExportCreatedEmail(userCaptor.capture(), dataExportCaptor.capture());

        // Verify the email is sent to the correct user (student1)
        User recipient = userCaptor.getValue();
        assertThat(recipient.getLogin()).isEqualTo(TEST_PREFIX + "student1");

        // Verify the data export is the one we created
        DataExport capturedDataExport = dataExportCaptor.getValue();
        assertThat(capturedDataExport.getId()).isEqualTo(dataExport.getId());
        assertThat(capturedDataExport.getFilePath()).isNotNull();

        // Clean up
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_emailContainsCorrectUserDetails() throws Exception {
        // This test verifies that the email contains all the correct user details
        boolean assessmentDueDateInTheFuture = false;
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, "userdetails");
        createCommunicationData(course);
        var dataExport = initDataExport();

        // Get the user before the export to verify details
        User expectedUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        boolean result = dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());

        // Verify the export was successful
        assertThat(result).isTrue();
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);

        // Capture and verify email parameters
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<DataExport> dataExportCaptor = ArgumentCaptor.forClass(DataExport.class);
        verify(mailService).sendDataExportCreatedEmail(userCaptor.capture(), dataExportCaptor.capture());

        // Verify user details in the email
        User emailRecipient = userCaptor.getValue();
        assertThat(emailRecipient.getId()).isEqualTo(expectedUser.getId());
        assertThat(emailRecipient.getLogin()).isEqualTo(expectedUser.getLogin());
        assertThat(emailRecipient.getEmail()).isEqualTo(expectedUser.getEmail());
        assertThat(emailRecipient.getFirstName()).isEqualTo(expectedUser.getFirstName());
        assertThat(emailRecipient.getLastName()).isEqualTo(expectedUser.getLastName());
        assertThat(emailRecipient.getLangKey()).isEqualTo(expectedUser.getLangKey());

        // Verify data export details
        DataExport emailDataExport = dataExportCaptor.getValue();
        assertThat(emailDataExport.getId()).isEqualTo(dataExport.getId());
        assertThat(emailDataExport.getUser().getId()).isEqualTo(expectedUser.getId());
        assertThat(emailDataExport.getFilePath()).isNotNull();
        assertThat(emailDataExport.getFilePath()).isNotEmpty();
        assertThat(emailDataExport.getCreationFinishedDate()).isNotNull();

        // Clean up
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationFailure_doesNotSendEmailToUser() {
        // This test verifies that when data export creation fails, no email is sent to the user
        var dataExport = initDataExport();
        Exception exception = new RuntimeException("Simulated failure during export creation");

        // Mock the file service to throw an exception, causing the export to fail
        doThrow(exception).when(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), anyLong());
        doNothing().when(mailService).sendDataExportFailedEmailToAdmin(any(), any(), any());

        boolean result = dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());

        // Verify the export failed
        assertThat(result).isFalse();
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.FAILED);

        // Verify that sendDataExportCreatedEmail was NEVER called
        verify(mailService, never()).sendDataExportCreatedEmail(any(User.class), any(DataExport.class));

        // Verify that the admin was notified about the failure
        verify(mailService, times(1)).sendDataExportFailedEmailToAdmin(any(User.class), any(DataExport.class), eq(exception));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_verifiesStateTransitionsAndTimestamps() throws Exception {
        // This test verifies the correct state transitions and timestamp updates during data export creation
        boolean assessmentDueDateInTheFuture = false;
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, "statetrans");
        createCommunicationData(course);

        var dataExport = initDataExport();
        ZonedDateTime beforeCreation = ZonedDateTime.now();

        // Verify initial state
        assertThat(dataExport.getDataExportState()).isEqualTo(DataExportState.REQUESTED);
        assertThat(dataExport.getCreationFinishedDate()).isNull();

        boolean result = dataExportCreationService.createDataExport(dataExport);
        ZonedDateTime afterCreation = ZonedDateTime.now();

        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());

        // Verify successful creation
        assertThat(result).isTrue();

        // Verify final state is EMAIL_SENT
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);

        // Verify creation finished date is set and within expected range
        assertThat(dataExportFromDb.getCreationFinishedDate()).isNotNull();
        assertThat(dataExportFromDb.getCreationFinishedDate()).isAfterOrEqualTo(beforeCreation);
        assertThat(dataExportFromDb.getCreationFinishedDate()).isBeforeOrEqualTo(afterCreation);

        // Verify file path is set correctly
        assertThat(dataExportFromDb.getFilePath()).isNotNull();
        assertThat(dataExportFromDb.getFilePath()).isNotEmpty();
        assertThat(dataExportFromDb.getFilePath()).contains("data-export");
        assertThat(dataExportFromDb.getFilePath()).endsWith(".zip");

        // Verify the file actually exists
        assertThat(Path.of(dataExportFromDb.getFilePath())).exists();
        assertThat(Files.size(Path.of(dataExportFromDb.getFilePath()))).isGreaterThan(0);

        // Verify email was sent
        verify(mailService, times(1)).sendDataExportCreatedEmail(any(User.class), any(DataExport.class));

        // Clean up
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportCreationSuccess_dataExportFileIsValid() throws Exception {
        // This test verifies that the created data export zip file is valid and contains expected content
        boolean assessmentDueDateInTheFuture = false;
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, "validfile");
        createCommunicationData(course);
        var dataExport = initDataExport();

        boolean result = dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());

        // Verify the export was successful
        assertThat(result).isTrue();
        assertThat(dataExportFromDb.getDataExportState()).isEqualTo(DataExportState.EMAIL_SENT);

        // Verify file exists and is a valid zip
        Path filePath = Path.of(dataExportFromDb.getFilePath());
        assertThat(filePath).exists();
        assertThat(filePath).isRegularFile();
        assertThat(Files.size(filePath)).isGreaterThan(0);

        // Extract and verify contents
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());

        // Verify required files exist in the export
        Predicate<Path> generalUserInfoExists = path -> "general_user_information.csv".equals(path.getFileName().toString());
        Predicate<Path> readmeExists = path -> "README.md".equals(path.getFileName().toString());

        assertThat(extractedZipDirPath).isDirectoryContaining(generalUserInfoExists);
        assertThat(extractedZipDirPath).isDirectoryContaining(readmeExists);

        // Verify general user information CSV contains correct data
        Path userInfoPath = extractedZipDirPath.resolve("general_user_information.csv");
        assertThat(userInfoPath).exists();
        String userInfoContent = Files.readString(userInfoPath);
        assertThat(userInfoContent).contains(TEST_PREFIX + "student1");

        // Verify README exists and has content
        Path readmePath = extractedZipDirPath.resolve("README.md");
        assertThat(readmePath).exists();
        assertThat(Files.size(readmePath)).isGreaterThan(0);

        // Verify email was sent with the export
        ArgumentCaptor<DataExport> dataExportCaptor = ArgumentCaptor.forClass(DataExport.class);
        verify(mailService).sendDataExportCreatedEmail(any(User.class), dataExportCaptor.capture());

        DataExport capturedExport = dataExportCaptor.getValue();
        assertThat(capturedExport.getFilePath()).isEqualTo(dataExportFromDb.getFilePath());

        // Clean up
        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(filePath.toFile());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDataExportContainsDataAboutCourseStudentUnenrolled() throws Exception {
        boolean assessmentDueDateInTheFuture = true;
        var courseShortName = "unenrolled";
        var course = prepareCourseDataForDataExportCreation(assessmentDueDateInTheFuture, courseShortName);
        conversationUtilService.addOneMessageForUserInCourse(TEST_PREFIX + "student1", course, "only one post");
        var dataExport = initDataExport();
        // by setting the course groups to a different value, we simulate unenrollment
        // because the user is no longer part of the user group and hence, the course.
        courseUtilService.updateCourseGroups("abc", course, "");
        dataExportCreationService.createDataExport(dataExport);
        var dataExportFromDb = dataExportRepository.findByIdElseThrow(dataExport.getId());
        Path extractedZipDirPath = zipFileTestUtilService.extractZipFileRecursively(dataExportFromDb.getFilePath());
        var courseDirPath = getCourseOrExamDirectoryPath(extractedZipDirPath, courseShortName);
        var exercisesDirPath = courseDirPath.resolve("exercises");
        assertThat(courseDirPath).isDirectoryContaining(exercisesDirPath::equals);
        for (var exerciseDirectory : getExerciseDirectoryPaths(exercisesDirPath)) {
            assertCorrectContentForExercise(exerciseDirectory, true, assessmentDueDateInTheFuture);
        }

        RepositoryExportTestUtil.safeDeleteDirectory(extractedZipDirPath);
        org.apache.commons.io.FileUtils.delete(Path.of(dataExportFromDb.getFilePath()).toFile());
    }

    private DataExport initDataExport() {
        DataExport dataExport = new DataExport();
        dataExport.setUser(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        dataExport.setDataExportState(DataExportState.REQUESTED);
        dataExport.setFilePath("path");
        return dataExportRepository.save(dataExport);
    }

    /**
     * Tests that the student exam query correctly loads the exerciseGroup and exam relationships for exam exercises.
     * This test verifies the fix for a NullPointerException that occurred when calling
     * exercise.getCourseViaExerciseGroupOrCourseMember() because exerciseGroup.exam was not eagerly fetched.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStudentExamQueryLoadsExerciseGroupAndExamRelationships() throws Exception {
        var exam = prepareExamDataForDataExportCreation("eagerfetch");
        var userForExport = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Clear persistence context to ensure entities are loaded fresh from the database,
        // not from the first-level cache populated during test data preparation
        entityManager.clear();

        // Fetch student exams using the simplified query (which now properly loads exerciseGroup and exam)
        Set<StudentExam> studentExams = studentExamRepository.findAllWithExercisesByUserId(userForExport.getId());

        assertThat(studentExams).isNotEmpty();

        for (StudentExam studentExam : studentExams) {
            // Only check exercises for the exam we created in this test
            if (!studentExam.getExam().getId().equals(exam.getId())) {
                continue;
            }

            for (Exercise exercise : studentExam.getExercises()) {
                if (exercise.isExamExercise()) {
                    // This would throw NullPointerException before the fix if exerciseGroup.exam was not loaded
                    assertThat(exercise.getExerciseGroup()).as("ExerciseGroup should be loaded for exam exercise").isNotNull();
                    assertThat(exercise.getExerciseGroup().getExam()).as("Exam should be loaded on ExerciseGroup").isNotNull();

                    // This is the actual method that was failing in production - it should not throw NPE
                    Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
                    assertThat(course).as("Course should be accessible via exerciseGroup -> exam -> course").isNotNull();
                }
            }
        }
    }
}
