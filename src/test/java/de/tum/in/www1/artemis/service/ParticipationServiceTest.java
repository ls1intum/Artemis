package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.UserRepository;

public class ParticipationServiceTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private UserRepository userRepository;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        database.addUsers(3, 0, 0, 0);
        Course course = database.addCourseWithOneProgrammingExercise();
        this.programmingExercise = database.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Test for methods of {@link ParticipationService} used by {@link de.tum.in.www1.artemis.web.rest.ResultResource#createResultForExternalSubmission(Long, String, Result)}.
     */
    @Test
    public void testCreateParticipationForExternalSubmission() throws Exception {
        Optional<User> student = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1");
        var someURL = new VcsRepositoryUrl("http://vcs.fake.fake");
        // Copy Repository in ParticipationService#copyRepository(..)
        doReturn(someURL).when(versionControlService).copyRepository(any(String.class), any(String.class), any(String.class), any(String.class));
        // Configure Repository in ParticipationService#configureRepository(..)
        doNothing().when(versionControlService).configureRepository(any(), any(), any(), anyBoolean());
        // Configure WebHook in ParticipationService#configureRepositoryWebHook(..)
        doNothing().when(versionControlService).addWebHookForParticipation(any());
        // Do Nothing when setRepositoryPermissionsToReadOnly in ParticipationService#createParticipationWithEmptySubmissionIfNotExisting
        doNothing().when(versionControlService).setRepositoryPermissionsToReadOnly(any(), any(String.class), any());

        StudentParticipation participation = participationService.createParticipationWithEmptySubmissionIfNotExisting(programmingExercise, student.get(), SubmissionType.EXTERNAL);
        assertThat(participation).isNotNull();
    }

}
