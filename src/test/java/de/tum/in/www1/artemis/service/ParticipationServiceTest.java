package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.UserRepository;

class ParticipationServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserRepository userRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        database.addUsers(3, 0, 0, 1);
        Course course = database.addCourseWithOneProgrammingExercise();
        this.programmingExercise = database.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        MockitoAnnotations.openMocks(this);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        database.resetDatabase();
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    /**
     * Test for methods of {@link ParticipationService} used by {@link de.tum.in.www1.artemis.web.rest.ResultResource#createResultForExternalSubmission(Long, String, Result)}.
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCreateParticipationForExternalSubmission() throws Exception {
        Optional<User> student = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1");
        var someURL = new VcsRepositoryUrl("http://vcs.fake.fake");
        // Copy Repository in ParticipationService#copyRepository(..)
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), any(String.class), any(String.class), any(String.class), any(String.class));
        // Configure Repository in ParticipationService#configureRepository(..)
        doNothing().when(versionControlService).configureRepository(any(), any(), anyBoolean());
        // Configure WebHook in ParticipationService#configureRepositoryWebHook(..)
        doNothing().when(versionControlService).addWebHookForParticipation(any());
        // Do Nothing when setRepositoryPermissionsToReadOnly in ParticipationService#createParticipationWithEmptySubmissionIfNotExisting
        doNothing().when(versionControlService).setRepositoryPermissionsToReadOnly(any(), any(String.class), any());
        // Return the default branch for all repositories of the exercise
        doReturn(defaultBranch).when(versionControlService).getOrRetrieveBranchOfExercise(programmingExercise);

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

}
