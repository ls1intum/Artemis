package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * The produced artifacts and integrity-gate inputs the post-loop {@link DifferentialVerificationService#verify} decides on, bundled so the "what to verify" travels as one value
 * next to the "where to run it" ({@code sandbox}, {@code sessionId}, {@code exercise}) the verifier keeps as separate arguments.
 * <p>
 * These are exactly the sandbox-free inputs the in-loop self-check cannot supply: the read-back repository files and the seed snapshot the agent loop lacks mid-session.
 *
 * @param seedTestsFiles               tests-repo files snapshotted at seed time; enables the harness-immutability and generated-source-layout gates
 * @param seedTemplateFiles            template-repo files snapshotted at seed time; unchanged legacy files remain valid during adaptation
 * @param seedSolutionFiles            solution-repo files snapshotted at seed time; unchanged legacy files remain valid during adaptation
 * @param producedTestsFiles           tests-repo files read back after generation; compared against the seed snapshot for the harness-immutability gate
 * @param producedTemplateFiles        template-repo files read back after generation; enables the solution-leak gate
 * @param producedSolutionFiles        solution-repo files read back after generation; the leak gate flags solution bodies that surfaced in the template
 * @param extractionFailedRepositories repository directory names whose read-back failed (seeded non-empty but extracted empty); fail-closed signal distinct from a genuinely empty
 *                                         repo
 * @param seededStructuralTestNames    the authoritative structural test names the seeder injected this run (never agent-supplied); a {@code [task]} bound to one is exempt from
 *                                         binding resolution but still participates in the differential. Empty for callers without it (the from-scratch path falls back to the
 *                                         name-shape exemption)
 * @param baselineGradedTestNames      the pre-adapt baseline for the adapt total-wipe gate (see {@link ExerciseIntegrityGate#adaptWipedGradedTestsReasons}); empty for generate
 *                                         leaves the gate inert (fail-open)
 * @param producedProblemStatement     problem statement captured with the produced repository files; {@code null} only for tests and legacy internal callers that still read it
 *                                         from the sandbox
 * @param adaptation                   whether untouched legacy test sources may retain their pre-existing conventions; new or modified tests always use current conventions
 */
public record VerificationRequest(Map<String, String> seedTestsFiles, Map<String, String> seedTemplateFiles, Map<String, String> seedSolutionFiles,
        Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories,
        Set<String> seededStructuralTestNames, Set<String> baselineGradedTestNames, @Nullable String producedProblemStatement, boolean adaptation) {

    public VerificationRequest(Map<String, String> seedTestsFiles, Map<String, String> seedTemplateFiles, Map<String, String> seedSolutionFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories,
            Set<String> seededStructuralTestNames, Set<String> baselineGradedTestNames, @Nullable String producedProblemStatement) {
        this(seedTestsFiles, seedTemplateFiles, seedSolutionFiles, producedTestsFiles, producedTemplateFiles, producedSolutionFiles, extractionFailedRepositories,
                seededStructuralTestNames, baselineGradedTestNames, producedProblemStatement, false);
    }

    public VerificationRequest(Map<String, String> seedTestsFiles, Map<String, String> seedTemplateFiles, Map<String, String> seedSolutionFiles,
            Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles, Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories,
            Set<String> seededStructuralTestNames, Set<String> baselineGradedTestNames) {
        this(seedTestsFiles, seedTemplateFiles, seedSolutionFiles, producedTestsFiles, producedTemplateFiles, producedSolutionFiles, extractionFailedRepositories,
                seededStructuralTestNames, baselineGradedTestNames, null, false);
    }

    public VerificationRequest(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles,
            Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories, Set<String> seededStructuralTestNames, Set<String> baselineGradedTestNames) {
        this(seedTestsFiles, Map.of(), Map.of(), producedTestsFiles, producedTemplateFiles, producedSolutionFiles, extractionFailedRepositories, seededStructuralTestNames,
                baselineGradedTestNames, null, false);
    }

    public VerificationRequest(Map<String, String> seedTestsFiles, Map<String, String> producedTestsFiles, Map<String, String> producedTemplateFiles,
            Map<String, String> producedSolutionFiles, Set<String> extractionFailedRepositories, Set<String> seededStructuralTestNames, Set<String> baselineGradedTestNames,
            @Nullable String producedProblemStatement) {
        this(seedTestsFiles, Map.of(), Map.of(), producedTestsFiles, producedTemplateFiles, producedSolutionFiles, extractionFailedRepositories, seededStructuralTestNames,
                baselineGradedTestNames, producedProblemStatement, false);
    }
}
