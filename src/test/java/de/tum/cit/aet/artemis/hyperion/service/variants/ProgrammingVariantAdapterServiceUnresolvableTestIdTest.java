package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for dropping {@code <testid>} references that name no test case of the variant.
 * <p>
 * Applied right after the source-to-variant id remap, where the valid id set is known exactly. Anything still
 * unresolvable there was never a real id in either exercise: the planner writes its statement before the variant
 * exists, so it cannot know an id, and every id the source did have has just been remapped. Such a reference is
 * silently unlinked from grading rather than rejected anywhere — observed on a run that shipped six invented ids
 * while every verification gate reported green.
 */
class ProgrammingVariantAdapterServiceUnresolvableTestIdTest {

    private static final List<Long> VALID_IDS = List.of(2595L, 2596L);

    @Test
    void shouldKeepAReferenceToARealTestCase() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("[task][A](<testid>2595</testid>)", VALID_IDS)).isEqualTo("[task][A](<testid>2595</testid>)");
    }

    @Test
    void shouldDropAnInventedIdAndLeaveTheTaskTextIntact() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("[task][B](<testid>1800</testid>)", VALID_IDS)).isEqualTo("[task][B]()");
    }

    @Test
    void shouldDropOnlyTheInventedIdInAMixedMarker() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("[task][C](<testid>1800</testid>,<testid>2595</testid>)", VALID_IDS))
                .isEqualTo("[task][C](<testid>2595</testid>)");
    }

    @Test
    void shouldNotLeaveATrailingCommaWhenTheLastReferenceIsDropped() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("[task][D](<testid>2595</testid>,<testid>1800</testid>)", VALID_IDS))
                .isEqualTo("[task][D](<testid>2595</testid>)");
    }

    @Test
    void shouldCollapseSeparatorsWhenSeveralConsecutiveReferencesAreDropped() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("[task][E](<testid>1800</testid>,<testid>1801</testid>,<testid>2596</testid>)", VALID_IDS))
                .isEqualTo("[task][E](<testid>2596</testid>)");
    }

    @Test
    void shouldLeavePlainTestNamesUntouched() {
        // Plain names are resolved by name, not by id, so this method must not touch them at all — including
        // bracketed structural names, whose brackets must survive verbatim or the reference stops resolving.
        String statement = "[task][F](testBubbleSort,testClass[SortStrategy])";
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds(statement, VALID_IDS)).isEqualTo(statement);
    }

    @Test
    void shouldLeaveProseWithoutMarkersUntouched() {
        String statement = "In this exercise, implement two sorting algorithms.\n\n@startuml\nclass A\n@enduml";
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds(statement, VALID_IDS)).isEqualTo(statement);
    }

    @Test
    void shouldDropEveryReferenceWhenTheVariantHasNoTestCasesYet() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("[task][G](<testid>1800</testid>)", Set.of())).isEqualTo("[task][G]()");
    }

    @Test
    void shouldNotRewriteCodeSamplesWhenAReferenceIsDropped() {
        // The separator tidy-up must stay inside the task markers: a problem statement also carries code, where
        // a trailing comma or a double comma is content the instructor wrote, not an artefact of the removal.
        String statement = """
                [task][H](<testid>1800</testid>,<testid>2595</testid>)

                Call it like `foo(a, )` and note that `[1,,3]` is intentional.""";
        String expected = """
                [task][H](<testid>2595</testid>)

                Call it like `foo(a, )` and note that `[1,,3]` is intentional.""";
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds(statement, VALID_IDS)).isEqualTo(expected);
    }

    @Test
    void shouldReturnTheStatementUnchangedWhenNothingIsDropped() {
        // Nothing was removed, so there is no dangling separator to tidy and the statement must come back verbatim.
        String statement = """
                [task][I](<testid>2595</testid>)

                Edge case: `bar(x, )` and `[4,,6]`.""";
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds(statement, VALID_IDS)).isEqualTo(statement);
    }

    @Test
    void shouldHandleNullAndBlankStatements() {
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds(null, VALID_IDS)).isNull();
        assertThat(ProgrammingVariantAdapterService.dropUnresolvableTestIds("  ", VALID_IDS)).isEqualTo("  ");
    }
}
