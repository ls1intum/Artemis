package de.tum.cit.aet.artemis.hyperion.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;

/**
 * Reads the two things a model is asked for — a title and a difficulty — out of whatever it actually answered.
 * <p>
 * The prompt asks for one small JSON object, and most answers are one. The rest are the usual: a fenced code block, a sentence before the object, or plain prose with no object at
 * all. None of those may cost the instructor their suggestion, so parsing is layered: the JSON object if there is one, the first line if there is not, and the difficulty read
 * from whichever text is available. Anything unrecognised leaves {@link DifficultyLevel#MEDIUM}, which is the difficulty the dialog used to default to silently.
 */
final class HyperionExerciseMetadataParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Matches the difficulty wherever it is stated, in a JSON value ({@code "EASY"}) as well as in prose ({@code "Difficulty: easy."}). */
    private static final Pattern DIFFICULTY_WORD = Pattern.compile("\\b(easy|medium|hard)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * An explicit statement of difficulty in an instructor's brief, in either order: "Difficulty: easy" and "an easy difficulty" both count, "easy to test" does not, because the
     * word has to be tied to the noun to be a statement about the exercise rather than about something in it.
     */
    private static final Pattern DIFFICULTY_IN_BRIEF = Pattern.compile("difficulty\\s*(?:level)?\\s*[:=-]?\\s*(easy|medium|hard)|\\b(easy|medium|hard)\\s+difficulty",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*```[a-zA-Z]*\\s*|\\s*```\\s*$");

    private HyperionExerciseMetadataParser() {
    }

    /**
     * What the model said, before any of it is checked against what Artemis accepts.
     *
     * @param title      the raw title text, empty when the answer carried none
     * @param difficulty the difficulty the answer stated, null when it stated none this parser recognises
     */
    record ModelAnswer(String title, @Nullable DifficultyLevel difficulty) {
    }

    /**
     * Parses a model answer into a title and a difficulty.
     *
     * @param rawAnswer the model's answer, possibly null, fenced, prefixed with prose, or not JSON at all
     * @return what could be read out of it; the title is raw and still has to be sanitised by the caller
     */
    static ModelAnswer parse(@Nullable String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            return new ModelAnswer("", null);
        }
        String answer = CODE_FENCE.matcher(rawAnswer.strip()).replaceAll("").strip();
        JsonNode object = readJsonObject(answer);
        if (object != null) {
            String title = textOf(object, "title");
            DifficultyLevel difficulty = parseDifficulty(textOf(object, "difficulty"));
            if (!title.isBlank()) {
                return new ModelAnswer(title, difficulty);
            }
            // An object without a usable title is no better than prose, so the prose path still gets its turn at naming the exercise.
            return new ModelAnswer(firstLine(answer), difficulty);
        }
        return new ModelAnswer(firstLine(answer), parseDifficulty(answer));
    }

    /**
     * Reads a difficulty an instructor stated in their own words, used when the model reported none. A brief that says how hard the exercise is has already answered the question,
     * and honouring it means a suggestion is still right when there is no model at all.
     *
     * @param brief the instructor's brief
     * @return the difficulty the brief states, or null when it states none
     */
    @Nullable
    static DifficultyLevel difficultyStatedInBrief(String brief) {
        Matcher matcher = DIFFICULTY_IN_BRIEF.matcher(brief);
        if (!matcher.find()) {
            return null;
        }
        String word = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return parseDifficulty(word);
    }

    /** The first non-blank line, which is where a model that ignores "JSON only" puts the title before explaining itself. */
    private static String firstLine(String answer) {
        return answer.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst().orElse("");
    }

    /** Null unless the answer contains something that parses as a JSON object; a model may put a sentence in front of it, so the braces are located rather than assumed. */
    @Nullable
    private static JsonNode readJsonObject(String answer) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(answer.substring(start, end + 1));
            return node != null && node.isObject() ? node : null;
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String textOf(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || !value.isTextual() ? "" : value.asText().strip();
    }

    /** Null rather than {@link DifficultyLevel#MEDIUM} for an unrecognised value, so the caller can still try the brief before settling for the default. */
    @Nullable
    private static DifficultyLevel parseDifficulty(String text) {
        Matcher matcher = DIFFICULTY_WORD.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
            case "easy" -> DifficultyLevel.EASY;
            case "hard" -> DifficultyLevel.HARD;
            default -> DifficultyLevel.MEDIUM;
        };
    }
}
