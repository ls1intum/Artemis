package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.revwalk.RevCommit;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.dto.RepoNameProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

class ProgrammingExerciseParticipationIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "programmingexerciseparticipation";

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

    // Spy is only used for simulating non-feasible failure scenarios. Please use the real bean otherwise.
    @MockitoSpyBean
    private GitService gitServiceSpy;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
        programmingExerciseIntegrationTestService.addAuxiliaryRepositoryToExercise(programmingExercise);
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
        StudentParticipation participation = (StudentParticipation) firstResult.getSubmission().getParticipation();
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
        Submission submission = result.getSubmission();
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(1), submission);
        submissionRepository.save(submission);
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_afterExam_hidesResultsBeforeExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(4, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(1), result.getSubmission());
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);
        assertThat(participationUtilService.getResultsForParticipation(requestedParticipation)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetParticipationAllResults_showsResultsAfterExamResultsPublished() throws Exception {
        var result = setupExamExerciseWithParticipationAndResult(10, TEST_PREFIX + "student1");
        Submission submission = result.getSubmission();
        StudentParticipation participation = (StudentParticipation) result.getSubmission().getParticipation();
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now().minusMinutes(1), submission);
        submissionRepository.save(submission);
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK,
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

        assertThat(result).usingRecursiveComparison().ignoringFields("submission", "feedbacks", "participation", "lastModifiedDate").isEqualTo(resultResponse);
        if (withSubmission) {
            assertThat(submission).isEqualTo(resultResponse.getSubmission());
        }
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
        // Submission has a result, therefore not considered pending.

        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        submission.addResult(result);
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLatestPendingSubmissionIfNotExists_ta() throws Exception {
        // Submission has a result, therefore not considered pending.
        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        submission.addResult(result);
        Submission returnedSubmission = request.getNullable(participationsBaseUrl + submission.getParticipation().getId() + "/latest-pending-submission", HttpStatus.OK,
                ProgrammingSubmission.class);
        assertThat(returnedSubmission).isEqualTo(submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLatestPendingSubmissionIfNotExists_instructor() throws Exception {
        // Submission has a result, therefore not considered pending.
        Result result = resultRepository.save(new Result());
        ProgrammingSubmission submission = (ProgrammingSubmission) new ProgrammingSubmission().submissionDate(ZonedDateTime.now().minusSeconds(61L));
        submission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");
        submission.addResult(result);
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
        Map<Long, ProgrammingSubmission> submissions = new HashMap<>();
        submissions.put(submission.getParticipation().getId(), submission);
        submissions.put(submission2.getParticipation().getId(), submission2);
        submissions.put(notPendingSubmission.getParticipation().getId(), null);
        Map<Long, ProgrammingSubmission> returnedSubmissions = request.getMap(exercisesBaseUrl + programmingExercise.getId() + "/latest-pending-submissions", HttpStatus.OK,
                Long.class, ProgrammingSubmission.class);
        assertThat(returnedSubmissions).isEqualTo(submissions);
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

    private @NotNull String generateRandomRepoUrl(ProgrammingExerciseStudentParticipation participation, boolean valid) {
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
            String repoName = valid ? String.format("%s-%s", randomKey, randomName) : randomName;
            repoUrl = String.format("%s/%s/%s.git", basePath, randomKey, repoName);

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
        var programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithProgrammingExerciseAndExamDates(ZonedDateTime.now().plusHours(1),
                ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusHours(3), ZonedDateTime.now().plusHours(4), TEST_PREFIX + "student1", 1000);
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
        programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
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
            var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
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
            PATH_PREFIX = "/api/programming/programming-exercise/" + programmingExerciseWithAuxRepo.getId() + "/commit-history/";
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
                localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);
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

        var files = request.getMap("/api/programming/programming-exercise-participations/" + participation.getId() + "/files-content/" + commit.getName(), HttpStatus.OK,
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
            var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
            programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
            programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
            programmingExerciseIntegrationTestService.addAuxiliaryRepositoryToExercise(programmingExercise);
            RepositoryExportTestUtil.createAndWireBaseRepositories(localVCLocalCITestService, programmingExercise);
            programmingExerciseRepository.save(programmingExercise);

            participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
            Map<String, String> studentFiles = Map.of("student/Example.java", "class StudentExample {}\n");
            var studentParticipation = (ProgrammingExerciseStudentParticipation) participation;
            studentCommitHash = commitToParticipationRepository(studentParticipation, studentFiles, "Student detail commit").getName();

            templateCommitHash = commitToRepository(programmingExercise.getTemplateRepositoryUri(), Map.of("template/Example.java", "class TemplateDetail {}"),
                    "Template detail commit").getName();
            solutionCommitHash = commitToRepository(programmingExercise.getSolutionRepositoryUri(), Map.of("solution/Example.java", "class SolutionDetail {}"),
                    "Solution detail commit").getName();

            basePath = "/api/programming/programming-exercise/" + programmingExercise.getId() + "/files-content-commit-details/";
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnBadRequestWithoutAnyProvidedParameters() throws Exception {
            request.getMap(basePath + studentCommitHash, HttpStatus.BAD_REQUEST, String.class, String.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnForParticipation() throws Exception {
            var files = request.getMap(basePath + studentCommitHash + "?participationId=" + participation.getId(), HttpStatus.OK, String.class, String.class);
            assertThat(files).containsEntry("student/Example.java", "class StudentExample {}\n");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnFilesForTemplateRepository() throws Exception {
            var files = request.getMap(basePath + templateCommitHash + "?repositoryType=TEMPLATE", HttpStatus.OK, String.class, String.class);
            assertThat(files).containsEntry("template/Example.java", "class TemplateDetail {}");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldReturnFilesForSolutionRepository() throws Exception {
            var files = request.getMap(basePath + solutionCommitHash + "?repositoryType=SOLUTION", HttpStatus.OK, String.class, String.class);
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
        Result r = programmingExerciseUtilService.addProgrammingSubmissionWithResult(programmingExercise, submission, TEST_PREFIX + "student1");
        r.successful(true).rated(true).score(100D).assessmentType(assessmentType).completionDate(completionDate);

        return participationUtilService.addVariousVisibilityFeedbackToResult(r);
    }

    private TemplateProgrammingExerciseParticipation addTemplateParticipationWithResult() {
        programmingExerciseParticipation = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise)
                .getTemplateParticipation();
        Result r = programmingExerciseUtilService.addTemplateSubmissionWithResult(programmingExercise);
        r.successful(true).rated(true).score(100D).assessmentType(AssessmentType.AUTOMATIC).completionDate(null);

        participationUtilService.addVariousVisibilityFeedbackToResult(r);
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

    private RevCommit commitToRepository(VcsRepositoryUri repositoryUri, Map<String, String> files, String message) throws Exception {
        if (repositoryUri == null) {
            throw new IllegalStateException("Repository URI is not configured for this participation.");
        }
        LocalVCRepositoryUri localUri = repositoryUri instanceof LocalVCRepositoryUri local ? local : new LocalVCRepositoryUri(repositoryUri.toString());
        return commitToRepository(localUri, files, message);
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
        localVCLocalCITestService.createAndConfigureLocalRepository(repositoryUri.getProjectKey(), repositorySlug);
    }

    private RevCommit writeFilesAndPush(Path remoteRepoPath, Map<String, String> files, String message) throws Exception {
        Path workingCopy = Files.createTempDirectory(tempPath, "repo-clone");
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
            RevCommit commit = de.tum.cit.aet.artemis.programming.service.GitService.commit(git).setMessage(message).call();
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
        programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithProgrammingExerciseAndExamDates(visibilityDate, startDate, endDate, publishResultsDate,
                userLogin, workingTime);
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userLogin);
        return addStudentParticipationWithResult(AssessmentType.AUTOMATIC, startDate.plusMinutes(2));
    }

}
