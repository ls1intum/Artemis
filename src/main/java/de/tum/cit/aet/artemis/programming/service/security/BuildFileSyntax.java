package de.tum.cit.aet.artemis.programming.service.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the syntax of the build files of a tests repository as far as the Security Framework needs it: which parts are comments, strings or Maven
 * profiles, so that a pinned Ares version, the Ares agent or a library in a comment is not mistaken for code, see {@link SecurityPolicyRepositoryFiles}.
 */
final class BuildFileSyntax {

    /**
     * The Groovy keywords after which an expression, and therefore a slashy string, may follow; any other word is an operand, such as a variable,
     * {@code this} or {@code null}.
     */
    private static final Set<String> GROOVY_KEYWORDS_BEFORE_EXPRESSIONS = Set.of("return", "throw", "assert", "case", "in", "yield", "else", "do");

    /** The Ares agent as a Gradle dependency of a configuration, e.g. {@code aresAgent "de.tum.cit.ase:ares:${aresVersion}:agent"}; group 1 is the configuration. */
    private static final Pattern GRADLE_ARES_AGENT = Pattern.compile("([A-Za-z_$][\\w$]*+)\\s*+\\(?\\s*+['\"]de\\.tum\\.cit\\.ase:ares:[^'\"\\s]+:agent['\"]");

    /** The variable, property or object an interpolation in a Gradle string starts with, e.g. {@code agentJar} in {@code ${agentJar.singleFile}}. */
    private static final Pattern INTERPOLATED_NAME = Pattern.compile("\\$\\{?\\s*+([A-Za-z_]\\w*+)");

    /** How a statement gives a variable or property its value right after its name: an assignment or a call such as {@code .from(...)}. */
    private static final Pattern TAKES_VALUE = Pattern.compile("\\s*+(?:\\+?=(?!=)|\\.\\s*+(?:from|setFrom|set|convention)\\s*+\\()");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*+");

    /** A reference to a Gradle configuration, e.g. {@code configurations.aresAgent}. */
    private static final Pattern CONFIGURATION_REFERENCE = Pattern.compile("configurations\\s*+\\.\\s*+([A-Za-z_$][\\w$]*+)");

    /**
     * A dependency declaration at the start of a line, after a semicolon or after the brace that opens a block, e.g. {@code aresAgent "de.tum.cit.ase:ares:1.0:agent"}
     * or {@code dependencies { agent("de.tum.cit.ase:ares:1.0:agent") }}, with the name of its configuration; the arguments in parentheses may start on the next
     * line.
     */
    private static final Pattern DEPENDENCY_DECLARATION = Pattern.compile("(?m)(?:^|[;{])[ \\t]*+([A-Za-z_$][\\w$]*+)[ \\t]*+(?:\\(\\s*+)?(?:['\"]|\\w++[ \\t]*+[(:])");

    /** An assignment of an empty file collection. */
    private static final Pattern EMPTY_COLLECTION = Pattern.compile("\\s*+=\\s*+(?:files|objects\\s*+\\.\\s*+fileCollection)\\s*+\\(\\s*+\\)\\s*+");

    /** An interpolation in a Gradle or Maven string, e.g. {@code ${agentJar.singleFile}} or {@code $agentJar}, also one that is not closed. */
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{[^}'\"\\n]*+}?|\\$[A-Za-z_]\\w*+");

    /** The last characters of a line after which a Gradle statement continues on the next line. */
    private static final String CONTINUING_ENDS = "=(,.+:";

    /** The argument of a {@code -javaagent} option; group 1 is the agent, up to the end of the string or argument. */
    private static final Pattern JAVA_AGENT = Pattern.compile("-javaagent:((?:\\$\\{[^}'\"\\n]*+}?|[^'\"\\s,)<$]|\\$(?!\\{))*+)");

    /** The Ares agent as a Maven artifact: the {@code agent} classifier of the {@code ares} artifact. */
    private static final Pattern MAVEN_ARES_AGENT = Pattern
            .compile("<artifactId>\\s*ares\\s*</artifactId>\\s*(<version>[^<]*</version>\\s*)?(<type>[^<]*</type>\\s*)?<classifier>\\s*agent\\s*</classifier>");

    private static final String ARES_1_ARTIFACT = "artemis-java-test-sandbox";

    /** The Ares 1 library in a Gradle dependency, e.g. {@code 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'} or {@code name: 'artemis-java-test-sandbox'}. */
    private static final Pattern GRADLE_ARES_1_DEPENDENCY = Pattern.compile("[:'\"]\\s*+" + ARES_1_ARTIFACT + "\\s*+[:'\"]");

    /** The Ares 1 library as the artifact of a Maven dependency. */
    private static final Pattern MAVEN_ARES_1_DEPENDENCY = Pattern.compile("<artifactId>\\s*+" + ARES_1_ARTIFACT + "\\s*+</artifactId>");

    /** A keyword or type right before a Gradle variable that declares it, e.g. {@code def}, matched at the end of the code before the variable. */
    private static final Pattern GRADLE_DECLARATION_BEFORE = Pattern.compile("(?<!\\p{javaJavaIdentifierPart})(?:def|var|final|String|Object)\\s++$");

    private static final String MAVEN_PROFILES_START = "<profiles>";

    private static final String MAVEN_PROFILES_END = "</profiles>";

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
     * Whether the build attaches the Ares agent with {@code -javaagent}. Only then does Ares enforce a policy whose language configuration uses
     * {@code INSTRUMENTATION}; the build files of the Artemis templates attach it. A {@code -javaagent} option counts if it names the Ares agent, e.g.
     * {@code ares-agent.jar}, or, in Gradle, takes its file from the configuration that declares the Ares agent, like the templates do with
     * {@code configurations.aresAgent}. Another agent, such as JaCoCo, and text in comments do not count.
     *
     * @param buildFiles the build files at the root of a repository
     * @return whether a build file attaches the Ares agent
     */
    static boolean attachesAresAgent(SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        boolean gradle = Stream.ofNullable(buildFiles.gradleBuild()).map(content -> withoutMarked(content, gradleComments(content)))
                .anyMatch(BuildFileSyntax::gradleAttachesAresAgent);
        boolean maven = Stream.ofNullable(buildFiles.mavenPom()).map(content -> withoutMarked(content, mavenComments(content)))
                .anyMatch(code -> MAVEN_ARES_AGENT.matcher(code).find() && javaAgents(code).anyMatch(BuildFileSyntax::namesAres));
        return gradle || maven;
    }

    private static boolean gradleAttachesAresAgent(String code) {
        Matcher dependency = GRADLE_ARES_AGENT.matcher(code);
        if (!dependency.find()) {
            return false;
        }
        String configuration = dependency.group(1);
        // A configuration with more dependencies than the Ares agent leaves open which file an agent takes from it, e.g. JaCoCo, so the agent then has to
        // name Ares itself.
        boolean onlyTheAresAgent = DEPENDENCY_DECLARATION.matcher(code).results().filter(declaration -> declaration.group(1).equals(configuration)).count() == 1;
        return javaAgents(code).anyMatch(agent -> namesAres(agent) || (onlyTheAresAgent && takesFileFrom(code, agent, configuration)));
    }

    /**
     * Whether an interpolated agent takes its file from the configuration: directly, as in {@code "-javaagent:${configurations.aresAgent.singleFile}"}, or
     * through the variable or property it starts with, as the templates do with {@code "-javaagent:${agentJar.singleFile.absolutePath}"} and
     * {@code aresJvmArguments.agentJar.from(configurations.aresAgent)}.
     */
    private static boolean takesFileFrom(String code, String agent, String configuration) {
        Matcher interpolation = INTERPOLATED_NAME.matcher(agent);
        if (!interpolation.find()) {
            return false;
        }
        if (referencesConfiguration(agent, configuration)) {
            return true;
        }
        // Every statement that gives the variable a value has to take it from the configuration, so a later reassignment to another file does not count.
        String name = interpolation.group(1);
        Matcher identifier = IDENTIFIER.matcher(code);
        boolean takesValue = false;
        while (identifier.find()) {
            if (!identifier.group().equals(name)) {
                continue;
            }
            String statement = statementAfter(code, identifier.end());
            // An empty file collection, e.g. "def agentJar = files()", only gets its content from the next statements.
            if (TAKES_VALUE.matcher(statement).lookingAt() && !EMPTY_COLLECTION.matcher(statement).matches()) {
                if (!referencesConfiguration(statement, configuration)) {
                    return false;
                }
                takesValue = true;
            }
        }
        return takesValue;
    }

    private static boolean referencesConfiguration(String text, String configuration) {
        return CONFIGURATION_REFERENCE.matcher(text).results().anyMatch(reference -> reference.group(1).equals(configuration));
    }

    /**
     * @return the rest of the statement after the offset, up to a semicolon: the rest of its line and every following line while the statement continues,
     *         because a line ends with an operator such as {@code =} or the next one starts with a method call, as in {@code def agentJar =} followed by
     *         {@code configurations.aresAgent} on the next line
     */
    private static String statementAfter(String code, int offset) {
        String statement = continuedLinesAfter(code, offset);
        int semicolon = statement.indexOf(';');
        return semicolon < 0 ? statement : statement.substring(0, semicolon);
    }

    private static String continuedLinesAfter(String code, int offset) {
        int end = code.indexOf('\n', offset);
        while (end >= 0) {
            String statement = code.substring(offset, end).stripTrailing();
            int next = end + 1;
            while (next < code.length() && Character.isWhitespace(code.charAt(next))) {
                next++;
            }
            boolean continues = (!statement.isEmpty() && CONTINUING_ENDS.indexOf(statement.charAt(statement.length() - 1)) >= 0)
                    || (next < code.length() && code.charAt(next) == '.');
            if (!continues) {
                return code.substring(offset, end);
            }
            end = code.indexOf('\n', next);
        }
        return code.substring(offset);
    }

    private static Stream<String> javaAgents(String code) {
        return JAVA_AGENT.matcher(code).results().map(match -> match.group(1));
    }

    /**
     * Whether the literal part of an agent path names Ares, e.g. {@code ares-agent.jar}. Interpolations are left out, because a variable is named freely, e.g.
     * {@code ${aresAgentJar}} may hold JaCoCo; an interpolated agent counts only through {@link #takesFileFrom(String, String, String)}.
     */
    private static boolean namesAres(String agent) {
        return INTERPOLATION.matcher(agent).replaceAll("").toLowerCase(Locale.ROOT).contains("ares");
    }

    /** @return the content without the marked characters */
    private static String withoutMarked(String content, boolean[] marked) {
        StringBuilder code = new StringBuilder(content.length());
        for (int index = 0; index < content.length(); index++) {
            if (!marked[index]) {
                code.append(content.charAt(index));
            }
        }
        return code.toString();
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
        int start = code.indexOf(MAVEN_PROFILES_START);
        while (start >= 0) {
            int close = code.indexOf(MAVEN_PROFILES_END, start);
            int end = close < 0 ? content.length() : close + MAVEN_PROFILES_END.length();
            Arrays.fill(profiles, start, end, true);
            start = code.indexOf(MAVEN_PROFILES_START, end);
        }
        return profiles;
    }

    /**
     * @param noCode for each character of the build file whether it belongs to a comment or a string
     * @return whether the Gradle code at the offset declares a local variable of a method or closure, e.g. {@code def aresVersion = '2.3.0'} in a helper
     *         method, which sets no project property; a declaration at the top level of the script declares a script variable the build may use
     */
    static boolean declaresLocalGradleVariable(String content, boolean[] noCode, int offset) {
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
