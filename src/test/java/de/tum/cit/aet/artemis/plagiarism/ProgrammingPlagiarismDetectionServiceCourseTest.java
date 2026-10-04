package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismWebsocketService;
import de.tum.cit.aet.artemis.plagiarism.service.ProgrammingPlagiarismDetectionService;
import de.tum.cit.aet.artemis.plagiarism.service.cache.PlagiarismCacheService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.programming.service.UriService;

class ProgrammingPlagiarismDetectionServiceCourseTest {

    @Test
    void checkPlagiarism_withExerciseWithoutCourse_isRejectedBeforeClaimingTheCheck() {
        var programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        var plagiarismCacheService = mock(PlagiarismCacheService.class);
        var service = new ProgrammingPlagiarismDetectionService(mock(FileService.class), programmingExerciseRepository, mock(PlagiarismService.class), mock(GitService.class),
                mock(StudentParticipationRepository.class), mock(ProgrammingExerciseExportService.class), mock(PlagiarismWebsocketService.class), plagiarismCacheService,
                mock(UriService.class));
        var exercise = new ProgrammingExercise();
        exercise.setId(5L);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(5L)).thenReturn(exercise);

        assertThatThrownBy(() -> service.checkPlagiarism(5L, 50f, 0, 0)).isInstanceOf(IllegalStateException.class).hasMessageContaining("5");
        verifyNoInteractions(plagiarismCacheService);
    }
}
