package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseRepositoryService;

class ProgrammingExerciseRepositoryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    private ProgrammingExercise programmingExerciseBeforeUpdate;

    private ProgrammingExercise updatedProgrammingExercise;

    @BeforeEach
    void init() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseBeforeUpdate = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExerciseBeforeUpdate.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExerciseBeforeUpdate);

        updatedProgrammingExercise = programmingExerciseRepository.findById(programmingExerciseBeforeUpdate.getId()).orElseThrow();
    }

    @Test
    void shouldLockRepositoriesWhenOfflineIDEGetsForbidden() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(true);
        updatedProgrammingExercise.setAllowOfflineIde(false);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendLockAllStudentRepositories(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldUnlockRepositoriesWhenOfflineIDEGetsAllowed() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(true);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendUnlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldUnlockRepositoriesAndParticipationsWhenOfflineIDEGetsAllowedAndStartDateIsSetInThePast() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(true);
        programmingExerciseBeforeUpdate.setStartDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldLockRepositoriesAndParticipationsWhenDueDateIsSetInThePast() {
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldLockRepositoriesAndParticipationsWhenDueDateIsSetInThePastAndNoDueDateBefore() {
        programmingExerciseBeforeUpdate.setDueDate(null);
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldLockParticipationsWhenDueDateIsSetInThePastAndOfflineIDEGetsForbidden() {
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseBeforeUpdate.setAllowOfflineIde(true);
        updatedProgrammingExercise.setAllowOfflineIde(false);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendLockAllStudentParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldUnlockRepositoriesAndParticipationsWhenDueDateIsSetInTheFuture() {
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().minusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldNotUnlockRepositoriesWhenDueDateIsSetInTheFutureAndNoOfflineIDE() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(false);
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().minusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendUnlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldLockRepositoriesAndParticipationsWhenExerciseGetsUnreleased() {
        updatedProgrammingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendLockAllStudentRepositoriesAndParticipations(programmingExerciseBeforeUpdate.getId());
    }

    @Test
    void shouldUnlockRepositoriesAndParticipationsWhenExerciseGetsReleasedImmediately() {
        programmingExerciseBeforeUpdate.setReleaseDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService, times(1)).sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
    }
}
