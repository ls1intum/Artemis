package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.BuildLogEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class ParticipationServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "participationservice";

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private BuildLogEntryService buildLogEntryService;

    @Autowired
    private BuildLogEntryRepository buildLogEntryRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        this.programmingExercise = exerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        // TODO: is this actually needed?
        closeable = MockitoAnnotations.openMocks(this);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Test for methods of {@link ParticipationService} used by {@link de.tum.in.www1.artemis.web.rest.ResultResource#createResultForExternalSubmission(Long, String, Result)}.
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartExerciseWithInitializationDate_newParticipation() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise modelling = course.getExercises().iterator().next();
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        ZonedDateTime initializationDate = ZonedDateTime.now().minusHours(5);

        StudentParticipation studentParticipationReceived = participationService.startExerciseWithInitializationDate(modelling, participant, true, initializationDate);

        assertThat(studentParticipationReceived.getExercise()).isEqualTo(modelling);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        assertThat(studentParticipationReceived.getInitializationDate()).isEqualTo(initializationDate);
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
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

        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).orElseThrow();

        assertThat(studentParticipationReceived.getId()).isNotEqualTo(practiceParticipation.getId());
        assertThat(programmingExercise.getStudentParticipations()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartExercise_newParticipation() {
        Course course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise modelling = course.getExercises().iterator().next();
        Participant participant = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation studentParticipationReceived = participationService.startExercise(modelling, participant, true);

        assertThat(studentParticipationReceived.getExercise()).isEqualTo(modelling);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        // Acceptance range, initializationDate is to be set to now()
        assertThat(studentParticipationReceived.getInitializationDate()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationDate()).isBeforeOrEqualTo(ZonedDateTime.now().plusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
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
                Optional.of((StudentParticipation) gradedResult.getParticipation()), useGradedParticipation);

        assertThat(studentParticipationReceived.isPracticeMode()).isTrue();
        assertThat(studentParticipationReceived.getExercise()).isEqualTo(programmingExercise);
        assertThat(studentParticipationReceived.getStudent()).isPresent();
        assertThat(studentParticipationReceived.getStudent().get()).isEqualTo(participant);
        // Acceptance range, initializationDate is to be set to now()
        assertThat(studentParticipationReceived.getInitializationDate()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationDate()).isBeforeOrEqualTo(ZonedDateTime.now().plusSeconds(10));
        assertThat(studentParticipationReceived.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteParticipation_removesBuildLogEntries() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        var programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        // Setup: Create participation, submission and build log entries for template, solution and student
        var templateParticipation = programmingExerciseUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise).getTemplateParticipation();
        var templateSubmission = programmingExerciseUtilService.createProgrammingSubmission(templateParticipation, true);
        BuildLogEntry buildLogEntryTemplate = new BuildLogEntry(ZonedDateTime.now(), "Some sample build log");
        var templateSavedBuildLogs = buildLogEntryService.saveBuildLogs(List.of(buildLogEntryTemplate), templateSubmission);
        templateSubmission.setBuildLogEntries(templateSavedBuildLogs);
        programmingSubmissionRepository.save(templateSubmission);

        var solutionParticipation = programmingExerciseUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise).getSolutionParticipation();
        var solutionSubmission = programmingExerciseUtilService.createProgrammingSubmission(solutionParticipation, true);
        BuildLogEntry buildLogEntrySolution = new BuildLogEntry(ZonedDateTime.now(), "Some sample build log");
        var solutionSavedBuildLogs = buildLogEntryService.saveBuildLogs(List.of(buildLogEntrySolution), solutionSubmission);
        solutionSubmission.setBuildLogEntries(solutionSavedBuildLogs);
        programmingSubmissionRepository.save(solutionSubmission);

        var studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var studentSubmission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, true);
        BuildLogEntry buildLogEntryStudent = new BuildLogEntry(ZonedDateTime.now(), "Some sample build log");
        var studentSavedBuildLogs = buildLogEntryService.saveBuildLogs(List.of(buildLogEntryStudent), studentSubmission);
        studentSubmission.setBuildLogEntries(studentSavedBuildLogs);
        programmingSubmissionRepository.save(studentSubmission);

        // Delete and assert removal
        assertThat(buildLogEntryRepository.findById(templateSavedBuildLogs.get(0).getId())).isPresent();
        participationService.deleteResultsAndSubmissionsOfParticipation(templateParticipation.getId(), true);
        assertThat(buildLogEntryRepository.findById(templateSavedBuildLogs.get(0).getId())).isEmpty();

        assertThat(buildLogEntryRepository.findById(solutionSavedBuildLogs.get(0).getId())).isPresent();
        participationService.deleteResultsAndSubmissionsOfParticipation(solutionParticipation.getId(), true);
        assertThat(buildLogEntryRepository.findById(solutionSavedBuildLogs.get(0).getId())).isEmpty();

        assertThat(buildLogEntryRepository.findById(studentSavedBuildLogs.get(0).getId())).isPresent();
        participationService.deleteResultsAndSubmissionsOfParticipation(studentParticipation.getId(), true);
        assertThat(buildLogEntryRepository.findById(studentSavedBuildLogs.get(0).getId())).isEmpty();
    }
}
