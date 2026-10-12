package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;

/**
 * Unit tests for {@link PlantUmlTaskColorResolver#resolve}, in particular for test names that contain spaces, as Java DisplayNames do.
 */
class PlantUmlTaskColorResolverTest {

    private static final Map<Long, TestFeedbackInputDTO> TEST_RESULTS = Map.of(1L, new TestFeedbackInputDTO(1L, "Test Attributes of Class", true, null, 1.0), 2L,
            new TestFeedbackInputDTO(2L, "testClass[Car]", false, null, 0.0), 3L, new TestFeedbackInputDTO(3L, "Test Methods of Class(1, 2)", true, null, 1.0));

    @Test
    void resolve_colorTagWithSpacesInTheTestName() {
        String resolved = PlantUmlTaskColorResolver.resolve("<color:testsColor(Test Attributes of Class)>-foo: String</color>", TEST_RESULTS);

        assertThat(resolved).isEqualTo("<color:green>-foo: String</color>");
    }

    @Test
    void resolve_arrowAndTextColoringWithSpacesInTheTestName() {
        String resolved = PlantUmlTaskColorResolver.resolve("class A #text:testsColor(Test Attributes of Class)\nA <|-- B #testsColor(Test Attributes of Class)", TEST_RESULTS);

        assertThat(resolved).isEqualTo("class A #text:green\nA <|-- B #green");
    }

    @Test
    void resolve_spacesAndParametersInTheTestName() {
        String resolved = PlantUmlTaskColorResolver.resolve("<color:testsColor(Test Methods of Class(1, 2))>+ foo()</color>", TEST_RESULTS);

        assertThat(resolved).isEqualTo("<color:green>+ foo()</color>");
    }

    @Test
    void resolve_unknownSpacedTestNameIsGreyAndFailingTestIsRed() {
        String resolved = PlantUmlTaskColorResolver.resolve("#testsColor(Some Unknown Test) and #testsColor(testClass[Car])", TEST_RESULTS);

        assertThat(resolved).isEqualTo("#grey and #red");
    }

    @Test
    void resolve_testNamesWithoutSpacesStillWork() {
        String resolved = PlantUmlTaskColorResolver.resolve("<color:testsColor(testClass[Car])>x</color> #testsColor(<testid>1</testid>)", TEST_RESULTS);

        assertThat(resolved).isEqualTo("<color:red>x</color> #green");
    }

    @Test
    void resolve_withoutTestResultsEverythingIsGrey() {
        assertThat(PlantUmlTaskColorResolver.resolve("#testsColor(Test Attributes of Class)", null)).isEqualTo("#grey");
    }
}
