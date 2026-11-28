package de.tum.cit.aet.artemis.iris.service.pyris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

class PyrisSolutionReferenceTest {

    private RepositoryService repositories;

    private PyrisDTOService service;

    private ProgrammingExercise exercise;

    @BeforeEach
    void setup() {
        repositories = mock(RepositoryService.class);
        service = new PyrisDTOService(repositories, mock(ProgrammingFeedbackSynthesizerService.class), mock(BuildLogEntryService.class), new IrisProactiveProperties());
        exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        var solution = new SolutionProgrammingExerciseParticipation();
        solution.setProgrammingExercise(exercise);
        solution.setRepositoryUri("http://localhost:8080/git/ABC/abc-solution.git");
        exercise.setSolutionParticipation(solution);
        var template = new TemplateProgrammingExerciseParticipation();
        template.setProgrammingExercise(exercise);
        template.setRepositoryUri("http://localhost:8080/git/ABC/abc-template.git");
        exercise.setTemplateParticipation(template);
        exercise.setTestRepositoryUri("http://localhost:8080/git/ABC/abc-tests.git");
    }

    @Test
    void readsOnlySolutionCodeAndNeverTemplateOrTestRepositories() throws IOException {
        var uri = exercise.getSolutionParticipation().getVcsRepositoryUri();
        when(repositories.getContextFilesContentFromBareRepositoryForLastCommit(eq(uri), any(), anyLong()))
                .thenReturn(Map.of("src/Main.java", "INSTRUCTOR SOLUTION", "data/private.csv", "UNRELATED RESOURCE"));
        var result = service.toPyrisProgrammingExerciseWithSolutionDTO(exercise);
        assertThat(result.solutionRepository()).containsExactly(Map.entry("src/Main.java", "INSTRUCTOR SOLUTION"));
        assertThat(result.templateRepository()).isEmpty();
        assertThat(result.testRepository()).isEmpty();
        verify(repositories).getContextFilesContentFromBareRepositoryForLastCommit(eq(uri), any(), anyLong());
        verifyNoMoreInteractions(repositories);
    }

    @Test
    void missingSolutionKeepsTheReferenceEmpty() {
        exercise.setSolutionParticipation(null);
        assertThat(service.toPyrisProgrammingExerciseWithSolutionDTO(exercise).solutionRepository()).isEmpty();
        verifyNoInteractions(repositories);
    }

    @Test
    void unreadableSolutionKeepsTheReferenceEmpty() throws IOException {
        when(repositories.getContextFilesContentFromBareRepositoryForLastCommit(any(), any(), anyLong())).thenThrow(new IOException("Synthetic unavailable repository"));
        assertThat(service.toPyrisProgrammingExerciseWithSolutionDTO(exercise).solutionRepository()).isEmpty();
    }

    @Test
    void ordinaryExerciseMetadataDoesNotReadTheSolution() {
        assertThat(service.toPyrisProgrammingExerciseMetadataDTO(exercise).solutionRepository()).isEmpty();
        verifyNoInteractions(repositories);
    }
}
