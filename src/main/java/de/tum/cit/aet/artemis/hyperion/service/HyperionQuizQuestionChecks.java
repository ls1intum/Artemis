package de.tum.cit.aet.artemis.hyperion.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;

/**
 * Deterministic structural checks applied to every question of a filtered generation before the model-based filter judges it.
 */
final class HyperionQuizQuestionChecks {

    /** Answer options a single-choice or multiple-choice question must carry. */
    static final int OPTION_COUNT = 4;

    private static final int TRUE_FALSE_OPTION_COUNT = 2;

    /**
     * Returns how many answer options a question of the given type carries: two for true-false, four otherwise.
     *
     * @param type the question type
     * @return the option count the generator is asked for and the checks require
     */
    static int optionCountFor(QuizQuestionGenerationType type) {
        return type == QuizQuestionGenerationType.TRUE_FALSE ? TRUE_FALSE_OPTION_COUNT : OPTION_COUNT;
    }

    /** Length limits matching the size constraints of {@code GeneratedQuizQuestionDTO} and {@code GeneratedQuizAnswerOptionDTO}. */
    private static final int MAX_QUESTION_TITLE_LENGTH = 500;

    private static final int MAX_QUESTION_TEXT_LENGTH = 10_000;

    private static final int MAX_OPTION_TEXT_LENGTH = 2_000;

    /** Options a multiple-choice question may mark correct at most when the caller names no bound. */
    private static final int MAX_CORRECT_OPTIONS_MULTIPLE_CHOICE = 3;

    /** Minimum absolute length excess before the correct option counts as conspicuously long. */
    private static final int MAX_CORRECT_OPTION_LENGTH_MARGIN = 20;

    /** Minimum proportional length excess before the correct option counts as conspicuously long. */
    private static final double MIN_CORRECT_OPTION_LENGTH_RATIO = 1.15;

    /**
     * Options that refer to the option set instead of the subject matter: "all of the above", "none of these options",
     * "both a and b", "all answers are correct" and their variants. Matching is case-insensitive and anchored on word boundaries.
     */
    private static final Pattern BANNED_CONSTRUCTION = Pattern
            .compile("\\b(all|none|both|any|either|neither) of (the |these |those )?(above|below|these|those|options|answers|preceding)\\b"
                    + "|\\bboth [a-d] and [a-d]\\b|\\ba and b\\b|\\ball answers are correct\\b", Pattern.CASE_INSENSITIVE);

    /**
     * References to an artifact the question would have to contain: "the following tableau", "the given network", "das folgende Tableau".
     * A question matching this while its text holds no multi-line data block names material the reader cannot see, so it is unanswerable.
     */
    private static final Pattern DANGLING_REFERENCE = Pattern
            .compile(
                    "\\b(the|this|that|das|die|der|dem|den) (following|above|given|shown|presented|folgende\\w*|obige\\w*|gegebene\\w*|gezeigte\\w*|dargestellte\\w*) "
                            + "(tableau|table|figure|diagram|network|matrix|linear program|program|lp|code|tabelle|abbildung|graph\\w*|netzwerk|programm)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:]+$");

    private HyperionQuizQuestionChecks() {
    }

    /**
     * Why a generated question was discarded before the filter judged it.
     */
    enum Defect {
        /** The model's output could not be mapped onto a question at all. */
        UNMAPPABLE,
        /** The question carries a type the request did not ask for. */
        TYPE_NOT_REQUESTED,
        /** The title or the question text is empty. */
        BLANK_TEXT,
        /** The title, the question text or an option exceeds its length limit. */
        TEXT_TOO_LONG,
        /** The question names a tableau, figure or program whose data it does not contain. */
        DANGLING_REFERENCE,
        /** The question carries the wrong number of answer options. */
        OPTION_COUNT,
        /** An answer option has no text. */
        BLANK_OPTION,
        /** Two answer options have the same text. */
        DUPLICATE_OPTIONS,
        /** An option uses a banned construction such as "all of the above". */
        BANNED_CONSTRUCTION,
        /** One option's text contains another's, so the pair is not independent. */
        CONTAINED_OPTION,
        /** The number of options marked correct is wrong for the question type. */
        CORRECT_COUNT,
        /** The correct option is conspicuously longer than the distractors. */
        CONSPICUOUS_LENGTH
    }

    /**
     * Validates a question against the rules every question of a filtered generation must satisfy; a multiple-choice question may mark
     * one to three options correct.
     *
     * @param question     the mapped question
     * @param allowedTypes question types the request asked for
     * @return the first defect found, empty when the question is usable
     */
    static Optional<Defect> defectOf(GeneratedQuizQuestionDTO question, Set<QuizQuestionGenerationType> allowedTypes) {
        return defectOf(question, allowedTypes, MAX_CORRECT_OPTIONS_MULTIPLE_CHOICE);
    }

    /**
     * Validates a question against the rules every question of a filtered generation must satisfy.
     *
     * @param question          the mapped question
     * @param allowedTypes      question types the request asked for
     * @param maxCorrectOptions the most options a multiple-choice question may mark correct
     * @return the first defect found, empty when the question is usable
     */
    static Optional<Defect> defectOf(GeneratedQuizQuestionDTO question, Set<QuizQuestionGenerationType> allowedTypes, int maxCorrectOptions) {
        if (!allowedTypes.contains(question.type())) {
            return Optional.of(Defect.TYPE_NOT_REQUESTED);
        }
        if (isBlank(question.title()) || isBlank(question.questionText())) {
            return Optional.of(Defect.BLANK_TEXT);
        }
        if (question.title().length() > MAX_QUESTION_TITLE_LENGTH || question.questionText().length() > MAX_QUESTION_TEXT_LENGTH) {
            return Optional.of(Defect.TEXT_TOO_LONG);
        }
        if (DANGLING_REFERENCE.matcher(question.questionText()).find() && !question.questionText().contains("\n")) {
            return Optional.of(Defect.DANGLING_REFERENCE);
        }
        return optionDefectOf(question, maxCorrectOptions);
    }

    /** The first defect of the question's answer options, empty when they are usable. */
    private static Optional<Defect> optionDefectOf(GeneratedQuizQuestionDTO question, int maxCorrectOptions) {
        List<GeneratedQuizAnswerOptionDTO> options = question.options();
        int requiredOptions = optionCountFor(question.type());
        if (options == null || options.size() != requiredOptions) {
            return Optional.of(Defect.OPTION_COUNT);
        }
        if (options.stream().anyMatch(option -> isBlank(option.text()))) {
            return Optional.of(Defect.BLANK_OPTION);
        }
        if (options.stream().anyMatch(option -> option.text().length() > MAX_OPTION_TEXT_LENGTH)) {
            return Optional.of(Defect.TEXT_TOO_LONG);
        }
        long distinct = options.stream().map(option -> comparable(option.text())).distinct().count();
        if (distinct != requiredOptions) {
            return Optional.of(Defect.DUPLICATE_OPTIONS);
        }
        if (options.stream().anyMatch(option -> BANNED_CONSTRUCTION.matcher(option.text()).find())) {
            return Optional.of(Defect.BANNED_CONSTRUCTION);
        }
        if (hasContainedOption(options)) {
            return Optional.of(Defect.CONTAINED_OPTION);
        }
        long correct = options.stream().filter(GeneratedQuizAnswerOptionDTO::correct).count();
        boolean correctCountValid = switch (question.type()) {
            case SINGLE_CHOICE, TRUE_FALSE -> correct == 1;
            case MULTIPLE_CHOICE -> correct >= 1 && correct <= maxCorrectOptions;
        };
        if (!correctCountValid) {
            return Optional.of(Defect.CORRECT_COUNT);
        }
        if (correctOptionIsConspicuouslyLong(options)) {
            return Optional.of(Defect.CONSPICUOUS_LENGTH);
        }
        return Optional.empty();
    }

    /**
     * Reduces a question text to a form in which two questions that differ only in case or whitespace compare equal.
     *
     * @param text the question text
     * @return the normalised text
     */
    static String normalise(String text) {
        return WHITESPACE.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }

    /**
     * Detects the correct option being so much longer than every distractor that its length gives it away.
     *
     * @return {@code true} when the longest correct option exceeds the longest distractor by at least {@link #MAX_CORRECT_OPTION_LENGTH_MARGIN}
     *         characters and by more than {@link #MIN_CORRECT_OPTION_LENGTH_RATIO} in proportion
     */
    private static boolean correctOptionIsConspicuouslyLong(List<GeneratedQuizAnswerOptionDTO> options) {
        int correct = options.stream().filter(GeneratedQuizAnswerOptionDTO::correct).mapToInt(option -> option.text().strip().length()).max().orElse(0);
        int longestDistractor = options.stream().filter(option -> !option.correct()).mapToInt(option -> option.text().strip().length()).max().orElse(0);
        if (longestDistractor == 0) {
            return false;
        }
        return correct - longestDistractor >= MAX_CORRECT_OPTION_LENGTH_MARGIN && (double) correct / longestDistractor > MIN_CORRECT_OPTION_LENGTH_RATIO;
    }

    /**
     * Detects one option appearing as a whole phrase inside another, which lets a student eliminate by overlap.
     * Matching on word boundaries avoids flagging coincidental substrings such as "2" inside "12".
     */
    /** The form in which two option texts are compared: lower case, whitespace collapsed, trailing punctuation removed. */
    private static String comparable(String text) {
        return TRAILING_PUNCTUATION.matcher(normalise(text)).replaceAll("").strip();
    }

    private static boolean hasContainedOption(List<GeneratedQuizAnswerOptionDTO> options) {
        List<String> normalised = options.stream().map(option -> comparable(option.text())).toList();
        for (int i = 0; i < normalised.size(); i++) {
            for (int j = 0; j < normalised.size(); j++) {
                if (i == j) {
                    continue;
                }
                String shorter = normalised.get(i);
                String longer = normalised.get(j);
                if (shorter.length() < longer.length() && containsAsWholePhrase(longer, shorter)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether {@code phrase} occurs in {@code text} with no letter or digit directly before or after it.
     */
    private static boolean containsAsWholePhrase(String text, String phrase) {
        if (phrase.isEmpty()) {
            return false;
        }
        int start = text.indexOf(phrase);
        while (start >= 0) {
            int end = start + phrase.length();
            boolean boundaryBefore = start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
            boolean boundaryAfter = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (boundaryBefore && boundaryAfter) {
                return true;
            }
            start = text.indexOf(phrase, start + 1);
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
