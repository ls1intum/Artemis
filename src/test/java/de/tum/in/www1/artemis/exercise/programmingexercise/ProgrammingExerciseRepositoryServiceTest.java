package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseRepositoryService;
import de.tum.in.www1.artemis.user.UserUtilService;

class ProgrammingExerciseRepositoryServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "progexreposervice";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private ProgrammingExercise programmingExerciseBeforeUpdate;

    private ProgrammingExercise updatedProgrammingExercise;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private static final long TIMEOUT_MS = 1000;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseBeforeUpdate = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseBeforeUpdate, TEST_PREFIX + "student1");
        programmingExerciseBeforeUpdate.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExerciseBeforeUpdate);

        updatedProgrammingExercise = programmingExerciseRepository.findById(programmingExerciseBeforeUpdate.getId()).orElseThrow();
        // These methods take a long time so we only test that they were called => we don't care about the inner workings
        doNothing().when(programmingExerciseParticipationService).unlockStudentRepository(Mockito.any());
        doNothing().when(programmingExerciseParticipationService).unlockStudentParticipation(Mockito.any());
        doNothing().when(programmingExerciseParticipationService).lockStudentRepository(Mockito.any(), Mockito.any());
        doNothing().when(programmingExerciseParticipationService).lockStudentParticipation(Mockito.any());
    }

    @Test
    void shouldLockRepositoriesWhenOfflineIDEGetsForbidden() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(true);
        updatedProgrammingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendLockAllStudentRepositories(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentRepository(programmingExerciseBeforeUpdate, studentParticipation);
        verify(programmingExerciseParticipationService, never()).lockStudentParticipation(Mockito.any());
    }

    @Test
    void shouldUnlockRepositoriesWhenOfflineIDEGetsAllowed() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(true);
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendUnlockAllStudentRepositoriesWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentRepository(studentParticipation);
        verify(programmingExerciseParticipationService, never()).unlockStudentParticipation(Mockito.any());
    }

    @Test
    void shouldUnlockRepositoriesAndParticipationsWhenOfflineIDEGetsAllowedAndStartDateIsSetInThePast() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(true);
        programmingExerciseBeforeUpdate.setStartDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentRepository(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentParticipation(studentParticipation);
    }

    @Test
    void shouldLockRepositoriesAndParticipationsWhenDueDateIsSetInThePast() {
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentParticipation(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentRepository(programmingExerciseBeforeUpdate, studentParticipation);
    }

    @Test
    void shouldLockRepositoriesAndParticipationsWhenDueDateIsSetInThePastAndNoDueDateBefore() {
        programmingExerciseBeforeUpdate.setDueDate(null);
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendLockAllStudentRepositoriesAndParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentParticipation(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentRepository(programmingExerciseBeforeUpdate, studentParticipation);
    }

    @Test
    void shouldLockParticipationsAndRepositoriesSeparatelyWhenDueDateIsSetInThePastAndOfflineIDEGetsForbidden() {
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseBeforeUpdate.setAllowOfflineIde(true);
        updatedProgrammingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendLockAllStudentParticipationsWithEarlierDueDate(programmingExerciseBeforeUpdate.getId());
        verify(instanceMessageSendService).sendLockAllStudentRepositories(programmingExerciseBeforeUpdate.getId());

        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentParticipation(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentRepository(programmingExerciseBeforeUpdate, studentParticipation);
    }

    @Test
    void shouldUnlockRepositoriesAndParticipationsWhenDueDateIsSetInTheFuture() {
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().minusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentRepository(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentParticipation(studentParticipation);
    }

    @Test
    void shouldOnlyUnlockParticipationsWhenDueDateIsSetInTheFutureAndNoOfflineIDE() {
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(false);
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().minusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendUnlockAllStudentParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentParticipation(studentParticipation);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(Mockito.any());
    }

    @Test
    void shouldLockRepositoriesAndParticipationsWhenExerciseGetsUnreleased() {
        updatedProgrammingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendLockAllStudentRepositoriesAndParticipations(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentParticipation(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).lockStudentRepository(programmingExerciseBeforeUpdate, studentParticipation);
    }

    @Test
    void shouldUnlockRepositoriesAndParticipationsWhenExerciseGetsReleasedImmediately() {
        programmingExerciseBeforeUpdate.setReleaseDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(instanceMessageSendService).sendUnlockAllStudentRepositoriesAndParticipationsWithEarlierStartDateAndLaterDueDate(programmingExerciseBeforeUpdate.getId());
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentRepository(studentParticipation);
        verify(programmingExerciseParticipationService, timeout(TIMEOUT_MS)).unlockStudentParticipation(studentParticipation);
    }
}
