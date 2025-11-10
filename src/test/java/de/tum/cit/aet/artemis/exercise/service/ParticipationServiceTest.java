package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.web.ResultResource;
import de.tum.cit.aet.artemis.buildagent.util.BuildJobUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipationServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "participationservice";

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildJobUtilService buildJobUtilService;

    private ProgrammingExercise programmingExercise;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        this.programmingExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        // TODO: is this actually needed?
        closeable = MockitoAnnotations.openMocks(this);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Test for methods of {@link ParticipationService} used by {@link ResultResource#createResultForExternalSubmission(Long, String, Result)}.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateParticipationForExternalSubmission() throws Exception {
        Optional<User> student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1");
        participationUtilService.mockCreationOfExerciseParticipation(false, null, programmingExercise, uriService, versionControlService, continuousIntegrationService);

        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(programmingExercise, student.orElseThrow(),
                SubmissionType.EXTERNAL);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student.get());
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().orElseThrow();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.EXTERNAL);
        assertThat(programmingSubmission.getResults()).isNullOrEmpty(); // results are not added in the invoked method above
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildJobsForResultsOfParticipation() throws Exception {
        User student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        StudentParticipation participation = setupParticipation(programmingExercise, student, SubmissionType.EXTERNAL);

        Map<Long, String> resultBuildJobMap = resultService.getLogsAvailabilityForResults(participation.getId());
        assertThat(resultBuildJobMap).hasSize(1);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student);
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().orElseThrow();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.EXTERNAL);
        assertThat(programmingSubmission.getResults()).hasSize(1);
    }

    @NotNull
    private StudentParticipation setupParticipation(ProgrammingExercise programmingExercise, User student, SubmissionType external) throws URISyntaxException {
        participationUtilService.mockCreationOfExerciseParticipation(false, null, programmingExercise, uriService, versionControlService, continuousIntegrationService);
        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(programmingExercise, student, external);
        Submission submission = participation.getSubmissions().iterator().next();
        Result result = participationUtilService.addResultToSubmission(participation, submission);
        buildJobUtilService.addBuildJobForParticipationId(participation.getId(), programmingExercise.getId(), result);
        return participation;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildJobsForResultsOfExamParticipation() throws Exception {
        User student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(TEST_PREFIX + "student1").orElseThrow();
        ProgrammingExercise examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(examExercise);
        StudentParticipation participation = setupParticipation(examExercise, student, SubmissionType.INSTRUCTOR);

        Map<Long, String> resultBuildJobMap = resultService.getLogsAvailabilityForResults(participation.getId());
        assertThat(resultBuildJobMap).hasSize(1);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student);
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().orElseThrow();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.INSTRUCTOR);
        assertThat(programmingSubmission.getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void canStartExerciseWithPracticeParticipationAfterDueDateChange() throws URISyntaxException {
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        participationUtilService.mockCreationOfExerciseParticipation(false, null, programmingExercise, uriService, versionControlService, continuousIntegrationService);

        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().minusHours(1));
        StudentParticipation practiceParticipation = participationService.startPracticeMode(programmingExercise, participant, Optional.empty(), false);

        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().plusHours(1));
        StudentParticipation studentParticipationReceived = participationService.startExercise(programmingExercise, participant, true);

        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        assertThat(studentParticipationReceived.getId()).isNotEqualTo(practiceParticipation.getId());
        assertThat(programmingExercise.getStudentParticipations()).hasSize(2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(value = ExerciseType.class, names = { "PROGRAMMING", "TEXT" })
    void testStartExercise_newParticipation(ExerciseType exerciseType) {
        Course course;
        if (exerciseType == ExerciseType.PROGRAMMING) {
            course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
            setUpProgrammingExerciseMocks();
        }
        else {
            course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        }
        Exercise exercise = course.getExercises().iterator().next();
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation studentParticipationReceived = participationService.startExercise(exercise, participant, true);

        assertThat(studentParticipationReceived.getExercise()).isEqualTo(exercise);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        // Acceptance range, initializationDate is to be set to now()
        assertThat(studentParticipationReceived.getInitializationDate()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationDate()).isBeforeOrEqualTo(ZonedDateTime.now().plusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    private void setUpProgrammingExerciseMocks() {
        doReturn("fake-build-plan-id").when(continuousIntegrationService).copyBuildPlan(any(), anyString(), any(), anyString(), anyString(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(ProgrammingExerciseParticipation.class));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void testStartPracticeMode(boolean useGradedParticipation) throws URISyntaxException {
        exerciseUtilService.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().minusMinutes(2));
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Result gradedResult = participationUtilService.addProgrammingParticipationWithResultForExercise(programmingExercise, TEST_PREFIX + "student1");

        participationUtilService.mockCreationOfExerciseParticipation(useGradedParticipation, gradedResult, programmingExercise, uriService, versionControlService,
                continuousIntegrationService);

        StudentParticipation studentParticipationReceived = participationService.startPracticeMode(programmingExercise, participant,
                Optional.of((StudentParticipation) gradedResult.getSubmission().getParticipation()), useGradedParticipation);

        assertThat(studentParticipationReceived.isPracticeMode()).isTrue();
        assertThat(studentParticipationReceived.getExercise()).isEqualTo(programmingExercise);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        // Acceptance range, initializationDate is to be set to now()
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

}
