package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
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
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ProgrammingExerciseTestCaseService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.util.TestConstants;

public class ResultServiceIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    ResultService resultService;

    @Autowired
    ProgrammingExerciseTestCaseService programmingExerciseTestCaseService;

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    QuizExerciseRepository quizExerciseRepository;

    @Autowired
    FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @Autowired
    UserRepository userRepo;

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private ModelingExercise modelingExercise;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation studentParticipation;

    private Result result;

    @BeforeEach
    public void reset() {
        database.addUsers(10, 2, 2);
        course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = programmingExerciseRepository.findAll().get(0);
        // This is done to avoid an unproxy issue in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).get();
        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");

        database.addCourseWithOneModelingExercise();
        modelingExercise = modelingExerciseRepository.findAll().get(0);
        modelingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        studentParticipation = database.addParticipationForExercise(modelingExercise, "student2");

        result = ModelFactory.generateResult(true, 200).resultString("Good effort!").participation(programmingExerciseStudentParticipation);
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
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldUpdateTestCasesAndResultScoreFromSolutionParticipationResult() throws Exception {
        // TODO: This should be refactoring into the ModelUtilService.
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.type(SubmissionType.MANUAL).submissionDate(ZonedDateTime.now()).setParticipation(programmingExerciseStudentParticipation);
        programmingSubmission.setCommitHash(TestConstants.COMMIT_HASH_STRING);
        programmingSubmission.setParticipation(programmingExercise.getSolutionParticipation());
        submissionRepository.save(programmingSubmission);

        Set<ProgrammingExerciseTestCase> expectedTestCases = new HashSet<>();
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test1").active(true).weight(1).id(1L));
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test2").active(true).weight(1).id(2L));
        expectedTestCases.add(new ProgrammingExerciseTestCase().exercise(programmingExercise).testName("test4").active(true).weight(1).id(3L));

        final var resultNotification = ModelFactory.generateBambooBuildResult(Constants.ASSIGNMENT_REPO_NAME, List.of("test1", "test2", "test4"), List.of());
        final var optionalResult = resultService.processNewProgrammingExerciseResult(solutionParticipation, resultNotification);

        Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseService.findByExerciseId(programmingExercise.getId());
        assertThat(testCases).isEqualTo(expectedTestCases);
        assertThat(optionalResult).isPresent();
        assertThat(optionalResult.get().getScore()).isEqualTo(100L);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(programmingExerciseStudentParticipation);
        result = database.addFeedbacksToResult(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);

        List<Feedback> feedbacks = request.getList("/api/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);

        request.getList("/api/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation_studentForbidden() throws Exception {
        Result result = database.addResultToParticipation(solutionParticipation);
        result = database.addFeedbacksToResult(result);

        request.getList("/api/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void shouldReturnNotFoundForNonExistingResult() throws Exception {
        Result result = database.addResultToParticipation(solutionParticipation);
        result = database.addFeedbacksToResult(result);

        request.getList("/api/results/" + 66 + "/details", HttpStatus.NOT_FOUND, Feedback.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void programmingExerciseManualResultUpdate_noManualReviewsAllowed_forbidden() throws Exception {
        request.post("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void programmingExerciseManualResultNew_noManualReviewsAllowed_forbidden() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        result.setSubmission(programmingSubmission);

        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1", roles = "INSTRUCTOR")
    public void programmingExerciseManualResultNew_noManualReviewsWithoutSubmission_badRequest() throws Exception {
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @MethodSource("setResultRatedPermutations")
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void setProgrammingExerciseResultRated(boolean shouldBeRated, ZonedDateTime buildAndTestAfterDueDate, SubmissionType submissionType, ZonedDateTime dueDate) {

        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(submissionType).submitted(true)
                .submissionDate(ZonedDateTime.now());
        database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");
        Result result = database.addResultToParticipation(programmingExerciseStudentParticipation, programmingSubmission);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExercise.setDueDate(dueDate);
        programmingExerciseRepository.save(programmingExercise);

        result.setRatedIfNotExceeded(programmingExercise.getDueDate(), programmingSubmission);
        assertThat(result.isRated() == shouldBeRated).isTrue();
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult() throws Exception {
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);
        result.setParticipation(participation);

        Result response = request.postWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class);
        assertThat(response.getResultString()).isEqualTo(result.getResultString());
        assertThat(response.getSubmission()).isNotNull();
        assertThat(response.getParticipation()).isEqualTo(result.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(result.getFeedbacks().size());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_manualResultsNotAllowed() throws Exception {
        var participation = setParticipationForProgrammingExercise(AssessmentType.AUTOMATIC);
        result.setParticipation(participation);

        request.postWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_resultExists() throws Exception {
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);
        result.setParticipation(participation);
        result = resultRepository.save(result);

        request.postWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_resultPropertyMissing() throws Exception {
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);
        Result result = new Result();

        // Result string is missing
        request.postWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);

        // Result score is missing
        result.setResultString("Good work here");
        request.postWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);

        // Feedbacks have empty text
        result.setScore(100L);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        result.setFeedbacks(feedbacks);
        request.postWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateManualProgrammingExerciseResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);

        result.setParticipation(participation);
        result = resultRepository.save(result);
        result.setSubmission(programmingSubmission);

        // Remove feedbacks, change text and score.
        result.setFeedbacks(result.getFeedbacks().subList(0, 1));
        result.setResultString("Changed text");
        result.setScore(77L);

        Result response = request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.OK);
        assertThat(response.getResultString()).isEqualTo(result.getResultString());
        assertThat(response.getSubmission()).isEqualTo(result.getSubmission());
        assertThat(response.getParticipation()).isEqualTo(result.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(result.getFeedbacks().size());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateManualProgrammingExerciseResult_newResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);

        result.setParticipation(participation);
        result.setSubmission(programmingSubmission);

        // Remove feedbacks, change text and score.
        result.setFeedbacks(result.getFeedbacks().subList(0, 1));
        result.setResultString("Changed text");
        result.setScore(77L);

        Result response = request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.CREATED);
        assertThat(response.getResultString()).isEqualTo(result.getResultString());
        assertThat(response.getParticipation()).isEqualTo(result.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(result.getFeedbacks().size());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForProgrammingExercise() throws Exception {
        var now = ZonedDateTime.now();

        for (int i = 1; i <= 10; i++) {
            ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
            programmingSubmission.submitted(true);
            programmingSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(programmingExercise, programmingSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(programmingSubmission, AssessmentType.AUTOMATIC, null, 10L, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(programmingSubmission, AssessmentType.AUTOMATIC, null, 20L, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + programmingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
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
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 10L, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, 20L, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + quizExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForModelingExercise() throws Exception {
        var now = ZonedDateTime.now();
        for (int i = 1; i <= 10; i++) {
            ModelingSubmission modelingSubmission = new ModelingSubmission();
            modelingSubmission.model("Text");
            modelingSubmission.submitted(true);
            modelingSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(modelingExercise, modelingSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(modelingSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10L, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(modelingSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"), 20L, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + modelingExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
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
                database.addResultToSubmission(textSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10L, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"), 20L, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + textExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetResultsForFileUploadExercise() throws Exception {
        var now = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), "pdf", course);
        course.addExercises(fileUploadExercise);
        fileUploadExerciseRepository.save(fileUploadExercise);

        for (int i = 1; i <= 10; i++) {
            FileUploadSubmission fileUploadSubmission = new FileUploadSubmission();
            fileUploadSubmission.submitted(true);
            fileUploadSubmission.submissionDate(now.minusHours(3));
            database.addSubmission(fileUploadExercise, fileUploadSubmission, "student" + i);
            if (i % 3 == 0) {
                database.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 10L, true);
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(fileUploadSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"), 20L, true);
            }
        }

        List<Result> results = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/results", HttpStatus.OK, Result.class);
        assertThat(results).hasSize(5);
        // TODO: check additional values
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResult() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);
        Result returnedResult = request.get("/api/results/" + result.getId(), HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull();
        assertThat(returnedResult).isEqualTo(result);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getResult_asStudent() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        request.get("/api/results/" + result.getId(), HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getLatestResultWithFeedbacks() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result.setCompletionDate(ZonedDateTime.now().minusHours(10));
        Result latestResult = database.addResultToParticipation(studentParticipation);
        latestResult.setCompletionDate(ZonedDateTime.now());
        result = database.addFeedbacksToResult(result);
        latestResult = database.addFeedbacksToResult(latestResult);
        Result returnedResult = request.get("/api/participations/" + studentParticipation.getId() + "/latest-result", HttpStatus.OK, Result.class);
        assertThat(returnedResult).isNotNull();
        assertThat(returnedResult).isEqualTo(latestResult);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getLatestResultWithFeedbacks_asStudent() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);
        request.get("/api/participations/" + studentParticipation.getId() + "/latest-result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void deleteResult() throws Exception {
        Result result = database.addResultToParticipation(studentParticipation);
        result = database.addFeedbacksToResult(result);
        request.delete("/api/results/" + result.getId(), HttpStatus.OK);
        assertThat(resultRepository.existsById(result.getId())).isFalse();
        request.delete("api/results/" + result.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResultForSubmission() throws Exception {
        var now = ZonedDateTime.now();
        TextExercise textExercise = ModelFactory.generateTextExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), course);
        course.addExercises(textExercise);
        textExerciseRepository.save(textExercise);
        TextSubmission textSubmission = new TextSubmission();
        database.addSubmission(textExercise, textSubmission, "student1");
        Result result = database.addResultToSubmission(textSubmission);
        Result returnedResult = request.get("/api/results/submission/" + textSubmission.getId(), HttpStatus.OK, Result.class);
        assertThat(returnedResult).isEqualTo(result);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createExampleResult() throws Exception {
        var modelingSubmission = database.addSubmission(modelingExercise, new ModelingSubmission(), "student1");
        var exampleSubmission = ModelFactory.generateExampleSubmission(modelingSubmission, modelingExercise, false);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        modelingSubmission.setExampleSubmission(true);
        submissionRepository.save(modelingSubmission);
        request.postWithResponseBody("/api/submissions/" + modelingSubmission.getId() + "/example-result", exampleSubmission, Result.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_quizExercise() throws Exception {
        var now = ZonedDateTime.now();
        var quizExercise = ModelFactory.generateQuizExercise(now.minusDays(1), now.minusHours(2), course);
        course.addExercises(quizExercise);
        quizExerciseRepository.save(quizExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_studentNotInTheCourse() throws Exception {
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=student11", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_dueDateNotPassed() throws Exception {
        modelingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        Result result = new Result().rated(false);
        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createResultForExternalSubmission_resultExists() throws Exception {
        var now = ZonedDateTime.now();
        var modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course);
        course.addExercises(modelingExercise);
        modelingExerciseRepository.save(modelingExercise);
        var participation = database.addParticipationForExercise(modelingExercise, "student1");
        var result = database.addResultToParticipation(participation);
        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    private ProgrammingExerciseStudentParticipation setParticipationForProgrammingExercise(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setAssessmentType(assessmentType);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        return database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
    }
}
