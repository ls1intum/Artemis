package de.tum.in.www1.artemis.programmingexercise;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseRepositoryService;

class ProgrammingExerciseRepositoryServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "progexreposervice";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private ProgrammingExercise programmingExerciseBeforeUpdate;

    private ProgrammingExercise updatedProgrammingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void init() {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        var course = database.addCourseWithOneProgrammingExercise();
        programmingExerciseBeforeUpdate = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExerciseBeforeUpdate.setReleaseDate(null);
        programmingExerciseRepository.save(programmingExerciseBeforeUpdate);

        updatedProgrammingExercise = programmingExerciseRepository.findById(programmingExerciseBeforeUpdate.getId()).orElseThrow();
    }

    private void addParticipation() {
        // Cannot happen in BeforeEach because this needs the authentication object set (@WithMockUser) which does not work on BeforeEach.
        participation = database.addStudentParticipationForProgrammingExercise(programmingExerciseBeforeUpdate, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldLockRepositoriesWhenOfflineIDEGetsForbidden() {
        addParticipation();
        programmingExerciseBeforeUpdate.setAllowOfflineIde(true);
        updatedProgrammingExercise.setAllowOfflineIde(false);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).lockStudentRepository(updatedProgrammingExercise, participation);
        verify(programmingExerciseParticipationService, never()).lockStudentParticipation(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUnlockRepositoriesWhenOfflineIDEGetsAllowed() {
        addParticipation();
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(true);
        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepository(updatedProgrammingExercise, participation);

        verify(programmingExerciseParticipationService, never()).unlockStudentParticipation(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldLockRepositoriesAndParticipationsWhenDueDateIsSetInThePast() {
        addParticipation();
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).lockStudentRepositoryAndParticipation(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldLockRepositoriesAndParticipationsWhenDueDateIsSetInThePastAndNoDueDateBefore() {
        addParticipation();
        programmingExerciseBeforeUpdate.setDueDate(null);
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).lockStudentRepositoryAndParticipation(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUnlockRepositoriesAndParticipationsWhenDueDateIsSetInTheFuture() {
        addParticipation();
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().minusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepositoryAndParticipation(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldNotUnlockRepositoriesWhenDueDateIsSetInTheFutureAndNoOfflineIDE() {
        addParticipation();
        programmingExerciseBeforeUpdate.setAllowOfflineIde(false);
        updatedProgrammingExercise.setAllowOfflineIde(false);
        programmingExerciseBeforeUpdate.setDueDate(ZonedDateTime.now().minusHours(1));
        updatedProgrammingExercise.setDueDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).unlockStudentParticipation(updatedProgrammingExercise, participation);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldLockRepositoriesAndParticipationsWhenExerciseGetsUnreleased() {
        addParticipation();
        updatedProgrammingExercise.setReleaseDate(ZonedDateTime.now().plusHours(1));
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).lockStudentRepositoryAndParticipation(updatedProgrammingExercise, participation);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void shouldUnlockRepositoriesAndParticipationsWhenExerciseGetsReleasedImmediately() {
        addParticipation();
        programmingExerciseBeforeUpdate.setReleaseDate(ZonedDateTime.now().plusHours(1));
        updatedProgrammingExercise.setReleaseDate(ZonedDateTime.now());
        programmingExerciseRepository.save(updatedProgrammingExercise);

        programmingExerciseRepositoryService.handleRepoAccessRightChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepositoryAndParticipation(updatedProgrammingExercise, participation);
    }
}
