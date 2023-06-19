package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.fileuploadexercise.FileUploadExerciseFactory;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseFactory;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlRepositoryPermission;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ResultWithPointsPerGradingCriterionDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ResultServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "resultserviceintegration";

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private ModelingExercise modelingExercise;

    private ModelingExercise examModelingExercise;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation studentParticipation;

    private final int numberOfStudents = 4;

    @BeforeEach
    void setupTest() {
        userUtilService.addUsers(TEST_PREFIX, numberOfStudents, 2, 0, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        ProgrammingExercise programmingExerciseWithStaticCodeAnalysis = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, true);
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis, TEST_PREFIX + "student1");

        Course secondCourse = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        modelingExercise = exerciseUtilService.getFirstExerciseWithType(secondCourse, ModelingExercise.class);
        modelingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        studentParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student2");

        Exam exam = examUtilService.addExamWithExerciseGroup(this.course, true);
        this.examModelingExercise = new ModelingExercise();
        this.examModelingExercise.setMaxPoints(100D);
        this.examModelingExercise.setExerciseGroup(exam.getExerciseGroups().get(0));
        this.modelingExerciseRepository.save(this.examModelingExercise);
        this.examRepository.save(exam);

        Result result = ParticipationFactory.generateResult(true, 200D).participation(programmingExerciseStudentParticipation);
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        result.setFeedbacks(feedbacks);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRemoveCIDirectoriesFromPath() {
        // 1. Test that paths not containing the Constant.STUDENT_WORKING_DIRECTORY are not shortened
        String pathWithoutWorkingDir = "Path/Without/StudentWorkingDirectory/Constant";

        var resultNotification1 = ProgrammingExerciseFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        for (var reports : resultNotification1.getBuild().jobs().iterator().next().staticCodeAnalysisReports()) {
            for (var issue : reports.getIssues()) {
                issue.setFilePath(pathWithoutWorkingDir);
            }
        }
        var staticCodeAnalysisFeedback1 = feedbackRepository.createFeedbackFromStaticCodeAnalysisReports(resultNotification1.getBuild().jobs().get(0).staticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback1) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(pathWithoutWorkingDir).isEqualTo(issueJSON.get("filePath"));
        }

        // 2. Test that null or empty paths default to FeedbackRepository.DEFAULT_FILEPATH
        var resultNotification2 = ProgrammingExerciseFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        var reports2 = resultNotification2.getBuild().jobs().iterator().next().staticCodeAnalysisReports();
        for (int i = 0; i < reports2.size(); i++) {
            var report = reports2.get(i);
            // Set null or empty String to test both
            if (i % 2 == 0) {
                for (var issue : report.getIssues()) {
                    issue.setFilePath("");
                }
            }
            else {
                for (var issue : report.getIssues()) {
                    issue.setFilePath(null);
                }
            }
        }
        final var staticCodeAnalysisFeedback2 = feedbackRepository
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification2.getBuild().jobs().get(0).staticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback2) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(issueJSON.get("filePath")).isEqualTo("notAvailable");
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnTheResultDetailsForAnInstructorWithoutSensitiveInformationFiltered() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        // Set programming exercise due date in future.
        exerciseUtilService.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().plusHours(10));

        List<Feedback> feedbacks = request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.OK,
                Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnTheResultDetailsForAStudentParticipation_studentForbidden() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);

        request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation_studentForbidden() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, solutionParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);
        request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotFoundForNonExistingResult() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, solutionParticipation);
        participationUtilService.addSampleFeedbackToResults(result);
        request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + 11667 + "/details", HttpStatus.NOT_FOUND, Feedback.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestForNonMatchingParticipationId() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, solutionParticipation);
        participationUtilService.addSampleFeedbackToResults(result);
        request.getList("/api/participations/" + 1337 + "/results/" + result.getId() + "/details", HttpStatus.BAD_REQUEST, Feedback.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("setResultRatedPermutations")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void setProgrammingExerciseResultRated(boolean shouldBeRated, ZonedDateTime buildAndTestAfterDueDate, SubmissionType submissionType, ZonedDateTime dueDate) {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(submissionType).submitted(true)
                .submissionDate(ZonedDateTime.now());
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");
        Result result = participationUtilService.addResultToParticipation(programmingExerciseStudentParticipation, programmingSubmission);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExercise.setDueDate(dueDate);
        programmingExerciseRepository.save(programmingExercise);

        result.setRatedIfNotExceeded(programmingExercise.getDueDate(), programmingSubmission, programmingSubmission.getParticipation());
        assertThat(result.isRated()).isSameAs(shouldBeRated);
    }

    private static Stream<Arguments> setResultRatedPermutations() {
        ZonedDateTime dateInFuture = ZonedDateTime.now().plusHours(1);
        ZonedDateTime dateInPast = ZonedDateTime.now().minusHours(1);
        return Stream.of(
                // The due date has not passed, normal student submission => rated result.
                Arguments.of(true, null, SubmissionType.MANUAL, dateInFuture),
                // The due date is not set, normal student submission => rated result.
                Arguments.of(true, null, SubmissionType.MANUAL, null),
                // The due date has passed, normal student submission => unrated result.
                Arguments.of(false, null, SubmissionType.MANUAL, dateInPast),
                // The result was generated by an instructor => rated result.
                Arguments.of(true, null, SubmissionType.INSTRUCTOR, dateInPast),
                // The result was generated by test update => rated result.
                Arguments.of(true, null, SubmissionType.TEST, dateInPast),
                // The build and test date has passed, the due date has passed, the result is generated by a test update => rated result.
                Arguments.of(true, dateInPast, SubmissionType.TEST, dateInPast),
                // The build and test date has passed, the due date has passed, the result is generated by an instructor => rated result.
                Arguments.of(true, dateInPast, SubmissionType.INSTRUCTOR, dateInPast),
                // The build and test date has not passed, due date has not passed, normal student submission => rated result.
                Arguments.of(true, dateInFuture, SubmissionType.MANUAL, dateInFuture),
                // The build and test date has not passed, due date has passed, normal student submission => unrated result.
                Arguments.of(false, dateInFuture, SubmissionType.MANUAL, dateInPast));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetResultsWithPointsForFileUploadExerciseNoGradingCriteria() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();

        List<Result> results = fileUploadExercise.getStudentParticipations().stream().flatMap(participation -> participation.getSubmissions().stream())
                .flatMap(submission -> submission.getResults().stream()).toList();
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results-with-points-per-criterion",
                HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(numberOfStudents / 2);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::result).toList();
        assertThat(resultWithPoints2).containsExactlyInAnyOrderElementsOf(results);

        // the exercise has no grading criteria -> empty points map in every resultWithPoints
        for (final var resultWithPoints : resultsWithPoints) {
            assertThat(resultWithPoints.pointsPerCriterion()).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetResultsWithPointsForFileUploadExerciseWithGradingCriteria() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();
        addFeedbacksWithGradingCriteriaToExercise(fileUploadExercise);

        List<Result> results = fileUploadExercise.getStudentParticipations().stream().flatMap(participation -> participation.getSubmissions().stream())
                .flatMap(submission -> submission.getResults().stream()).toList();
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results-with-points-per-criterion",
                HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(numberOfStudents / 2);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::result).toList();
        assertThat(resultWithPoints2).containsExactlyInAnyOrderElementsOf(results);

        final GradingCriterion criterion1 = getGradingCriterionByTitle(fileUploadExercise, "test title");
        final GradingCriterion criterion2 = getGradingCriterionByTitle(fileUploadExercise, "test title2");

        for (final var resultWithPoints : resultsWithPoints) {
            final Map<Long, Double> points = resultWithPoints.pointsPerCriterion();
            if (resultWithPoints.result().getScore() == 10.0) {
                // feedback without criterion (1.1 points) is considered in the total points calculation
                assertThat(resultWithPoints.totalPoints()).isEqualTo(6.1);
                // two feedbacks of the same criterion -> credits should be summed up in one entry of the map
                assertThat(points).hasSize(1);
                assertThat(points).containsEntry(criterion1.getId(), 5.0);
            }
            else {
                assertThat(resultWithPoints.totalPoints()).isEqualTo(14);
                // two feedbacks of different criteria -> map should contain two entries
                assertThat(resultWithPoints.pointsPerCriterion()).hasSize(2);
                assertThat(points).containsEntry(criterion1.getId(), 1.0);
                assertThat(points).containsEntry(criterion2.getId(), 3.0);
            }
        }
    }

    private FileUploadExercise setupFileUploadExerciseWithResults() {
        var now = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), "pdf", course);
        course.addExercises(fileUploadExercise);
        fileUploadExerciseRepository.save(fileUploadExercise);

        for (int i = 1; i <= numberOfStudents; i++) {
            FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
            fileUploadSubmission.submitted(true);
            fileUploadSubmission.submissionDate(now.minusHours(3));
            participationUtilService.addSubmission(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student" + i);
            fileUploadExercise.addParticipation((StudentParticipation) fileUploadSubmission.getParticipation());

            if (i % 3 == 0) {
                participationUtilService.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                participationUtilService.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), 20D, true);
            }
            submissionRepository.save(fileUploadSubmission);
        }

        fileUploadExerciseRepository.save(fileUploadExercise);

        return fileUploadExercise;
    }

    private void addFeedbacksWithGradingCriteriaToExercise(FileUploadExercise fileUploadExercise) {
        List<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        fileUploadExerciseRepository.save(fileUploadExercise);

        final GradingCriterion criterion1 = getGradingCriterionByTitle(fileUploadExercise, "test title");
        final GradingCriterion criterion2 = getGradingCriterionByTitle(fileUploadExercise, "test title2");

        final GradingInstruction instruction1a = criterion1.getStructuredGradingInstructions().get(0);
        final GradingInstruction instruction1b = criterion1.getStructuredGradingInstructions().get(1);
        final GradingInstruction instruction2 = criterion2.getStructuredGradingInstructions().get(0);

        for (final var participation : fileUploadExercise.getStudentParticipations()) {
            for (final var result : participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).toList()) {
                if (result.getScore() == 10.0) {
                    final Feedback feedback1 = new Feedback().credits(2.0);
                    feedback1.setGradingInstruction(instruction1a);
                    feedbackRepository.save(feedback1);
                    participationUtilService.addFeedbackToResult(feedback1, result);

                    final Feedback feedback2 = new Feedback().credits(3.0);
                    feedback2.setGradingInstruction(instruction1b);
                    feedbackRepository.save(feedback2);
                    participationUtilService.addFeedbackToResult(feedback2, result);

                    // one feedback without grading instruction should be included in total score calculation
                    final Feedback feedback3 = new Feedback().credits(1.111);
                    feedbackRepository.save(feedback3);
                    participationUtilService.addFeedbackToResult(feedback3, result);
                }
                else {
                    final Feedback feedback1 = new Feedback().credits(1.0);
                    feedback1.setGradingInstruction(instruction1a);
                    feedbackRepository.save(feedback1);
                    participationUtilService.addFeedbackToResult(feedback1, result);

                    final Feedback feedback2 = new Feedback().credits(3.0);
                    feedback2.setGradingInstruction(instruction2);
                    feedbackRepository.save(feedback2);
                    participationUtilService.addFeedbackToResult(feedback2, result);

                    final Feedback feedback3 = new Feedback().credits(10.0);
                    feedbackRepository.save(feedback3);
                    participationUtilService.addFeedbackToResult(feedback3, result);
                }
            }
        }
    }

    private GradingCriterion getGradingCriterionByTitle(Exercise exercise, String title) {
        return exercise.getGradingCriteria().stream().filter(criteria -> title.equals(criteria.getTitle())).findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getResult() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);
        Result returnedResult = request.get("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull().isEqualTo(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getResult_asStudent() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        request.get("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetResultsWithPointsForExamExercise() throws Exception {
        List<Result> results = setupExamModelingExerciseWithResults();
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request
                .getList("/api/exercises/" + this.examModelingExercise.getId() + "/results-with-points-per-criterion", HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(numberOfStudents);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::result).toList();
        assertThat(resultWithPoints2).containsExactlyElementsOf(results);

        // the exercise has no grading criteria -> empty points map in every resultWithPoints
        for (final var resultWithPoints : resultsWithPoints) {
            assertThat(resultWithPoints.pointsPerCriterion()).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetResultsWithPointsForExamExercise_asTutor() throws Exception {
        setupExamModelingExerciseWithResults();
        request.getList("/api/exercises/" + this.examModelingExercise.getId() + "/results-with-points-per-criterion", HttpStatus.FORBIDDEN, Result.class);
    }

    private List<Result> setupExamModelingExerciseWithResults() {
        List<Result> results = new ArrayList<>();
        var now = ZonedDateTime.now();
        for (int i = 1; i <= numberOfStudents; i++) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.model("TestingSubmission");
            modelingSubmission.submitted(true);
            modelingSubmission.submissionDate(now.minusHours(2));
            participationUtilService.addSubmission(this.examModelingExercise, modelingSubmission, TEST_PREFIX + "student" + i);
            Submission submission = participationUtilService.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL,
                    userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), 12D, true);
            results.addAll(submission.getResults());
        }

        // empty participation with submission
        StudentParticipation participation = new StudentParticipation();
        participation.setInitializationDate(ZonedDateTime.now());
        participation.setParticipant(null);
        participation.setExercise(this.examModelingExercise);
        studentParticipationRepository.save(participation);
        ModelingSubmission modelingSubmission = new ModelingSubmission();
        modelingSubmission.model("Text");
        modelingSubmission.submitted(true);
        modelingSubmission.submissionDate(now.minusHours(3));
        participation.addSubmission(modelingSubmission);
        modelingSubmission.setParticipation(participation);
        submissionRepository.save(modelingSubmission);
        studentParticipationRepository.save(participation);

        return results;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteResult() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> resultRepository.findByIdWithEagerSubmissionAndFeedbackElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> resultRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> resultRepository.findByIdWithEagerFeedbacksElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDescElseThrow(Long.MAX_VALUE));

        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);
        request.delete("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.OK);
        assertThat(resultRepository.existsById(result.getId())).isFalse();
        request.delete("api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteResult_wrongParticipationId() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);
        long randomId = 1653;
        request.delete("/api/participations/" + randomId + "/results/" + result.getId(), HttpStatus.BAD_REQUEST);
        assertThat(resultRepository.existsById(result.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteResultStudent() throws Exception {
        Result result = participationUtilService.addResultToParticipation(null, null, studentParticipation);
        result = participationUtilService.addSampleFeedbackToResults(result);
        request.delete("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission() throws Exception {
        Result result = new Result().rated(false);
        var createdResult = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=" + TEST_PREFIX + "student1",
                result, Result.class, HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_wrongExerciseId() throws Exception {
        Result result = new Result().rated(false);
        long randomId = 2145;
        var createdResult = request.postWithResponseBody("/api/exercises/" + randomId + "/external-submission-results", result, Result.class, HttpStatus.BAD_REQUEST);
        assertThat(createdResult).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_programmingExercise() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
        var studentLogin = TEST_PREFIX + "student1";
        User user = userRepository.findOneByLogin(studentLogin).orElseThrow();
        final var repositorySlug = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        bitbucketRequestMockProvider.mockSetStudentRepositoryPermission(repositorySlug, programmingExercise.getProjectKey(), studentLogin,
                VersionControlRepositoryPermission.REPO_READ);
        mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user), true);
        Result result = new Result().rated(false);
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(5));
        programmingExerciseRepository.save(programmingExercise);
        var createdResult = request.postWithResponseBody(externalResultPath(programmingExercise.getId(), studentLogin), result, Result.class, HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void createResultForExternalSubmission_quizExercise(QuizMode quizMode) throws Exception {
        var now = ZonedDateTime.now();
        var quizExercise = QuizExerciseFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), quizMode, course);
        course.addExercises(quizExercise);
        quizExerciseRepository.save(quizExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(quizExercise.getId(), TEST_PREFIX + "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_studentNotInTheCourse() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), TEST_PREFIX + "student11"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmissionExam() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + this.examModelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    private String externalResultPath(long exerciseId, String studentLogin) {
        return "/api/exercises/" + exerciseId + "/external-submission-results?studentLogin=" + studentLogin;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_dueDateNotPassed() throws Exception {
        modelingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), TEST_PREFIX + "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_resultExists() throws Exception {
        var now = ZonedDateTime.now();
        var modelingExercise = ModelingExerciseFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course);
        course.addExercises(modelingExercise);
        modelingExerciseRepository.save(modelingExercise);
        var participation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        var result = participationUtilService.addResultToParticipation(null, null, participation);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), TEST_PREFIX + "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAssessmentCountByCorrectionRound() {
        // exercise
        var now = ZonedDateTime.now();
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.plusHours(2), course);
        textExercise = textExerciseRepository.save(textExercise);

        // participation
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setParticipant(userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        studentParticipation.setExercise(textExercise);
        studentParticipationRepository.save(studentParticipation);

        // submission
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setParticipation(studentParticipation);
        textSubmission.setSubmitted(true);
        textSubmission.setText("abc");
        textSubmission = submissionRepository.save(textSubmission);

        // result 1
        var result1 = participationUtilService.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), studentParticipation, TEST_PREFIX + "instructor1",
                new ArrayList<>());
        result1.setRated(true);
        result1 = participationUtilService.addFeedbackToResults(result1);
        result1.setSubmission(textSubmission);

        // result 2
        var result2 = participationUtilService.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), studentParticipation, TEST_PREFIX + "tutor1",
                new ArrayList<>());
        result2.setRated(true);
        result2 = participationUtilService.addFeedbackToResults(result2);
        result2.setSubmission(textSubmission);

        textSubmission.addResult(result1);
        textSubmission = submissionRepository.save(textSubmission);

        textSubmission.addResult(result2);
        submissionRepository.save(textSubmission);

        var assessments = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(textExercise, 2);
        assertThat(assessments[0].inTime()).isEqualTo(1);    // correction round 1
        assertThat(assessments[1].inTime()).isEqualTo(1);    // correction round 2
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAssessmentCountByCorrectionRoundForProgrammingExercise() {
        // exercise
        Course course = courseUtilService.createCourse();
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);
        programmingExercise.setDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // participation
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setParticipant(userRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        programmingExerciseStudentParticipation.setExercise(programmingExercise);
        programmingExerciseStudentParticipationRepository.save(programmingExerciseStudentParticipation);

        // submission
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(programmingExerciseStudentParticipation);
        programmingSubmission.setSubmitted(true);
        programmingSubmission.setBuildArtifact(true);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // result 1
        Result result1 = participationUtilService.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation,
                TEST_PREFIX + "instructor1", new ArrayList<>());
        result1.setRated(true);
        result1 = participationUtilService.addFeedbackToResults(result1);
        result1.setSubmission(programmingSubmission);

        // result 2
        Result result2 = participationUtilService.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation,
                TEST_PREFIX + "tutor1", new ArrayList<>());
        result2.setRated(true);
        result2 = participationUtilService.addFeedbackToResults(result2);
        result2.setSubmission(programmingSubmission);

        programmingSubmission.addResult(result1);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        programmingSubmission.addResult(result2);
        submissionRepository.save(programmingSubmission);

        var assessments = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(programmingExercise, 2);
        assertThat(assessments[0].inTime()).isEqualTo(1);    // correction round 1
        assertThat(assessments[1].inTime()).isEqualTo(1);    // correction round 2
    }
}
