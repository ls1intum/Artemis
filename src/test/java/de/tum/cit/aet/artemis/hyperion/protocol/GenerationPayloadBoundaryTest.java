package de.tum.cit.aet.artemis.hyperion.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationPayloadBoundaryTest {

    private final WorkspaceSnapshot snapshot = new WorkspaceSnapshot(List.of());

    private final VerificationResult unverified = new VerificationResult(false, false, false, 0, List.of());

    private final ExecutionIdentity identity = new ExecutionIdentity("job", 7, UUID.randomUUID(), "worker", UUID.randomUUID(), 0);

    @ParameterizedTest
    @ValueSource(strings = { "profile", "turns", "tokens", "duration", "negativeDuration", "window", "weightZero", "weightNegative", "weightTooLarge", "weightNaN", "context" })
    void resolvedParametersRejectUnboundedOrUnusableRunBudgets(String field) {
        assertThatThrownBy(() -> new GenerationParameters(field.equals("profile") ? "p".repeat(65) : "profile", field.equals("turns") ? 0 : 10, field.equals("tokens") ? 0 : 1000,
                field.equals("duration") ? null : field.equals("negativeDuration") ? Duration.ofSeconds(-1) : Duration.ofMinutes(5), field.equals("window") ? 0 : 64000, null, null,
                null, null, null, false, field.equals("context") ? "OTHER" : "FRESH", null, switch (field) {
                    case "weightZero" -> 0;
                    case "weightNegative" -> -1;
                    case "weightTooLarge" -> 1.01;
                    case "weightNaN" -> Double.NaN;
                    default -> 0.5;
                })).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid resolved generation parameters");
    }

    @ParameterizedTest
    @ValueSource(strings = { "title", "shortName", "namespace", "prompt", "statement", "source", "mode", "toolchain", "titleNull", "shortNameNull", "namespaceNull", "promptNull" })
    void authoringBriefBoundsEveryExternallySuppliedTextAndRequiresItsMode(String field) {
        assertThatThrownBy(() -> new ExerciseBrief(field.equals("titleNull") ? null : field.equals("title") ? "x".repeat(256) : "Title",
                field.equals("shortNameNull") ? null : field.equals("shortName") ? "x".repeat(256) : "short",
                field.equals("namespaceNull") ? null : field.equals("namespace") ? "x".repeat(256) : "de.example", field.equals("statement") ? "x".repeat(65537) : null,
                field.equals("promptNull") ? null : field.equals("prompt") ? "x".repeat(65537) : "Generate", field.equals("mode") ? null : ExerciseBrief.Mode.GENERATE,
                field.equals("source") ? "x".repeat(65537) : null, field.equals("toolchain") ? null : GenerationToolchain.JAVA_GRADLE, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid exercise authoring brief");
    }

    @Test
    void authoringBriefAcceptsExactTextLimitsAndPreservesEmptyParameterProfile() {
        var brief = new ExerciseBrief("t".repeat(255), "s".repeat(255), "n".repeat(255), "p".repeat(65536), "i".repeat(65536), ExerciseBrief.Mode.ADAPT, "s".repeat(65536),
                GenerationToolchain.JAVA_GRADLE, null);
        assertThat(brief.problemStatement()).hasSize(65536);
        assertThat(brief.sourceBrief()).hasSize(65536);
        var parameters = new GenerationParameters(null, 1, 1, Duration.ofSeconds(1), 1, null, null, null, null, null, false, "FRESH", null, 1);
        assertThat(parameters.effortProfile()).isEmpty();
        assertThat(parameters.cachedInputTokenWeight()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "identity", "brief", "parameters", "seed", "deadline", "grading", "image", "mutableImage" })
    void assignmentRequiresCompleteAuthorityAndImmutableImage(String field) {
        var brief = new ExerciseBrief("Title", "short", "de.example", null, "Generate", ExerciseBrief.Mode.GENERATE);
        var parameters = new GenerationParameters("", 1, 1, Duration.ofSeconds(1), 64000, null, null, null, null, null, false, "FRESH");
        assertThatThrownBy(() -> new GenerationAssignment(field.equals("identity") ? null : identity, field.equals("brief") ? null : brief,
                field.equals("parameters") ? null : parameters, field.equals("seed") ? null : snapshot, field.equals("deadline") ? null : Instant.now(),
                field.equals("image") ? null : field.equals("mutableImage") ? "latest" : "sha256:" + "a".repeat(64),
                field.equals("grading") ? null : new GradingContext(false, Set.of(), Set.of()))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(field.equals("image") || field.equals("mutableImage") ? "immutable worker image" : "fields must be present");
    }

    @ParameterizedTest
    @ValueSource(strings = { "candidate", "review", "verification", "accounting", "reason", "reasonLength", "profileLength", "sealWithoutUsage", "digest" })
    void outputRequiresExactVerifiedCandidateAndAccountingEvidence(String field) {
        assertThatThrownBy(() -> new GenerationOutput(field.equals("candidate") ? null : snapshot,
                field.equals("verification") ? null : field.equals("digest") ? new VerificationResult(true, true, true, 1, List.of()) : unverified,
                field.equals("digest") ? "wrong digest" : null, field.equals("review") ? null : SpecFidelityReport.empty(),
                field.equals("reason") ? null : field.equals("reasonLength") ? "x".repeat(129) : "RUN_FAILED", null,
                field.equals("accounting") ? null : field.equals("sealWithoutUsage") ? GenerationOutput.AccountingState.COMPLETE : GenerationOutput.AccountingState.INCOMPLETE,
                field.equals("profileLength") ? "x".repeat(65) : "")).isInstanceOf(IllegalArgumentException.class).hasMessage(switch (field) {
                    case "candidate", "review", "verification", "accounting" -> "Candidate, review, verification and accounting state are required";
                    case "digest" -> "Verification must refer to the exact frozen candidate";
                    default -> "Invalid generation outcome or accounting seal";
                });
    }

    @Test
    void capacityCannotAdvertiseMoreOrDuplicateOrOutOfRangeExecutionSlots() {
        assertThatThrownBy(() -> new WorkerCapacity(0, List.of())).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid worker capacity");
        assertThatThrownBy(() -> new WorkerCapacity(17, List.of())).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid worker capacity");
        assertThatThrownBy(() -> new WorkerCapacity(1, List.of(identity, identity))).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid worker capacity");
        var second = new ExecutionIdentity("other", 7, UUID.randomUUID(), "worker", identity.workerIncarnation(), 0);
        assertThatThrownBy(() -> new WorkerCapacity(2, List.of(identity, second))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct configured slot");
        var outside = new ExecutionIdentity("other", 7, UUID.randomUUID(), "worker", identity.workerIncarnation(), 2);
        assertThatThrownBy(() -> new WorkerCapacity(2, List.of(outside))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distinct configured slot");
        var mutable = new ArrayList<>(List.of(identity));
        var capacity = new WorkerCapacity(1, mutable);
        mutable.clear();
        assertThat(capacity.executions()).containsExactly(identity);
        assertThat(new WorkerCapacity(16, null).executions()).isEmpty();
    }

    @Test
    void gradingContextRejectsBlankOversizedAndExcessiveNamesAndCopiesItsInput() {
        assertThatThrownBy(() -> new GradingContext(false, Set.of(" "), Set.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
        assertThatThrownBy(() -> new GradingContext(false, Set.of("x".repeat(1025)), Set.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
        var tooMany = java.util.stream.IntStream.rangeClosed(0, WorkspaceSnapshot.MAX_FILES).mapToObj(index -> "test" + index).collect(java.util.stream.Collectors.toSet());
        assertThatThrownBy(() -> new GradingContext(false, tooMany, Set.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
        assertThatThrownBy(() -> new GradingContext(false, Set.of(), Set.of(" "))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
        assertThatThrownBy(() -> new GradingContext(false, Set.of(), Set.of("x".repeat(1025)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
        assertThatThrownBy(() -> new GradingContext(false, Set.of(), tooMany)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bounds");
        Set<String> names = new HashSet<>(Set.of("test"));
        var context = new GradingContext(true, names, names);
        names.clear();
        assertThat(context.baselineGradedTestNames()).containsExactly("test");
        assertThat(context.baselineHiddenTestNames()).containsExactly("test");
        assertThat(context.hasDueDate()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "model", "request", "input", "output", "cacheNegative", "cacheTooLarge" })
    void providerUsageCannotCarryOversizedIdentityOrNegativeTokenEvidence(String field) {
        Long cachedTokens = switch (field) {
            case "cacheNegative" -> -1L;
            case "cacheTooLarge" -> 11L;
            default -> null;
        };
        assertThatThrownBy(() -> new ProviderUsageUpdate.Call(field.equals("model") ? "x".repeat(1025) : "model", field.equals("request") ? "x".repeat(1025) : null,
                field.equals("input") ? -1 : 10, field.equals("output") ? -1 : 2, cachedTokens)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid provider usage metadata");
    }
}
