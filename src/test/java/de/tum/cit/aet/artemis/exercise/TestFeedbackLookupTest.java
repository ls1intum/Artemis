package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.exercise.service.TestFeedbackLookup;
import de.tum.cit.aet.artemis.exercise.service.TestOutcome;

class TestFeedbackLookupTest {

    private static final TestFeedbackInputDTO PASSED = new TestFeedbackInputDTO(1L, "testPassed()", true, null, null);

    private static final TestFeedbackInputDTO FAILED = new TestFeedbackInputDTO(2L, "testFailed()", false, null, null);

    private static final TestFeedbackInputDTO NOT_EXECUTED = new TestFeedbackInputDTO(3L, "testNotExecuted()", null, null, null);

    private static TestFeedbackLookup lookup() {
        return TestFeedbackLookup.of(Map.of(1L, PASSED, 2L, FAILED, 3L, NOT_EXECUTED));
    }

    @Test
    void shouldResolveByTestIdWrapper() {
        assertThat(lookup().resolve("<testid>1</testid>")).isEqualTo(PASSED);
    }

    @Test
    void shouldResolveByExactName() {
        assertThat(lookup().resolve("testFailed()")).isEqualTo(FAILED);
    }

    @Test
    void shouldNotResolveDifferentCaseOrStrippedParentheses() {
        assertThat(lookup().resolve("TESTFAILED()")).isNull();
        assertThat(lookup().resolve("testFailed")).isNull();
    }

    @Test
    void shouldMapOutcomes() {
        var lookup = lookup();
        assertThat(lookup.outcomeOf("<testid>1</testid>")).isEqualTo(TestOutcome.PASSED);
        assertThat(lookup.outcomeOf("testFailed()")).isEqualTo(TestOutcome.FAILED);
        assertThat(lookup.outcomeOf("testNotExecuted()")).isEqualTo(TestOutcome.NOT_EXECUTED);
        assertThat(lookup.outcomeOf("unknownTest()")).isEqualTo(TestOutcome.NOT_EXECUTED);
    }

    @Test
    void shouldTreatOutOfRangeTestIdAsUnresolved() {
        // The id overflows a long, so it can never name a test case. It must degrade to an unresolved reference
        // rather than propagate a NumberFormatException out of the renderer.
        assertThat(lookup().resolve("<testid>999999999999999999999999</testid>")).isNull();
        assertThat(lookup().outcomeOf("<testid>999999999999999999999999</testid>")).isEqualTo(TestOutcome.NOT_EXECUTED);
    }

    @Test
    void shouldReportMissingResults() {
        var empty = TestFeedbackLookup.of(null);
        assertThat(empty.hasResults()).isFalse();
        assertThat(empty.resolve("<testid>1</testid>")).isNull();
        assertThat(lookup().hasResults()).isTrue();
    }

    @Test
    void shouldTreatAmbiguousNameAsUnresolvedButKeepBothIdsResolvable() {
        // test_name has no uniqueness constraint in the database, so two distinct test ids may share a name.
        // Resolving by that name must not silently pick either one; resolving by id must still work for both.
        var first = new TestFeedbackInputDTO(10L, "sameName()", true, null, null);
        var second = new TestFeedbackInputDTO(11L, "sameName()", false, null, null);
        var lookup = TestFeedbackLookup.of(Map.of(10L, first, 11L, second));

        assertThat(lookup.resolve("sameName()")).isNull();
        assertThat(lookup.outcomeOf("sameName()")).isEqualTo(TestOutcome.NOT_EXECUTED);
        assertThat(lookup.resolve("<testid>10</testid>")).isEqualTo(first);
        assertThat(lookup.resolve("<testid>11</testid>")).isEqualTo(second);
    }

    @Test
    void shouldStayAmbiguousWithMoreThanTwoSharedNames() {
        // A third entry sharing the name must not resurrect it as unambiguous.
        var first = new TestFeedbackInputDTO(20L, "sameName()", true, null, null);
        var second = new TestFeedbackInputDTO(21L, "sameName()", false, null, null);
        var third = new TestFeedbackInputDTO(22L, "sameName()", null, null, null);
        var lookup = TestFeedbackLookup.of(Map.of(20L, first, 21L, second, 22L, third));

        assertThat(lookup.resolve("sameName()")).isNull();
    }
}
