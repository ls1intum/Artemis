package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseRepositoryService;

class ProgrammingExerciseRepositoryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexreposervice";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private ProgrammingExercise programmingExercise1;

    private ProgrammingExercise programmingExercise2;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 0, 0, 0, 2);

        var course1 = database.addCourseWithOneProgrammingExercise();
        programmingExercise1 = database.getFirstExerciseWithType(course1, ProgrammingExercise.class);
        programmingExercise1.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExercise1);

        var course2 = database.addCourseWithOneProgrammingExercise();
        programmingExercise2 = database.getFirstExerciseWithType(course2, ProgrammingExercise.class);
        programmingExercise2.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExercise2);
    }

    @Test
    void shouldLockRepositoriesWhenOfflineIDEGetsForbidden() {
        programmingExercise1.setAllowOfflineIde(true);
        programmingExercise2.setAllowOfflineIde(false);
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenOfflineIDEGetsAllowed() {
        programmingExercise1.setAllowOfflineIde(false);
        programmingExercise2.setAllowOfflineIde(true);
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldNotUnlockRepositoriesWhenOfflineIDEGetsAllowedAndDueDateInPast() {
        programmingExercise1.setAllowOfflineIde(false);
        programmingExercise2.setAllowOfflineIde(true);
        programmingExercise1.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExercise2.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldLockRepositoriesWhenDueDateIsSetInThePast() {
        programmingExercise2.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendLockAllRepositoriesWithoutLaterIndividualDueDate(programmingExercise1.getId());

        programmingExercise1.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(2)).sendLockAllRepositoriesWithoutLaterIndividualDueDate(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldLockRepositoriesWhenDueDateIsSetInThePastAndNoDueDateBefore() {
        programmingExercise1.setDueDate(null);
        programmingExercise2.setDueDate(ZonedDateTime.now().minusHours(1));

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        verify(instanceMessageSendService, times(1)).sendLockAllRepositoriesWithoutLaterIndividualDueDate(programmingExercise1.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenDueDateIsSetInTheFuture() {
        programmingExercise1.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(1)).sendUnlockAllRepositoriesWithoutEarlierIndividualDueDate(programmingExercise1.getId());

        programmingExercise2.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, times(2)).sendUnlockAllRepositoriesWithoutEarlierIndividualDueDate(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldNotUnlockRepositoriesWhenDueDateIsSetInTheFutureAndNoOfflineIDE() {
        programmingExercise1.setAllowOfflineIde(false);
        programmingExercise2.setAllowOfflineIde(false);

        programmingExercise1.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        programmingExercise2.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldLockRepositoriesWhenExerciseGetsUnreleased() {
        programmingExercise2.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        verify(instanceMessageSendService, times(1)).sendLockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendUnlockAllRepositories(programmingExercise1.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenExerciseGetsReleasedImmediately() {
        programmingExercise1.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExercise1, programmingExercise2);

        verify(instanceMessageSendService, times(1)).sendUnlockAllRepositories(programmingExercise1.getId());
        verify(instanceMessageSendService, never()).sendLockAllRepositories(programmingExercise1.getId());
    }
}
