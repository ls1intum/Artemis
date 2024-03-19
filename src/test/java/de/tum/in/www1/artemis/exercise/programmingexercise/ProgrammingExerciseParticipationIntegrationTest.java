package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.NoHeadException;
import org.junit.jupiter.api.BeforeEach;
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

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CommitInfoDTO;

class ProgrammingExerciseParticipationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "programmingexerciseparticipation";

    private final String participationsBaseUrl = "/api/programming-exercise-participations/";

    private final String exercisesBaseUrl = "/api/programming-exercises/";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private Participation programmingExerciseParticipation;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();
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
        StudentParticipation participation = (StudentParticipation) result.getParticipation();
        var expectedStatus = expectLastCreatedResult ? HttpStatus.OK : HttpStatus.NOT_FOUND;
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", expectedStatus,
                ProgrammingExerciseStudentParticipation.class);

        if (expectedStatus == HttpStatus.OK) {
            assertThat(requestedParticipation.getResults()).hasSize(1);
            var requestedResult = requestedParticipation.getResults().iterator().next();
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
        var secondResult = participationUtilService.addResultToParticipation(assessmentType, completionDate, programmingExerciseParticipation);
        StudentParticipation participation = (StudentParticipation) secondResult.getParticipation();

        // Expect the request to always be ok because it should at least return the first automatic result
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);

        assertThat(requestedParticipation.getResults()).hasSize(1);
        var requestedResult = requestedParticipation.getResults().iterator().next();

        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isInvisible);
        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isAfterDueDate);

        // Depending on the parameters we expect to get the first or the second created result from the server
        if (expectLastCreatedResult) {
            secondResult.filterSensitiveInformation();
            secondResult.filterSensitiveFeedbacks(true);
            assertThat(requestedResult).isEqualTo(secondResult);
        }
        else {
            firstResult.filterSensitiveInformation();
            firstResult.filterSensitiveFeedbacks(true);
            assertThat(requestedResult).isEqualTo(firstResult);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetParticipationWithLatestResultAsAnInstructor_noCompletionDate_notFound() throws Exception {
        var result = addStudentParticipationWithResult(AssessmentType.SEMI_AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getParticipation();
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-latest-result-and-feedbacks", HttpStatus.NOT_FOUND,
                ProgrammingExerciseStudentParticipation.class);
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
        var thirdResult = participationUtilService.addResultToParticipation(assessmentType, completionDate, programmingExerciseParticipation);
        StudentParticipation participation = (StudentParticipation) thirdResult.getParticipation();

        // Expect the request to always be ok because it should at least return the first automatic result
        var requestedParticipation = request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.OK,
                ProgrammingExerciseStudentParticipation.class);

        if (expectLastCreatedResult) {
            assertThat(requestedParticipation.getResults()).hasSize(3);
        }
        else {
            assertThat(requestedParticipation.getResults()).hasSize(2);
        }
        for (var result : requestedParticipation.getResults()) {
            assertThat(result.getFeedbacks()).noneMatch(Feedback::isInvisible);
            assertThat(result.getFeedbacks()).noneMatch(Feedback::isAfterDueDate);
        }
        firstResult.filterSensitiveInformation();
        firstResult.filterSensitiveFeedbacks(true);
        assertThat(requestedParticipation.getResults()).contains(firstResult);
        secondResult.filterSensitiveInformation();
        secondResult.filterSensitiveFeedbacks(true);
        assertThat(requestedParticipation.getResults()).contains(secondResult);

        // Depending on the parameters we expect to get the third result too
        if (expectLastCreatedResult) {
            thirdResult.filterSensitiveInformation();
            thirdResult.filterSensitiveFeedbacks(true);
            assertThat(requestedParticipation.getResults()).contains(thirdResult);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetParticipationWithAllResultsAsAnInstructor_noCompletionDate_notFound() throws Exception {
        var result = addStudentParticipationWithResult(AssessmentType.SEMI_AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getParticipation();
        request.get(participationsBaseUrl + participation.getId() + "/student-participation-with-all-results", HttpStatus.NOT_FOUND, ProgrammingExerciseStudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testGetParticipationWithAllResults_cannotAccessParticipation1() throws Exception {
        // student4 should have no connection to student1's participation and should thus receive a Forbidden HTTP status.
        var result = addStudentParticipationWithResult(AssessmentType.AUTOMATIC, null);
        StudentParticipation participation = (StudentParticipation) result.getParticipation();
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
        StudentParticipation participation = (StudentParticipation) result.getParticipation();
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
    void testGetLatestResultWithFeedbacksAsStudent() throws Exception {
        var result = addStudentParticipationWithResult(null, null);
        StudentParticipation participation = (StudentParticipation) result.getParticipation();
        var requestedResult = request.get(participationsBaseUrl + participation.getId() + "/latest-result-with-feedbacks", HttpStatus.OK, Result.class);

        assertThat(requestedResult.getFeedbacks()).noneMatch(Feedback::isInvisible);
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
                .findByExerciseIdAndStudentId(programmingExercise.getId(), userUtilService.getUserByLogin(TEST_PREFIX + "student1").getId()).get(0);

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
        participationUtilService.addResultToParticipation(null, null, programmingExerciseParticipation);

        final var response = request.get("/api/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/has-result", HttpStatus.OK, Boolean.class);

        assertThat(response).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkIfParticipationHasResult_withoutResult_returnsFalse() throws Exception {
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        final var response = request.get("/api/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/has-result", HttpStatus.OK, Boolean.class);

        assertThat(response).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_noAccess_forbidden() throws Exception {
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        request.put("/api/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/reset-repository", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_noAccessToGradedParticipation_forbidden() throws Exception {
        var gradedParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        var practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        practiceParticipation.setPracticeMode(true);
        participationRepository.save(practiceParticipation);

        request.put("/api/programming-exercise-participations/" + practiceParticipation.getId() + "/reset-repository?gradedParticipationId=" + gradedParticipation.getId(), null,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_participationLocked_forbidden() throws Exception {
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = participationUtilService
                .addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        programmingExerciseStudentParticipation.setLocked(true);
        programmingExerciseParticipation = programmingExerciseStudentParticipationRepository.save(programmingExerciseStudentParticipation);

        request.put("/api/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/reset-repository", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void checkResetRepository_exam_badRequest() throws Exception {
        programmingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");

        request.put("/api/programming-exercise-participations/" + programmingExerciseParticipation.getId() + "/reset-repository", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retrieveCommitInfoInstructorSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var commitInfo = new CommitInfoDTO("hash", "msg1", ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")), "author", "authorEmail");
        var commitInfo2 = new CommitInfoDTO("hash2", "msg2", ZonedDateTime.of(2020, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC")), "author2", "authorEmail2");
        doReturn(List.of(commitInfo, commitInfo2)).when(gitService).getCommitInfos(participation.getVcsRepositoryUri());
        request.getList("/api/programming-exercise-participations/" + participation.getId() + "/commits-info", HttpStatus.OK, CommitInfoDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void retrieveCommitInfoGitExceptionEmptyList() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        doThrow(new NoHeadException("error")).when(gitService).getCommitInfos(participation.getVcsRepositoryUri());
        assertThat(request.getList("/api/programming-exercise-participations/" + participation.getId() + "/commits-info", HttpStatus.OK, CommitInfoDTO.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void retrieveCommitInfoEditorForbidden() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        request.getList("/api/programming-exercise-participations/" + participation.getId() + "/commits-info", HttpStatus.FORBIDDEN, CommitInfoDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void retrieveCommitHistoryStudentSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var commitInfo = new CommitInfoDTO("hash", "msg1", ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")), "author", "authorEmail");
        var commitInfo2 = new CommitInfoDTO("hash2", "msg2", ZonedDateTime.of(2020, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC")), "author2", "authorEmail2");
        doReturn(List.of(commitInfo, commitInfo2)).when(gitService).getCommitInfos(participation.getVcsRepositoryUri());
        request.getList("/api/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.OK, CommitInfoDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void retrieveCommitHistoryStudentNotOwningParticipationForbidden() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        request.getList("/api/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.FORBIDDEN, CommitInfoDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveCommitHistoryTutorNotOwningParticipationSuccess() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var commitInfo = new CommitInfoDTO("hash", "msg1", ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")), "author", "authorEmail");
        var commitInfo2 = new CommitInfoDTO("hash2", "msg2", ZonedDateTime.of(2020, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC")), "author2", "authorEmail2");
        doReturn(List.of(commitInfo, commitInfo2)).when(gitService).getCommitInfos(participation.getVcsRepositoryUri());
        request.getList("/api/programming-exercise-participations/" + participation.getId() + "/commit-history", HttpStatus.OK, CommitInfoDTO.class);
    }

    private Result addStudentParticipationWithResult(AssessmentType assessmentType, ZonedDateTime completionDate) {
        programmingExerciseParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        Result r = participationUtilService.addResultToParticipation(assessmentType, completionDate, programmingExerciseParticipation);
        return participationUtilService.addVariousVisibilityFeedbackToResult(r);
    }

    private TemplateProgrammingExerciseParticipation addTemplateParticipationWithResult() {
        programmingExerciseParticipation = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise).getTemplateParticipation();
        Result r = participationUtilService.addResultToParticipation(AssessmentType.AUTOMATIC, null, programmingExerciseParticipation);
        participationUtilService.addVariousVisibilityFeedbackToResult(r);
        return (TemplateProgrammingExerciseParticipation) programmingExerciseParticipation;
    }

    private SolutionProgrammingExerciseParticipation addSolutionParticipationWithResult() {
        programmingExerciseParticipation = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise).getSolutionParticipation();
        Result result = participationUtilService.addResultToParticipation(AssessmentType.AUTOMATIC, null, programmingExerciseParticipation);
        participationUtilService.addVariousVisibilityFeedbackToResult(result);
        return (SolutionProgrammingExerciseParticipation) programmingExerciseParticipation;
    }

}
