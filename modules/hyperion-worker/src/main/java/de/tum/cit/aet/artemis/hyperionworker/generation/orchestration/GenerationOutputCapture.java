package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationAssignment;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceSnapshot;
import de.tum.cit.aet.artemis.hyperionworker.generation.WorkerUsageRecorder;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.BinaryContent;
import de.tum.cit.aet.artemis.hyperionworker.generation.workspace.GenerationWorkspaceService;

/** Seals the exact verified text and original binary/mode metadata before returning a candidate to core. */
final class GenerationOutputCapture {

    private GenerationOutputCapture() {
    }

    static GenerationOutput capture(GenerationAssignment assignment, GenerationOutcome outcome, WorkerUsageRecorder usage, boolean checkpoint) {
        Map<String, WorkspaceFile> seed = assignment.seed().files().stream().collect(Collectors.toMap(WorkspaceFile::path, Function.identity()));
        List<WorkspaceFile> files = new ArrayList<>();
        outcome.capturedProducedFiles().forEach((role, textFiles) -> {
            String prefix = GenerationWorkspaceService.directoryFor(role) + "/";
            textFiles.forEach((path, content) -> {
                String fullPath = prefix + path;
                WorkspaceFile before = seed.get(fullPath);
                files.add(new WorkspaceFile(fullPath, content.getBytes(StandardCharsets.UTF_8), before != null && before.executable()));
            });
            seed.values().stream().filter(file -> file.path().startsWith(prefix) && BinaryContent.isBinary(file.content())).forEach(files::add);
        });
        if (outcome.hasCapturedArtifacts()) {
            files.add(new WorkspaceFile("problem-statement.md", outcome.producedProblemStatement().getBytes(StandardCharsets.UTF_8), false));
        }
        if (outcome.specDocument() != null) {
            files.add(new WorkspaceFile("SPEC.md", outcome.specDocument().getBytes(StandardCharsets.UTF_8), false));
        }
        if (outcome.testPlanJson() != null) {
            files.add(new WorkspaceFile("test-plan.json", outcome.testPlanJson().getBytes(StandardCharsets.UTF_8), false));
        }
        WorkspaceSnapshot snapshot = new WorkspaceSnapshot(files);
        VerificationResult verification = outcome.verification() == null
                ? new VerificationResult(false, false, false, 0, List.of(outcome.errorMessage() == null ? "The candidate was not verified." : outcome.errorMessage()))
                : outcome.verification();
        String reason = outcome.terminationReason() == null ? "RUN_FAILED" : outcome.terminationReason().name();
        return new GenerationOutput(snapshot, verification, verification.mechanicallyVerified() ? snapshot.sha256() : null, outcome.specFidelityReport(), reason, usage.snapshot(),
                checkpoint ? GenerationOutput.AccountingState.PENDING : usage.accountingState(), assignment.parameters().effortProfile());
    }
}
