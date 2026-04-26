package de.tum.cit.aet.artemis.deimos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

class DeimosAnalysisServiceTest {

    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    private StudentParticipationRepository studentParticipationRepository;

    private DeimosLlmClient deimosLlmClient;

    private RepositoryService repositoryService;

    private GitService gitService;

    private DeimosPromptTemplateService deimosPromptTemplateService;

    private DeimosAnalysisService deimosAnalysisService;

    private Repository bareRepository;

    @BeforeEach
    void setUp() {
        programmingSubmissionRepository = Mockito.mock(ProgrammingSubmissionRepository.class);
        studentParticipationRepository = Mockito.mock(StudentParticipationRepository.class);
        deimosLlmClient = Mockito.mock(DeimosLlmClient.class);
        repositoryService = Mockito.mock(RepositoryService.class);
        gitService = Mockito.mock(GitService.class);
        deimosPromptTemplateService = new DeimosPromptTemplateService();
        bareRepository = Mockito.mock(Repository.class);

        deimosAnalysisService = new DeimosAnalysisService(programmingSubmissionRepository, studentParticipationRepository, deimosLlmClient, deimosPromptTemplateService,
                repositoryService, gitService);
    }

    @Test
    void analyzeBuildsCommitHistoryWithIncrementalAndCumulativeDiffs() throws Exception {
        long participationId = 10L;
        var participation = Mockito.mock(ProgrammingExerciseStudentParticipation.class);
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        var repoUri = Mockito.mock(LocalVCRepositoryUri.class);

        when(studentParticipationRepository.findById(participationId)).thenReturn(Optional.of(participation));
        when(participation.getProgrammingExercise()).thenReturn(exercise);
        when(participation.getVcsRepositoryUri()).thenReturn(repoUri);

        var sub1 = createSubmission(1L, "commit1", ZonedDateTime.now().minusHours(2));
        var sub2 = createSubmission(2L, "commit2", ZonedDateTime.now().minusHours(1));

        when(programmingSubmissionRepository.findByParticipationIdOrderBySubmissionDateAsc(participationId)).thenReturn(List.of(sub1, sub2));
        when(gitService.getBareRepository(repoUri, false)).thenReturn(bareRepository);
        when(gitService.getFirstCommitWithMessage(bareRepository, "Set up template for exercise")).thenReturn("setup000");

        Map<String, String> templateFiles = Map.of("src/Main.java", "class Main {}");
        Map<String, String> commit1Files = Map.of("src/Main.java", "class Main { void probe() {} }");
        Map<String, String> commit2Files = Map.of("src/Main.java", "class Main { void probe() {} }", "src/Evil.java", "class Evil {}");

        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "setup000")).thenReturn(templateFiles);
        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "commit1")).thenReturn(commit1Files);
        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "commit2")).thenReturn(commit2Files);
        when(deimosLlmClient.analyze(any())).thenReturn(new DeimosLlmResponse(true, "Incremental probing detected"));

        DeimosBatchSummaryDTO summary = deimosAnalysisService.analyze("run-1", DeimosTriggerType.MANUAL, DeimosBatchScope.EXERCISE, ZonedDateTime.now().minusHours(3),
                ZonedDateTime.now(), List.of(participationId));

        ArgumentCaptor<DeimosLlmRequest> requestCaptor = ArgumentCaptor.forClass(DeimosLlmRequest.class);
        verify(deimosLlmClient).analyze(requestCaptor.capture());

        DeimosLlmRequest request = requestCaptor.getValue();
        assertThat(request.participationId()).isEqualTo(participationId);
        assertThat(request.systemPrompt()).contains("commit history");
        assertThat(request.userPrompt()).contains("Participation ID: 10");

        // Commit 1: incremental diff vs template — Modified Main.java
        assertThat(request.userPrompt()).contains("=== Commit 1");
        assertThat(request.userPrompt()).contains("### Modified: src/Main.java");

        // Commit 2: incremental diff vs commit 1 — Added Evil.java (Main.java unchanged)
        assertThat(request.userPrompt()).contains("=== Commit 2");
        assertThat(request.userPrompt()).contains("### Added: src/Evil.java");

        // Final cumulative diff vs template
        assertThat(request.userPrompt()).contains("=== Final state vs. template ===");

        assertThat(summary.analyzedParticipations()).hasSize(1);
        assertThat(summary.analyzedParticipations().getFirst().exerciseId()).isEqualTo(42L);
        assertThat(summary.analyzedParticipations().getFirst().malicious()).isTrue();
        assertThat(summary.analyzedParticipations().getFirst().rationale()).isEqualTo("Incremental probing detected");
        assertThat(summary.maliciousCount()).isEqualTo(1);
        assertThat(summary.benignCount()).isZero();
    }

    @Test
    void analyzeCountsLlmFailureAsFailed() throws Exception {
        long participationId = 20L;
        var participation = Mockito.mock(ProgrammingExerciseStudentParticipation.class);
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        var repoUri = Mockito.mock(LocalVCRepositoryUri.class);

        when(studentParticipationRepository.findById(participationId)).thenReturn(Optional.of(participation));
        when(participation.getProgrammingExercise()).thenReturn(exercise);
        when(participation.getVcsRepositoryUri()).thenReturn(repoUri);

        var sub = createSubmission(99L, "def456", ZonedDateTime.now().minusHours(1));
        when(programmingSubmissionRepository.findByParticipationIdOrderBySubmissionDateAsc(participationId)).thenReturn(List.of(sub));

        when(gitService.getBareRepository(repoUri, false)).thenReturn(bareRepository);
        when(gitService.getFirstCommitWithMessage(eq(bareRepository), any())).thenReturn("setup000");
        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "setup000")).thenReturn(Map.of());
        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "def456")).thenReturn(Map.of("src/App.java", "class App {}"));
        when(deimosLlmClient.analyze(any())).thenThrow(new IllegalStateException("ChatClient not configured"));

        DeimosBatchSummaryDTO summary = deimosAnalysisService.analyze("run-2", DeimosTriggerType.MANUAL, DeimosBatchScope.EXERCISE, ZonedDateTime.now().minusHours(2),
                ZonedDateTime.now(), List.of(participationId));

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.analyzed()).isZero();
        assertThat(summary.analyzedParticipations()).isEmpty();
    }

    @Test
    void analyzeShowsDeletedFilesInCommitHistory() throws Exception {
        long participationId = 30L;
        var participation = Mockito.mock(ProgrammingExerciseStudentParticipation.class);
        var exercise = new ProgrammingExercise();
        exercise.setId(42L);
        var repoUri = Mockito.mock(LocalVCRepositoryUri.class);

        when(studentParticipationRepository.findById(participationId)).thenReturn(Optional.of(participation));
        when(participation.getProgrammingExercise()).thenReturn(exercise);
        when(participation.getVcsRepositoryUri()).thenReturn(repoUri);

        var sub = createSubmission(50L, "del789", ZonedDateTime.now().minusHours(1));
        when(programmingSubmissionRepository.findByParticipationIdOrderBySubmissionDateAsc(participationId)).thenReturn(List.of(sub));

        when(gitService.getBareRepository(repoUri, false)).thenReturn(bareRepository);
        when(gitService.getFirstCommitWithMessage(eq(bareRepository), any())).thenReturn("setup000");
        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "setup000")).thenReturn(Map.of("src/Helper.java", "class Helper {}"));
        when(repositoryService.getFilesContentFromBareRepository(bareRepository, "del789")).thenReturn(Map.of());
        when(deimosLlmClient.analyze(any())).thenReturn(new DeimosLlmResponse(false, "File deletion only"));

        deimosAnalysisService.analyze("run-3", DeimosTriggerType.MANUAL, DeimosBatchScope.EXERCISE, ZonedDateTime.now().minusHours(2), ZonedDateTime.now(),
                List.of(participationId));

        ArgumentCaptor<DeimosLlmRequest> requestCaptor = ArgumentCaptor.forClass(DeimosLlmRequest.class);
        verify(deimosLlmClient).analyze(requestCaptor.capture());

        assertThat(requestCaptor.getValue().userPrompt()).contains("### Deleted: src/Helper.java");
    }

    private static ProgrammingSubmission createSubmission(long id, String commitHash, ZonedDateTime submissionDate) {
        var submission = Mockito.mock(ProgrammingSubmission.class);
        when(submission.getId()).thenReturn(id);
        when(submission.getCommitHash()).thenReturn(commitHash);
        when(submission.getSubmissionDate()).thenReturn(submissionDate);
        return submission;
    }
}
