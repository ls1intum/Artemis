package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import org.jspecify.annotations.Nullable;

/**
 * Replaces the typographic punctuation a model emits (non-breaking/en/em dashes, smart quotes, non-breaking spaces, ellipsis) with the plain ASCII equivalents.
 * <p>
 * Applied at the agent's write path so the bytes the differential oracle builds are the bytes that get persisted: a non-breaking hyphen inside a graded exception message would
 * otherwise let the sandbox accept an exercise that real grading fails, because the test's expected literal and the solution's literal differ by an invisible character. Persist
 * re-runs it, which is then a no-op. Only punctuation is mapped, so a genuine data literal the exercise is about (CJK, emoji, combining marks) is untouched.
 */
final class TypographyNormalizer {

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
        return text.replaceAll("[\u2010-\u2015]", "-").replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2018', '\'').replace('\u2019', '\'').replace('\u201C', '"')
                .replace('\u201D', '"').replace("\u2026", "...");
    }
}
