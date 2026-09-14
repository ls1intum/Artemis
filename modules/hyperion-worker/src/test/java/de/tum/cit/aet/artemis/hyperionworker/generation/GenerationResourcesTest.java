package de.tum.cit.aet.artemis.hyperionworker.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GenerationResourcesTest {

    @Test
    void generatedFixturesDoNotAttributeNewExercisesToTheReferenceAuthor() {
        String fixture = """
                /**
                 * Checks the public API.
                 * @author Reference author
                 * @version 1.0
                 * @return test cases
                 */
                """;

        assertThat(GenerationResources.javaGradleFixture(fixture)).isEqualTo("""
                /**
                 * Checks the public API.
                 * @return test cases
                 */
                """);
    }

    @Test
    void teachingStatementTestLabelsMatchSimpleNamesWithoutChangingStudentApiCalls() {
        String statement = "Implement sort().\n[task][Sorting](testSort(),testClass[Sorter],testEmpty())\ntestsColor(testSort())";

        assertThat(GenerationResources.javaGradleStatementFixture(statement))
                .isEqualTo("Implement sort().\n[task][Sorting](testSort,testClass[Sorter],testEmpty)\ntestsColor(testSort)");
    }

    @Test
    void teachingFixturesUseGradleSecurityPathsAndStableReportNamesWithoutChangingTheTestBody() {
        String fixture = """
                @Public
                @WhitelistPath("target")
                @BlacklistPath("target/test-classes")
                class StackTest {
                    @Test @StrictTimeout(1)
                    void pushes() { assertEquals(1, stack.size()); }
                }
                """;

        assertThat(GenerationResources.javaGradleFixture(fixture)).isEqualTo("""
                @org.junit.jupiter.api.DisplayNameGeneration(org.junit.jupiter.api.DisplayNameGenerator.Simple.class)
                @Public
                @WhitelistPath("build")
                @BlacklistPath("build/classes/java/test")
                class StackTest {
                    @Test @StrictTimeout(1)
                    void pushes() { assertEquals(1, stack.size()); }
                }
                """);
    }
}
