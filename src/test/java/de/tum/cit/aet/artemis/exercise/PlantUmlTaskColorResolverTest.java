package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.exercise.service.PlantUmlTaskColorResolver;

class PlantUmlTaskColorResolverTest {

    private static final Map<Long, TestFeedbackInputDTO> RESULTS = Map.of(1L, new TestFeedbackInputDTO(1L, "testPassed()", true, null, null), 2L,
            new TestFeedbackInputDTO(2L, "testFailed()", false, null, null), 3L, new TestFeedbackInputDTO(3L, "testNotExecuted()", null, null, null));

    @Test
    void shouldColorTagFormByName() {
        assertThat(PlantUmlTaskColorResolver.resolve("<color:testsColor(testPassed())>A</color>", RESULTS, false)).isEqualTo("<color:green>A</color>");
    }

    @Test
    void shouldColorArrowFormById() {
        assertThat(PlantUmlTaskColorResolver.resolve("A -> B #testsColor(<testid>2</testid>)", RESULTS, false)).isEqualTo("A -> B #red");
    }

    @Test
    void shouldColorTextForm() {
        assertThat(PlantUmlTaskColorResolver.resolve("#text:testsColor(testPassed())", RESULTS, false)).isEqualTo("#text:green");
    }

    @Test
    void shouldUseGreyForNotExecutedUnknownAndMissingResults() {
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testNotExecuted())", RESULTS, false)).isEqualTo("#grey");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(unknownTest())", RESULTS, false)).isEqualTo("#grey");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testPassed())", null, false)).isEqualTo("#grey");
    }

    @Test
    void shouldColorEveryReferenceGreenWhenAllTestsPassedWithoutResults() {
        // A successful result without any feedback: nothing can be resolved, so every form and both reference kinds
        // (name and id) must go green — otherwise the task markers turn green while the diagram stays grey.
        assertThat(PlantUmlTaskColorResolver.resolve("<color:testsColor(testPassed())>A</color>", null, true)).isEqualTo("<color:green>A</color>");
        assertThat(PlantUmlTaskColorResolver.resolve("#text:testsColor(unknownTest())", null, true)).isEqualTo("#text:green");
        assertThat(PlantUmlTaskColorResolver.resolve("A -> B #testsColor(<testid>7</testid>)", null, true)).isEqualTo("A -> B #green");
    }

    @Test
    void shouldLetIndividualOutcomesWinOverAllTestsPassed() {
        // A contradictory request (the flag plus actual test results) is decided by the results: green only where the
        // feedback says so. This is the same precedence the task status and the task counts use.
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testFailed())", RESULTS, true)).isEqualTo("#red");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testNotExecuted())", RESULTS, true)).isEqualTo("#grey");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(unknownTest())", RESULTS, true)).isEqualTo("#grey");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testPassed())", RESULTS, true)).isEqualTo("#green");
    }

    @Test
    void shouldColorGreyForAllTestsPassedWithAnEmptyResultMap() {
        // An empty (but present) result map is "a result exists and knows nothing about this test", not "no result".
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testPassed())", Map.of(), true)).isEqualTo("#grey");
    }

    @Test
    void shouldAcceptLeadingButNotTrailingWhitespaceInReference() {
        // TESTS_COLOR_INNER allows leading whitespace inside testsColor(...) but not a space before the closing
        // parenthesis, so the second form does not match the pattern at all and is left untouched.
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor( testPassed())", RESULTS, false)).isEqualTo("#green");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor( testPassed() )", RESULTS, false)).isEqualTo("#testsColor( testPassed() )");
    }
}
