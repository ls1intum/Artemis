package de.tum.cit.aet.artemis.hyperion.service;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Turns free-form model output into a string Artemis accepts as an exercise title.
 * <p>
 * Artemis validates a programming exercise title twice with the same two rules: at least three characters, and every character inside
 * {@link de.tum.cit.aet.artemis.core.config.Constants#TITLE_NAME_PATTERN}. A model answers in prose — it quotes, it adds a colon, it explains itself on a second line — so a
 * suggestion is filtered into that shape here rather than offered to the instructor and rejected on save. Anything that cannot be filtered into a valid title becomes the empty
 * string, which is the caller's signal to use its own fallback.
 */
final class HyperionExerciseTitleSanitizer {

    /** The minimum {@link de.tum.cit.aet.artemis.exercise.domain.Exercise#validateTitle()} accepts. */
    static final int MIN_TITLE_LENGTH = 3;

    /** A title is a label in an exercise list, not a summary; the database column allows far more, but a suggestion that long reads as a sentence. */
    static final int MAX_TITLE_LENGTH = 60;

    /**
     * The complement of the character class in {@link de.tum.cit.aet.artemis.core.config.Constants#TITLE_NAME_PATTERN}. Kept as its own literal rather than derived from that
     * pattern, because a pattern anchored with {@code ^} and quantified with {@code *} cannot be inverted mechanically;
     * {@code HyperionExerciseTitleSanitizerTest} asserts every sanitised result against the real pattern so the two cannot drift.
     */
    private static final Pattern DISALLOWED_CHARACTER = Pattern.compile("[^\\p{L}\\p{M}\\p{N}_\\-\\s]");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private HyperionExerciseTitleSanitizer() {
    }

    /**
     * Filters a model's answer into a valid title.
     *
     * @param candidate the raw model answer, possibly null, multi-line, quoted, or punctuated
     * @return a title matching {@link de.tum.cit.aet.artemis.core.config.Constants#TITLE_NAME_PATTERN} and at least {@link #MIN_TITLE_LENGTH} characters long, or the empty string
     *         when nothing usable is left
     */
    static String sanitize(@Nullable String candidate) {
        if (candidate == null) {
            return "";
        }
        // A model that ignores "one line only" puts the title first and its reasoning after, so the first non-blank line is the answer.
        String firstLine = candidate.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
        // Replaced with a space rather than removed: dropping the colon in "Stack: Bounded" would weld two words together.
        String allowedOnly = DISALLOWED_CHARACTER.matcher(firstLine).replaceAll(" ");
        String collapsed = WHITESPACE_RUN.matcher(allowedOnly).replaceAll(" ").strip();
        String truncated = truncate(collapsed);
        return truncated.length() < MIN_TITLE_LENGTH ? "" : truncated;
    }

    /**
     * Appends a numeric disambiguation suffix, shortening the title itself as far as needed to stay within {@link #MAX_TITLE_LENGTH}.
     *
     * @param title  an already sanitised title
     * @param suffix the disambiguating number
     * @return the suffixed title, still within the length cap
     */
    static String withSuffix(String title, long suffix) {
        String appendix = " " + suffix;
        int room = MAX_TITLE_LENGTH - appendix.length();
        String base = title.length() <= room ? title : title.substring(0, Math.max(room, 0)).strip();
        // A suffix long enough to leave no room at all still has to produce something valid, so the number alone is the title of last resort.
        return base.isEmpty() ? String.valueOf(suffix) : base + appendix;
    }

    /** Cuts at a word boundary when that leaves a usable title, so a capped suggestion does not end mid-word. */
    private static String truncate(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        String cut = title.substring(0, MAX_TITLE_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace >= MIN_TITLE_LENGTH) {
            cut = cut.substring(0, lastSpace);
        }
        return cut.strip();
    }
}
