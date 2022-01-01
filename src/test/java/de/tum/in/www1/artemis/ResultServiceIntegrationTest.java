package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.ResultWithPointsPerGradingCriterionDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class ResultServiceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    private Course course;

    private ProgrammingExercise programmingExercise;

    private ModelingExercise modelingExercise;

    private ModelingExercise examModelingExercise;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation studentParticipation;

    @BeforeEach
    public void reset() {
        database.addUsers(10, 2, 0, 2);
        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = (ProgrammingExercise) course.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).findAny().orElseThrow();
        ProgrammingExercise programmingExerciseWithStaticCodeAnalysis = database.addProgrammingExerciseToCourse(course, true);
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).get();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
        database.addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis, "student1");

        database.addCourseWithOneModelingExercise();
        modelingExercise = modelingExerciseRepository.findAll().get(0);
        modelingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "student2");

        Exam exam = database.addExamWithExerciseGroup(this.course, true);
        this.examModelingExercise = new ModelingExercise();
        this.examModelingExercise.setMaxPoints(100D);
        this.examModelingExercise.setExerciseGroup(exam.getExerciseGroups().get(0));
        this.modelingExerciseRepository.save(this.examModelingExercise);
        this.examRepository.save(exam);

        Result result = ModelFactory.generateResult(true, 200D).resultString("Good effort!").participation(programmingExerciseStudentParticipation);
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Good work here")).collect(Collectors.toList());
        result.setFeedbacks(feedbacks);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testRemoveCIDirectoriesFromPath() {
        // 1. Test that paths not containing the Constant.STUDENT_WORKING_DIRECTORY are not shortened
        String pathWithoutWorkingDir = "Path/Without/StudentWorkingDirectory/Constant";

        var resultNotification1 = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        for (var reports : resultNotification1.getBuild().getJobs().iterator().next().getStaticCodeAnalysisReports()) {
            for (var issue : reports.getIssues()) {
                issue.setFilePath(pathWithoutWorkingDir);
            }
        }
        var staticCodeAnalysisFeedback1 = feedbackRepository
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification1.getBuild().getJobs().get(0).getStaticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback1) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(pathWithoutWorkingDir).isEqualTo(issueJSON.get("filePath"));
        }

        // 2. Test that null or empty paths default to FeedbackService.DEFAULT_FILEPATH
        var resultNotification2 = ModelFactory.generateBambooBuildResultWithStaticCodeAnalysisReport(Constants.ASSIGNMENT_REPO_NAME, List.of("test1"), List.of(),
                ProgrammingLanguage.JAVA);
        var reports2 = resultNotification2.getBuild().getJobs().iterator().next().getStaticCodeAnalysisReports();
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
                .createFeedbackFromStaticCodeAnalysisReports(resultNotification2.getBuild().getJobs().get(0).getStaticCodeAnalysisReports());

        for (var feedback : staticCodeAnalysisFeedback2) {
            JSONObject issueJSON = new JSONObject(feedback.getDetailText());
            assertThat(FeedbackRepository.DEFAULT_FILEPATH).isEqualTo(issueJSON.get("filePath"));
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void shouldReturnTheResultDetailsForAnInstructorWithoutSensitiveInformationFiltered() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        result = database.addVariousVisibilityFeedbackToResults(result);

        // Set programming exercise due date in future.
        database.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().plusHours(10));

        List<Feedback> feedbacks = request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.OK,
                Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);

        request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(null, null, solutionParticipation);
        result = database.addSampleFeedbackToResults(result);
        request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldReturnNotFoundForNonExistingResult() throws Exception {
        Result result = database.addResultToParticipation(null, null, solutionParticipation);
        database.addSampleFeedbackToResults(result);
        request.getList("/api/participations/" + result.getParticipation().getId() + "/results/" + 11667 + "/details", HttpStatus.NOT_FOUND, Feedback.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void shouldReturnBadrequestForNonMatchingParticipationId() throws Exception {
        Result result = database.addResultToParticipation(null, null, solutionParticipation);
        database.addSampleFeedbackToResults(result);
        request.getList("/api/participations/" + 1337 + "/results/" + result.getId() + "/details", HttpStatus.BAD_REQUEST, Feedback.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("setResultRatedPermutations")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void setProgrammingExerciseResultRated(boolean shouldBeRated, ZonedDateTime buildAndTestAfterDueDate, SubmissionType submissionType, ZonedDateTime dueDate) {

        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(submissionType).submitted(true)
                .submissionDate(ZonedDateTime.now());
        database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");
        Result result = database.addResultToParticipation(programmingExerciseStudentParticipation, programmingSubmission);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExercise.setDueDate(dueDate);
        programmingExerciseRepository.save(programmingExercise);

        result.setRatedIfNotExceeded(programmingExercise.getDueDate(), programmingSubmission);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForQuizExercise() throws Exception {
        var now = ZonedDateTime.now();

        QuizExercise quizExercise = database.createQuiz(course, now.minusMinutes(5), now.minusMinutes(2));
        quizExerciseRepository.save(quizExercise);

        for (int i = 1; i <= 10; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();
            quizSubmission.setScoreInPoints(2.0);
            quizSubmission.submitted(true);
            quizSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(quizExercise, quizSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + quizExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForModelingExercise() throws Exception {
        var now = ZonedDateTime.now();
        for (int i = 1; i <= 10; i++) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.model("Text");
            modelingSubmission.submitted(true);
            modelingSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(modelingExercise, modelingSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(modelingSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"), 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + modelingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForTextExercise() throws Exception {
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        course.addExercises(textExercise);
        textExerciseRepository.save(textExercise);

        for (int i = 1; i <= 10; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(textExercise, textSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"), 20D, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + textExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();
        List<Result> results = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsWithPointsForFileUploadExerciseNoGradingCriteria() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();

        List<Result> results = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results", HttpStatus.OK, Result.class);
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results-with-points-per-criterion",
                HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(5);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::getResult).collect(Collectors.toList());
        assertThat(resultWithPoints2).containsExactlyElementsOf(results);

        // the exercise has no grading criteria -> empty points map in every resultWithPoints
        for (final var resultWithPoints : resultsWithPoints) {
            assertThat(resultWithPoints.getPointsPerCriterion()).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsWithPointsForFileUploadExerciseWithGradingCriteria() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();
        addFeedbacksWithGradingCriteriaToExercise(fileUploadExercise);

        List<Result> results = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results", HttpStatus.OK, Result.class);
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results-with-points-per-criterion",
                HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(5);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::getResult).collect(Collectors.toList());
        assertThat(resultWithPoints2).containsExactlyElementsOf(results);

        final GradingCriterion criterion1 = getGradingCriterionByTitle(fileUploadExercise, "test title");
        final GradingCriterion criterion2 = getGradingCriterionByTitle(fileUploadExercise, "test title2");

        for (final var resultWithPoints : resultsWithPoints) {
            final Map<Long, Double> points = resultWithPoints.getPointsPerCriterion();
            if (resultWithPoints.getResult().getScore() == 10.0) {
                // feedback without criterion (1.1 points) is considered in the total points calculation
                assertThat(resultWithPoints.getTotalPoints()).isEqualTo(6.1);
                // two feedbacks of the same criterion -> credits should be summed up in one entry of the map
                assertThat(points).hasSize(1);
                assertThat(points).containsEntry(criterion1.getId(), 5.0);
            }
            else {
                assertThat(resultWithPoints.getTotalPoints()).isEqualTo(14);
                // two feedbacks of different criteria -> map should contain two entries
                assertThat(resultWithPoints.getPointsPerCriterion()).hasSize(2);
                assertThat(points).containsEntry(criterion1.getId(), 1.0);
                assertThat(points).containsEntry(criterion2.getId(), 3.0);
            }
        }
    }

    private FileUploadExercise setupFileUploadExerciseWithResults() {
        var now = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), "pdf", course);
        course.addExercises(fileUploadExercise);
        fileUploadExerciseRepository.save(fileUploadExercise);

        for (int i = 1; i <= 10; i++) {
            FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
            fileUploadSubmission.submitted(true);
            fileUploadSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(fileUploadExercise, fileUploadSubmission, "student" + i);
            fileUploadExercise.addParticipation((StudentParticipation) fileUploadSubmission.getParticipation());

            if (i % 3 == 0) {
                database.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10D, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 20D, true);
            }
            submissionRepository.save(fileUploadSubmission);
        }

        fileUploadExerciseRepository.save(fileUploadExercise);

        return fileUploadExercise;
    }

    private void addFeedbacksWithGradingCriteriaToExercise(FileUploadExercise fileUploadExercise) {
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(fileUploadExercise);
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
                    database.addFeedbackToResult(feedback1, result);

                    final Feedback feedback2 = new Feedback().credits(3.0);
                    feedback2.setGradingInstruction(instruction1b);
                    feedbackRepository.save(feedback2);
                    database.addFeedbackToResult(feedback2, result);

                    // one feedback without grading instruction should be included in total score calculation
                    final Feedback feedback3 = new Feedback().credits(1.111);
                    feedbackRepository.save(feedback3);
                    database.addFeedbackToResult(feedback3, result);
                }
                else {
                    final Feedback feedback1 = new Feedback().credits(1.0);
                    feedback1.setGradingInstruction(instruction1a);
                    feedbackRepository.save(feedback1);
                    database.addFeedbackToResult(feedback1, result);

                    final Feedback feedback2 = new Feedback().credits(3.0);
                    feedback2.setGradingInstruction(instruction2);
                    feedbackRepository.save(feedback2);
                    database.addFeedbackToResult(feedback2, result);

                    final Feedback feedback3 = new Feedback().credits(10.0);
                    feedbackRepository.save(feedback3);
                    database.addFeedbackToResult(feedback3, result);
                }
            }
        }
    }

    private GradingCriterion getGradingCriterionByTitle(Exercise exercise, String title) {
        return exercise.getGradingCriteria().stream().filter(crit -> title.equals(crit.getTitle())).findFirst().get();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getResult() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        Result returnedResult = request.get("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull();
        assertThat(returnedResult).isEqualTo(result);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getResult_asStudent() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        request.get("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForExamExercise() throws Exception {
        setupExamModelingExerciseWithResults();
        List<Result> results = request.getList("/api/exercises/" + this.examModelingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetResultsForExamExercise_asStudent() throws Exception {
        setupExamModelingExerciseWithResults();
        request.getList("/api/exercises/" + this.examModelingExercise.getId() + "/results", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsWithPointsForExamExercise() throws Exception {
        setupExamModelingExerciseWithResults();
        List<Result> results = request.getList("/api/exercises/" + this.examModelingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request
                .getList("/api/exercises/" + this.examModelingExercise.getId() + "/results-with-points-per-criterion", HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(5);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::getResult).collect(Collectors.toList());
        assertThat(resultWithPoints2).containsExactlyElementsOf(results);

        // the exercise has no grading criteria -> empty points map in every resultWithPoints
        for (final var resultWithPoints : resultsWithPoints) {
            assertThat(resultWithPoints.getPointsPerCriterion()).isEmpty();
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetResultsWithPointsForExamExercise_asTutor() throws Exception {
        setupExamModelingExerciseWithResults();
        request.getList("/api/exercises/" + this.examModelingExercise.getId() + "/results-with-points-per-criterion", HttpStatus.FORBIDDEN, Result.class);
    }

    private void setupExamModelingExerciseWithResults() {
        var now = ZonedDateTime.now();
        for (int i = 1; i <= 5; i++) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.model("Testingsubmission");
            modelingSubmission.submitted(true);
            modelingSubmission.submissionDate(now.minusHours(2));
            database.addSubmission(this.examModelingExercise, modelingSubmission, "student" + i);
            database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 12D, true);
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
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacks() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result.setCompletionDate(ZonedDateTime.now().minusHours(10));
        Result latestResult = database.addResultToParticipation(null, null, studentParticipation);
        latestResult.setCompletionDate(ZonedDateTime.now());
        database.addSampleFeedbackToResults(result);
        latestResult = database.addSampleFeedbackToResults(latestResult);
        Result returnedResult = request.get("/api/participations/" + studentParticipation.getId() + "/latest-result", HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull();
        assertThat(returnedResult).isEqualTo(latestResult);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLatestResultWithFeedbacks_asStudent() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        database.addSampleFeedbackToResults(result);
        request.get("/api/participations/" + studentParticipation.getId() + "/latest-result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteResult() throws Exception {
        assertThrows(EntityNotFoundException.class, () -> resultRepository.findByIdWithEagerSubmissionAndFeedbackElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> resultRepository.findByIdElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> resultRepository.findByIdWithEagerFeedbacksElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> resultRepository.findFirstWithFeedbacksByParticipationIdOrderByCompletionDateDescElseThrow(Long.MAX_VALUE));

        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        request.delete("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.OK);
        assertThat(resultRepository.existsById(result.getId())).isFalse();
        request.delete("api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void deleteResult_wrongParticipationId() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        long randomId = 1653;
        request.delete("/api/participations/" + randomId + "/results/" + result.getId(), HttpStatus.BAD_REQUEST);
        assertThat(resultRepository.existsById(result.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void deleteResultStudent() throws Exception {
        Result result = database.addResultToParticipation(null, null, studentParticipation);
        result = database.addSampleFeedbackToResults(result);
        request.delete("/api/participations/" + studentParticipation.getId() + "/results/" + result.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExampleResult() throws Exception {
        var modelingSubmission = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        var exampleSubmission = ModelFactory.generateExampleSubmission(modelingSubmission, modelingExercise, false);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        modelingSubmission.setExampleSubmission(true);
        submissionRepository.save(modelingSubmission);
        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions/" + modelingSubmission.getId() + "/example-results", exampleSubmission,
                Result.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExampleResult_wrongExerciseId() throws Exception {
        var modelingSubmission = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        var exampleSubmission = ModelFactory.generateExampleSubmission(modelingSubmission, modelingExercise, false);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        modelingSubmission.setExampleSubmission(true);
        submissionRepository.save(modelingSubmission);
        long randomId = 1874;
        request.postWithResponseBody("/api/exercises/" + randomId + "/example-submissions/" + modelingSubmission.getId() + "/example-results", exampleSubmission, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createExampleResult_notExampleSubmission() throws Exception {
        var modelingSubmission = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        var exampleSubmission = ModelFactory.generateExampleSubmission(modelingSubmission, modelingExercise, false);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        modelingSubmission.setExampleSubmission(false);
        submissionRepository.save(modelingSubmission);

        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions/" + modelingSubmission.getId() + "/example-results", exampleSubmission,
                Result.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission() throws Exception {
        Result result = new Result().rated(false);
        var createdResult = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_wrongExerciseId() throws Exception {
        Result result = new Result().rated(false);
        long randomId = 2145;
        var createdResult = request.postWithResponseBody("/api/exercises/" + randomId + "/external-submission-results", result, Result.class, HttpStatus.BAD_REQUEST);
        assertThat(createdResult).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_programmingExercise() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);
        var studentLogin = "student1";
        User user = userRepository.findOneByLogin(studentLogin).orElseThrow();
        mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user), true, HttpStatus.CREATED);
        final var repositorySlug = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), Set.of(user));
        Result result = new Result().rated(false);
        programmingExercise.setDueDate(ZonedDateTime.now().minusMinutes(5));
        programmingExerciseRepository.save(programmingExercise);
        var createdResult = request.postWithResponseBody(externalResultPath(programmingExercise.getId(), studentLogin), result, Result.class, HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_quizExercise() throws Exception {
        var now = ZonedDateTime.now();
        var quizExercise = ModelFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), course);
        course.addExercises(quizExercise);
        quizExerciseRepository.save(quizExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(quizExercise.getId(), "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_studentNotInTheCourse() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), "student11"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmissionExam() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + this.examModelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    private String externalResultPath(long exerciseId, String studentLogin) {
        return "/api/exercises/" + exerciseId + "/external-submission-results?studentLogin=" + studentLogin;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_dueDateNotPassed() throws Exception {
        modelingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_resultExists() throws Exception {
        var now = ZonedDateTime.now();
        var modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course);
        course.addExercises(modelingExercise);
        modelingExerciseRepository.save(modelingExercise);
        var participation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        var result = database.addResultToParticipation(null, null, participation);
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAssessmentCountByCorrectionRound() {
        // exercise
        TextExercise textExercise = new TextExercise();
        textExerciseRepository.save(textExercise);

        // participation
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(textExercise);
        studentParticipationRepository.save(studentParticipation);

        // submission
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setParticipation(studentParticipation);
        textSubmission.setSubmitted(true);
        textSubmission.setText("abc");
        textSubmission = submissionRepository.save(textSubmission);

        // result 1
        var result1 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), studentParticipation, "text result string 1", "instructor1", new ArrayList<>());
        result1.setRated(true);
        result1 = database.addFeedbackToResults(result1);
        result1.setSubmission(textSubmission);

        // result 2
        var result2 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), studentParticipation, "text result string 2", "tutor1", new ArrayList<>());
        result2.setRated(true);
        result2 = database.addFeedbackToResults(result2);
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAssessmentCountByCorrectionRoundForProgrammingExercise() {
        // exercise
        Course course = database.createCourse();
        ProgrammingExercise programmingExercise = database.addProgrammingExerciseToCourse(course, false);
        programmingExercise.setDueDate(null);
        programmingExerciseRepository.save(programmingExercise);

        // participation
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setExercise(programmingExercise);
        programmingExerciseStudentParticipationRepository.save(programmingExerciseStudentParticipation);

        // submission
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(programmingExerciseStudentParticipation);
        programmingSubmission.setSubmitted(true);
        programmingSubmission.setBuildArtifact(true);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // result 1
        Result r1 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation, "text result string 1", "instructor1",
                new ArrayList<>());
        r1.setRated(true);
        r1 = database.addFeedbackToResults(r1);
        r1.setSubmission(programmingSubmission);

        // result 2
        Result r2 = database.addResultToParticipation(AssessmentType.MANUAL, ZonedDateTime.now(), programmingExerciseStudentParticipation, "text result string 2", "tutor1",
                new ArrayList<>());
        r2.setRated(true);
        r2 = database.addFeedbackToResults(r2);
        r2.setSubmission(programmingSubmission);

        programmingSubmission.addResult(r1);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        programmingSubmission.addResult(r2);
        submissionRepository.save(programmingSubmission);

        var assessments = resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(programmingExercise, 2);
        assertThat(assessments[0].inTime()).isEqualTo(1);    // correction round 1
        assertThat(assessments[1].inTime()).isEqualTo(1);    // correction round 2
    }
}
