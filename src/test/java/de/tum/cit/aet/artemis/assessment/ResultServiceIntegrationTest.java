package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackAnalysisResponseDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDetailDTO;
import de.tum.cit.aet.artemis.assessment.dto.ResultWithPointsPerGradingCriterionDTO;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class ResultServiceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "resultserviceintegration";

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private ModelingExercise modelingExercise;

    private ModelingExercise examModelingExercise;

    private SolutionProgrammingExerciseParticipation solutionParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation2;

    private StudentParticipation studentParticipation;

    private final int NUMBER_OF_STUDENTS = 3;

    @BeforeEach
    void setupTest() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 2, 0, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        ProgrammingExercise programmingExerciseWithStaticCodeAnalysis = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, true);
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        solutionParticipation = solutionProgrammingExerciseRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        programmingExerciseStudentParticipation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        var programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseWithStaticCodeAnalysis,
                TEST_PREFIX + "student1");
        Submission programmingSubmission = participationUtilService.addSubmission(programmingExerciseParticipation, new ProgrammingSubmission());
        participationUtilService.addSubmission(programmingExerciseStudentParticipation2, new ProgrammingSubmission());
        Course secondCourse = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        modelingExercise = ExerciseUtilService.getFirstExerciseWithType(secondCourse, ModelingExercise.class);
        modelingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        studentParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student2");
        Submission modelingSubmission = participationUtilService.addSubmission(studentParticipation, new ModelingSubmission());

        Exam exam = examUtilService.addExamWithExerciseGroup(this.course, true);
        this.examModelingExercise = new ModelingExercise();
        this.examModelingExercise.setMaxPoints(100D);
        this.examModelingExercise.setExerciseGroup(exam.getExerciseGroups().getFirst());
        this.modelingExerciseRepository.save(this.examModelingExercise);
        this.examRepository.save(exam);

        Result result = ParticipationFactory.generateResult(true, 200D).submission(modelingSubmission);
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        result.setFeedbacks(feedbacks);
        result.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        Result result2 = ParticipationFactory.generateResult(true, 200D).submission(programmingSubmission);
        List<Feedback> feedbacks2 = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        result2.setFeedbacks(feedbacks2);
        result2.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void shouldReturnTheResultDetailsForAnInstructorWithoutSensitiveInformationFiltered() throws Exception {
        Result result = participationUtilService.addResultToSubmission(null, null, studentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addSampleFeedbackToResults(result);
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        // Set programming exercise due date in future.
        exerciseUtilService.updateExerciseDueDate(studentParticipation.getExercise().getId(), ZonedDateTime.now().plusHours(10));

        List<Feedback> feedbacks = request.getList(
                "/api/assessment/participations/" + result.getSubmission().getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.OK, Feedback.class);

        assertThat(feedbacks).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnTheResultDetailsForAStudentParticipation_studentForbidden() throws Exception {
        Result result = participationUtilService.addResultToSubmission(null, null, studentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addSampleFeedbackToResults(result);

        request.getList("/api/assessment/participations/" + result.getSubmission().getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN,
                Feedback.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnTheResultDetailsForAProgrammingExerciseStudentParticipation_studentForbidden() throws Exception {
        Submission submission = participationUtilService.addSubmission(solutionParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(null, null, submission);
        result = participationUtilService.addSampleFeedbackToResults(result);
        request.getList("/api/assessment/participations/" + result.getSubmission().getParticipation().getId() + "/results/" + result.getId() + "/details", HttpStatus.FORBIDDEN,
                Feedback.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotFoundForNonExistingResult() throws Exception {
        Submission submission = participationUtilService.addSubmission(solutionParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(null, null, submission);
        participationUtilService.addSampleFeedbackToResults(result);
        request.getList(
                "/api/assessment/participations/" + result.getSubmission().getParticipation().getId() + "/results/" + UUID.randomUUID().getMostSignificantBits() + "/details",
                HttpStatus.NOT_FOUND, Feedback.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestForNonMatchingParticipationId() throws Exception {
        Submission submission = participationUtilService.addSubmission(solutionParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(null, null, submission);
        participationUtilService.addSampleFeedbackToResults(result);
        request.getList("/api/assessment/participations/" + 1337 + "/results/" + result.getId() + "/details", HttpStatus.BAD_REQUEST, Feedback.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("setResultRatedPermutations")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void setProgrammingExerciseResultRated(boolean shouldBeRated, ZonedDateTime buildAndTestAfterDueDate, SubmissionType submissionType, ZonedDateTime dueDate,
            ZonedDateTime submissionDate) {
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestAfterDueDate);
        programmingExercise.setDueDate(dueDate);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExerciseStudentParticipation.setProgrammingExercise(programmingExercise);

        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(submissionType).submitted(true)
                .submissionDate(submissionDate);
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");
        Result result = participationUtilService.addResultToSubmission(programmingExerciseStudentParticipation, programmingSubmission);

        result.setRatedIfNotAfterDueDate();
        assertThat(result.isRated()).isSameAs(shouldBeRated);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestRunsNonRated() {
        programmingExerciseStudentParticipation.setPracticeMode(true);
        programmingExerciseStudentParticipation = programmingExerciseStudentParticipationRepository.save(programmingExerciseStudentParticipation);

        var submission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").type(SubmissionType.MANUAL).submitted(true);
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        Result result = participationUtilService.addResultToSubmission(programmingExerciseStudentParticipation, submission);

        result.setRatedIfNotAfterDueDate();
        // The participation is a test run -> should not be rated
        assertThat(result.isRated()).isFalse();
    }

    private static Stream<Arguments> setResultRatedPermutations() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime dateInFuture = now.plusHours(1);
        ZonedDateTime dateInPast = now.minusHours(1);
        ZonedDateTime dateClosePast = now.minusSeconds(5);
        ZonedDateTime dateWithinGracePeriod = now.minusSeconds(1);
        return Stream.of(
                // The result was created shortly after the due date within the grace period => rated
                Arguments.of(true, dateInPast, SubmissionType.MANUAL, dateWithinGracePeriod, now),
                // The result was created shortly after the due date and grace period => unrated
                Arguments.of(false, dateInPast, SubmissionType.MANUAL, dateClosePast, now),
                // The due date has not passed, normal student submission => rated result.
                Arguments.of(true, null, SubmissionType.MANUAL, dateInFuture, now),
                // The due date is not set, normal student submission => rated result.
                Arguments.of(true, null, SubmissionType.MANUAL, null, now),
                // The due date has passed, normal student submission => unrated result.
                Arguments.of(false, null, SubmissionType.MANUAL, dateInPast, now),
                // The result was generated by an instructor => rated result.
                Arguments.of(true, null, SubmissionType.INSTRUCTOR, dateInPast, now),
                // The result was generated by test update => rated result.
                Arguments.of(true, null, SubmissionType.TEST, dateInPast, now),
                // The build and test date has passed, the due date has passed, the result is generated by a test update => rated result.
                Arguments.of(true, dateInPast, SubmissionType.TEST, dateInPast, now),
                // The build and test date has passed, the due date has passed, the result is generated by an instructor => rated result.
                Arguments.of(true, dateInPast, SubmissionType.INSTRUCTOR, dateInPast, now),
                // The build and test date has not passed, due date has not passed, normal student submission => rated result.
                Arguments.of(true, dateInFuture, SubmissionType.MANUAL, dateInFuture, now),
                // The build and test date has not passed, due date has passed, normal student submission => unrated result.
                Arguments.of(false, dateInFuture, SubmissionType.MANUAL, dateInPast, now));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetResultsWithPointsForFileUploadExerciseNoGradingCriteria() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();

        List<Result> results = fileUploadExercise.getStudentParticipations().stream().flatMap(participation -> participation.getSubmissions().stream())
                .flatMap(submission -> submission.getResults().stream()).toList();
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList(
                "/api/assessment/exercises/" + fileUploadExercise.getId() + "/results-with-points-per-criterion", HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(NUMBER_OF_STUDENTS / 2);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::result).toList();
        assertThat(resultWithPoints2).containsExactlyInAnyOrderElementsOf(results);

        // the exercise has no grading criteria -> empty points map in every resultWithPoints
        for (final var resultWithPoints : resultsWithPoints) {
            assertThat(resultWithPoints.pointsPerCriterion()).isNullOrEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetResultsWithPointsForFileUploadExerciseWithGradingCriteria() throws Exception {
        FileUploadExercise fileUploadExercise = setupFileUploadExerciseWithResults();
        addFeedbacksWithGradingCriteriaToExercise(fileUploadExercise);

        List<Result> results = fileUploadExercise.getStudentParticipations().stream().flatMap(participation -> participation.getSubmissions().stream())
                .flatMap(submission -> submission.getResults().stream()).toList();
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList(
                "/api/assessment/exercises/" + fileUploadExercise.getId() + "/results-with-points-per-criterion", HttpStatus.OK, ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(NUMBER_OF_STUDENTS / 2);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::result).toList();
        assertThat(resultWithPoints2).containsExactlyInAnyOrderElementsOf(results);

        final GradingCriterion criterion1 = GradingCriterionUtil.findGradingCriterionByTitle(fileUploadExercise, "test title");
        final GradingCriterion criterion2 = GradingCriterionUtil.findGradingCriterionByTitle(fileUploadExercise, "test title2");

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

        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
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
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        fileUploadExerciseRepository.save(fileUploadExercise);

        final GradingCriterion criterion1 = GradingCriterionUtil.findGradingCriterionByTitle(fileUploadExercise, "test title");
        final GradingCriterion criterion2 = GradingCriterionUtil.findGradingCriterionByTitle(fileUploadExercise, "test title2");

        final List<GradingInstruction> gradingInstructions1 = List.copyOf(criterion1.getStructuredGradingInstructions());
        // as long as credits are equal we can choose any two here
        assertThat(gradingInstructions1).allMatch(instruction -> instruction.getCredits() == 1);
        final GradingInstruction instruction1a = gradingInstructions1.getFirst();
        final GradingInstruction instruction1b = gradingInstructions1.get(1);
        final Set<GradingInstruction> gradingInstructions2 = criterion2.getStructuredGradingInstructions();
        assertThat(gradingInstructions2).hasSize(1);
        final GradingInstruction instruction2 = gradingInstructions2.stream().findFirst().orElseThrow();

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetResultsWithPointsForExamExercise() throws Exception {
        List<Result> results = setupExamModelingExerciseWithResults();
        List<ResultWithPointsPerGradingCriterionDTO> resultsWithPoints = request.getList(
                "/api/assessment/exercises/" + this.examModelingExercise.getId() + "/results-with-points-per-criterion", HttpStatus.OK,
                ResultWithPointsPerGradingCriterionDTO.class);

        // with points should return the same results as the /results endpoint
        assertThat(results).hasSize(NUMBER_OF_STUDENTS);
        assertThat(resultsWithPoints).hasSameSizeAs(results);
        final List<Result> resultWithPoints2 = resultsWithPoints.stream().map(ResultWithPointsPerGradingCriterionDTO::result).toList();
        assertThat(resultWithPoints2).containsExactlyElementsOf(results);

        // the exercise has no grading criteria -> empty points map in every resultWithPoints
        for (final var resultWithPoints : resultsWithPoints) {
            assertThat(resultWithPoints.pointsPerCriterion()).isNullOrEmpty();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetResultsWithPointsForExamExercise_asTutor() throws Exception {
        setupExamModelingExerciseWithResults();
        request.getList("/api/assessment/exercises/" + this.examModelingExercise.getId() + "/results-with-points-per-criterion", HttpStatus.FORBIDDEN, Result.class);
    }

    private List<Result> setupExamModelingExerciseWithResults() {
        List<Result> results = new ArrayList<>();
        var now = ZonedDateTime.now();
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission() throws Exception {
        Result result = new Result().rated(false).exerciseId(modelingExercise.getId());
        var createdResult = request.postWithResponseBody(
                "/api/assessment/exercises/" + modelingExercise.getId() + "/external-submission-results?studentLogin=" + TEST_PREFIX + "student1", result, Result.class,
                HttpStatus.CREATED);
        assertThat(createdResult).isNotNull();
        assertThat(createdResult.isRated()).isFalse();
        // TODO: we should assert that the result has been created with all corresponding objects in the database
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_wrongExerciseId() throws Exception {
        Result result = new Result().rated(false).exerciseId(modelingExercise.getId());
        long randomId = 2145;
        var createdResult = request.postWithResponseBody("/api/assessment/exercises/" + randomId + "/external-submission-results", result, Result.class, HttpStatus.BAD_REQUEST);
        assertThat(createdResult).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_programmingExercise() throws Exception {
        var studentLogin = TEST_PREFIX + "student1";
        User user = userTestRepository.findOneByLogin(studentLogin).orElseThrow();
        mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user), true);
        Result result = new Result().rated(false).exerciseId(programmingExercise.getId());
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
        Result result = new Result().rated(false).exerciseId(quizExercise.getId());
        request.postWithResponseBody(externalResultPath(quizExercise.getId(), TEST_PREFIX + "student1"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_studentNotInTheCourse() throws Exception {
        Result result = new Result().rated(false).exerciseId(modelingExercise.getId());
        request.postWithResponseBody(externalResultPath(modelingExercise.getId(), TEST_PREFIX + "student11"), result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmissionExam() throws Exception {
        Result result = new Result().rated(false).exerciseId(this.examModelingExercise.getId());
        request.postWithResponseBody("/api/assessment/exercises/" + this.examModelingExercise.getId() + "/external-submission-results?studentLogin=student1", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    private String externalResultPath(long exerciseId, String studentLogin) {
        return "/api/assessment/exercises/" + exerciseId + "/external-submission-results?studentLogin=" + studentLogin;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createResultForExternalSubmission_dueDateNotPassed() throws Exception {
        modelingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        modelingExerciseRepository.save(modelingExercise);
        Result result = new Result().rated(false).exerciseId(modelingExercise.getId());
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
        Submission submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        var result = participationUtilService.addResultToSubmission(null, null, submission);
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
        studentParticipation.setParticipant(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        studentParticipation.setExercise(textExercise);
        studentParticipationRepository.save(studentParticipation);

        // submission
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setParticipation(studentParticipation);
        textSubmission.setSubmitted(true);
        textSubmission.setText("abc");
        textSubmission = submissionRepository.save(textSubmission);

        // result 1
        var result1 = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), textSubmission, TEST_PREFIX + "instructor1", new ArrayList<>());
        result1.setRated(true);
        result1 = participationUtilService.addFeedbackToResults(result1);
        result1.setSubmission(textSubmission);

        // result 2
        var result2 = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), textSubmission, TEST_PREFIX + "tutor1", new ArrayList<>());
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
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        programmingExercise.setDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        // participation
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
        programmingExerciseStudentParticipation.setParticipant(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        programmingExerciseStudentParticipation.setExercise(programmingExercise);
        programmingExerciseStudentParticipationRepository.save(programmingExerciseStudentParticipation);

        // submission
        ProgrammingSubmission programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(programmingExerciseStudentParticipation);
        programmingSubmission.setSubmitted(true);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // result 1
        Submission submission1 = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result1 = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), submission1, TEST_PREFIX + "instructor1", new ArrayList<>());
        result1.setRated(true);
        result1 = participationUtilService.addFeedbackToResults(result1);
        result1.setSubmission(programmingSubmission);

        // result 2
        Submission submission2 = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result2 = participationUtilService.addResultToSubmission(AssessmentType.MANUAL, ZonedDateTime.now(), submission2, TEST_PREFIX + "tutor1", new ArrayList<>());
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllFeedbackDetailsForExercise() throws Exception {
        Submission submission = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission);
        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");
        testCase.setId(1L);
        Feedback feedback = new Feedback();
        feedback.setPositive(false);
        feedback.setDetailText("Some feedback");
        feedback.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback, result);

        String url = "/api/assessment/exercises/" + programmingExercise.getId() + "/feedback-details" + "?page=1&pageSize=10&sortedColumn=count&sortingOrder=ASCENDING"
                + "&searchTerm=&filterTasks=&filterTestCases=&filterOccurrence=&filterErrorCategories=&groupFeedback=false";

        FeedbackAnalysisResponseDTO response = request.get(url, HttpStatus.OK, FeedbackAnalysisResponseDTO.class);

        assertThat(response.feedbackDetails().getResultsOnPage()).isNotEmpty();
        FeedbackDetailDTO feedbackDetail = response.feedbackDetails().getResultsOnPage().getFirst();
        assertThat(feedbackDetail.count()).isEqualTo(1);
        assertThat(feedbackDetail.relativeCount()).isEqualTo(100.0);
        assertThat(feedbackDetail.detailTexts()).isEqualTo(List.of("Some feedback"));
        assertThat(feedbackDetail.testCaseName()).isEqualTo("test1");
        assertThat(response.errorCategories()).containsExactlyInAnyOrder("Student Error", "Ares Error", "AST Error");

        assertThat(response.totalItems()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllFeedbackDetailsForExerciseWithMultipleFeedback() throws Exception {
        Submission submission1 = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result1 = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission1);
        Submission submission2 = participationUtilService.addSubmission(programmingExerciseStudentParticipation2, new ProgrammingSubmission());
        Result result2 = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission2);
        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");
        testCase.setId(1L);

        Feedback feedback1 = new Feedback();
        feedback1.setPositive(false);
        feedback1.setDetailText("Some feedback");
        feedback1.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback1, result1);

        Feedback feedback2 = new Feedback();
        feedback2.setPositive(false);
        feedback2.setDetailText("Some feedback");
        feedback2.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback2, result2);

        Feedback feedback3 = new Feedback();
        feedback3.setPositive(false);
        feedback3.setDetailText("Some different feedback");
        feedback3.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback3, result1);

        String url = "/api/assessment/exercises/" + programmingExercise.getId() + "/feedback-details" + "?page=1&pageSize=10&sortedColumn=count&sortingOrder=ASCENDING"
                + "&searchTerm=&filterTasks=&filterTestCases=&filterOccurrence=&filterErrorCategories=&groupFeedback=false";

        FeedbackAnalysisResponseDTO response = request.get(url, HttpStatus.OK, FeedbackAnalysisResponseDTO.class);

        List<FeedbackDetailDTO> feedbackDetails = response.feedbackDetails().getResultsOnPage();
        assertThat(feedbackDetails).hasSize(2);

        FeedbackDetailDTO firstFeedbackDetail = feedbackDetails.stream().filter(feedbackDetail -> List.of("Some feedback").equals(feedbackDetail.detailTexts())).findFirst()
                .orElseThrow();

        FeedbackDetailDTO secondFeedbackDetail = feedbackDetails.stream().filter(feedbackDetail -> List.of("Some different feedback").equals(feedbackDetail.detailTexts()))
                .findFirst().orElseThrow();

        assertThat(firstFeedbackDetail.count()).isEqualTo(2);
        assertThat(firstFeedbackDetail.relativeCount()).isEqualTo(100.0);
        assertThat(firstFeedbackDetail.detailTexts()).isEqualTo(List.of("Some feedback"));
        assertThat(firstFeedbackDetail.testCaseName()).isEqualTo("test1");

        assertThat(secondFeedbackDetail.count()).isEqualTo(1);
        assertThat(secondFeedbackDetail.relativeCount()).isEqualTo(50.0);
        assertThat(secondFeedbackDetail.detailTexts()).isEqualTo(List.of("Some different feedback"));
        assertThat(secondFeedbackDetail.testCaseName()).isEqualTo("test1");
        assertThat(response.errorCategories()).containsExactlyInAnyOrder("Student Error", "Ares Error", "AST Error");

        assertThat(response.totalItems()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllFeedbackDetailsForExerciseWithGroupFeedbackAndMultipleSimilarFeedback() throws Exception {
        Submission submission1 = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result1 = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission1);
        Submission submission2 = participationUtilService.addSubmission(programmingExerciseStudentParticipation2, new ProgrammingSubmission());
        Result result2 = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission2);
        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");
        testCase.setId(1L);

        Feedback feedback1 = new Feedback();
        feedback1.setPositive(false);
        feedback1.setDetailText("Some feedback");
        feedback1.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback1, result1);

        Feedback feedback2 = new Feedback();
        feedback2.setPositive(false);
        feedback2.setDetailText("Some feedbacks");
        feedback2.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback2, result2);

        String url = "/api/assessment/exercises/" + programmingExercise.getId() + "/feedback-details" + "?page=1&pageSize=10&sortedColumn=count&sortingOrder=ASCENDING"
                + "&searchTerm=&filterTasks=&filterTestCases=&filterOccurrence=&filterErrorCategories=&groupFeedback=true";

        FeedbackAnalysisResponseDTO response = request.get(url, HttpStatus.OK, FeedbackAnalysisResponseDTO.class);

        List<FeedbackDetailDTO> feedbackDetails = response.feedbackDetails().getResultsOnPage();
        assertThat(feedbackDetails).hasSize(1);

        FeedbackDetailDTO firstFeedbackDetail = feedbackDetails.stream().filter(feedbackDetail -> List.of("Some feedback", "Some feedbacks").equals(feedbackDetail.detailTexts()))
                .findFirst().orElseThrow();

        assertThat(firstFeedbackDetail.count()).isEqualTo(2);
        assertThat(firstFeedbackDetail.relativeCount()).isEqualTo(100.0);
        assertThat(firstFeedbackDetail.detailTexts()).isEqualTo(List.of("Some feedback", "Some feedbacks"));
        assertThat(firstFeedbackDetail.testCaseName()).isEqualTo("test1");

        assertThat(response.errorCategories()).containsExactlyInAnyOrder("Student Error", "Ares Error", "AST Error");

        assertThat(response.totalItems()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetMaxCountForExercise() throws Exception {
        Submission submission = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission);

        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");
        testCase.setId(1L);
        Feedback feedback = new Feedback();
        feedback.setPositive(false);
        feedback.setDetailText("Some feedback");
        feedback.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback, result);

        long maxCount = request.get("/api/assessment/exercises/" + programmingExercise.getId() + "/feedback-details-max-count", HttpStatus.OK, Long.class);

        assertThat(maxCount).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetMaxCountForExerciseWithMultipleFeedback() throws Exception {
        Submission submission1 = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result1 = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission1);
        Submission submission2 = participationUtilService.addSubmission(programmingExerciseStudentParticipation2, new ProgrammingSubmission());
        Result result2 = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission2);
        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");
        testCase.setId(1L);

        Feedback feedback1 = new Feedback();
        feedback1.setPositive(false);
        feedback1.setDetailText("Some feedback");
        feedback1.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback1, result1);

        Feedback feedback2 = new Feedback();
        feedback2.setPositive(false);
        feedback2.setDetailText("Some feedback");
        feedback2.setTestCase(testCase);
        participationUtilService.addFeedbackToResult(feedback2, result2);

        long maxCount = request.get("/api/assessment/exercises/" + programmingExercise.getId() + "/feedback-details-max-count", HttpStatus.OK, Long.class);

        assertThat(maxCount).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetParticipationForFeedbackDetailText() throws Exception {
        Submission submission = participationUtilService.addSubmission(programmingExerciseStudentParticipation, new ProgrammingSubmission());
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission);
        ProgrammingExerciseTestCase testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "test1");
        testCase.setId(1L);

        Feedback feedback = new Feedback();
        feedback.setPositive(false);
        feedback.setDetailText("The AttributeTest test can only run if the structural oracle (test.json) is present. If you do not provide it, delete AttributeTest.java!");
        feedback.setTestCase(testCase);
        feedback = feedbackRepository.saveAndFlush(feedback);

        participationUtilService.addFeedbackToResult(feedback, result);

        String url = "/api/assessment/exercises/" + programmingExercise.getId() + "/feedback-details-participation?feedbackId1=" + feedback.getId();

        var response = request.get(url, HttpStatus.OK, List.class);
        assertThat(response).hasSize(1);

    }
}
