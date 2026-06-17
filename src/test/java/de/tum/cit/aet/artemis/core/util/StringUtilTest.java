package de.tum.cit.aet.artemis.core.util;

import static de.tum.cit.aet.artemis.core.util.StringUtil.ILLEGAL_CHARACTERS;
import static de.tum.cit.aet.artemis.core.util.StringUtil.sanitizeStringForFileName;
import static de.tum.cit.aet.artemis.core.util.StringUtil.stripIllegalCharacters;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StringUtilTest {

    @Test
    void testEmpty() {
        assertThat(stripIllegalCharacters("")).isEmpty();
    }

    @Test
    void testOnlyIllegals() {
        assertThat(stripIllegalCharacters(ILLEGAL_CHARACTERS)).isEmpty();
    }

    @Test
    void testNormal() {
        assertThat(stripIllegalCharacters("abcdefg")).isEqualTo("abcdefg");
    }

    @Test
    void testUmlaut() {
        assertThat(stripIllegalCharacters("abcdefgäöü")).isEqualTo("abcdefgäöü");
    }

    @Test
    void testWithIllegal() {
        assertThat(stripIllegalCharacters("a^b\"c$d%e&f/g(ä)ö?ü")).isEqualTo("a^bcdefg(ä)öü");
    }

    @ParameterizedTest
    @CsvSource({
            // umlauts and accents are reduced to their ASCII base letter so file names stay ASCII-only
            "Lärche, Larche", "Müller Übung, Muller_Ubung", "naïve café, naive_cafe",
            // whitespace collapses to a single underscore; filesystem-reserved characters are removed
            "'exercise abc?+#', exercise_abc", "'a   b', a_b", "'a/b\\c:d*e', abcde",
            // plain ASCII is unchanged
            "Algorithmen-1, Algorithmen-1" })
    void sanitizeStringForFileNameReducesToAsciiAndRemovesReservedCharacters(String input, String expected) {
        assertThat(sanitizeStringForFileName(input)).isEqualTo(expected);
    }

    @Test
    void sanitizeStringForFileNameProducesAsciiOnlyOutput() {
        // even letters without an ASCII decomposition (e.g. ß, ø) must not leave non-ASCII characters in a file name
        assertThat(sanitizeStringForFileName("Maß Sørensen")).containsPattern("^[\\x00-\\x7F]*$");
    }

    @Test
    void sanitizeStringForFileNameReturnsEmptyForNullOrOnlyNonAsciiInput() {
        // a title with no ASCII representation sanitizes to empty; callers must add a uniqueness fallback
        // (see BaseExercise#getSanitizedExerciseTitle) so export directories cannot collide
        assertThat(sanitizeStringForFileName(null)).isEmpty();
        assertThat(sanitizeStringForFileName("テスト")).isEmpty();
        assertThat(sanitizeStringForFileName("Σθμ")).isEmpty();
        assertThat(sanitizeStringForFileName("😀")).isEmpty();
    }

    @Test
    void sanitizeStringForFileNameKeepsAsciiPartsOfMixedInput() {
        // non-ASCII letters are dropped while surrounding ASCII content is preserved and whitespace collapses to underscores
        assertThat(sanitizeStringForFileName("A テスト B")).isEqualTo("A_B");
    }
}
