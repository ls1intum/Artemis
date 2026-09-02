package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_PACKAGE_NAME_LENGTH;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

/**
 * Derives the two identifiers Artemis needs for a programming exercise but an instructor gains nothing from inventing: the short name and the Java package name.
 * <p>
 * Both are derived from the title rather than asked for or guessed by a model, so the same title always produces the same identifiers and the result can be checked against the
 * rules that will judge it: {@link de.tum.cit.aet.artemis.core.config.Constants#SHORT_NAME_PATTERN} for the short name, and the Java/Kotlin package rule of
 * {@link ProgrammingExerciseValidationService} for the package name.
 * <p>
 * The short name is a repository and project-key component — it appears twice in every student repository URL — so it is kept far shorter than the {@code 36} characters Artemis
 * tolerates, and readable: "Grade Classification with Enum Outcomes" becomes {@code gradeclassenum} rather than a timestamp nobody can place.
 */
final class HyperionExerciseIdentifierDeriver {

    /** The minimum {@link de.tum.cit.aet.artemis.core.config.Constants#SHORT_NAME_PATTERN} accepts: a letter plus two more alphanumerics. */
    static final int MIN_SHORT_NAME_LENGTH = 3;

    /**
     * Well inside the {@code 36} characters Artemis allows, because the short name is concatenated into the project key, the repository slugs, and the build plan ids; the room
     * left
     * over is what a disambiguating suffix is appended into.
     */
    static final int MAX_SHORT_NAME_LENGTH = 18;

    /**
     * Each word contributes at most this many characters. Without it a single long word eats the whole budget and the words after it — the ones that say what the exercise is
     * actually about — are dropped: "Grade Classification with Enum Outcomes" would become {@code grade} instead of {@code gradeclassenum}.
     */
    private static final int MAX_WORD_LENGTH = 5;

    /** Carry no meaning in a title and would crowd out the words that do. Lower-case, because words are lower-cased before this set is consulted. */
    private static final Set<String> STOP_WORDS = Set.of("with", "and", "the", "a", "an", "of", "for", "to", "in");

    /**
     * Appended when a title reduces to too little to be a short name, and used as the last-resort package segment. Chosen because it is a word rather than noise, and because it is
     * not a Java keyword, so it can rescue a keyword collision as well as a length one.
     */
    private static final String FILLER = "exercise";

    /**
     * The institutional package prefix Artemis already proposes for AI-derived Java packages on the client (see {@code deriveProposedPackageName} in
     * {@code problem-statement.utils.ts}), reused here so a generated exercise reads like every other course exercise. A deployment-specific prefix belongs in server configuration
     * rather than here, and nothing in the course or the programming configuration supplies one today.
     */
    private static final String PACKAGE_PREFIX = "de.tum.cit.aet";

    private static final Pattern COMBINING_MARK = Pattern.compile("\\p{M}+");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Za-z0-9]+");

    private static final Pattern LEADING_NON_LETTER = Pattern.compile("^[^A-Za-z]+");

    private HyperionExerciseIdentifierDeriver() {
    }

    /**
     * Derives a readable short name from an exercise title.
     *
     * @param title the exercise title, already sanitised into something Artemis accepts as a title
     * @return a short name matching {@link de.tum.cit.aet.artemis.core.config.Constants#SHORT_NAME_PATTERN} and at most {@link #MAX_SHORT_NAME_LENGTH} characters long; never blank
     */
    static String deriveShortName(String title) {
        List<String> words = meaningfulWords(title);
        StringBuilder slug = new StringBuilder();
        for (String word : words) {
            String abbreviated = word.length() <= MAX_WORD_LENGTH ? word : word.substring(0, MAX_WORD_LENGTH);
            if (!slug.isEmpty() && slug.length() + abbreviated.length() > MAX_SHORT_NAME_LENGTH) {
                break;
            }
            slug.append(abbreviated);
        }
        // The pattern demands a leading letter, and a title like "3D Vectors" does not offer one until its digits are gone.
        String candidate = LEADING_NON_LETTER.matcher(slug.toString()).replaceFirst("");
        if (candidate.length() > MAX_SHORT_NAME_LENGTH) {
            candidate = candidate.substring(0, MAX_SHORT_NAME_LENGTH);
        }
        if (candidate.length() < MIN_SHORT_NAME_LENGTH) {
            // A title of one letter, of digits, or of punctuation still has to yield a name Artemis accepts.
            candidate = (candidate + FILLER).substring(0, Math.min(candidate.length() + FILLER.length(), MAX_SHORT_NAME_LENGTH));
        }
        return candidate;
    }

    /**
     * Appends a numeric disambiguation suffix, shortening the short name itself as far as needed to stay within {@link #MAX_SHORT_NAME_LENGTH}.
     *
     * @param shortName an already derived short name
     * @param suffix    the disambiguating number
     * @return the suffixed short name, still matching the short-name pattern and still within the length cap
     */
    static String withSuffix(String shortName, long suffix) {
        String appendix = String.valueOf(suffix);
        int room = MAX_SHORT_NAME_LENGTH - appendix.length();
        String base = shortName.length() <= room ? shortName : shortName.substring(0, Math.max(room, 0));
        // The suffix is digits, so the base has to carry the leading letter and enough length on its own.
        if (base.length() < MIN_SHORT_NAME_LENGTH) {
            base = FILLER.substring(0, Math.max(MIN_SHORT_NAME_LENGTH, Math.min(room, FILLER.length())));
        }
        return base + appendix;
    }

    /**
     * Derives the Java package name from an already derived short name.
     * <p>
     * A short name that is a Java keyword ({@code enum}, {@code class}, ...) is a valid short name and an invalid package segment, so the segment — not the short name — carries
     * the escape.
     *
     * @param shortName   the exercise short name
     * @param projectType the project type the exercise will use; {@code MAVEN_BLACKBOX} takes a single-identifier package, everything else the dotted institutional prefix
     * @return a package name matching the Java/Kotlin package rule and within {@link de.tum.cit.aet.artemis.core.config.Constants#MAX_PACKAGE_NAME_LENGTH}
     */
    static String derivePackageName(String shortName, @Nullable ProjectType projectType) {
        // Blackbox exercises are validated against the same Java pattern but conventionally carry a bare identifier rather than a dotted package.
        String prefix = projectType == ProjectType.MAVEN_BLACKBOX ? "" : PACKAGE_PREFIX + ".";
        int budget = MAX_PACKAGE_NAME_LENGTH - prefix.length();
        String segment = shortName.length() <= budget ? shortName : shortName.substring(0, budget);
        for (String candidateSegment : List.of(segment, truncate(segment + FILLER, budget), FILLER)) {
            String candidate = prefix + candidateSegment;
            if (ProgrammingExerciseValidationService.PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN.matcher(candidate).matches() && candidate.length() <= MAX_PACKAGE_NAME_LENGTH) {
                return candidate;
            }
        }
        // Unreachable: the loop's last candidate is the prefix plus a non-keyword word that fits by construction.
        return prefix + FILLER;
    }

    /** The title's words, lower-cased and stripped of diacritics, with stop words dropped unless dropping them would leave nothing behind. */
    private static List<String> meaningfulWords(String title) {
        String ascii = COMBINING_MARK.matcher(Normalizer.normalize(title, Normalizer.Form.NFKD)).replaceAll("");
        List<String> words = new ArrayList<>();
        for (String word : NON_ALPHANUMERIC.split(ascii)) {
            if (!word.isEmpty()) {
                words.add(word.toLowerCase(Locale.ROOT));
            }
        }
        List<String> meaningful = words.stream().filter(word -> !STOP_WORDS.contains(word)).toList();
        return meaningful.isEmpty() ? words : meaningful;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, Math.max(maxLength, 0));
    }
}
