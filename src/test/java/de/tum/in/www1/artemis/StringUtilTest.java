package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.web.rest.util.StringUtil.ILLEGAL_CHARACTERS;
import static de.tum.in.www1.artemis.web.rest.util.StringUtil.stripIllegalCharacters;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringUtilTest {

    @Test
    public void testEmpty() {
        assertEquals("", stripIllegalCharacters(""));
    }

    @Test
    public void testOnlyIllegals() {
        assertEquals("", stripIllegalCharacters(ILLEGAL_CHARACTERS));
    }

    @Test
    public void testNormal() {
        assertEquals("abcdefg", stripIllegalCharacters("abcdefg"));
    }

    @Test
    public void testUmlaut() {
        assertEquals("abcdefgäöü", stripIllegalCharacters("abcdefgäöü"));
    }

    @Test
    public void testWithIllegal() {
        assertEquals("a^bcdefg(ä)öü", stripIllegalCharacters("a^b\"c$d%e&f/g(ä)ö?ü"));
    }
}
