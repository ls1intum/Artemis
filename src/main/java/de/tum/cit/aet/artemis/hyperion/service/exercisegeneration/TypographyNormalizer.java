package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Maps the typographic punctuation a model emits (non-breaking/en/em dashes, smart quotes, non-breaking spaces, ellipsis) to its ASCII equivalent, and deletes the characters that
 * render as nothing at all (soft hyphen, zero-width space, directional marks, byte-order mark).
 * <p>
 * Applied at the agent's write path so the bytes the differential oracle builds are the bytes that get persisted: a non-breaking hyphen inside a graded exception message would
 * otherwise let the sandbox accept an exercise that real grading fails, because the test's expected literal and the solution's literal differ by an invisible character. Persist
 * re-runs it, which is then a no-op ({@code normalize} is idempotent: no replacement's output re-enters another's domain).
 * <p>
 * Only punctuation and invisibles are touched, so a genuine data literal the exercise is about (CJK, emoji, combining marks, {@code U+2212 MINUS SIGN}) survives unchanged.
 */
final class TypographyNormalizer {

    /** The dash family (hyphen, non-breaking hyphen, figure dash, en dash, em dash, horizontal bar); all render like a hyphen-minus and none is ever meant as data. */
    private static final Pattern DASHES = Pattern.compile("[\\u2010-\\u2015]");

    /**
     * Characters that render as nothing at all: soft hyphen, zero-width space, LTR/RTL marks, byte-order mark. They cannot be seen, typed or grepped, so they are always an
     * accident; a soft hyphen inside a graded exception message reproduces the invisible-character mismatch the dash family causes. Deleted rather than mapped. Deliberately does
     * NOT include semantic look-alikes such as U+2212 MINUS SIGN, which an arithmetic exercise may legitimately assert on.
     */
    private static final Pattern INVISIBLES = Pattern.compile("[\u00AD\u200B\u200E\u200F\uFEFF]");

    private TypographyNormalizer() {
    }

    /**
     * @param text the text to normalize; may be {@code null}
     * @return the text with typographic punctuation replaced by ASCII, or {@code null} when the input was {@code null}
     */
    @Nullable
    static String normalize(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String dashed = DASHES.matcher(text).replaceAll("-");
        String visible = INVISIBLES.matcher(dashed).replaceAll("");
        return visible.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201C', '"').replace('\u201D', '"').replace("\u2026",
                "...");
    }
}
