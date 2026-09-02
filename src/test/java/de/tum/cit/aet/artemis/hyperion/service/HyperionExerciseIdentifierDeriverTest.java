package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PACKAGE_NAME_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.SHORT_NAME_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

/**
 * The deriver's contract is that Artemis accepts whatever it returns and that an instructor recognises it, so every assertion here is either against the production rule itself or
 * against readability.
 */
class HyperionExerciseIdentifierDeriverTest {

    @ParameterizedTest
    @CsvSource({ "Grade Classification with Enum Outcomes, gradeclassenum", "Grade Classification with Enum, gradeclassenum", "Bounded Stack, boundstack",
            "Shortest Paths with Dijkstra, shortpathsdijks" })
    void namesTheExerciseAfterWhatItIsAbout(String title, String expected) {
        assertThat(HyperionExerciseIdentifierDeriver.deriveShortName(title)).isEqualTo(expected);
    }

    @Test
    void dropsStopWordsSoTheMeaningfulWordsSurviveTheLengthCap() {
        // "of" and "the" would otherwise consume the budget that "binary" and "search" need.
        assertThat(HyperionExerciseIdentifierDeriver.deriveShortName("Traversal of the Binary Search Trees")).isEqualTo("travebinarsearc");
        assertThat(HyperionExerciseIdentifierDeriver.deriveShortName("Traversal in Binary Trees")).isEqualTo("travebinartrees");
    }

    @Test
    void keepsStopWordsWhenDroppingThemWouldLeaveNothing() {
        assertThat(HyperionExerciseIdentifierDeriver.deriveShortName("The And Of")).isEqualTo("theandof");
    }

    @Test
    void stripsDiacriticsRatherThanTheLettersCarryingThem() {
        assertThat(HyperionExerciseIdentifierDeriver.deriveShortName("Café Ordering für Anfänger")).isEqualTo("cafeorderfuranfan");
    }

    @Test
    void findsALeadingLetterForATitleThatStartsWithDigits() {
        String shortName = HyperionExerciseIdentifierDeriver.deriveShortName("3D Vector Maths");

        assertThat(SHORT_NAME_PATTERN.matcher(shortName).matches()).isTrue();
        assertThat(shortName).startsWith("d");
    }

    @ParameterizedTest
    @ValueSource(strings = { "A", "42", "!!!", "Q", "a b", "  " })
    void stillYieldsAValidNameForATitleThatReducesToAlmostNothing(String title) {
        String shortName = HyperionExerciseIdentifierDeriver.deriveShortName(title);

        assertThat(SHORT_NAME_PATTERN.matcher(shortName).matches()).isTrue();
        assertThat(shortName).hasSizeGreaterThanOrEqualTo(HyperionExerciseIdentifierDeriver.MIN_SHORT_NAME_LENGTH);
    }

    @Test
    void keepsEveryDerivedNameInsideTheRuleAndTheRepositoryBudget() {
        for (String title : new String[] { "Grade Classification with Enum Outcomes", "a".repeat(200), "Ünïcödé Ëxërcïsë Nämës", "3 2 1", "Bounded Stack" }) {
            String shortName = HyperionExerciseIdentifierDeriver.deriveShortName(title);

            assertThat(SHORT_NAME_PATTERN.matcher(shortName).matches()).as("short name for '%s'", title).isTrue();
            assertThat(shortName).hasSizeLessThanOrEqualTo(HyperionExerciseIdentifierDeriver.MAX_SHORT_NAME_LENGTH)
                    .hasSizeLessThanOrEqualTo(PROGRAMMING_EXERCISE_SHORT_NAME_MAX_LENGTH);
        }
    }

    @Test
    void disambiguatesWithANumberRatherThanARename() {
        assertThat(HyperionExerciseIdentifierDeriver.withSuffix("gradeclassenum", 2)).isEqualTo("gradeclassenum2");
    }

    @Test
    void shortensTheNameRatherThanExceedingTheLengthCapWhenSuffixing() {
        String suffixed = HyperionExerciseIdentifierDeriver.withSuffix("a".repeat(HyperionExerciseIdentifierDeriver.MAX_SHORT_NAME_LENGTH), 137);

        assertThat(suffixed).hasSize(HyperionExerciseIdentifierDeriver.MAX_SHORT_NAME_LENGTH).endsWith("137");
        assertThat(SHORT_NAME_PATTERN.matcher(suffixed).matches()).isTrue();
    }

    @Test
    void buildsThePackageOnTheInstitutionalPrefix() {
        assertThat(HyperionExerciseIdentifierDeriver.derivePackageName("gradeclassenum", ProjectType.PLAIN_MAVEN)).isEqualTo("de.tum.cit.aet.gradeclassenum");
    }

    @ParameterizedTest
    @ValueSource(strings = { "enum", "class", "int", "static", "synchronized", "gradeclassenum", "boundstack", "exercise" })
    void producesAPackageTheJavaRuleAccepts(String shortName) {
        String packageName = HyperionExerciseIdentifierDeriver.derivePackageName(shortName, ProjectType.PLAIN_GRADLE);

        assertThat(ProgrammingExerciseValidationService.PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN.matcher(packageName).matches()).as("package name '%s'", packageName).isTrue();
        assertThat(packageName).hasSizeLessThanOrEqualTo(MAX_PACKAGE_NAME_LENGTH);
    }

    @Test
    void escapesAShortNameThatIsAJavaKeywordInsteadOfProducingAnInvalidPackage() {
        // "enum" is a perfectly good short name and an impossible package segment, so the segment is what changes.
        assertThat(HyperionExerciseIdentifierDeriver.derivePackageName("enum", ProjectType.PLAIN_MAVEN)).isEqualTo("de.tum.cit.aet.enumexercise");
    }

    @Test
    void keepsThePackageWithinTheLengthCapEvenForAnAbsurdShortName() {
        String packageName = HyperionExerciseIdentifierDeriver.derivePackageName("a".repeat(200), ProjectType.PLAIN_MAVEN);

        assertThat(packageName).hasSize(MAX_PACKAGE_NAME_LENGTH);
        assertThat(ProgrammingExerciseValidationService.PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN.matcher(packageName).matches()).isTrue();
    }

    @Test
    void givesABlackboxProjectTheBareIdentifierItsLayoutExpects() {
        assertThat(HyperionExerciseIdentifierDeriver.derivePackageName("boundstack", ProjectType.MAVEN_BLACKBOX)).isEqualTo("boundstack");
    }

    @Test
    void treatsAnAbsentProjectTypeAsADottedOne() {
        assertThat(HyperionExerciseIdentifierDeriver.derivePackageName("boundstack", null)).isEqualTo("de.tum.cit.aet.boundstack");
    }
}
