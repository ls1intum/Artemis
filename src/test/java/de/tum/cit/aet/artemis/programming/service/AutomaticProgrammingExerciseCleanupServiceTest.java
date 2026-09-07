package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for the nightly cleanup of the checkouts an Artemis server accumulates.
 * <p>
 * Every checkout a build or an export produced stays on disk until this job removes it, so a job that quietly stops
 * working fills the disk of a production server, and one that removes too much deletes repositories that are still in
 * use. Neither shows up until it is too late, which is what these tests are for: the window it looks at, the
 * repositories it removes, and the guards that keep it from running where it should not.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticProgrammingExerciseCleanupServiceTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Mock
    private ParticipationDeletionService participationDeletionService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private GitService gitService;

    @InjectMocks
    private AutomaticProgrammingExerciseCleanupService cleanupService;

    private static ProgrammingExercise exerciseWithRepositories() {
        Course course = new Course();
        course.setShortName("course1");
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setCourse(course);
        exercise.setShortName("exercise1");
        exercise.generateAndSetProjectKey();
        // The template and solution URIs live on the participations, so the exercise needs them before the convenience setters do anything.
        exercise.setTemplateParticipation(new TemplateProgrammingExerciseParticipation());
        exercise.setSolutionParticipation(new SolutionProgrammingExerciseParticipation());
        exercise.setTemplateRepositoryUri("http://localhost:8080/git/ABC/abc-exercise.git");
        exercise.setSolutionRepositoryUri("http://localhost:8080/git/ABC/abc-solution.git");
        exercise.setTestRepositoryUri("http://localhost:8080/git/ABC/abc-tests.git");
        return exercise;
    }

    private void withoutAnyStudentParticipations() {
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByCourseExerciseDueDateBetween(any(), any(), any())).thenReturn(Page.empty());
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByExamExercisesEndDateBetween(any(), any(), any())).thenReturn(Page.empty());
    }

    private void withoutAnyExercises() {
        when(programmingExerciseRepository.findAllByRecentCourseEndDate(any(), any())).thenReturn(new java.util.ArrayList<>());
        when(programmingExerciseRepository.findAllByRecentExamEndDate(any(), any())).thenReturn(new java.util.ArrayList<>());
    }

    @Test
    void cleanup_outsideProduction_doesNothing() {
        // The job deletes repositories on disk, so it must never run against a developer's or a test server's checkouts.
        when(profileService.isProductionActive()).thenReturn(false);

        cleanupService.cleanup();

        verifyNoInteractions(gitService, programmingExerciseRepository, programmingExerciseStudentParticipationRepository);
    }

    @Test
    void cleanup_onLocalCI_skipsTheBuildPlanCleanupButStillRemovesTheCheckouts() {
        // Local CI has no build plans to clean up, but its checkouts still accumulate.
        when(profileService.isProductionActive()).thenReturn(true);
        when(profileService.isLocalCIActive()).thenReturn(true);
        withoutAnyStudentParticipations();
        withoutAnyExercises();

        cleanupService.cleanup();

        verify(programmingExerciseStudentParticipationRepository, never()).findAllWithBuildPlanIdWithResults();
        verify(programmingExerciseStudentParticipationRepository).findRepositoryUrisByCourseExerciseDueDateBetween(any(), any(), any());
    }

    @Test
    void cleanupGitWorkingCopies_removesTheThreeInstructorRepositoriesAndTheirFolder() {
        withoutAnyStudentParticipations();
        ProgrammingExercise exercise = exerciseWithRepositories();
        when(programmingExerciseRepository.findAllByRecentCourseEndDate(any(), any())).thenReturn(new java.util.ArrayList<>(List.of(exercise)));
        when(programmingExerciseRepository.findAllByRecentExamEndDate(any(), any())).thenReturn(new java.util.ArrayList<>());

        cleanupService.cleanupGitWorkingCopiesOnArtemisServer();

        // Deleting the three checkouts alone would leave the project directory behind, one per exercise, forever.
        verify(gitService).deleteLocalRepository(exercise.getVcsTemplateRepositoryUri());
        verify(gitService).deleteLocalRepository(exercise.getVcsSolutionRepositoryUri());
        verify(gitService).deleteLocalRepository(exercise.getVcsTestRepositoryUri());
        verify(gitService).deleteLocalProgrammingExerciseReposFolder(exercise);
    }

    @Test
    void cleanupGitWorkingCopies_looksAtExercisesThatEndedBetweenOneYearAndEightWeeksAgo() {
        withoutAnyStudentParticipations();
        withoutAnyExercises();

        cleanupService.cleanupGitWorkingCopiesOnArtemisServer();

        ArgumentCaptor<ZonedDateTime> earliest = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> latest = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(programmingExerciseRepository).findAllByRecentCourseEndDate(earliest.capture(), latest.capture());
        // Eight weeks of grace, so that a repository is not removed while an instructor is still marking, and one year back so that older leftovers are caught too.
        assertThat(latest.getValue()).isCloseTo(ZonedDateTime.now().minusWeeks(8).truncatedTo(ChronoUnit.DAYS), org.assertj.core.api.Assertions.within(1, ChronoUnit.DAYS));
        assertThat(earliest.getValue()).isEqualTo(latest.getValue().minusYears(1));
    }

    @Test
    void cleanupGitWorkingCopies_removesTheCheckoutOfEveryStudentParticipationItFinds() {
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByCourseExerciseDueDateBetween(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of("http://localhost:8080/git/ABC/abc-student1.git", "http://localhost:8080/git/ABC/abc-student2.git")));
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByExamExercisesEndDateBetween(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of("http://localhost:8080/git/ABC/abc-examstudent.git")));
        withoutAnyExercises();

        cleanupService.cleanupGitWorkingCopiesOnArtemisServer();

        ArgumentCaptor<LocalVCRepositoryUri> deleted = ArgumentCaptor.forClass(LocalVCRepositoryUri.class);
        verify(gitService, org.mockito.Mockito.times(3)).deleteLocalRepository(deleted.capture());
        assertThat(deleted.getAllValues()).extracting(uri -> uri.getURI().toString()).containsExactlyInAnyOrder("http://localhost:8080/git/ABC/abc-student1.git",
                "http://localhost:8080/git/ABC/abc-student2.git", "http://localhost:8080/git/ABC/abc-examstudent.git");
    }

    @Test
    void cleanupGitWorkingCopies_readsEveryPageOfParticipations() {
        // A term produces far more participations than fit in one page, and stopping after the first would leave most of them on disk.
        Pageable firstPage = PageRequest.of(0, 2);
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByCourseExerciseDueDateBetween(any(), any(), any())).thenReturn(
                new PageImpl<>(List.of("http://localhost:8080/git/ABC/abc-student1.git", "http://localhost:8080/git/ABC/abc-student2.git"), firstPage, 3),
                new PageImpl<>(List.of("http://localhost:8080/git/ABC/abc-student3.git"), firstPage.next(), 3));
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByExamExercisesEndDateBetween(any(), any(), any())).thenReturn(Page.empty());
        withoutAnyExercises();

        cleanupService.cleanupGitWorkingCopiesOnArtemisServer();

        verify(gitService, org.mockito.Mockito.times(3)).deleteLocalRepository(any(LocalVCRepositoryUri.class));
    }

    @Test
    void cleanupGitWorkingCopies_forAParticipationWithAnUnusableUri_carriesOnWithTheRest() {
        // One malformed repository URI in the database must not stop the whole night's cleanup.
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByCourseExerciseDueDateBetween(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of("not a repository uri", "http://localhost:8080/git/ABC/abc-student2.git")));
        when(programmingExerciseStudentParticipationRepository.findRepositoryUrisByExamExercisesEndDateBetween(any(), any(), any())).thenReturn(Page.empty());
        withoutAnyExercises();

        cleanupService.cleanupGitWorkingCopiesOnArtemisServer();

        ArgumentCaptor<LocalVCRepositoryUri> deleted = ArgumentCaptor.forClass(LocalVCRepositoryUri.class);
        verify(gitService).deleteLocalRepository(deleted.capture());
        assertThat(deleted.getValue().getURI().toString()).isEqualTo("http://localhost:8080/git/ABC/abc-student2.git");
    }
}
