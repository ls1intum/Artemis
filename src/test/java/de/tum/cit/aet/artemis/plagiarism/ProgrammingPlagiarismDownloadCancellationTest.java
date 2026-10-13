package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.CanceledException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismWebsocketService;
import de.tum.cit.aet.artemis.plagiarism.service.ProgrammingPlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.cache.PlagiarismCacheService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.programming.service.UriService;

class ProgrammingPlagiarismDownloadCancellationTest {

    @Test
    void canceledCheckoutDoesNotCancelTheNextRepositoryOnTheSameWorker() throws Exception {
        var git = mock(GitService.class);
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        var first = mock(ProgrammingExerciseParticipation.class);
        var second = mock(ProgrammingExerciseParticipation.class);
        var template = mock(Repository.class);
        var student = mock(Repository.class);
        var target = Path.of("plagiarism-check");
        when(git.getOrCheckoutRepository(exercise.getTemplateParticipation(), target, false)).thenReturn(template);
        when(git.getOrCheckoutRepositoryForJPlag(first, target)).thenAnswer(_ -> {
            Thread.currentThread().interrupt();
            throw new CanceledException("checkout interrupted");
        });
        when(git.getOrCheckoutRepositoryForJPlag(second, target)).thenAnswer(_ -> {
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            return student;
        });
        var service = new ProgrammingPlagiarismDetectionService(mock(FileService.class), mock(ProgrammingExerciseRepository.class), mock(PlagiarismService.class), git,
                mock(StudentParticipationRepository.class), mock(ProgrammingExerciseExportService.class), mock(PlagiarismWebsocketService.class),
                mock(PlagiarismCacheService.class), mock(UriService.class));
        @SuppressWarnings("unchecked")
        List<ProgrammingExerciseParticipation> participations = mock(List.class);
        when(participations.size()).thenReturn(2);
        // Use one worker so the test does not depend on the parallel stream's scheduling.
        when(participations.parallelStream()).thenReturn(Stream.of(first, second));

        try {
            List<Repository> downloaded = ReflectionTestUtils.invokeMethod(service, "downloadRepositories", exercise, participations, target, 0);
            assertThat(downloaded).containsExactly(template, student);
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
        }
        finally {
            Thread.interrupted();
        }
    }
}
