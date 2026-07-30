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
        assertThat(PlantUmlTaskColorResolver.resolve("<color:testsColor(testPassed())>A</color>", RESULTS)).isEqualTo("<color:green>A</color>");
    }

    @Test
    void shouldColorArrowFormById() {
        assertThat(PlantUmlTaskColorResolver.resolve("A -> B #testsColor(<testid>2</testid>)", RESULTS)).isEqualTo("A -> B #red");
    }

    @Test
    void shouldColorTextForm() {
        assertThat(PlantUmlTaskColorResolver.resolve("#text:testsColor(testPassed())", RESULTS)).isEqualTo("#text:green");
    }

    @Test
    void shouldUseGreyForNotExecutedUnknownAndMissingResults() {
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testNotExecuted())", RESULTS)).isEqualTo("#grey");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(unknownTest())", RESULTS)).isEqualTo("#grey");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(testPassed())", null)).isEqualTo("#grey");
    }

    @Test
    void shouldAcceptLeadingButNotTrailingWhitespaceInReference() {
        // TESTS_COLOR_INNER allows leading whitespace inside testsColor(...) but not a space before the closing
        // parenthesis, so the second form does not match the pattern at all and is left untouched.
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor( testPassed())", RESULTS)).isEqualTo("#green");
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor( testPassed() )", RESULTS)).isEqualTo("#testsColor( testPassed() )");
    }
}
