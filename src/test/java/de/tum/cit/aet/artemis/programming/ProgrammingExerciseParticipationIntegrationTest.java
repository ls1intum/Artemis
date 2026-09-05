package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.dto.PendingProgrammingSubmissionDTO;
import de.tum.cit.aet.artemis.programming.dto.RepoNameProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class ProgrammingExerciseParticipationIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "programmingexerciseparticipation";

    private static final String SCORPIO_REPOSITORY_URI = "http://artemis.local/git/TESTPROJECT/testproject-student1.git";

    private static final String SCORPIO_STUDENT_IMAGE_URL = "/api/core/files/user/profile-picture/1/avatar.png";

    private static final String COURSE_COLOR = "#691b0b";

    private static final String COURSE_ICON_PATH = "/api/core/files/course/icons/1/icon.png";

    private static final String COURSE_ENROLLMENT_CONFIRMATION_MESSAGE = "Welcome to the course";

    private static final String COURSE_ARCHIVE_PATH = "Course-Archive-1.zip";

    private static final String COURSE_TIME_ZONE = "Europe/Berlin";

    private static final String COURSE_CODE_OF_CONDUCT = "Be excellent to each other";

    private static final String COURSE_SEMESTER = "WS 2025/26";

    private static final String COURSE_DESCRIPTION = "Everything about compilers";

    private static final String EXAM_GROUP_TITLE = "Scorpio exercise group";

    private static final String EXAM_START_TEXT = "Good luck";

    private static final String EXAM_END_TEXT = "Well done";

    private static final String EXAM_CONFIRMATION_START_TEXT = "I read the rules";

    private static final String EXAM_CONFIRMATION_END_TEXT = "I submitted everything";

    private static final String EXAM_EXAMINER = "Prof. Krusche";

    private static final String EXAM_MODULE_NUMBER = "IN0006";

    private static final String EXAM_COURSE_NAME = "Introduction to Software Engineering";

    private static final String EXAM_ARCHIVE_PATH = "Exam-Archive-1.zip";

    private final String participationsBaseUrl = "/api/programming/programming-exercise-participations/";

    private final String exercisesBaseUrl = "/api/programming/programming-exercises/";

    private ProgrammingExercise programmingExercise;

    private Participation programmingExerciseParticipation;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
        programmingExerciseIntegrationTestService.addAuxiliaryRepositoryToExercise(programmingExercise);
    }

    @AfterEach
    void cleanup() {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    private static Stream<Arguments> argumentsForGetParticipationResults() {
        ZonedDateTime startDate = ZonedDateTime.now().minusDays(3);
        ZonedDateTime releaseDate = ZonedDateTime.now().minusDays(4);
        ZonedDateTime someDate = ZonedDateTime.now();
        ZonedDateTime futureDate = ZonedDateTime.now().plusDays(3);
        ZonedDateTime pastDate = ZonedDateTime.now().minusDays(1);
        return Stream.of(
                // No assessmentType and no completionDate -> notFound
                Arguments.of(null, null, null, null, null, false),
                // Automatic result is always returned
                Arguments.of(startDate, releaseDate, AssessmentType.AUTOMATIC, null, null, true),
                Arguments.of(startDate, releaseDate, AssessmentType.AUTOMATIC, someDate, null, true),
                Arguments.of(startDate, releaseDate, AssessmentType.AUTOMATIC, someDate, futureDate, true),
                Arguments.of(startDate, releaseDate, AssessmentType.AUTOMATIC, someDate, pastDate, true),
                Arguments.of(startDate, releaseDate, AssessmentType.AUTOMATIC, null, futureDate, true),
                Arguments.of(startDate, releaseDate, AssessmentType.AUTOMATIC, null, pastDate, true),
                // Manual result without completion date (assessment was only saved but no submitted) is not returned
                Arguments.of(startDate, releaseDate, AssessmentType.SEMI_AUTOMATIC, null, null, false),
                Arguments.of(startDate, releaseDate, AssessmentType.SEMI_AUTOMATIC, null, futureDate, false),
                Arguments.of(startDate, releaseDate, AssessmentType.SEMI_AUTOMATIC, null, pastDate, false),
                // Manual result is not returned if completed and assessment due date has not passed
                Arguments.of(startDate, releaseDate, AssessmentType.SEMI_AUTOMATIC, someDate, futureDate, false),
                // Manual result is returned if completed and assessmentDue date has passed
                Arguments.of(startDate, releaseDate, AssessmentType.SEMI_AUTOMATIC, someDate, pastDate, true));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("argumentsForGetParticipationResults")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResultAsAStudent(ZonedDateTime startDate, ZonedDateTime releaseDate, AssessmentType assessmentType, ZonedDateTime completionDate,
            ZonedDateTime assessmentDueDate, boolean expectLastCreatedResult) throws Exception {
        programmingExercise.setStartDate(startDate);
        programmingExercise.setReleaseDate(releaseDate);
        programmingExercise.setAssessmentDueDate(assessmentDueDate);
        programmingExerciseRepository.save(programmingExercise);
        var result = addStudentParticipationWithResult(assessmentType, completionDate);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        if (expectLastCreatedResult) {
            Set<Result> results = participationUtilService.getResultsForParticipation(requestedParticipation);
            assertThat(results).hasSize(1);
            var requestedResult = results.iterator().next();
            assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isInvisible);
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("argumentsForGetParticipationResults")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_multipleResultsAvailable(ZonedDateTime startDate, ZonedDateTime releaseDate, AssessmentType assessmentType,
            ZonedDateTime completionDate, ZonedDateTime assessmentDueDate, boolean expectLastCreatedResult) throws Exception {
        programmingExercise.setStartDate(startDate);
        programmingExercise.setReleaseDate(releaseDate);
        // Add an automatic result first
        var firstResult = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        programmingExercise.setAssessmentDueDate(assessmentDueDate);
        programmingExerciseRepository.save(programmingExercise);
        // Add a parameterized second result
        StudentParticipation participation = studentParticipationRepository.findWithEagerResultsAndFeedbackById(firstResult.getSubmission().getParticipation().getId())
                .orElseThrow();
        Result secondResult = participationUtilService.addResultToSubmission(participation, participation.getSubmissions().iterator().next());
        secondResult.successful(true).rated(true).score(100D).assessmentType(assessmentType).completionDate(completionDate);
        secondResult = participationUtilService.addVariousVisibilityFeedbackToResult(secondResult);
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);

        Set<Result> results = participationUtilService.getResultsForParticipation(requestedParticipation);
        assertThat(results).hasSize(1);
        var requestedResult = results.iterator().next();

        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isInvisible);
        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isAfterDueDate);

        // Depending on the parameters we expect to get the first or the second created result from the server
        if (expectLastCreatedResult) {
            assertThat(requestedResult).isEqualTo(secondResult);
        }
        else {
            firstResult.filterSensitiveInformation();
            firstResult.filterSensitiveFeedbacks(true);
            assertThat(requestedResult).isEqualTo(firstResult);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testGetParticipationWithLatestResult_cannotAccessParticipation() throws Exception {
        // student4 should have no connection to student1's participation and should thus receive a Forbidden HTTP status.
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.FORBIDDEN,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_studentCannotAccessParticipationIfExerciseNotStarted() throws Exception {
        ZonedDateTime startDate = ZonedDateTime.now().plusDays(1);
        programmingExercise.setStartDate(startDate);
        programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.FORBIDDEN,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetParticipationWithLatestResult_canAccessParticipationIfExerciseNotStartedAndNotStudent() throws Exception {
        ZonedDateTime startDate = ZonedDateTime.now().plusDays(1);
        programmingExercise.setStartDate(startDate);
        programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_showsResultsDuringExam() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(1, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_afterExam_hidesResultsBeforeExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(4, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_showsResultsAfterExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(10, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetParticipationWithLatestResult_showsResultsDuringTestRun() throws Exception {
        var participation = setupExamTestRunWithParticipation(TEST_PREFIX + "instructor1");
        var url = participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks";

        // The state in the reported reproduction: the test run is opened before any build result exists
        var participationWithoutResult = request.get(url, HttpStatus.OK, ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(participationWithoutResult)).isEmpty();

        addResultToTestRunParticipation(TEST_PREFIX + "instructor1");
        var participationWithResult = request.get(url, HttpStatus.OK, ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(participationWithResult)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestResultWithFeedbacks_showsResultsDuringTestRun() throws Exception {
        var participation = setupExamTestRunWithParticipation(TEST_PREFIX + "instructor1");
        var url = participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks";

        // The state in the reported reproduction: the test run is opened before any build result exists
        assertThat(request.get(url, HttpStatus.OK, Result.class)).isNull();

        var result = addResultToTestRunParticipation(TEST_PREFIX + "instructor1");
        var requestedResult = request.get(url, HttpStatus.OK, Result.class);
        assertThat(requestedResult).isNotNull();
        assertThat(requestedResult.getId()).isEqualTo(result.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("argumentsForGetParticipationResults")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithAllResults(ZonedDateTime startDate, ZonedDateTime releaseDate, AssessmentType assessmentType, ZonedDateTime completionDate,
            ZonedDateTime assessmentDueDate, boolean expectLastCreatedResult) throws Exception {
        programmingExercise.setStartDate(startDate);
        programmingExercise.setReleaseDate(releaseDate);
        // Add an automatic result first
        var firstResult = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        programmingExercise.setAssessmentDueDate(assessmentDueDate);
        programmingExerciseRepository.save(programmingExercise);
        // Add another automatic result
        var secondResult = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        programmingExercise.setAssessmentDueDate(assessmentDueDate);
        programmingExerciseRepository.save(programmingExercise);
        // Add a parameterized third result
        Result thirdResult = addStudentParticipationWithResult(assessmentType, completionDate);
        StudentParticipation participation = (StudentParticipation) thirdResult.getSubmission().getParticipation();

        // Expect the request to always be ok because it should at least return the first automatic result
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        Set<Result> results = participationUtilService.getResultsForParticipation(requestedParticipation);
        if (expectLastCreatedResult) {
            assertThat(results).hasSize(3);
        }
        else {
            assertThat(results).hasSize(2);
        }
        for (var result : results) {
            assertThat(result.getFeedbacks()).noneMatch(Feedback::isInvisible);
            assertThat(result.getFeedbacks()).noneMatch(Feedback::isAfterDueDate);
        }
        firstResult.filterSensitiveInformation();
        firstResult.filterSensitiveFeedbacks(true);
        assertThat(results).contains(firstResult);
        secondResult.filterSensitiveInformation();
        secondResult.filterSensitiveFeedbacks(true);
        assertThat(results).contains(secondResult);

        // Depending on the parameters we expect to get the third result too
        if (expectLastCreatedResult) {
            assertThat(results).contains(thirdResult);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetParticipationWithAllResultsAsAnInstructor_noCompletionDate_notFound() throws Exception {
        var result = addStudentParticipationWithResult(AssessmentType.SEMI_AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.NOT_FOUND, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testGetParticipationWithAllResults_cannotAccessParticipation1() throws Exception {
        // student4 should have no connection to student1's participation and should thus receive a Forbidden HTTP status.
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.FORBIDDEN, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testGetParticipationWithAllResults_cannotAccessParticipation2() throws Exception {
        // student4 should have no connection to student1's participation and should thus receive a Forbidden HTTP status.
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.FORBIDDEN, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAllResultsAsTutor() throws Exception {
        // tutor should have access
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_studentCannotAccessParticipationIfExerciseNotStarted() throws Exception {
        ZonedDateTime startDate = ZonedDateTime.now().plusDays(1);
        programmingExercise.setStartDate(startDate);
        programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.FORBIDDEN, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_studentCanAccessIfNoStartDateSet() throws Exception {
        programmingExercise.setStartDate(null);
        programmingExercise.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetParticipationAllResults_canAccessParticipationIfExerciseNotStartedAndNotStudent() throws Exception {
        ZonedDateTime startDate = ZonedDateTime.now().plusDays(1);
        programmingExercise.setStartDate(startDate);
        programmingExerciseRepository.save(programmingExercise);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise,
                TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_showsResultsDuringExam() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(1, TEST_PREFIX + "student1");
        var submission = submissionRepository.findByIdWithResultsElseThrow(result.getSubmission().getId());
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(1), submission);
        submissionRepository.save(submission);
        var requestedParticipation = request.get(participationsBaseUrl + submission.getParticipation().getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_afterExam_hidesResultsBeforeExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(4, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var submission = submissionRepository.findByIdWithResultsElseThrow(result.getSubmission().getId());
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(1), submission);
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_showsResultsAfterExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(10, TEST_PREFIX + "student1");
        Submission submission = submissionRepository.findByIdWithResultsElseThrow(result.getSubmission().getId());
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(1), submission);
        submissionRepository.save(submission);
        var requestedParticipation = request.get(participationsBaseUrl + submission.getParticipation().getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacksAsStudent() throws Exception {
        var result = addStudentParticipationWithResult(null, null);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isInvisible);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacksAsStudent_showsResultDuringExam() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(1, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacksAsStudent_afterExam_hidesResultBeforeExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(4, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacksAsStudent_showsResultAfterExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(10, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult).isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetResultWithFeedbacksFilteredBeforeLastDueDate(AssessmentType assessmentType) throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusDays(1));
        participationRepository.save(participation2);

        addStudentParticipationWithResult(assessmentType, null);
        StudentParticipation participation = studentParticipationRepository
                .findByExerciseIdAndStudentId(programmingExercise.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId()).getFirst();

        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);
        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isInvisible);
        if (AssessmentType.AUTOMATIC == assessmentType) {
            assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isAfterDueDate);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacksForTemplateParticipationAsTutorShouldReturnForbidden() throws Exception {
        TemplateProgrammingExerciseParticipation participation = addTemplateParticipationWithResult();
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLatestResultWithFeedbacksForTemplateParticipationAsTutor() throws Exception {
        TemplateProgrammingExerciseParticipation participation = addTemplateParticipationWithResult();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult.getFeedbacks().stream().filter(Feedback::isInvisible)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestResultWithFeedbacksForTemplateParticipationAsInstructor() throws Exception {
        TemplateProgrammingExerciseParticipation participation = addTemplateParticipationWithResult();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult.getFeedbacks().stream().filter(Feedback::isInvisible)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacksForSolutionParticipationAsTutorShouldReturnForbidden() throws Exception {
        SolutionProgrammingExerciseParticipation participation = addSolutionParticipationWithResult();
        request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLatestResultWithFeedbacksForSolutionParticipationAsTutor() throws Exception {
        SolutionProgrammingExerciseParticipation participation = addSolutionParticipationWithResult();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult.getFeedbacks().stream().filter(Feedback::isInvisible)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestResultWithFeedbacksForSolutionParticipationAsInstructor() throws Exception {
        SolutionProgrammingExerciseParticipation participation = addSolutionParticipationWithResult();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult.getFeedbacks().stream().filter(Feedback::isInvisible)).hasSize(1);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithSubmission(boolean withSubmission) throws Exception {
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        result.setSuccessful(true);
        result = participationUtilService.addFeedbackToResults(result);
        var submission = programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(result,
                (ProgrammingExerciseStudentParticipation) programmingExerciseParticipation, "ABC");
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("withSubmission", String.valueOf(withSubmission));
        var resultResponse = request.get(participationsBaseUrl + programmingExerciseParticipation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class,
                parameters);

        result.filterSensitiveInformation();
        result.filterSensitiveFeedbacks(true);
        assertThat(resultResponse.getFeedbacks()).noneMatch(Feedback::isInvisible);
        assertThat(resultResponse.getFeedbacks()).noneMatch(Feedback::isAfterDueDate);
        assertThat(resultResponse.getFeedbacks()).containsExactlyInAnyOrderElementsOf(result.getFeedbacks());

        assertThat(result).usingRecursiveComparison().ignoringFields("submission", "feedbacks", "participation", "lastModifiedDate", "exerciseId").isEqualTo(resultResponse);
        if (withSubmission) {
            assertThat(submission).isEqualTo(resultResponse.getSubmission());
        }
    }

    // --- wire shape of the migrated participation and build-log responses ------------------------------------------

    /**
     * The clone/code button decides that a repository belongs to the caller — and therefore that it must use the
     * participation token instead of the broad staff token — solely from {@code participation.student.login}. This
     * pins that field, together with the rest of the traced read contract of the participation root response.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_wireShape() throws Exception {
        startExercise();
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();

        JsonNode body = getJson(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks");

        assertThat(body.path("type").asText()).isEqualTo("programming");
        assertThat(body.path("student").path("login").asText()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(body.path("repositoryUri").isMissingNode()).isFalse();
        // the nested exercise keeps its nested course, which the client renders display links off
        assertThat(body.path("exercise").path("id").asLong()).isEqualTo(programmingExercise.getId());
        assertThat(body.path("exercise").path("course").path("id").asLong()).isEqualTo(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(body.path("exercise").path("course").path("title").asText()).isEqualTo(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
        // the results stay nested under their submission, where the client reads them
        assertThat(body.path("submissions")).hasSize(1);
        assertThat(body.path("submissions").get(0).path("submissionExerciseType").asText()).isEqualTo("programming");
        assertThat(body.path("submissions").get(0).path("results")).isNotEmpty();
    }

    /**
     * Masking must drop results from the response only, never from the database: the submission owns its results with
     * {@code cascade = ALL, orphanRemoval = true}, so shaping the JSON by emptying the loaded collection deletes rows.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithLatestResult_examMaskingDeletesNoRows() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(4, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        Set<Long> resultIdsBefore = resultIdsOfExercise();
        Set<Long> submissionIdsBefore = submissionIdsOfParticipation(participation.getId());
        assertThat(resultIdsBefore).isNotEmpty();

        JsonNode body = getJson(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks");

        assertThat(body.path("submissions").get(0).path("results")).isEmpty();
        assertThat(resultIdsOfExercise()).isEqualTo(resultIdsBefore);
        assertThat(submissionIdsOfParticipation(participation.getId())).isEqualTo(submissionIdsBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithAllResults_wireShape() throws Exception {
        startExercise();
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        ProgrammingSubmission submission = (ProgrammingSubmission) result.getSubmission();
        submission.setCommitHash("1234567890abcdef");
        submissionRepository.save(submission);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();

        JsonNode body = getJson(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results");

        assertThat(body.path("type").asText()).isEqualTo("programming");
        assertThat(body.path("student").path("login").asText()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(body.path("exercise").path("course").path("id").asLong()).isEqualTo(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertThat(body.path("submissions")).isNotEmpty();
        // the commit history matches the submissions against git hashes, so every submission needs its commit hash
        assertThat(body.path("submissions").get(0).path("commitHash").asText()).isEqualTo("1234567890abcdef");
        assertThat(body.path("submissions").get(0).path("results")).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationWithAllResults_examMaskingDeletesNoRows() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(4, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        Set<Long> resultIdsBefore = resultIdsOfExercise();
        assertThat(resultIdsBefore).isNotEmpty();

        JsonNode body = getJson(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results");

        assertThat(body.path("submissions").get(0).path("results")).isEmpty();
        assertThat(resultIdsOfExercise()).isEqualTo(resultIdsBefore);
    }

    /**
     * This route is annotated {@code @AllowedTools(ToolTokenType.SCORPIO)} and read by the out-of-repo IntelliJ
     * plugin, so its payload must stay a superset of the entity payload it replaced. Both variants of
     * {@code withSubmission} are asserted against the raw JSON, because deserializing into the entity would hide a
     * dropped key, and the fixture populates every nested node so that no key can be absent by {@code NON_EMPTY}
     * instead of by being dropped.
     *
     * @param withSubmission the request flag; it selects the fetch graph, not the response shape, because
     *                           {@code Result.submission} is an eager association
     */
    @ParameterizedTest(name = "{displayName} [{index}] withSubmission={0}")
    @ValueSource(booleans = { false, true })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacks_keepsTheScorpioKeySet(boolean withSubmission) throws Exception {
        var result = setUpFullyPopulatedResult();
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();

        Map<String, Object> body = getJsonMap(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks?withSubmission=" + withSubmission);

        assertThat(body).containsOnlyKeys("id", "exerciseId", "completionDate", "successful", "score", "rated", "hasComplaint", "exampleResult", "submission", "feedbacks",
                "assessmentType", "correctionRound", "testCaseCount", "passedTestCaseCount", "codeIssueCount");
        assertThat(body.get("id")).isEqualTo(result.getId().intValue());
        assertThat(body.get("hasComplaint")).isEqualTo(true);
        assertThat(body.get("exampleResult")).isEqualTo(true);
        assertThat(body.get("correctionRound")).isEqualTo(1);
        // exerciseId is a non-nullable column with a plain getter, so it was on the entity wire for every result
        assertThat(body.get("exerciseId")).isEqualTo(programmingExercise.getId().intValue());
        assertThat(body.get("assessmentType")).isEqualTo(AssessmentType.AUTOMATIC.name());
        assertScorpioFeedbackKeySets(body);
        assertScorpioSubmissionKeySets(body, participation);
    }

    /**
     * Asserts the feedback subtree. The manual feedback pins the structured grading instruction and the automatic one
     * pins the test case, so both associations the {@link Feedback} entity put on this wire are covered.
     *
     * @param body the parsed response
     */
    private void assertScorpioFeedbackKeySets(Map<String, Object> body) {
        List<Map<String, Object>> feedbacks = listOf(body, "feedbacks");
        Map<String, Object> manualFeedback = feedbacks.stream().filter(feedback -> FeedbackType.MANUAL.name().equals(feedback.get("type"))).findFirst().orElseThrow();
        assertThat(manualFeedback).containsOnlyKeys("id", "text", "detailText", "hasLongFeedbackText", "reference", "credits", "positive", "type", "visibility",
                "gradingInstruction");
        assertThat(manualFeedback.get("credits")).isEqualTo(2.0);
        assertThat(manualFeedback.get("reference")).isEqualTo("reference-1");
        assertThat(manualFeedback.get("hasLongFeedbackText")).isEqualTo(false);

        Map<String, Object> gradingInstruction = mapOf(manualFeedback, "gradingInstruction");
        assertThat(gradingInstruction).containsOnlyKeys("id", "credits", "gradingScale", "instructionDescription", "feedback", "usageCount");
        assertThat(gradingInstruction.get("gradingScale")).isEqualTo("good test");
        assertThat(gradingInstruction.get("feedback")).isEqualTo("test feedback");
        assertThat(gradingInstruction.get("usageCount")).isEqualTo(0);

        Map<String, Object> automaticFeedback = feedbacks.stream().filter(feedback -> feedback.containsKey("testCase")).findFirst().orElseThrow();
        assertThat(automaticFeedback.get("type")).isEqualTo(FeedbackType.AUTOMATIC.name());
        Map<String, Object> testCase = mapOf(automaticFeedback, "testCase");
        assertThat(testCase).containsOnlyKeys("id", "testName", "weight", "active", "visibility", "bonusMultiplier", "bonusPoints", "type");
        assertThat(testCase.get("weight")).isEqualTo(3.0);
        assertThat(testCase.get("bonusMultiplier")).isEqualTo(2.0);
        assertThat(testCase.get("bonusPoints")).isEqualTo(1.0);
        assertThat(testCase.get("active")).isEqualTo(true);
        assertThat(testCase.get("visibility")).isEqualTo(Visibility.ALWAYS.name());
        assertThat(testCase.get("type")).isEqualTo(ProgrammingExerciseTestCaseType.DEFAULT.name());
    }

    /**
     * Asserts the submission subtree down to the participant. {@code Result.submission} is an eager association, so
     * this ran for both values of {@code withSubmission} on the entity wire and has to keep doing so.
     *
     * @param body          the parsed response
     * @param participation the participation the fixture built
     */
    private void assertScorpioSubmissionKeySets(Map<String, Object> body, StudentParticipation participation) {
        Map<String, Object> submissionJson = mapOf(body, "submission");
        assertThat(submissionJson).containsOnlyKeys("id", "submissionExerciseType", "submissionDate", "commitHash", "type", "submitted", "exampleSubmission", "buildFailed",
                "empty", "durationInMinutes", "participation");
        assertThat(submissionJson.get("submissionExerciseType")).isEqualTo("programming");
        assertThat(submissionJson.get("commitHash")).isEqualTo("1234567890abcdef");
        assertThat(submissionJson.get("type")).isEqualTo(SubmissionType.MANUAL.name());
        assertThat(submissionJson.get("submitted")).isEqualTo(true);
        assertThat(submissionJson.get("exampleSubmission")).isEqualTo(true);
        assertThat(submissionJson.get("buildFailed")).isEqualTo(true);
        // A programming submission is never empty; the constant was on the entity wire and stays on this one.
        assertThat(submissionJson.get("empty")).isEqualTo(false);
        assertThat(submissionJson.get("durationInMinutes")).isEqualTo(5);

        Map<String, Object> participationJson = mapOf(submissionJson, "participation");
        assertThat(participationJson).containsOnlyKeys("id", "type", "initializationState", "initializationDate", "individualDueDate", "testRun", "attempt", "presentationScore",
                "student", "participantIdentifier", "participantName", "repositoryUri", "buildPlanId", "branch", "userIndependentRepositoryUri", "exercise");
        assertThat(participationJson.get("id")).isEqualTo(participation.getId().intValue());
        assertThat(participationJson.get("type")).isEqualTo("programming");
        assertThat(participationJson.get("initializationState")).isEqualTo(InitializationState.INITIALIZED.name());
        assertThat(participationJson.get("testRun")).isEqualTo(true);
        assertThat(participationJson.get("attempt")).isEqualTo(0);
        assertThat(participationJson.get("presentationScore")).isEqualTo(42.0);
        assertThat(participationJson.get("repositoryUri")).isEqualTo(SCORPIO_REPOSITORY_URI);
        assertThat(participationJson.get("buildPlanId")).isEqualTo("TESTPROJECT-STUDENT1");
        assertThat(participationJson.get("branch")).isEqualTo("main");
        // The user info is stripped from the authority, so a plain http URI comes back unchanged.
        assertThat(participationJson.get("userIndependentRepositoryUri")).isEqualTo(SCORPIO_REPOSITORY_URI);
        assertThat(participationJson.get("participantIdentifier")).isEqualTo(TEST_PREFIX + "student1");

        // visibleRegistrationNumber is the one component missing here: it is a transient that only the exam and course
        // management read paths unmask, so it was absent from the entity payload of this route as well.
        Map<String, Object> student = mapOf(participationJson, "student");
        assertThat(student).containsOnlyKeys("id", "login", "name", "firstName", "lastName", "email", "imageUrl", "langKey", "activated", "deleted", "internal", "testUser", "bot",
                "participantIdentifier", "createdDate");
        assertThat(student.get("login")).isEqualTo(TEST_PREFIX + "student1");
        assertThat(student.get("participantIdentifier")).isEqualTo(TEST_PREFIX + "student1");
        assertThat(student.get("imageUrl")).isEqualTo(SCORPIO_STUDENT_IMAGE_URL);
        assertThat(student.get("bot")).isEqualTo(false);

        // The exercise keeps its own response shape; only the members the entity computed on top of it are pinned here.
        // Every exercise property the record does not carry is a @Transient that only a dashboard or statistics service
        // fills in (numberOfSubmissions, averageRating, latestExamEndDate, ...) or a lazy relation this route never
        // initializes, so none of them was on the entity payload of this route either.
        Map<String, Object> exercise = mapOf(participationJson, "exercise");
        assertThat(exercise).containsKeys("id", "type", "exerciseType", "visibleToStudents", "studentAssignedTeamIdComputed", "defaultTestCaseVisibility",
                "gradingInstructionFeedbackUsed", "course");
        assertThat(exercise.get("exerciseType")).isEqualTo("programming");
        assertThat(exercise.get("visibleToStudents")).isEqualTo(true);
        assertThat(exercise.get("studentAssignedTeamIdComputed")).isEqualTo(false);
        assertThat(exercise.get("defaultTestCaseVisibility")).isEqualTo(Visibility.ALWAYS.name());
        assertScorpioCourseKeySet(mapOf(exercise, "course"));
    }

    /**
     * Asserts the nested course, which is the widest projection on this route and the one a wire dump is least able to
     * check: most of its properties are optional columns that a course fixture leaves null, and {@code NON_EMPTY} then
     * drops them from the entity payload and from the projection alike. {@link #populateEveryCourseScalar} therefore
     * gives every nullable course scalar a value first, so the key set below is exact rather than incidental.
     * <p>
     * The keys are the complete serializable scalar set of the {@code Course} entity. Its associations and its
     * {@code @Transient} dashboard counters are absent by design; see {@code ProgrammingExerciseCourseDTO}.
     *
     * @param course the parsed course node
     */
    private void assertScorpioCourseKeySet(Map<String, Object> course) {
        assertThat(course).containsOnlyKeys("id", "title", "description", "shortName", "startDate", "endDate", "enrollmentStartDate", "enrollmentEndDate", "unenrollmentEndDate",
                "semester", "testCourse", "language", "defaultProgrammingLanguage", "onlineCourse", "courseInformationSharingConfiguration", "maxComplaints", "maxTeamComplaints",
                "maxComplaintTimeDays", "maxRequestMoreFeedbackTimeDays", "maxComplaintTextLimit", "maxComplaintResponseTextLimit", "complaintsEnabled",
                "requestMoreFeedbackEnabled", "accuracyOfScores", "presentationScore", "enrollmentEnabled", "unenrollmentEnabled", "onboardingDone",
                "restrictedAthenaModulesAccess", "learningPathsEnabled", "gradeRelevant", "dataRetentionHold", "trainingEnabled", "color", "courseIcon",
                "enrollmentConfirmationMessage", "courseArchivePath", "maxPoints", "timeZone", "courseInformationSharingMessagingCodeOfConduct");
        assertThat(course.get("color")).isEqualTo(COURSE_COLOR);
        assertThat(course.get("courseIcon")).isEqualTo(COURSE_ICON_PATH);
        assertThat(course.get("enrollmentConfirmationMessage")).isEqualTo(COURSE_ENROLLMENT_CONFIRMATION_MESSAGE);
        assertThat(course.get("courseArchivePath")).isEqualTo(COURSE_ARCHIVE_PATH);
        assertThat(course.get("maxPoints")).isEqualTo(120);
        assertThat(course.get("timeZone")).isEqualTo(COURSE_TIME_ZONE);
        assertThat(course.get("courseInformationSharingMessagingCodeOfConduct")).isEqualTo(COURSE_CODE_OF_CONDUCT);
        assertThat(course.get("semester")).isEqualTo(COURSE_SEMESTER);
        assertThat(course.get("description")).isEqualTo(COURSE_DESCRIPTION);
        assertThat(course.get("language")).isEqualTo(Language.ENGLISH.name());
        assertThat(course.get("defaultProgrammingLanguage")).isEqualTo(ProgrammingLanguage.JAVA.name());
        assertThat(course.get("presentationScore")).isEqualTo(3);
        assertThat(course.get("accuracyOfScores")).isEqualTo(2);
    }

    /**
     * Gives every nullable scalar of a course a non-default value. Without it a dropped key and a key {@code NON_EMPTY}
     * suppressed look the same on the wire, which is how the six optional course columns survived three rounds of
     * payload diffing. The primitive scalars need no value: they are on the wire whatever they hold.
     *
     * @param course the course to populate
     * @return the saved course
     */
    private Course populateEveryCourseScalar(Course course) {
        course.setDescription(COURSE_DESCRIPTION);
        course.setSemester(COURSE_SEMESTER);
        course.setStartDate(ZonedDateTime.now().minusMonths(2));
        course.setEndDate(ZonedDateTime.now().plusMonths(2));
        course.setEnrollmentStartDate(ZonedDateTime.now().minusMonths(3));
        course.setEnrollmentEndDate(ZonedDateTime.now().minusMonths(1));
        course.setUnenrollmentEndDate(ZonedDateTime.now().plusMonths(1));
        course.setLanguage(Language.ENGLISH);
        course.setDefaultProgrammingLanguage(ProgrammingLanguage.JAVA);
        course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        course.setMaxComplaints(5);
        course.setMaxTeamComplaints(4);
        course.setMaxComplaintTimeDays(9);
        course.setMaxRequestMoreFeedbackTimeDays(8);
        course.setMaxComplaintTextLimit(1500);
        course.setMaxComplaintResponseTextLimit(1400);
        course.setAccuracyOfScores(2);
        course.setPresentationScore(3);
        course.setEnrollmentEnabled(true);
        course.setUnenrollmentEnabled(true);
        course.setOnboardingDone(true);
        course.setRestrictedAthenaModulesAccess(true);
        course.setColor(COURSE_COLOR);
        course.setCourseIcon(COURSE_ICON_PATH);
        course.setEnrollmentConfirmationMessage(COURSE_ENROLLMENT_CONFIRMATION_MESSAGE);
        course.setCourseArchivePath(COURSE_ARCHIVE_PATH);
        course.setMaxPoints(120);
        course.setTimeZone(COURSE_TIME_ZONE);
        course.setCourseInformationSharingMessagingCodeOfConduct(COURSE_CODE_OF_CONDUCT);
        return courseRepository.save(course);
    }

    /**
     * Builds a result whose every nested node carries a non-default value: a structured grading instruction on a
     * manual feedback, a test case on an automatic feedback, and a participation with a repository, a branch, an
     * initialization state and {@code testRun} set. Without that, a key the projection dropped would be
     * indistinguishable from a key {@code NON_EMPTY} suppressed.
     *
     * @return the result the route serves
     */
    private Result setUpFullyPopulatedResult() {
        populateEveryCourseScalar(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setImageUrl(SCORPIO_STUDENT_IMAGE_URL);
        userTestRepository.save(student);
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(5));
        programmingExercise.setStartDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(3));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExercise.setShowTestNamesToStudents(true);
        exerciseUtilService.addGradingInstructionsToExercise(programmingExercise);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusDays(1));
        result.setHasComplaint(true);
        result.setExampleResult(true);
        // Synthetic for an automatic result, but the key-set assertion needs every component non-empty so no key can
        // hide behind NON_EMPTY.
        result.setCorrectionRound(1);
        var participation = (ProgrammingExerciseStudentParticipation) result.getSubmission().getParticipation();
        // Truncate to milliseconds so durationInMinutes stays deterministic across the datetime(3) round trip.
        participation.setInitializationDate(ZonedDateTime.now().minusMinutes(30).truncatedTo(ChronoUnit.MILLIS));
        participation.setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        participation.setInitializationState(InitializationState.INITIALIZED);
        participation.setRepositoryUri(SCORPIO_REPOSITORY_URI);
        participation.setBranch("main");
        participation.setBuildPlanId("TESTPROJECT-STUDENT1");
        participation.setTestRun(true);
        participation.setPresentationScore(42.0);
        participationRepository.save(participation);

        var submission = (ProgrammingSubmission) result.getSubmission();
        submission.setSubmissionDate(participation.getInitializationDate().plusMinutes(5));
        submission.setCommitHash("1234567890abcdef");
        submission.setType(SubmissionType.MANUAL);
        submission.setExampleSubmission(true);
        submission.setBuildFailed(true);
        submissionRepository.save(submission);

        var instruction = GradingCriterionUtil
                .findAnyInstructionWhere(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId()), anyInstruction -> true).orElseThrow();
        Feedback manualFeedback = new Feedback();
        manualFeedback.setType(FeedbackType.MANUAL);
        manualFeedback.setText("manual feedback text");
        manualFeedback.setDetailText("manual detail text");
        manualFeedback.setReference("reference-1");
        manualFeedback.setCredits(2.0);
        manualFeedback.setPositive(true);
        manualFeedback.setVisibility(Visibility.ALWAYS);
        manualFeedback.setGradingInstruction(instruction);
        participationUtilService.addFeedbackToResult(manualFeedback, result);

        var testCase = testCaseRepository.findByExerciseId(programmingExercise.getId()).iterator().next();
        testCase.setWeight(3.0);
        testCase.setBonusMultiplier(2.0);
        testCase.setBonusPoints(1.0);
        testCase.setActive(true);
        testCase.setVisibility(Visibility.ALWAYS);
        testCaseRepository.save(testCase);
        // The automatic feedback lives in the typed table; the route synthesizes the legacy view the wire carries.
        participationUtilService.addTestCaseFeedbackToResult(result, testCase, true, "automatic detail text");
        return result;
    }

    /**
     * The same route serves an exam exercise once the exam results are published, and then the nested exercise reaches
     * the client through {@code exerciseGroup.exam.course} instead of the sibling {@code course} slot. That branch has
     * its own three projections, so it needs its own exact key sets — the course-exercise test above never walks it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestResultWithFeedbacks_keepsTheScorpioExamKeySet() throws Exception {
        var result = setUpFullyPopulatedExamResult();
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();

        Map<String, Object> body = getJsonMap(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks");

        Map<String, Object> exercise = mapOf(mapOf(mapOf(body, "submission"), "participation"), "exercise");
        // An exam exercise carries no course member, exactly as the entity did; the course hangs under the exam.
        assertThat(exercise).doesNotContainKey("course");

        Map<String, Object> exerciseGroup = mapOf(exercise, "exerciseGroup");
        assertThat(exerciseGroup).containsOnlyKeys("id", "title", "isMandatory", "exam");
        assertThat(exerciseGroup.get("title")).isEqualTo(EXAM_GROUP_TITLE);
        assertThat(exerciseGroup.get("isMandatory")).isEqualTo(true);

        // The exercises of the group are absent on purpose: the collection is lazy, never fetched here, and it would
        // re-enter the very exercise this group hangs under.
        Map<String, Object> exam = mapOf(exerciseGroup, "exam");
        assertThat(exam).containsOnlyKeys("id", "title", "testExam", "examWithAttendanceCheck", "visibleDate", "startDate", "endDate", "publishResultsDate",
                "examStudentReviewStart", "examStudentReviewEnd", "examSummaryPublicationDate", "exampleSolutionPublicationDate", "gracePeriod", "workingTime", "startText",
                "endText", "confirmationStartText", "confirmationEndText", "examMaxPoints", "numberOfExercisesInExam", "numberOfCorrectionRoundsInExam", "randomizeExerciseOrder",
                "examiner", "moduleNumber", "courseName", "examArchivePath", "course");
        assertThat(exam.get("testExam")).isEqualTo(false);
        assertThat(exam.get("examWithAttendanceCheck")).isEqualTo(true);
        assertThat(exam.get("gracePeriod")).isEqualTo(210);
        assertThat(exam.get("workingTime")).isEqualTo(7200);
        assertThat(exam.get("startText")).isEqualTo(EXAM_START_TEXT);
        assertThat(exam.get("endText")).isEqualTo(EXAM_END_TEXT);
        assertThat(exam.get("confirmationStartText")).isEqualTo(EXAM_CONFIRMATION_START_TEXT);
        assertThat(exam.get("confirmationEndText")).isEqualTo(EXAM_CONFIRMATION_END_TEXT);
        assertThat(exam.get("examMaxPoints")).isEqualTo(37);
        assertThat(exam.get("numberOfExercisesInExam")).isEqualTo(1);
        assertThat(exam.get("numberOfCorrectionRoundsInExam")).isEqualTo(2);
        assertThat(exam.get("randomizeExerciseOrder")).isEqualTo(true);
        assertThat(exam.get("examiner")).isEqualTo(EXAM_EXAMINER);
        assertThat(exam.get("moduleNumber")).isEqualTo(EXAM_MODULE_NUMBER);
        assertThat(exam.get("courseName")).isEqualTo(EXAM_COURSE_NAME);
        assertThat(exam.get("examArchivePath")).isEqualTo(EXAM_ARCHIVE_PATH);

        // The exam's course is the same projection as the course-exercise slot, so it has to pass the same key set.
        assertScorpioCourseKeySet(mapOf(exam, "course"));
    }

    /**
     * Builds the exam counterpart of {@link #setUpFullyPopulatedResult}: an exam whose results are published, with
     * every nullable scalar of the exercise group, the exam and the exam's course carrying a value.
     *
     * @return the result the route serves
     */
    private Result setUpFullyPopulatedExamResult() {
        var result = setupExamExerciseWithParticipationAndResult(10, TEST_PREFIX + "student1");
        Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(programmingExercise.getExerciseGroup().getExam().getId());
        exam.setExamWithAttendanceCheck(true);
        exam.setPublishResultsDate(ZonedDateTime.now().minusHours(1));
        exam.setExamStudentReviewStart(ZonedDateTime.now().minusMinutes(30));
        exam.setExamStudentReviewEnd(ZonedDateTime.now().plusDays(1));
        exam.setExamSummaryPublicationDate(ZonedDateTime.now().minusHours(2));
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().minusMinutes(10));
        exam.setGracePeriod(210);
        // The default working time is its own column, not the exam duration; set it apart from the dates so the
        // assertion below cannot pass on a value the projection derived instead of carried.
        exam.setWorkingTime(7200);
        exam.setStartText(EXAM_START_TEXT);
        exam.setEndText(EXAM_END_TEXT);
        exam.setConfirmationStartText(EXAM_CONFIRMATION_START_TEXT);
        exam.setConfirmationEndText(EXAM_CONFIRMATION_END_TEXT);
        exam.setExamMaxPoints(37);
        exam.setNumberOfExercisesInExam(1);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.setRandomizeExerciseOrder(true);
        exam.setExaminer(EXAM_EXAMINER);
        exam.setModuleNumber(EXAM_MODULE_NUMBER);
        exam.setCourseName(EXAM_COURSE_NAME);
        exam.setExamArchivePath(EXAM_ARCHIVE_PATH);
        exam.getExerciseGroups().forEach(group -> {
            group.setTitle(EXAM_GROUP_TITLE);
            group.setIsMandatory(true);
        });
        examRepository.save(exam);
        populateEveryCourseScalar(exam.getCourse());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Map<String, Object> parent, String key) {
        assertThat(parent).containsKey(key);
        return (Map<String, Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> parent, String key) {
        assertThat(parent).containsKey(key);
        return (List<Map<String, Object>>) parent.get(key);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMigratedParticipationRoutes_stayWithinTheirQueryBudget() throws Exception {
        startExercise();
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();

        assertThatDb(() -> request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK, String.class))
                .hasBeenCalledAtMostTimes(25);
        assertThatDb(() -> request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK, String.class))
                .hasBeenCalledAtMostTimes(25);
        assertThatDb(() -> request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, String.class)).hasBeenCalledAtMostTimes(25);
    }

    /**
     * The participation routes reject a student while the exercise has not started yet, so the wire-shape tests move
     * the release and start dates into the past first.
     */
    private void startExercise() {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setStartDate(ZonedDateTime.now().minusDays(3));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
    }

    private JsonNode getJson(String url) throws Exception {
        return objectMapper.readTree(request.get(url, HttpStatus.OK, String.class));
    }

    /**
     * Reads a response as a plain map, so a key-set assertion sees exactly the keys on the wire.
     */
    private Map<String, Object> getJsonMap(String url) throws Exception {
        return objectMapper.readValue(request.get(url, HttpStatus.OK, String.class), new TypeReference<>() {
        });
    }

    private Set<Long> resultIdsOfExercise() {
        return resultRepository.findAllBySubmissionParticipationExerciseId(programmingExercise.getId()).stream().map(DomainObject::getId).collect(Collectors.toSet());
    }

    private Set<Long> submissionIdsOfParticipation(long participationId) {
        return submissionRepository.findAllByParticipationId(participationId).stream().map(DomainObject::getId).collect(Collectors.toSet());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestPendingSubmissionIfExists_student() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLatestPendingSubmissionIfExists_ta() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestPendingSubmissionIfExists_instructor() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        request.get(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestPendingSubmission_notProgrammingParticipation() throws Exception {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation = participationRepository.save(studentParticipation);
        request.get(participationsBaseUrl + studentParticipation.getId() + "/latest-pending-submission", HttpStatus.NOT_FOUND, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestPendingSubmissionIfNotExists_student() throws Exception {
        // The submission has no result yet and its submission date is old enough, so it counts as pending.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLatestPendingSubmissionIfNotExists_ta() throws Exception {
        // The submission has no result yet and its submission date is old enough, so it counts as pending.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestPendingSubmissionIfNotExists_instructor() throws Exception {
        // The submission has no result yet and its submission date is old enough, so it counts as pending.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testGetLatestPendingSubmission_cannotAccessParticipation() throws Exception {
        // student4 should have no connection to student1's participation and should thus receive a Forbidden HTTP status.
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now());
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.FORBIDDEN,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLatestSubmissionsForExercise_instructor() throws Exception {
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        ProgrammingSubmission submission2 = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission2 = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission2, TEST_PREFIX + "student2");
        ProgrammingSubmission notPendingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(55L));
        programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, notPendingSubmission, TEST_PREFIX + "student3");

        List<PendingProgrammingSubmissionDTO> returnedSubmissions = request.getList(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.OK,
                PendingProgrammingSubmissionDTO.class);

        // There is one entry per student participation; the two pending submissions carry their submission id, the
        // participation whose latest submission already has a result carries a null submission.
        assertThat(returnedSubmissions).hasSize(3);
        Map<Long, PendingProgrammingSubmissionDTO> byParticipationId = returnedSubmissions.stream()
                .collect(Collectors.toMap(PendingProgrammingSubmissionDTO::participationId, Function.identity()));
        assertThat(byParticipationId.get(submission.getParticipation().getId()).submission().id()).isEqualTo(submission.getId());
        assertThat(byParticipationId.get(submission2.getParticipation().getId()).submission().id()).isEqualTo(submission2.getId());
        assertThat(byParticipationId.get(notPendingSubmission.getParticipation().getId()).submission()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLatestSubmissionsForExercise_picksLatestBySubmissionDateNotById() throws Exception {
        // Create a submission with a newer submission date first (so it gets the lower id) ...
        ProgrammingSubmission newerByDate = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(30L));
        newerByDate = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, newerByDate, TEST_PREFIX + "student1");
        // ... then a submission with an older submission date for the same participation (so it gets the higher id).
        ProgrammingSubmission olderByDateHigherId = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(90L));
        olderByDateHigherId = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, olderByDateHigherId, TEST_PREFIX + "student1");
        assertThat(olderByDateHigherId.getId()).isGreaterThan(newerByDate.getId());
        assertThat(olderByDateHigherId.getParticipation().getId()).isEqualTo(newerByDate.getParticipation().getId());

        List<PendingProgrammingSubmissionDTO> returnedSubmissions = request.getList(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.OK,
                PendingProgrammingSubmissionDTO.class);

        long participationId = newerByDate.getParticipation().getId();
        PendingProgrammingSubmissionDTO entry = returnedSubmissions.stream().filter(dto -> dto.participationId() == participationId).findFirst().orElseThrow();
        // The latest submission is the one with the newer submission date, even though the other submission has a higher id.
        assertThat(entry.submission()).isNotNull();
        assertThat(entry.submission().id()).isEqualTo(newerByDate.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLatestSubmissionsForExercise_noParticipations_returnsEmptyList() throws Exception {
        // The exercise has no student participations yet, so the two lean queries (participation ids / latest submission
        // ids) return nothing and the endpoint returns an empty list rather than failing.
        List<PendingProgrammingSubmissionDTO> returnedSubmissions = request.getList(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.OK,
                PendingProgrammingSubmissionDTO.class);
        assertThat(returnedSubmissions).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLatestSubmissionsForExercise_returnsMinimalSubmissionFields() throws Exception {
        // A pending submission carries only the minimal fields the client reads: id, commit hash and submission date.
        ZonedDateTime submissionDate = ZonedDateTime.now().minusSeconds(30L);
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc123def456").submissionDate(submissionDate);
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");

        List<PendingProgrammingSubmissionDTO> returnedSubmissions = request.getList(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.OK,
                PendingProgrammingSubmissionDTO.class);

        long participationId = submission.getParticipation().getId();
        PendingProgrammingSubmissionDTO entry = returnedSubmissions.stream().filter(dto -> dto.participationId() == participationId).findFirst().orElseThrow();
        assertThat(entry.submission()).isNotNull();
        assertThat(entry.submission().id()).isEqualTo(submission.getId());
        assertThat(entry.submission().commitHash()).isEqualTo("abc123def456");
        // The submission date is serialized as part of the minimal projection (exact value is asserted elsewhere; a
        // strict equality here is fragile due to PostgreSQL's UTC storage and microsecond truncation).
        assertThat(entry.submission().submissionDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestSubmissionsForExercise_studentForbidden() throws Exception {
        request.getMap(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.FORBIDDEN, Long.class, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "stidemt1", roles = "USER")
    void testGetParticipationWithResultsForStudentParticipation_forbidden() throws Exception {
        request.getMap(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.FORBIDDEN, Long.class, ProgrammingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkIfParticipationHasResult_withResult_returnsTrue() throws Exception {
        addStudentParticipationWithResult(null, null);

        final var response = request.get("/api/programming/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/has-result", HttpStatus.OK,
                Boolean.class);

        assertThat(response).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkIfParticipationHasResult_withoutResult_returnsFalse() throws Exception {
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        final var response = request.get("/api/programming/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/has-result", HttpStatus.OK,
                Boolean.class);

        assertThat(response).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoName() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        var repoName = extractRepoName(participation.getRepositoryUri());
        RepoNameProgrammingStudentParticipationDTO participationDTO = request.get("/api/programming/programming-exercise-participations?repoName=" + repoName, HttpStatus.OK,
                RepoNameProgrammingStudentParticipationDTO.class);

        assertThat(participationDTO.id()).isEqualTo(participation.getId());
        assertThat(participationDTO.exercise().id()).isEqualTo(participation.getExercise().getId());
        assertThat(participationDTO.exercise().course().id()).isEqualTo(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationNoParam() throws Exception {
        request.get("/api/programming/programming-exercise-participations", HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoNameNotFound() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        String repoUrl = generateRandomRepoUrl(participation, true);

        var repoName = extractRepoName(repoUrl);
        request.get("/api/programming/programming-exercise-participations?repoName=" + repoName, HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByInvalidRepoName() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        String repoUrl = generateRandomRepoUrl(participation, false);

        var repoName = extractRepoName(repoUrl);
        request.get("/api/programming/programming-exercise-participations?repoName=" + repoName, HttpStatus.BAD_REQUEST, String.class);
    }

    private @NonNull String generateRandomRepoUrl(ProgrammingExerciseStudentParticipation participation, boolean valid) {
        String baseRepoPath = participation.getRepositoryUri();
        String repoUrl;
        Optional<ProgrammingExerciseStudentParticipation> foundParticipation;
        do {
            // Generate random segments for the path
            String randomKey = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            String randomName = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

            // Extract base path up to /git/ directory
            String basePath = baseRepoPath.substring(0, baseRepoPath.indexOf("/git/") + 4);

            // Format: /path/to/git/PROJECT_KEY/repo_name.git
            String repoName = valid ? "%s-%s".formatted(randomKey, randomName) : randomName;
            repoUrl = "%s/%s/%s.git".formatted(basePath, randomKey, repoName);

            foundParticipation = programmingExerciseStudentParticipationRepository.findByRepositoryUri(repoUrl);
        }
        while (foundParticipation.isPresent());
        return repoUrl;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoNameNotVisible() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        var repoName = extractRepoName(participation.getRepositoryUri());
        request.get("/api/programming/programming-exercise-participations?repoName=" + repoName, HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseStudentParticipationByRepoNameExam() throws Exception {
        var programmingExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithProgrammingExerciseAndExamDates(ZonedDateTime.now().plusHours(1),
                ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusHours(3), ZonedDateTime.now().plusHours(4), TEST_PREFIX + "student1", 1000, TEST_PREFIX);
        programmingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        var repoName = extractRepoName(participation.getRepositoryUri());
        request.get("/api/programming/programming-exercise-participations?repoName=" + repoName, HttpStatus.FORBIDDEN, String.class);
    }

    /**
     * Extracts the repository name from a Git repository URL ending in ".git".
     *
     * <p>
     * Assumes the URL format:
     * {@code http(s)://<host>/git/<project_key>/<repo-name>.git}
     * </p>
     *
     * <p>
     * <b>Examples:</b>
     * </p>
     *
     * <pre>
     * extractRepoName("http://localhost:7990/git/PROJ/proj-repo.git") → "proj-repo"
     * extractRepoName("https://example.com/git/ABC/abc-repo.git") → "abc-repo"
     * </pre>
     *
     * @param repoUrl the full URL of the Git repository (e.g., "http://localhost:7990/git/PROJ/my-repo.git")
     * @return the repository name without the ".git" suffix (e.g., "my-repo")
     * @throws IllegalArgumentException if the input does not end with ".git" or contains no slashes
     *
     */
    private String extractRepoName(String repoUrl) {
        if (repoUrl == null || !repoUrl.endsWith(".git") || !repoUrl.contains("/")) {
            throw new IllegalArgumentException("Invalid Git repository URL: " + repoUrl);
        }

        int lastSlash = repoUrl.lastIndexOf('/');
        return repoUrl.substring(lastSlash + 1, repoUrl.length() - 4); // remove ".git"
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_noAccess_forbidden() throws Exception {
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        request.put("/api/programming/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/reset-repository", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_noAccessToGradedParticipation_forbidden() throws Exception {
        var gradedParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        var practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        practiceParticipation.setPracticeMode(true);
        participationRepository.save(practiceParticipation);

        request.put(
                "/api/programming/programming-exercise-participations/" + practiceParticipation.getId() + "/reset-repository?gradedParticipationId=" + gradedParticipation.getId(),
                null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_participationLocked_forbidden() throws Exception {
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = participationUtilService
                .addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        programmingExerciseStudentParticipation.setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseStudentParticipation = studentParticipationRepository.save(programmingExerciseStudentParticipation);

        request.put("/api/programming/programming-exercise-participations/" + programmingExerciseStudentParticipation.getId() + "/reset-repository", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_exam_badRequest() throws Exception {
        programmingExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(programmingExercise.getExam().getId());
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 1);
        studentExamService.generateStudentExams(exam);
        request.put("/api/programming/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/reset-repository", null, HttpStatus.BAD_REQUEST);
    }

    /**
     * TODO move the following test into a different test file, as they do not use the programming-exercise-participations/.. endpoint, but programming-exercise/..
     * move the endpoint itself too
     * <p>
     * Test for GET - programming-exercise/{exerciseID}/commit-history/{repositoryType}
     */
    @Nested
    class GetCommitHistoryForTemplateSolutionTestOrAuxRepo {

        String PATH_PREFIX;

        ProgrammingExercise programmingExerciseWithAuxRepo;

        String templateCommitMessage;

        String solutionCommitMessage;

        String testsCommitMessage;

        String auxiliaryCommitMessage;

        @BeforeEach
        void setup() throws Exception {
            userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
            var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
            programmingExerciseWithAuxRepo = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
            programmingExerciseWithAuxRepo = programmingExerciseRepository
                    .findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExerciseWithAuxRepo.getId()).orElseThrow();
            programmingExerciseIntegrationTestService.addAuxiliaryRepositoryToExercise(programmingExerciseWithAuxRepo);
            RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExerciseWithAuxRepo);
            programmingExerciseRepository.save(programmingExerciseWithAuxRepo);
            templateCommitMessage = "Template commit";
            solutionCommitMessage = "Solution commit";
            testsCommitMessage = "Tests commit";
            auxiliaryCommitMessage = "Auxiliary commit";

            commitToRepository(programmingExerciseWithAuxRepo.getTemplateRepositoryUri(), Map.of("template/Example.java", "class Template {}"), templateCommitMessage);
            commitToRepository(programmingExerciseWithAuxRepo.getSolutionRepositoryUri(), Map.of("solution/Example.java", "class Solution {}"), solutionCommitMessage);
            commitToRepository(programmingExerciseWithAuxRepo.getTestRepositoryUri(), Map.of("tests/ExampleTest.java", "class Tests {}"), testsCommitMessage);
            AuxiliaryRepository auxiliaryRepository = ensureAuxiliaryRepositoryConfigured();
            commitToRepository(auxiliaryRepository.getRepositoryUri(), Map.of("auxiliary/Example.md", "Aux repo"), auxiliaryCommitMessage);
            PATH_PREFIX = "/api/programming/programming-exercises/" + programmingExerciseWithAuxRepo.getId() + "/commit-history/";
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestForInvalidRepositoryType() throws Exception {
            request.getList(PATH_PREFIX + "INVALIDTYPE", HttpStatus.BAD_REQUEST, CommitInfoDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetListForTemplateRepository() throws Exception {
            var commits = request.getList(PATH_PREFIX + "TEMPLATE", HttpStatus.OK, CommitInfoDTO.class);
            assertThat(commits).isNotEmpty();
            assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(templateCommitMessage));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetListForSolutionRepository() throws Exception {
            var commits = request.getList(PATH_PREFIX + "SOLUTION", HttpStatus.OK, CommitInfoDTO.class);
            assertThat(commits).isNotEmpty();
            assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(solutionCommitMessage));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetListForTestsRepository() throws Exception {
            var commits = request.getList(PATH_PREFIX + "TESTS", HttpStatus.OK, CommitInfoDTO.class);
            assertThat(commits).isNotEmpty();
            assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(testsCommitMessage));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldGetListForAuxiliaryRepository() throws Exception {
            var repositoryId = programmingExerciseWithAuxRepo.getAuxiliaryRepositories().getFirst().getId();
            var commits = request.getList(PATH_PREFIX + "AUXILIARY?repositoryId=" + repositoryId, HttpStatus.OK, CommitInfoDTO.class);
            assertThat(commits).isNotEmpty();
            assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(auxiliaryCommitMessage));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldThrowWithInvalidAuxiliaryRepositoryId() throws Exception {
            long maxId = auxiliaryRepositoryRepository.findAll().stream().mapToLong(DomainObject::getId).max().orElse(0);
            request.getList(PATH_PREFIX + "AUXILIARY?repositoryId=" + (maxId + 1), HttpStatus.NOT_FOUND, CommitInfoDTO.class);
        }

        private AuxiliaryRepository ensureAuxiliaryRepositoryConfigured() throws Exception {
            AuxiliaryRepository auxiliaryRepository = programmingExerciseWithAuxRepo.getAuxiliaryRepositories().getFirst();
            if (auxiliaryRepository.getRepositoryUri() == null) {
                String projectKey = programmingExerciseWithAuxRepo.getProjectKey();
                String repositorySlug = programmingExerciseWithAuxRepo.generateRepositoryName(auxiliaryRepository.getName());
                RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, repositorySlug));
                auxiliaryRepository.setRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repositorySlug));
                auxiliaryRepository = auxiliaryRepositoryRepository.save(auxiliaryRepository);
            }
            return auxiliaryRepository;
        }
    }

    /**
     * Tests for programming-exercise-participations/{participationId}/files-content/{commitId}
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getParticipationRepositoryFilesInstructorSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        Map<String, String> seededFiles = Map.of("README.md", "Instructor view", "src/App.java", "class App {}\n");
        var commit = commitToParticipationRepository(participation, seededFiles, "Seed instructor files");

        var files = request.getMap("/api/programming/programming-exercise-participations/" + participation.getId() + "/files-content?commitId=" + commit.getName(), HttpStatus.OK,
                String.class, String.class);
        assertThat(files).isNotEmpty();
        assertThat(files).containsEntry("README.md", "Instructor view");
        assertThat(files).containsEntry("src/App.java", "class App {}\n");
    }

    /**
     * TODO refactor endpoint to contain participation -> programming-exercise-participations
     * tests GET - programming-exercise/{exerciseId}/files-content-commit-details/{commitId}
     */
    @Nested
    class GetParticipationRepositoryFilesForCommitsDetailsView {

        String basePath;

        String studentCommitHash;

        String templateCommitHash;

        String solutionCommitHash;

        ProgrammingExerciseParticipation participation;

        @BeforeEach
        void setup() throws Exception {
            userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
            var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExerciseAndTestCases(TEST_PREFIX);
            programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
            programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
            programmingExerciseIntegrationTestService.addAuxiliaryRepositoryToExercise(programmingExercise);
            RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);
            programmingExerciseRepository.save(programmingExercise);

            participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
            Map<String, String> studentFiles = Map.of("student/Example.java", "class StudentExample {}\n", "student/file with spaces.txt", "content with spaces",
                    "student/pages/[id].tsx", "export const Page = () => null;", "student/check.sh", "#!/bin/sh\necho checked\n", "README.md", "Not requested");
            var studentParticipation = (ProgrammingExerciseStudentParticipation) participation;
            studentCommitHash = commitToParticipationRepository(studentParticipation, studentFiles, "Student detail commit").getName();

            templateCommitHash = commitToRepository(programmingExercise.getTemplateRepositoryUri(), Map.of("template/Example.java", "class TemplateDetail {}"),
                    "Template detail commit").getName();
            solutionCommitHash = commitToRepository(programmingExercise.getSolutionRepositoryUri(), Map.of("solution/Example.java", "class SolutionDetail {}"),
                    "Solution detail commit").getName();

            basePath = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/files-content-commit-details?commitId=";
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestWithoutAnyProvidedParameters() throws Exception {
            request.getMap(basePath + studentCommitHash, HttpStatus.BAD_REQUEST, String.class, String.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnForParticipation() throws Exception {
            var files = request.getMap(basePath + studentCommitHash + "&participationId=" + participation.getId(), HttpStatus.OK, String.class, String.class);
            assertThat(files).containsEntry("student/Example.java", "class StudentExample {}\n");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnOnlySelectedFilesForParticipation() throws Exception {
            var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/files-content-commit-details/selected?commitId=" + studentCommitHash
                    + "&participationId=" + participation.getId();

            Map<?, ?> files = request.postWithResponseBody(path, Set.of("student/Example.java"), Map.class, HttpStatus.OK);

            assertThat(files).hasSize(1);
            assertThat(files.get("student/Example.java")).isEqualTo("class StudentExample {}\n");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldReturnSelectedFilesWithLegalGitPathCharacters() throws Exception {
            var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/files-content-commit-details/selected?commitId=" + studentCommitHash
                    + "&participationId=" + participation.getId();

            Map<?, ?> files = request.postWithResponseBody(path, Set.of("student/file with spaces.txt", "student/pages/[id].tsx"), Map.class, HttpStatus.OK);

            assertThat(files.get("student/file with spaces.txt")).isEqualTo("content with spaces");
            assertThat(files.get("student/pages/[id].tsx")).isEqualTo("export const Page = () => null;");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldFilterBinarySelectedFilesByContentInsteadOfExtension() throws Exception {
            var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/files-content-commit-details/selected?commitId=" + studentCommitHash
                    + "&participationId=" + participation.getId();

            Map<?, ?> files = request.postWithResponseBody(path, Set.of("student/Example.java", "student/check.sh"), Map.class, HttpStatus.OK);

            assertThat(files).hasSize(2);
            assertThat(files.get("student/Example.java")).isEqualTo("class StudentExample {}\n");
            assertThat(files.get("student/check.sh")).isEqualTo("#!/bin/sh\necho checked\n");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldSkipInvalidGitPathWithoutRejectingValidPaths() throws Exception {
            var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/files-content-commit-details/selected?commitId=" + studentCommitHash
                    + "&participationId=" + participation.getId();

            Map<?, ?> files = request.postWithResponseBody(path, Set.of("student/../README.md", "student/Example.java"), Map.class, HttpStatus.OK);

            assertThat(files).hasSize(1);
            assertThat(files.get("student/Example.java")).isEqualTo("class StudentExample {}\n");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldRejectTooManySelectedFiles() throws Exception {
            var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/files-content-commit-details/selected?commitId=" + studentCommitHash
                    + "&participationId=" + participation.getId();
            Set<String> filePaths = new HashSet<>();
            for (int i = 0; i <= 100; i++) {
                filePaths.add("student/File" + i + ".java");
            }

            request.postWithResponseBody(path, filePaths, Map.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnFilesForTemplateRepository() throws Exception {
            var files = request.getMap(basePath + templateCommitHash + "&repositoryType=TEMPLATE", HttpStatus.OK, String.class, String.class);
            assertThat(files).containsEntry("template/Example.java", "class TemplateDetail {}");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnFilesForSolutionRepository() throws Exception {
            var files = request.getMap(basePath + solutionCommitHash + "&repositoryType=SOLUTION", HttpStatus.OK, String.class, String.class);
            assertThat(files).containsEntry("solution/Example.java", "class SolutionDetail {}");
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retrieveCommitHistoryInstructorSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        String commitMessage = "Instructor commit history";
        commitToParticipationRepository(participation, Map.of("src/Instructor.java", "class Instructor {}"), commitMessage);
        var commits = request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.OK, CommitInfoDTO.class);
        assertThat(commits).isNotEmpty();
        assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(commitMessage));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retrieveCommitHistoryGitExceptionEmptyList() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        doThrow(new NoHeadException("error")).when(gitServiceSpy).getCommitInfos(participation.getVcsRepositoryUri());
        assertThat(request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.OK, CommitInfoDTO.class))
                .isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void retrieveCommitHistoryStudentSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        String commitMessage = "Student commit history";
        commitToParticipationRepository(participation, Map.of("src/Student.java", "class Student {}"), commitMessage);
        var commits = request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.OK, CommitInfoDTO.class);
        assertThat(commits).isNotEmpty();
        assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(commitMessage));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void retrieveCommitHistoryStudentNotOwningParticipationForbidden() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.FORBIDDEN, CommitInfoDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveCommitHistoryTutorNotOwningParticipationSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        String commitMessage = "Tutor view commit";
        commitToParticipationRepository(participation, Map.of("src/Tutor.java", "class Tutor {}"), commitMessage);
        var commits = request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.OK, CommitInfoDTO.class);
        assertThat(commits).isNotEmpty();
        assertThat(commits).anySatisfy(commit -> assertThat(commit.message()).isEqualTo(commitMessage));
    }

    private Result addStudentParticipationWithResult(AssessmentType assessmentType, ZonedDateTime completionDate) {
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        Result result = programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submission, TEST_PREFIX + "student1");
        result.successful(true).rated(true).score(100D).assessmentType(assessmentType).completionDate(completionDate);

        return participationUtilService.addVariousVisibilityFeedbackToResult(result);
    }

    private TemplateProgrammingExerciseParticipation addTemplateParticipationWithResult() {
        programmingExerciseParticipation = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise)
                .getTemplateParticipation();
        Result result = programmingExerciseUtilService.addTemplateSubmissionWithResult(programmingExercise);
        result.successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(null);

        participationUtilService.addVariousVisibilityFeedbackToResult(result);
        return (TemplateProgrammingExerciseParticipation) programmingExerciseParticipation;
    }

    private SolutionProgrammingExerciseParticipation addSolutionParticipationWithResult() {
        programmingExerciseParticipation = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise)
                .getSolutionParticipation();
        Result result = this.programmingExerciseUtilService.addSolutionSubmissionWithResult(programmingExercise);
        result.successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(null);

        participationUtilService.addVariousVisibilityFeedbackToResult(result);
        return (SolutionProgrammingExerciseParticipation) programmingExerciseParticipation;
    }

    private RevCommit commitToParticipationRepository(ProgrammingExerciseStudentParticipation participation, Map<String, String> files, String message) throws Exception {
        return commitToRepository(participation.getVcsRepositoryUri(), files, message);
    }

    private RevCommit commitToRepository(String repositoryUri, Map<String, String> files, String message) throws Exception {
        return commitToRepository(new LocalVCRepositoryUri(repositoryUri), files, message);
    }

    private RevCommit commitToRepository(LocalVCRepositoryUri repositoryUri, Map<String, String> files, String message) throws Exception {
        ensureLocalVcRepositoryExists(repositoryUri);
        Path remoteRepoPath = repositoryUri.getLocalRepositoryPath(localVCBasePath);
        return writeFilesAndPush(remoteRepoPath, files, message);
    }

    private void ensureLocalVcRepositoryExists(LocalVCRepositoryUri repositoryUri) throws Exception {
        Path repoPath = repositoryUri.getLocalRepositoryPath(localVCBasePath);
        if (Files.exists(repoPath)) {
            return;
        }
        String slugWithGit = repositoryUri.getRelativeRepositoryPath().getFileName().toString();
        String repositorySlug = slugWithGit.endsWith(".git") ? slugWithGit.substring(0, slugWithGit.length() - 4) : slugWithGit;
        RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(repositoryUri.getProjectKey(), repositorySlug));
    }

    private RevCommit writeFilesAndPush(Path remoteRepoPath, Map<String, String> files, String message) throws Exception {
        Path workingCopy = tempFileUtilService.createTempDirectory(tempPath, "repo-clone");
        try (Git git = Git.cloneRepository().setURI(remoteRepoPath.toUri().toString()).setDirectory(workingCopy.toFile()).call()) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                Path filePath = workingCopy.resolve(entry.getKey());
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                FileUtils.writeStringToFile(filePath.toFile(), entry.getValue(), StandardCharsets.UTF_8);
            }
            git.add().addFilepattern(".").call();
            RevCommit commit = de.tum.cit.aet.artemis.localvc.service.GitService.commit(git).setMessage(message).call();
            git.push().call();
            return commit;
        }
        finally {
            RepositoryExportTestUtil.safeDeleteDirectory(workingCopy);
        }
    }

    /**
     * Sets up an exam exercise with a participation and a result. The exam duration is two hours and the publishResultsDate is 10 hours after the exam start.
     *
     * @param hoursSinceExamStart The hours since the exam start.
     * @param userLogin           The user login
     * @return The result attached to the participation
     */
    private Result setupExamExerciseWithParticipationAndResult(int hoursSinceExamStart, String userLogin) {
        var now = ZonedDateTime.now();
        var startDate = now.minusHours(hoursSinceExamStart);
        var endDate = startDate.plusHours(1);
        var visibilityDate = startDate.minusHours(1);
        var publishResultsDate = startDate.plusHours(10);
        var workingTime = 120 * 60;
        programmingExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithProgrammingExerciseAndExamDates(visibilityDate, startDate, endDate,
                publishResultsDate, userLogin, workingTime, TEST_PREFIX);
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userLogin);
        return addStudentParticipationWithResult(AssessmentType.AUTOMATIC, startDate.plusMinutes(2));
    }

    /**
     * Sets up an exam exercise conducted as an instructor test run, together with a test-run participation that has no
     * result yet. The exam started an hour ago and its results are not published yet, i.e. the state in which a test run
     * is conducted. A test run only has a student exam with {@code testRun = true} and deliberately no regular student exam.
     *
     * @param instructorLogin The login of the instructor conducting the test run
     * @return The test-run participation of the instructor
     */
    private ProgrammingExerciseStudentParticipation setupExamTestRunWithParticipation(String instructorLogin) {
        var startDate = ZonedDateTime.now().minusHours(1);
        programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        Exam exam = programmingExercise.getExerciseGroup().getExam();
        examUtilService.setVisibleStartAndEndDateOfExam(exam, startDate.minusHours(1), startDate, startDate.plusHours(1));
        exam.setPublishResultsDate(startDate.plusHours(10));
        examRepository.save(exam);

        var instructor = userUtilService.getUserByLogin(instructorLogin);
        studentExamRepository.save(examUtilService.generateTestRunForInstructor(exam, instructor, List.of(programmingExercise)));

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, instructorLogin);
        assertThat(participation.isTestRun()).isTrue();
        programmingExerciseParticipation = participation;
        return participation;
    }

    /**
     * Adds a submission with an automatic result to the test-run participation set up by {@link #setupExamTestRunWithParticipation}.
     *
     * @param instructorLogin The login of the instructor conducting the test run
     * @return The result attached to the test-run participation
     */
    private Result addResultToTestRunParticipation(String instructorLogin) {
        Result result = programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, ParticipationFactory.generateProgrammingSubmission(true),
                instructorLogin);
        result.successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(ZonedDateTime.now().minusMinutes(58));
        return participationUtilService.addVariousVisibilityFeedbackToResult(result);
    }

}
