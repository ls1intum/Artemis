package de.tum.cit.aet.artemis.globalsearch.service.embedding;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;

/**
 * Splits the text of one searchable entity into bounded embedding inputs ("chunks").
 * <p>
 * Every chunk is a header line (type label with the meaningful metadata words, the title, for exercises the short
 * name, for lecture units the lecture title) followed by a piece of the cleaned description. Each complete chunk stays
 * within {@link #TOKEN_LIMIT} estimated tokens and {@link #HARD_BYTES} UTF-8 bytes, so no embedding input exceeds
 * about 512 tokens of the Qwen3 embedding model. A description that fits completely yields exactly one chunk.
 * <p>
 * The token estimate is the cl100k token count plus one per digit: cl100k groups up to three digits into one token,
 * while Qwen3 encodes every digit on its own. Over the production corpus the Qwen3 count was at most 1.083 times this
 * estimate, so the limit of 470 keeps chunks at about 512 Qwen3 tokens.
 * <p>
 * The rules mirror the reference implementation the chunk size was evaluated with, including Python's notion of
 * whitespace and digits, so the character classes below are spelled out instead of using {@code \s} and {@code \d}.
 * Changing any rule changes the chunks of existing rows: bump {@link #CHUNKER_VERSION} so stored vectors are not
 * reused for chunks they were not computed for.
 */
public final class SearchableEntityChunker {

    /**
     * Version of the chunking rules; stored per row so a rule change never reuses vectors of old chunks.
     */
    public static final int CHUNKER_VERSION = 1;

    /**
     * Maximum estimated tokens of one complete chunk (header and body).
     */
    public static final int TOKEN_LIMIT = 470;

    /**
     * Maximum UTF-8 bytes of one complete chunk; a backstop that holds for any text.
     */
    public static final int HARD_BYTES = 2048;

    private static final int HEADER_MAX_BYTES = 400;

    private static final double CUT_FLOOR_SHARE = 0.6;

    private static final double OVERLAP_SHARE = 0.1;

    /**
     * Python's whitespace (what {@code str.isspace()} and {@code \s} accept), which is wider than Java's.
     */
    private static final String WS = "\\t\\n\\x0B\\f\\r\\x1C-\\x1F \\x85\\xA0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000";

    private static final String S = "[" + WS + "]";

    private static final String NOT_S = "[^" + WS + "]";

    private static final String DIGIT = "\\p{Nd}";

    /**
     * Only {@code \n} ends a line, as in Python.
     */
    private static final int LINES = Pattern.UNIX_LINES;

    private static final List<Rule> CLEANING_RULES = List.of(new Rule(Pattern.compile("@startuml.*?@enduml", Pattern.DOTALL | LINES), " "),
            new Rule(Pattern.compile("```.*?```", Pattern.DOTALL | LINES), " "), new Rule(Pattern.compile("\\$\\$.*?\\$\\$", Pattern.DOTALL | LINES), " "),
            new Rule(Pattern.compile("<testid>" + DIGIT + "*</testid>", LINES), ""), new Rule(Pattern.compile("\\[task\\]\\[([^\\]]*)\\]\\([^)]*\\)", LINES), "$1"),
            new Rule(Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)", LINES), " "), new Rule(Pattern.compile("\\[([^\\]]*)\\]\\((?:[^()]|\\([^)]*\\))*\\)", LINES), "$1"),
            new Rule(Pattern.compile("<[^>]+>", LINES), " "), new Rule(Pattern.compile("https?://" + NOT_S + "+", LINES), " "),
            new Rule(Pattern.compile("^" + S + "{0,3}#{1,6}" + S + "*", Pattern.MULTILINE | LINES), ""), new Rule(Pattern.compile("[*_`~]{1,3}", LINES), ""),
            new Rule(Pattern.compile("^" + S + "*\\|?[-:| ]{3,}\\|?" + S + "*$", Pattern.MULTILINE | LINES), " "), new Rule(Pattern.compile("\\|", LINES), " "));

    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \\t\\f\\x0B]+", LINES);

    private static final Pattern BLANK_LINES = Pattern.compile(S + "*\\n" + S + "*(\\n" + S + "*)+", LINES);

    private static final Pattern SPACED_NEWLINE = Pattern.compile(" ?\\n ?", LINES);

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?;])" + S + "+|\\n+", LINES);

    private static final Pattern WORD_BREAK = Pattern.compile(S + "+", LINES);

    private static final Pattern DIGITS = Pattern.compile(DIGIT, LINES);

    private static final String[] CUT_SEPARATORS = { "\n\n", "\n", ". ", "! ", "? ", "; " };

    private static final Map<String, String> TYPE_LABELS = Map.of(SearchableEntitySchema.TypeValues.LECTURE, "Lecture", SearchableEntitySchema.TypeValues.EXAM, "Exam",
            SearchableEntitySchema.TypeValues.COURSE, "Course", SearchableEntitySchema.TypeValues.CHANNEL, "Channel", SearchableEntitySchema.TypeValues.FAQ, "FAQ",
            SearchableEntitySchema.TypeValues.POST, "Post", SearchableEntitySchema.TypeValues.ANSWER_POST, "Answer");

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private record Rule(Pattern pattern, String replacement) {
    }

    private SearchableEntityChunker() {
    }

    /**
     * Returns the embedding inputs of an entity, derived only from its indexed property map, so the same chunks can
     * be rebuilt later from a stored row.
     *
     * @param properties the property map of the entity as written to the index
     * @return the chunk texts, at least one (the header alone when the entity has no description)
     */
    public static List<String> chunk(Map<String, Object> properties) {
        String header = header(properties);
        String body = clean(asString(properties.get(SearchableEntitySchema.Properties.DESCRIPTION)));
        String single = compose(header, body);
        if (body.isEmpty() || single.equals(header + "\n" + body)) {
            return List.of(single);
        }
        return chunks(header, body);
    }

    /**
     * Returns the description part of a chunk, without its header line.
     *
     * @param chunk a chunk text produced by {@link #chunk(Map)}
     * @return the text after the header line, empty for a header-only chunk
     */
    public static String body(String chunk) {
        int newline = chunk.indexOf('\n');
        return newline < 0 ? "" : chunk.substring(newline + 1);
    }

    /**
     * Estimates the embedding-model tokens of a text: cl100k tokens (special-token text counted as ordinary text) plus
     * one per digit.
     *
     * @param text the text
     * @return the estimate
     */
    public static int estimateTokens(String text) {
        Matcher digits = DIGITS.matcher(text);
        int digitCount = 0;
        while (digits.find()) {
            digitCount++;
        }
        return ENCODING.countTokensOrdinary(text) + digitCount;
    }

    /**
     * Removes markup that carries no meaning for search: diagram and code blocks, display math, images, HTML tags,
     * URLs, Markdown emphasis and table rules. Task markers keep their names and links keep their text.
     *
     * @param text the raw description, may be null
     * @return the cleaned text
     */
    static String clean(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cleaned = text;
        for (Rule rule : CLEANING_RULES) {
            cleaned = rule.pattern().matcher(cleaned).replaceAll(rule.replacement());
        }
        cleaned = HORIZONTAL_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = BLANK_LINES.matcher(cleaned).replaceAll("\n\n");
        cleaned = SPACED_NEWLINE.matcher(cleaned).replaceAll("\n");
        return strip(cleaned);
    }

    static String header(Map<String, Object> properties) {
        String type = asString(properties.get(SearchableEntitySchema.Properties.TYPE));
        String title = strip(asString(properties.get(SearchableEntitySchema.Properties.TITLE)));
        if (SearchableEntitySchema.TypeValues.EXERCISE.equals(type)) {
            String kind = strip(asString(properties.get(SearchableEntitySchema.Properties.EXERCISE_TYPE)).replace('-', ' ').replace('_', ' '));
            String label = kind.isEmpty() ? "Exercise" : capitalize(kind) + " exercise";
            String language = strip(asString(properties.get(SearchableEntitySchema.Properties.PROGRAMMING_LANGUAGE)));
            if (!language.isEmpty()) {
                label += " (" + titleCase(language.replace('_', ' ')) + ")";
            }
            String header = label + ": " + title;
            String shortName = asString(properties.get(SearchableEntitySchema.Properties.SHORT_NAME));
            return shortName.isEmpty() ? header : header + " [" + shortName + "]";
        }
        if (SearchableEntitySchema.TypeValues.LECTURE_UNIT.equals(type)) {
            String lectureTitle = asString(properties.get(SearchableEntitySchema.Properties.LECTURE_TITLE));
            return lectureTitle.isEmpty() ? "Lecture unit: " + title : "Lecture unit in " + lectureTitle + ": " + title;
        }
        String label = TYPE_LABELS.getOrDefault(type, capitalize(type));
        return title.isEmpty() ? label : label + ": " + title;
    }

    private static String compose(String header, String body) {
        String boundedHeader = cutTokens(cutBytes(header, HEADER_MAX_BYTES), TOKEN_LIMIT / 4);
        int rest = TOKEN_LIMIT - estimateTokens(boundedHeader) - 1;
        String boundedBody = rest > 0 && !body.isEmpty() ? cutTokens(cutBytes(body, HARD_BYTES - utf8Length(boundedHeader) - 1), rest) : "";
        return boundedBody.isEmpty() ? boundedHeader : boundedHeader + "\n" + boundedBody;
    }

    /**
     * Greedy sentence packing: every chunk meets the token and byte bound on its joined text, over-long sentences are
     * split into word pieces, and consecutive chunks share about ten percent of trailing sentences. Only a single word
     * longer than a whole chunk is cut.
     */
    private static List<String> chunks(String header, String body) {
        String boundedHeader = cutTokens(cutBytes(header, HEADER_MAX_BYTES), TOKEN_LIMIT / 4);
        int budget = TOKEN_LIMIT - estimateTokens(boundedHeader) - 1;
        int byteBudget = HARD_BYTES - utf8Length(boundedHeader) - 1;
        List<String> pieces = new ArrayList<>();
        for (String rawSentence : SENTENCE_BREAK.split(body, -1)) {
            String sentence = strip(rawSentence);
            if (sentence.isEmpty()) {
                continue;
            }
            if (fits(sentence, budget, byteBudget)) {
                pieces.add(sentence);
                continue;
            }
            List<String> words = new ArrayList<>();
            for (String word : WORD_BREAK.split(sentence, -1)) {
                if (!words.isEmpty() && !fits(joinWith(words, word), budget, byteBudget)) {
                    pieces.add(String.join(" ", words));
                    words = new ArrayList<>();
                }
                words.add(word);
            }
            if (!words.isEmpty()) {
                pieces.add(String.join(" ", words));
            }
        }
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String rawPiece : pieces) {
            String piece = fits(rawPiece, budget, byteBudget) ? rawPiece : cutTokens(cutBytes(rawPiece, byteBudget), budget);
            if (!current.isEmpty() && !fits(joinWith(current, piece), budget, byteBudget)) {
                chunks.add(String.join(" ", current));
                current = overlap(current, piece, budget, byteBudget);
            }
            current.add(piece);
        }
        if (!current.isEmpty()) {
            chunks.add(String.join(" ", current));
        }
        if (chunks.isEmpty()) {
            return List.of(boundedHeader);
        }
        return chunks.stream().map(chunk -> boundedHeader + "\n" + chunk).toList();
    }

    private static List<String> overlap(List<String> previous, String next, int budget, int byteBudget) {
        List<String> keep = new ArrayList<>();
        for (int i = previous.size() - 1; i >= 0; i--) {
            List<String> candidate = new ArrayList<>();
            candidate.add(previous.get(i));
            candidate.addAll(keep);
            if (estimateTokens(String.join(" ", candidate)) > budget * OVERLAP_SHARE || !fits(joinWith(candidate, next), budget, byteBudget)) {
                break;
            }
            keep.addFirst(previous.get(i));
        }
        return keep;
    }

    private static boolean fits(String text, int budget, int byteBudget) {
        return estimateTokens(text) <= budget && utf8Length(text) <= byteBudget;
    }

    private static String joinWith(List<String> parts, String next) {
        List<String> all = new ArrayList<>(parts);
        all.add(next);
        return String.join(" ", all);
    }

    /**
     * Longest natural-boundary prefix whose estimate is within the limit, found by a binary search over the byte cut.
     */
    private static String cutTokens(String text, int limit) {
        if (estimateTokens(text) <= limit) {
            return text;
        }
        int low = 0;
        int high = Math.min(utf8Length(text), HARD_BYTES);
        String best = "";
        while (low <= high) {
            int middle = (low + high) / 2;
            String candidate = cutBytes(text, middle);
            if (estimateTokens(candidate) <= limit) {
                best = candidate;
                low = middle + 1;
            }
            else {
                high = middle - 1;
            }
        }
        return best;
    }

    /**
     * Longest prefix within the byte limit, ending at a paragraph or sentence end if one lies in its last 40 percent,
     * else at a word end there.
     */
    private static String cutBytes(String text, int limit) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        if (raw.length <= limit) {
            return text;
        }
        String head = decodeIgnoringPartial(raw, limit);
        int floor = (int) (head.codePointCount(0, head.length()) * CUT_FLOOR_SHARE);
        for (String separator : CUT_SEPARATORS) {
            int index = head.lastIndexOf(separator);
            if (index >= 0 && head.codePointCount(0, index) >= floor) {
                return stripTrailing(head.substring(0, index + strip(separator).length()));
            }
        }
        int space = head.lastIndexOf(' ');
        return stripTrailing(space >= 0 && head.codePointCount(0, space) >= floor ? head.substring(0, space) : head);
    }

    private static String decodeIgnoringPartial(byte[] raw, int limit) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE).onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(raw, 0, limit));
            return decoded.toString();
        }
        catch (CharacterCodingException e) {
            throw new IllegalStateException("UTF-8 decoding with IGNORE cannot fail", e);
        }
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isPythonSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || codePoint == 0x85 || codePoint == 0xA0 || codePoint == 0x2007 || codePoint == 0x202F;
    }

    private static String strip(String text) {
        int start = 0;
        while (start < text.length() && isPythonSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        return stripTrailing(text.substring(start));
    }

    private static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && isPythonSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(0, end);
    }

    /**
     * Python's {@code str.capitalize()}: first character upper case, the rest lower case.
     */
    private static String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        int first = text.codePointAt(0);
        return new String(Character.toChars(Character.toTitleCase(first))) + text.substring(Character.charCount(first)).toLowerCase(Locale.ROOT);
    }

    /**
     * Python's {@code str.title()} for enum-like values: every letter after a non-letter upper case, others lower case.
     */
    private static String titleCase(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean previousIsLetter = false;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            boolean isLetter = Character.isLetter(codePoint);
            int mapped = isLetter ? (previousIsLetter ? Character.toLowerCase(codePoint) : Character.toTitleCase(codePoint)) : codePoint;
            result.appendCodePoint(mapped);
            previousIsLetter = isLetter;
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
