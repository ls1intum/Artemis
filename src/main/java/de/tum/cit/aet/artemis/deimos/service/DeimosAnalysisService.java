package de.tum.cit.aet.artemis.deimos.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.SET_UP_TEMPLATE_FOR_EXERCISE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchSummaryDTO;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class DeimosAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DeimosAnalysisService.class);

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final DeimosLlmClient deimosLlmClient;

    private final DeimosPromptTemplateService deimosPromptTemplateService;

    private final RepositoryService repositoryService;

    private final GitService gitService;

    private static final String SYSTEM_PROMPT_PATH = "prompts/deimos/analyze_submission_system.st";

    private static final String USER_PROMPT_PATH = "prompts/deimos/analyze_submission_user.st";

    @Value("${artemis.deimos.debug.dump-prompts-dir:}")
    private String dumpPromptsDir;

    public DeimosAnalysisService(ProgrammingSubmissionRepository programmingSubmissionRepository, StudentParticipationRepository studentParticipationRepository,
            DeimosLlmClient deimosLlmClient, DeimosPromptTemplateService deimosPromptTemplateService, RepositoryService repositoryService, GitService gitService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.deimosLlmClient = deimosLlmClient;
        this.deimosPromptTemplateService = deimosPromptTemplateService;
        this.repositoryService = repositoryService;
        this.gitService = gitService;
    }

    public DeimosBatchSummaryDTO analyze(String runId, DeimosTriggerType triggerType, DeimosBatchScope scope, ZonedDateTime from, ZonedDateTime to, List<Long> participationIds) {
        long analyzed = 0;
        long failed = 0;
        long maliciousCount = 0;
        long benignCount = 0;
        List<DeimosBatchSummaryDTO.ParticipationAnalysis> analyzedParticipations = new ArrayList<>();

        for (Long participationId : participationIds) {
            try {
                var participation = studentParticipationRepository.findById(participationId).orElseThrow();
                if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
                    log.warn("Participation {} is not a ProgrammingExerciseParticipation, skipping", participationId);
                    failed++;
                    continue;
                }

                DeimosLlmRequest request = buildPrompt(participationId, programmingParticipation);
                dumpPromptIfEnabled(runId, request);
                DeimosLlmResponse response = deimosLlmClient.analyze(request);
                analyzedParticipations.add(new DeimosBatchSummaryDTO.ParticipationAnalysis(participationId, response.malicious(), response.rationale()));

                analyzed++;
                if (response.malicious()) {
                    maliciousCount++;
                }
                else {
                    benignCount++;
                }
            }
            catch (Exception ex) {
                failed++;
                log.warn("Deimos analysis failed for participation {}", participationId, ex);
            }
        }

        return new DeimosBatchSummaryDTO(runId, triggerType.name(), scope.name(), from, to, participationIds.size(), analyzed, maliciousCount, benignCount, failed,
                List.copyOf(analyzedParticipations));
    }

    private DeimosLlmRequest buildPrompt(long participationId, ProgrammingExerciseParticipation participation) {
        String commitHistory = buildParticipationCommitHistory(participationId, participation);
        String systemPrompt = deimosPromptTemplateService.render(SYSTEM_PROMPT_PATH, Map.of());
        String userPrompt = deimosPromptTemplateService.render(USER_PROMPT_PATH, Map.of("participationId", String.valueOf(participationId), "commitHistory", commitHistory));
        return new DeimosLlmRequest(participationId, systemPrompt, userPrompt);
    }

    private void dumpPromptIfEnabled(String runId, DeimosLlmRequest request) {
        if (dumpPromptsDir == null || dumpPromptsDir.isBlank()) {
            return;
        }
        try {
            Path dir = Path.of(dumpPromptsDir, runId);
            Files.createDirectories(dir);
            String filename = "participation_" + request.participationId() + ".txt";
            String content = "=== SYSTEM PROMPT ===\n" + request.systemPrompt() + "\n\n=== USER PROMPT ===\n" + request.userPrompt() + "\n";
            Files.writeString(dir.resolve(filename), content, StandardCharsets.UTF_8);
        }
        catch (IOException ex) {
            log.warn("Failed to dump Deimos prompt for participation {}", request.participationId(), ex);
        }
    }

    private String buildParticipationCommitHistory(long participationId, ProgrammingExerciseParticipation participation) {
        if (participation.getProgrammingExercise() == null || participation.getVcsRepositoryUri() == null) {
            return "";
        }

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findByParticipationIdOrderBySubmissionDateAsc(participationId);
        if (submissions.isEmpty()) {
            return "";
        }

        try (Repository repository = gitService.getBareRepository(participation.getVcsRepositoryUri(), false)) {
            String setupCommitHash = gitService.getFirstCommitWithMessage(repository, SET_UP_TEMPLATE_FOR_EXERCISE);
            if (setupCommitHash == null) {
                log.warn("No setup commit found for participation {}, falling back to empty template", participationId);
            }

            Map<String, String> templateFiles = setupCommitHash != null ? repositoryService.getFilesContentFromBareRepository(repository, setupCommitHash) : Map.of();

            var sb = new StringBuilder();
            Map<String, String> previousFiles = templateFiles;
            Map<String, String> latestFiles = templateFiles;

            for (int i = 0; i < submissions.size(); i++) {
                ProgrammingSubmission submission = submissions.get(i);
                if (submission.getCommitHash() == null || submission.getCommitHash().isBlank()) {
                    continue;
                }

                Map<String, String> currentFiles = repositoryService.getFilesContentFromBareRepository(repository, submission.getCommitHash());
                String incrementalDiff = buildDiff(previousFiles, currentFiles);

                if (!incrementalDiff.isEmpty()) {
                    String timestamp = submission.getSubmissionDate() != null ? submission.getSubmissionDate().toString() : "unknown";
                    sb.append("=== Commit ").append(i + 1).append(" (").append(submission.getCommitHash(), 0, Math.min(8, submission.getCommitHash().length())).append(", ")
                            .append(timestamp).append(") ===\n");
                    sb.append(incrementalDiff).append("\n\n");
                }

                previousFiles = currentFiles;
                latestFiles = currentFiles;
            }

            String cumulativeDiff = buildDiff(templateFiles, latestFiles);
            if (!cumulativeDiff.isEmpty()) {
                sb.append("=== Final state vs. template ===\n");
                sb.append(cumulativeDiff);
            }

            return sb.toString().stripTrailing();
        }
        catch (Exception ex) {
            log.warn("Could not build commit history for participation {}", participationId, ex);
            return "";
        }
    }

    private String buildDiff(Map<String, String> baseFiles, Map<String, String> targetFiles) {
        var allPaths = new TreeSet<String>();
        allPaths.addAll(baseFiles.keySet());
        allPaths.addAll(targetFiles.keySet());

        var sb = new StringBuilder();
        for (String path : allPaths) {
            String baseContent = baseFiles.get(path);
            String targetContent = targetFiles.get(path);

            if (baseContent == null) {
                sb.append("### Added: ").append(path).append("\n");
                sb.append(targetContent).append("\n\n");
            }
            else if (targetContent == null) {
                sb.append("### Deleted: ").append(path).append("\n\n");
            }
            else if (!baseContent.equals(targetContent)) {
                sb.append("### Modified: ").append(path).append("\n");
                sb.append(targetContent).append("\n\n");
            }
        }
        return sb.toString().stripTrailing();
    }
}
