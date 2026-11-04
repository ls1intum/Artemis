package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.SemanticMutantOutcome.Disposition;

/** Every check reports through one outcome, so the derived views (survivors, kills, unavailable evidence, repair credit) must agree whichever path produced it. */
class MutantEvidenceTest {

    private static final SemanticMutant SURVIVOR = new SemanticMutant("R1", "src/Scheduler.java", "class Scheduler {}", "class Scheduler { int wrong; }",
            new ContractWitness("R1", "allPrerequisites", "@Test void allPrerequisites() {}", "honors only the first prerequisite"));

    private static final SemanticMutant PRINT_ONLY = SemanticMutant.printedTranscript("src/Main.java", "class Main {}", "class Main { }", "Main");

    /** The form in which each path reports a mutant's outcome: as proven, rekeyed to a changed file, or re-derived for a changed class. */
    static Stream<Arguments> paths() {
        return Stream.of(Arguments.of("verification acceptance recheck", SURVIVOR, SURVIVOR),
                Arguments.of("review recheck of a rekeyed survivor", SURVIVOR, SURVIVOR.rekeyedTo("class Scheduler { int contractFix; }")),
                Arguments.of("review recheck of a print-only probe", PRINT_ONLY, PRINT_ONLY),
                Arguments.of("probe re-derived for a changed class", PRINT_ONLY,
                        SemanticMutant.printedTranscript("src/Main.java", "class Main { /* reworded */ }", "class Main { }", "Main")),
                Arguments.of("silent-main resolution keyed to the changed class", PRINT_ONLY, PRINT_ONLY.rekeyedTo("class Main { /* reworded */ }")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paths")
    void aKillOfASurvivorIsCreditedAndBecomesRegressionCoverage(String path, SemanticMutant proven, SemanticMutant reported) {
        MutantEvidence evidence = new MutantEvidence();
        evidence.record(new SemanticMutantOutcome(proven, Disposition.SURVIVED_GRADED_SUITE));
        evidence.beginRepair();

        assertThat(evidence.record(new SemanticMutantOutcome(reported, Disposition.KILLED_BY_GRADED_SUITE))).isFalse();

        assertThat(evidence.killsSinceRepairBegan()).isOne();
        assertThat(evidence.kills()).containsExactly(reported);
        assertThat(evidence.unresolvedSurvivors()).isEmpty();
        assertThat(evidence.unverifiedKills()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paths")
    void aSurvivorWithoutAVerdictStaysUnresolvedAndEarnsNoCredit(String path, SemanticMutant proven, SemanticMutant reported) {
        MutantEvidence evidence = new MutantEvidence();
        evidence.record(new SemanticMutantOutcome(proven, Disposition.SURVIVED_GRADED_SUITE));
        evidence.beginRepair();

        evidence.recordWithoutVerdict(reported);

        assertThat(evidence.killsSinceRepairBegan()).isZero();
        assertThat(evidence.unverifiedSurvivors()).containsExactly(reported);
        assertThat(evidence.surviving()).isEmpty();
        assertThat(evidence.kills()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("paths")
    void aKillThatSurvivesAgainIsARegressionAndOneWithoutAVerdictIsUnverified(String path, SemanticMutant proven, SemanticMutant reported) {
        MutantEvidence evidence = new MutantEvidence();
        evidence.record(new SemanticMutantOutcome(proven, Disposition.SURVIVED_GRADED_SUITE));
        evidence.record(new SemanticMutantOutcome(proven, Disposition.KILLED_BY_GRADED_SUITE));
        evidence.beginRepair();

        assertThat(evidence.record(new SemanticMutantOutcome(reported, Disposition.SURVIVED_GRADED_SUITE))).as("regression").isTrue();
        assertThat(evidence.kills()).containsExactly(reported);
        assertThat(evidence.surviving()).containsExactly(reported);
        assertThat(evidence.regressions()).containsExactly(reported);

        evidence.recordWithoutVerdict(reported);
        assertThat(evidence.regressions()).containsExactly(reported);
        assertThat(evidence.unverifiedKills()).isEmpty();
        evidence.beginCandidate();
        assertThat(evidence.regressions()).isEmpty();
        assertThat(evidence.unverifiedKills()).containsExactly(reported);
        assertThat(evidence.killsSinceRepairBegan()).isZero();
    }

    @Test
    void killingAnotherImplementationOfTheSameMisconceptionDoesNotResolveTheSurvivor() {
        MutantEvidence evidence = new MutantEvidence();
        SemanticMutant other = new SemanticMutant(SURVIVOR.ruleId(), SURVIVOR.solutionPath(), SURVIVOR.originalSolutionSource(), "class Scheduler { int otherWrong; }",
                SURVIVOR.counterexample());
        evidence.record(new SemanticMutantOutcome(SURVIVOR, Disposition.SURVIVED_GRADED_SUITE));
        evidence.beginRepair();
        evidence.record(new SemanticMutantOutcome(other, Disposition.KILLED_BY_GRADED_SUITE));
        assertThat(evidence.surviving()).containsExactly(SURVIVOR);
        assertThat(evidence.kills()).isEmpty();
        assertThat(evidence.killsSinceRepairBegan()).isZero();

        evidence.record(new SemanticMutantOutcome(other, Disposition.SURVIVED_GRADED_SUITE));
        evidence.record(new SemanticMutantOutcome(other.rekeyedTo("class Scheduler { int fixed; }"), Disposition.KILLED_BY_GRADED_SUITE));
        assertThat(evidence.surviving()).containsExactly(SURVIVOR);
        assertThat(evidence.killsSinceRepairBegan()).isOne();
    }

    @Test
    void rederivedTranscriptProbeExplicitlyContinuesItsOriginalIdentity() {
        MutantEvidence evidence = new MutantEvidence();
        SemanticMutant replacement = SemanticMutant.printedTranscript("src/Main.java", "class Main { int changed; }", "class Main { int changed; /* print */ }", "Main");
        evidence.record(new SemanticMutantOutcome(PRINT_ONLY, Disposition.SURVIVED_GRADED_SUITE));
        evidence.beginRepair();
        evidence.inheritIdentity(PRINT_ONLY, replacement);
        evidence.record(new SemanticMutantOutcome(replacement, Disposition.KILLED_BY_GRADED_SUITE));
        assertThat(evidence.unresolvedSurvivors()).isEmpty();
        assertThat(evidence.kills()).containsExactly(replacement);
        assertThat(evidence.killsSinceRepairBegan()).isOne();
        evidence.beginRepair();
        assertThat(evidence.record(new SemanticMutantOutcome(replacement.rekeyedTo("class Main { int anotherChange; }"), Disposition.SURVIVED_GRADED_SUITE))).isTrue();
        assertThat(evidence.killsSinceRepairBegan()).isZero();
    }

    @Test
    void aMutantTheSuiteKilledAtOnceIsNotTrackedAndKillsComeLeastRecentlyCheckedFirst() {
        MutantEvidence evidence = new MutantEvidence();
        evidence.record(new SemanticMutantOutcome(SURVIVOR, Disposition.KILLED_BY_GRADED_SUITE));
        evidence.recordWithoutVerdict(PRINT_ONLY);
        assertThat(evidence.kills()).isEmpty();
        assertThat(evidence.unresolvedSurvivors()).isEmpty();

        evidence.record(new SemanticMutantOutcome(SURVIVOR, Disposition.SURVIVED_GRADED_SUITE));
        evidence.record(new SemanticMutantOutcome(PRINT_ONLY, Disposition.SURVIVED_GRADED_SUITE));
        evidence.record(new SemanticMutantOutcome(SURVIVOR, Disposition.KILLED_BY_GRADED_SUITE));
        evidence.record(new SemanticMutantOutcome(PRINT_ONLY, Disposition.KILLED_BY_GRADED_SUITE));
        evidence.record(new SemanticMutantOutcome(SURVIVOR, Disposition.KILLED_BY_GRADED_SUITE));
        assertThat(evidence.kills()).isEqualTo(List.of(PRINT_ONLY, SURVIVOR));
    }
}
