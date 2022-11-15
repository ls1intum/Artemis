package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

class ParticipationServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    private ProgrammingExercise programmingExercise;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        database.addUsers(3, 0, 0, 1);
        Course course = database.addCourseWithOneProgrammingExercise();
        this.programmingExercise = database.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        closeable = MockitoAnnotations.openMocks(this);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        database.resetDatabase();
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateParticipationForExternalSubmission() throws Exception {
        Optional<User> student = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1");
        mockCreationOfExerciseParticipation(false, null);

        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(programmingExercise, student.get(), SubmissionType.EXTERNAL);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getStudent()).contains(student.get());
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.findLatestSubmission().get();
        assertThat(programmingSubmission.getType()).isEqualTo(SubmissionType.EXTERNAL);
        assertThat(programmingSubmission.getResults()).isNullOrEmpty(); // results are not added in the invoked method above
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStartExerciseWithInitializationDate_newParticipation() {
        Course course = database.addCourseWithOneReleasedTextExercise();
        Exercise modelling = course.getExercises().iterator().next();
        Participant participant = database.getUserByLogin("student1");
        ZonedDateTime initializationDate = ZonedDateTime.now().minusHours(5);

        StudentParticipation studentParticipationReceived = participationService.startExerciseWithInitializationDate(modelling, participant, true, initializationDate);

        assertEquals(modelling, studentParticipationReceived.getExercise());
        assertTrue(studentParticipationReceived.getStudent().isPresent());
        assertEquals(participant, studentParticipationReceived.getStudent().get());
        assertEquals(initializationDate, studentParticipationReceived.getInitializationDate());
        assertEquals(InitializationState.INITIALIZED, studentParticipationReceived.getInitializationState());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void canStartExerciseWithPracticeParticipationAfterDueDateChange() throws URISyntaxException {
        Participant participant = database.getUserByLogin("student1");
        mockCreationOfExerciseParticipation(false, null);

        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        StudentParticipation practiceParticipation = participationService.startPracticeMode(programmingExercise, participant, Optional.empty(), false);

        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        StudentParticipation studentParticipationReceived = participationService.startExercise(programmingExercise, participant, true);

        programmingExercise = programmingExerciseRepository.findWithAllParticipationsById(programmingExercise.getId()).get();

        assertNotEquals(practiceParticipation.getId(), studentParticipationReceived.getId());
        assertThat(programmingExercise.getStudentParticipations()).hasSize(2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStartExercise_newParticipation() {
        Course course = database.addCourseWithOneReleasedTextExercise();
        Exercise modelling = course.getExercises().iterator().next();
        Participant participant = database.getUserByLogin("student1");

        StudentParticipation studentParticipationReceived = participationService.startExercise(modelling, participant, true);

        assertEquals(modelling, studentParticipationReceived.getExercise());
        assertTrue(studentParticipationReceived.getStudent().isPresent());
        assertEquals(participant, studentParticipationReceived.getStudent().get());
        // Acceptance range, initializationDate is to be set to now()
        assertTrue(ZonedDateTime.now().minusSeconds(10).isBefore(studentParticipationReceived.getInitializationDate()));
        assertTrue(ZonedDateTime.now().plusSeconds(10).isAfter(studentParticipationReceived.getInitializationDate()));
        assertEquals(InitializationState.INITIALIZED, studentParticipationReceived.getInitializationState());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void testStartPracticeMode(boolean useGradedParticipation) throws URISyntaxException {
        database.updateExerciseDueDate(programmingExercise.getId(), ZonedDateTime.now().minusMinutes(2));
        Participant participant = database.getUserByLogin("student1");
        Result gradedResult = database.addProgrammingParticipationWithResultForExercise(programmingExercise, "student1");

        mockCreationOfExerciseParticipation(useGradedParticipation, gradedResult);

        StudentParticipation studentParticipationReceived = participationService.startPracticeMode(programmingExercise, participant,
                Optional.of((StudentParticipation) gradedResult.getParticipation()), useGradedParticipation);

        assertTrue(studentParticipationReceived.isTestRun());
        assertEquals(programmingExercise, studentParticipationReceived.getExercise());
        assertTrue(studentParticipationReceived.getStudent().isPresent());
        assertEquals(participant, studentParticipationReceived.getStudent().get());
        // Acceptance range, initializationDate is to be set to now()
        assertTrue(ZonedDateTime.now().minusSeconds(10).isBefore(studentParticipationReceived.getInitializationDate()));
        assertTrue(ZonedDateTime.now().plusSeconds(10).isAfter(studentParticipationReceived.getInitializationDate()));
        assertEquals(InitializationState.INITIALIZED, studentParticipationReceived.getInitializationState());
    }

    private void mockCreationOfExerciseParticipation(boolean useGradedParticipationOfResult, Result gradedResult) throws URISyntaxException {
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfStudentParticipation(any());
        String templateRepoName;
        if (useGradedParticipationOfResult) {
            templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(((ProgrammingExerciseStudentParticipation) gradedResult.getParticipation()).getVcsRepositoryUrl());
        }
        else {
            templateRepoName = urlService.getRepositorySlugFromRepositoryUrl(programmingExercise.getVcsTemplateRepositoryUrl());
        }
        var someURL = new VcsRepositoryUrl("http://vcs.fake.fake");
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), eq(templateRepoName), any(String.class), any(String.class), any(String.class));
        doNothing().when(versionControlService).configureRepository(any(), any(), anyBoolean());
        doReturn("buildPlanId").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(), any());
        doNothing().when(continuousIntegrationService).performEmptySetupCommit(any());
        doNothing().when(versionControlService).addWebHookForParticipation(any());
    }
}
