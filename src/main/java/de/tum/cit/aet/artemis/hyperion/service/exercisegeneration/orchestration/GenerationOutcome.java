package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.TerminationReason;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationOutput;
import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;
import de.tum.cit.aet.artemis.hyperion.protocol.VerificationResult;
import de.tum.cit.aet.artemis.hyperion.protocol.WorkspaceFile;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService.Seed;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.BinaryContent;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Frozen worker output adapted to core persistence. It never contains a live sandbox or an executable callback. */
public final class GenerationOutcome {

    private final AgentLoopResult loopResult;

    @Nullable
    private final GenerationOutput output;

    private final Map<RepositoryType, Map<String, String>> files;

    private final Map<RepositoryType, String> heads;

    private final Map<String, String> documents;

    private GenerationOutcome(AgentLoopResult.Status status, @Nullable GenerationOutput output, Seed seed, String message) {
        this.output = output;
        this.loopResult = AgentLoopResult.outsideSession(status, message);
        this.heads = seed.heads();
        Map<RepositoryType, Map<String, String>> repositories = new EnumMap<>(RepositoryType.class);
        Map<String, String> rootFiles = new HashMap<>();
        Map<String, WorkspaceFile> original = seed.snapshot().files().stream().collect(Collectors.toMap(WorkspaceFile::path, file -> file));
        if (output != null) {
            Map<String, WorkspaceFile> produced = output.candidate().files().stream().collect(Collectors.toMap(WorkspaceFile::path, file -> file));
            // Unverified diagnostic snapshots contain only changed repositories; verified candidates must include every repository.
            Set<String> capturedRoots = produced.keySet().stream().map(path -> path.split("/", 2)[0]).collect(Collectors.toSet());
            for (WorkspaceFile seedFile : seed.snapshot().files()) {
                boolean captured = output.verification().mechanicallyVerified() || capturedRoots.contains(seedFile.path().split("/", 2)[0]);
                if (captured && BinaryContent.isBinary(seedFile.content()) && !seedFile.equals(produced.get(seedFile.path()))) {
                    throw new IllegalArgumentException("Generated output omitted or changed canonical binary scaffolding");
                }
            }
            for (WorkspaceFile file : output.candidate().files()) {
                WorkspaceFile previous = original.get(file.path());
                if (BinaryContent.isBinary(file.content())) {
                    if (!file.equals(previous)) {
                        throw new IllegalArgumentException("Generated binaries cannot replace the canonical seed");
                    }
                    continue;
                }
                if (file.executable() != (previous != null && previous.executable())) {
                    throw new IllegalArgumentException("Generated output changed a repository executable mode");
                }
                int slash = file.path().indexOf('/');
                if (slash < 0) {
                    if (!Set.of("problem-statement.md", "SPEC.md", "test-plan.json").contains(file.path())) {
                        throw new IllegalArgumentException("Unexpected generated document");
                    }
                    rootFiles.put(file.path(), text(file));
                }
                else {
                    RepositoryType role = switch (file.path().substring(0, slash)) {
                        case "template" -> RepositoryType.TEMPLATE;
                        case "solution" -> RepositoryType.SOLUTION;
                        case "tests" -> RepositoryType.TESTS;
                        default -> throw new IllegalArgumentException("Unexpected generated repository");
                    };
                    repositories.computeIfAbsent(role, ignored -> new HashMap<>()).put(file.path().substring(slash + 1), text(file));
                }
            }
        }
        this.files = repositories.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
        this.documents = Map.copyOf(rootFiles);
    }

    private static String text(WorkspaceFile file) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(file.content())).toString();
        }
        catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Generated text is not valid UTF-8", failure);
        }
    }

    static GenerationOutcome received(GenerationOutput output, Seed seed, boolean cancelled) {
        return new GenerationOutcome(cancelled ? AgentLoopResult.Status.CANCELLED : AgentLoopResult.Status.COMPLETED, output, seed, output.verification().report());
    }

    static GenerationOutcome stopped(Seed seed, boolean cancelled, String message) {
        return new GenerationOutcome(cancelled ? AgentLoopResult.Status.CANCELLED : AgentLoopResult.Status.ERROR, null, seed, message);
    }

    @Nullable
    public TerminationReason terminationReason() {
        return output == null ? TerminationReason.RUN_FAILED : TerminationReason.valueOf(output.terminationReason());
    }

    public SpecFidelityReport specFidelityReport() {
        return output == null ? SpecFidelityReport.empty() : output.review();
    }

    public boolean isMechanicallyVerified() {
        return output != null && output.verification().mechanicallyVerified();
    }

    public AgentLoopResult loopResult() {
        return loopResult;
    }

    public boolean hasCapturedArtifacts() {
        return !files.isEmpty() || !documents.isEmpty();
    }

    @Nullable
    public VerificationResult verification() {
        return output == null ? null : output.verification();
    }

    public String errorMessage() {
        return loopResult.finalMessage();
    }

    public Map<String, String> producedFiles(RepositoryType role) {
        return files.getOrDefault(role, Map.of());
    }

    public Map<RepositoryType, Map<String, String>> capturedProducedFiles() {
        return files;
    }

    public Map<RepositoryType, String> seedRepositoryHeads() {
        return heads;
    }

    @Nullable
    public String specDocument() {
        return documents.get("SPEC.md");
    }

    @Nullable
    public String testPlanJson() {
        return documents.get("test-plan.json");
    }

    public String producedProblemStatement() {
        return documents.getOrDefault("problem-statement.md", "");
    }

}
