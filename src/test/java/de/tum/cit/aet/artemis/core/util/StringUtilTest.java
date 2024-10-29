package de.tum.cit.aet.artemis.core.util;

import static de.tum.cit.aet.artemis.core.util.StringUtil.ILLEGAL_CHARACTERS;
import static de.tum.cit.aet.artemis.core.util.StringUtil.stripIllegalCharacters;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
