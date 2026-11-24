package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Reads the syntax of the build files of a tests repository as far as the Security Framework needs it: which parts are comments, strings or Maven
 * profiles, so that a pinned Ares version, the Ares agent or a library in a comment is not mistaken for code, see {@link SecurityPolicyRepositoryFiles} and
 * {@link AresAgentDetection}.
 */
final class BuildFileSyntax {

    /**
     * The Groovy keywords after which an expression, and therefore a slashy string, may follow; any other word is an operand, such as a variable,
     * {@code this} or {@code null}.
     */
    private static final Set<String> GROOVY_KEYWORDS_BEFORE_EXPRESSIONS = Set.of("return", "throw", "assert", "case", "in", "yield", "else", "do");

    private static final String ARES_1_ARTIFACT = "artemis-java-test-sandbox";

    /** The Ares 1 library in a Gradle dependency, e.g. {@code 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'} or {@code name: 'artemis-java-test-sandbox'}. */
    private static final Pattern GRADLE_ARES_1_DEPENDENCY = Pattern.compile("[:'\"]\\s*+" + ARES_1_ARTIFACT + "\\s*+[:'\"]");

    /** The Ares 1 library as the artifact of a Maven dependency. */
    private static final Pattern MAVEN_ARES_1_DEPENDENCY = Pattern.compile("<artifactId>\\s*+" + ARES_1_ARTIFACT + "\\s*+</artifactId>");

    /** A keyword or type right before a Gradle variable that declares it, e.g. {@code def}, matched at the end of the code before the variable. */
    private static final Pattern GRADLE_DECLARATION_BEFORE = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(?:def|var|final|String|Object)\\s++$");

    /** A closed Gradle block comment, e.g. {@code /* Ares 2 *}{@code /}, also one that spans several lines. */
    private static final String GRADLE_BLOCK_COMMENT = "/\\*(?:[^*]|\\*(?!/))*+\\*/";

    /**
     * The Ares version as a complete string literal on the right of {@code aresVersion =}, e.g. {@code aresVersion = '2.2.1'}. An expression such as
     * {@code '2.2' + '.1'} or an interpolation such as {@code "${base}.1"} pins no version Artemis could read or replace, so it is no pin. Block comments
     * after the literal are skipped, so {@code '2.2.' /* note *}{@code / + '1'} is an expression as well.
     */
    private static final Pattern GRADLE_ARES_VERSION = Pattern
            .compile("(?<!\\p{javaJavaIdentifierPart})(aresVersion\\s*=\\s*['\"])([^'\"$]+)(['\"])(?=(?:[ \\t]|" + GRADLE_BLOCK_COMMENT + ")*+(?:[;}\\r\\n]|//|$))");

    /** The Ares version as the text of {@code <ares.version>}; a reference to another property, e.g. {@code ${base.version}}, is no pin. */
    private static final Pattern MAVEN_ARES_VERSION = Pattern.compile("(<ares\\.version\\s*+>)([^<$]+)(</ares\\.version\\s*+>)");

    /** Any assignment of the Ares version in Gradle, whatever its value, e.g. also {@code aresVersion = "${base}.1"}; {@code ==} compares and is none. */
    private static final Pattern GRADLE_ARES_VERSION_ASSIGNMENT = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})aresVersion\\s*+=(?!=)");

    /**
     * Any definition of the Ares version in a POM, whatever its value, e.g. also {@code <ares.version>${next.ares.version}</ares.version>}; XML allows
     * whitespace before the {@code >} of both tags.
     */
    private static final Pattern MAVEN_ARES_VERSION_DEFINITION = Pattern
            .compile("<ares\\.version\\s*+(?:/>|>(?:[^<]|<!\\[CDATA\\[(?:[^\\]]|](?!]>))*+]]>)*+</ares\\.version\\s*+>)");

    /** The start tag of the profiles of a POM; XML allows whitespace before its {@code >}. */
    private static final Pattern MAVEN_PROFILES_START = Pattern.compile("<profiles\\s*+>");

    private static final Pattern MAVEN_PROFILES_END = Pattern.compile("</profiles\\s*+>");

    private BuildFileSyntax() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param buildFiles the build files at the root of a repository
     * @return whether a build file declares a dependency on the Ares 1 library {@code artemis-java-test-sandbox}; a mention in a comment, e.g. one that
     *         records the migration to Ares 2, does not count
     */
    static boolean declaresAres1Dependency(SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        String gradleBuild = buildFiles.gradleBuild();
        String mavenPom = buildFiles.mavenPom();
        return (gradleBuild != null && GRADLE_ARES_1_DEPENDENCY.matcher(withoutMarked(gradleBuild, gradleComments(gradleBuild))).find())
                || (mavenPom != null && MAVEN_ARES_1_DEPENDENCY.matcher(withoutMarked(mavenPom, mavenComments(mavenPom))).find());
    }

    /**
     * @param marked for each character of the content whether to remove it
     * @return the content with the marked characters replaced by spaces, so offsets stay the same, e.g. those of {@link #mavenProfiles(String, boolean[])}
     */
    static String withoutMarked(String content, boolean[] marked) {
        char[] characters = content.toCharArray();
        for (int index = 0; index < characters.length; index++) {
            if (marked[index]) {
                characters[index] = ' ';
            }
        }
        return new String(characters);
    }

    /**
     * @return for each character of a Gradle build file whether it belongs to a {@code //} or {@code /* *}{@code /} comment, to a quoted string or to a
     *         slashy string ({@code /.../} or {@code $/.../$}); a URL such as {@code 'https://example.org'} therefore starts no comment
     */
    static boolean[] gradleCommentsAndStrings(String content) {
        return gradleNonCode(content, true);
    }

    /**
     * @return for each character of a Gradle build file whether it belongs to a {@code //} or {@code /* *}{@code /} comment; strings are code here
     */
    static boolean[] gradleComments(String content) {
        return gradleNonCode(content, false);
    }

    private static boolean[] gradleNonCode(String content, boolean strings) {
        boolean[] comment = new boolean[content.length()];
        int index = 0;
        // The last token of code before the current one, without whitespace and comments: a word, ++, -- or a single character; a string counts as an operand.
        String previous = ";";
        while (index < content.length()) {
            int end = endOfComment(content, index);
            boolean marked = true;
            if (end < 0) {
                end = endOfStringLiteral(content, index, previous);
                if (end >= 0) {
                    previous = "\"";
                    marked = strings;
                }
            }
            if (end >= 0) {
                Arrays.fill(comment, index, end, marked);
            }
            else {
                end = endOfCodeToken(content, index);
                previous = tokenAfter(previous, content.substring(index, end));
            }
            index = end;
        }
        return comment;
    }

    /**
     * @return the offset after a {@code //} or {@code /* *}{@code /} comment that starts at {@code index}, or -1 if none starts there
     */
    private static int endOfComment(String content, int index) {
        if (content.startsWith("//", index)) {
            return endOfLine(content, index);
        }
        if (content.startsWith("/*", index)) {
            int close = content.indexOf("*/", index + 2);
            return close < 0 ? content.length() : close + 2;
        }
        return -1;
    }

    /**
     * @param previous the last token of code before {@code index}, which decides whether a {@code /} opens a slashy string
     * @return the offset after a quoted or slashy string that starts at {@code index}, or -1 if none starts there
     */
    private static int endOfStringLiteral(String content, int index, String previous) {
        char character = content.charAt(index);
        if (character == '\'' || character == '"') {
            String quote = String.valueOf(character);
            String delimiter = content.startsWith(quote.repeat(3), index) ? quote.repeat(3) : quote;
            return endOfString(content, index + delimiter.length(), delimiter);
        }
        if ((character == '/' || content.startsWith("$/", index)) && slashyStringMayStartAfter(previous)) {
            return endOfSlashyString(content, index);
        }
        return -1;
    }

    /**
     * @return the offset after the word that starts at {@code index}, or after its single character if no word starts there
     */
    private static int endOfCodeToken(String content, int index) {
        int end = index + 1;
        if (Character.isJavaIdentifierStart(content.charAt(index))) {
            while (end < content.length() && Character.isJavaIdentifierPart(content.charAt(end))) {
                end++;
            }
        }
        return end;
    }

    /**
     * @param previous the last token of code so far
     * @param token    the next token of code: a word or a single character
     * @return the last token of code after it; whitespace keeps the previous one, and a second {@code +} or {@code -} joins the first
     */
    private static String tokenAfter(String previous, String token) {
        char first = token.charAt(0);
        if (Character.isJavaIdentifierStart(first)) {
            return token;
        }
        if ((first == '+' || first == '-') && previous.equals(token)) {
            return token.repeat(2);
        }
        return Character.isWhitespace(first) ? previous : token;
    }

    /**
     * A {@code /} after an operand divides, as in {@code a / b}, {@code (a) / b} or {@code a++ / b}; elsewhere, as after {@code =}, {@code (}, {@code ,} or
     * a keyword such as {@code return}, it opens a slashy string.
     */
    private static boolean slashyStringMayStartAfter(String previous) {
        if (Character.isJavaIdentifierStart(previous.charAt(0))) {
            return GROOVY_KEYWORDS_BEFORE_EXPRESSIONS.contains(previous);
        }
        char last = previous.charAt(previous.length() - 1);
        return previous.length() == 1 && !Character.isJavaIdentifierPart(last) && last != ')' && last != ']' && last != '}' && last != '"';
    }

    /**
     * @param from the offset of the opening {@code /} or {@code $/}
     * @return the offset after the closing delimiter, or {@code -1} if the string is not closed, in which case the {@code /} is read as code
     */
    private static int endOfSlashyString(String content, int from) {
        boolean dollar = content.charAt(from) == '$';
        int index = from + (dollar ? 2 : 1);
        while (index < content.length()) {
            if (dollar && (content.startsWith("$$", index) || content.startsWith("$/", index))) {
                index += 2;
            }
            else if (dollar && content.startsWith("/$", index)) {
                return index + 2;
            }
            else if (!dollar && content.startsWith("\\/", index)) {
                index += 2;
            }
            else if (!dollar && content.charAt(index) == '/') {
                return index + 1;
            }
            else {
                index++;
            }
        }
        return -1;
    }

    /**
     * @param from      the offset after the opening delimiter
     * @param delimiter the quote that closes the string
     * @return the offset after the closing delimiter; a string with a single quote also ends at the end of its line
     */
    private static int endOfString(String content, int from, String delimiter) {
        int index = from;
        while (index < content.length()) {
            if (content.charAt(index) == '\\') {
                index += 2;
            }
            else if (content.startsWith(delimiter, index)) {
                return index + delimiter.length();
            }
            else if (delimiter.length() == 1 && content.charAt(index) == '\n') {
                return index + 1;
            }
            else {
                index++;
            }
        }
        return content.length();
    }

    private static int endOfLine(String content, int from) {
        int lineEnd = content.indexOf('\n', from);
        return lineEnd < 0 ? content.length() : lineEnd;
    }

    /**
     * @return for each character of a Maven POM whether it belongs to an XML comment
     */
    static boolean[] mavenComments(String content) {
        boolean[] comment = new boolean[content.length()];
        int start = content.indexOf("<!--");
        while (start >= 0) {
            int close = content.indexOf("-->", start + 4);
            int end = close < 0 ? content.length() : close + 3;
            Arrays.fill(comment, start, end, true);
            start = content.indexOf("<!--", end);
        }
        return comment;
    }

    /**
     * @param comments for each character of the POM whether it belongs to a comment
     * @return for each character of a Maven POM whether it lies in its {@code <profiles>} section, whose properties apply only while their profile is active
     */
    static boolean[] mavenProfiles(String content, boolean[] comments) {
        // Both tags are searched outside comments, e.g. not in <!-- Example: <profiles> -->; the masked text keeps the offsets of the content.
        String code = withoutMarked(content, comments);
        boolean[] profiles = new boolean[content.length()];
        Matcher start = MAVEN_PROFILES_START.matcher(code);
        Matcher end = MAVEN_PROFILES_END.matcher(code);
        int from = 0;
        while (start.find(from)) {
            from = end.find(start.end()) ? end.end() : content.length();
            Arrays.fill(profiles, start.start(), from, true);
        }
        return profiles;
    }

    /**
     * The places that pin the Ares version in a build file. A pin inside a comment, e.g. {@code // aresVersion = '2.3.0'}, or inside a Gradle string, e.g.
     * {@code "aresVersion = '2.3.0'"}, has no effect on the build, so it is neither read nor replaced. Neither is a pin in a Maven profile, which applies
     * only while its profile is active, so the pin of the POM itself is the one the build uses, nor a local variable of a Gradle method or closure, e.g.
     * {@code def aresVersion = '2.3.0'} in a helper method. If another assignment that counts has no readable value, e.g. {@code aresVersion = "${base}.1"}
     * or {@code <ares.version>${next}</ares.version>}, or if the pins name different versions, the version the build uses is unknown, so there are no pins.
     *
     * @param content   the content of the build file
     * @param buildTool the build tool the file belongs to
     * @return the pins outside comments and strings, in the order of the file
     */
    static List<MatchResult> aresVersionPins(String content, SecurityPolicyRepositoryFiles.BuildTool buildTool) {
        boolean gradle = buildTool == SecurityPolicyRepositoryFiles.BuildTool.GRADLE;
        // The literal is matched only where the build reads an assignment, so a pin-shaped line in a comment is not followed to the end of the comment.
        Matcher literal = (gradle ? GRADLE_ARES_VERSION : MAVEN_ARES_VERSION).matcher(content).useTransparentBounds(true);
        List<MatchResult> pins = new ArrayList<>();
        for (int start : assignments(content, gradle, readByTheBuild(content, gradle)).toArray()) {
            if (!literal.region(start, content.length()).lookingAt()) {
                return List.of();
            }
            pins.add(literal.toMatchResult());
        }
        // Pins of different versions leave open which one the dependency reads, e.g. after a later assignment of project.ext.aresVersion.
        boolean oneVersion = pins.stream().map(pin -> pin.group(2).trim()).distinct().count() <= 1;
        return oneVersion ? List.copyOf(pins) : List.of();
    }

    /**
     * @return whether a Gradle build file assigns the Ares version where the build reads it, whether Artemis can read the value or not, e.g. also
     *         {@code aresVersion = "${base}.1"} or two pins of different versions
     */
    static boolean assignsGradleAresVersion(String gradleBuild) {
        return assignments(gradleBuild, true, readByTheBuild(gradleBuild, true)).findAny().isPresent();
    }

    /**
     * @return the offsets of the assignments of the Ares version that the build reads, whatever their value
     */
    private static IntStream assignments(String content, boolean gradle, IntPredicate counts) {
        return (gradle ? GRADLE_ARES_VERSION_ASSIGNMENT : MAVEN_ARES_VERSION_DEFINITION).matcher(content).results().mapToInt(MatchResult::start).filter(counts);
    }

    /**
     * @return whether the build reads an assignment of the Ares version at an offset: not in a comment or a Gradle string, not in a Maven profile and not
     *         as a local variable of a Gradle method or closure
     */
    private static IntPredicate readByTheBuild(String content, boolean gradle) {
        boolean[] noCode = gradle ? gradleCommentsAndStrings(content) : mavenComments(content);
        boolean[] profiles = gradle ? new boolean[content.length()] : mavenProfiles(content, noCode);
        return start -> !noCode[start] && !profiles[start] && !(gradle && declaresLocalGradleVariable(content, noCode, start));
    }

    /**
     * @return whether a profile of the POM pins the Ares version; while such a profile is active, e.g. through {@code activeByDefault}, Maven uses its pin
     *         instead of the one of the POM itself
     */
    static boolean pinsInMavenProfile(String pom) {
        boolean[] comments = mavenComments(pom);
        boolean[] profiles = mavenProfiles(pom, comments);
        // Whatever its value, also a reference to another property, the definition of the profile replaces the one of the POM itself.
        return MAVEN_ARES_VERSION_DEFINITION.matcher(pom).results().anyMatch(definition -> !comments[definition.start()] && profiles[definition.start()]);
    }

    /**
     * @param noCode for each character of the build file whether it belongs to a comment or a string
     * @return whether the Gradle code at the offset declares a local variable of a method or closure, e.g. {@code def aresVersion = '2.3.0'} in a helper
     *         method, which sets no project property; a declaration at the top level of the script declares a script variable the build may use
     */
    private static boolean declaresLocalGradleVariable(String content, boolean[] noCode, int offset) {
        int depth = 0;
        for (int index = 0; index < offset; index++) {
            if (!noCode[index] && content.charAt(index) == '{') {
                depth++;
            }
            else if (!noCode[index] && content.charAt(index) == '}') {
                depth--;
            }
        }
        return depth > 0 && GRADLE_DECLARATION_BEFORE.matcher(withoutMarked(content.substring(0, offset), noCode)).find();
    }
}
