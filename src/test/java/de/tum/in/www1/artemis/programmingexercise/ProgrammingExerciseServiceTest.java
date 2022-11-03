package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;

class ProgrammingExerciseServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseService programmingExerciseService;

    private ProgrammingExercise programmingExercise1;

    private ProgrammingExercise programmingExercise2;

    @BeforeEach
    void init() {
        database.addUsers(0, 0, 0, 2);
        database.addCourseWithOneProgrammingExercise();
        database.addCourseWithOneProgrammingExercise();

        programmingExercise1 = programmingExerciseRepository.findAll().get(0);
        programmingExercise2 = programmingExerciseRepository.findAll().get(1);

        programmingExercise1.setReleaseDate(null);
        programmingExercise2.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExercise1);
        programmingExerciseRepository.save(programmingExercise2);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldFindProgrammingExerciseWithBuildAndTestDateInFuture() {
        programmingExercise1.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(programmingExercise1);
        programmingExercise2.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(programmingExercise2);

        List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAllWithBuildAndTestAfterDueDateInFuture();

        assertThat(programmingExercises).hasSize(1);
        assertThat(programmingExercises.get(0)).isEqualTo(programmingExercise1);
    }

    @Test
    void shouldLockRepositoriesWhenOfflineIDEGetsForbidden() {
        programmingExercise1.setAllowOfflineIde(true);
        programmingExercise2.setAllowOfflineIde(false);
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenOfflineIDEGetsAllowed() {
        programmingExercise1.setAllowOfflineIde(false);
        programmingExercise2.setAllowOfflineIde(true);
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldNotUnlockRepositoriesWhenOfflineIDEGetsAllowedAndDueDateInPast() {
        programmingExercise1.setAllowOfflineIde(false);
        programmingExercise2.setAllowOfflineIde(true);
        programmingExercise1.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise2.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldLockRepositoriesWhenDueDateIsSetInThePast() {
        programmingExercise2.setAllowOfflineIde(true);

        programmingExercise2.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendLockAllRepositories(programmingExercise1.getId());

        programmingExercise1.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(2)).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenDueDateIsSetInTheFuture() {
        programmingExercise2.setAllowOfflineIde(true);

        programmingExercise1.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendUnlockAllRepositories(programmingExercise1.getId());

        programmingExercise2.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(2)).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldNotUnlockRepositoriesWhenDueDateIsSetInTheFutureAndNoOfflineIDE() {
        programmingExercise2.setAllowOfflineIde(false);

        programmingExercise1.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        programmingExercise2.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldLockRepositoriesWhenExerciseGetsUnreleased() {
        programmingExercise1.setAllowOfflineIde(true);
        programmingExercise2.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        verify(instanceMessageSendService, times(1)).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenExerciseGetsReleasedImmediately() {
        programmingExercise1.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExercise2.setAllowOfflineIde(true);
        programmingExerciseService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        verify(instanceMessageSendService, times(1)).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }
}
