package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.aiworker.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.verification.GeneratedTestPlan;
import de.tum.cit.aet.artemis.hyperion.runtime.verification.JavaSourceText;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.SemanticMutant;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;

/**
 * Deterministic, model-free probe for graded tests that observe a student-owned {@code main} only through its printed transcript. It runs the reference {@code main} once
 * (no arguments, empty input, bounded time and output), synthesizes a variant whose {@code main} only prints that transcript, and executes the graded suite against it like a
 * semantic mutant. A surviving variant proves a student can pass without using the program's objects.
 */
final class OutputOnlyMainProbe {

    /** The sandbox command compiles the solution and runs one bounded {@code main}; the command's own timeouts keep it well inside this deadline. */
    static final Duration CAPTURE_TIMEOUT = Duration.ofMinutes(3);

    /**
     * The sandbox returns at most 50,000 characters of a command's output and keeps the tail. The base64 of this many bytes, line breaks included, stays below that, so a
     * captured transcript is never truncated into garbage.
     */
    static final int MAX_TRANSCRIPT_BYTES = 32 * 1024;

    private static final int MAX_TARGETS = 2;

    private static final int MAIN_RUN_SECONDS = 20;

    private static final int LITERAL_CHUNK_CHARS = 512;

    private static final Pattern MAIN_DECLARATION = Pattern.compile(
            "(?<![\\w$.])((?:(?:public|static|final|synchronized|strictfp)\\s+)+)void\\s+main\\s*\\(\\s*(?:final\\s+)?String\\s*(?:\\[\\s*]\\s*[\\w$]+|\\.\\.\\.\\s*[\\w$]+|[\\w$]+\\s*\\[\\s*])\\s*\\)");

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)\\s*;", Pattern.MULTILINE);

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    private static final Pattern READS_STANDARD_INPUT = Pattern.compile("\\bSystem\\s*\\.\\s*in\\b");

    /** The student source tree the exercise build compiles as the assignment; student-written tests and build files live elsewhere. */
    private static final String STUDENT_SOURCE_ROOT = "src/";

    /** Ways a test can capture what {@code main} prints so it can compare it. */
    private static final Pattern OUTPUT_CAPTURE = Pattern.compile("\\bSystem\\s*\\.\\s*setOut\\s*\\(|\\bIOTester\\b|\\btapSystemOut\\w*\\s*\\(|\\bByteArrayOutputStream\\b");

    /** An assertion that compares a value; {@code assertDoesNotThrow}, {@code assertThrows} and {@code assertTimeout} only check that the call completes or fails. */
    private static final Pattern COMPARING_ASSERTION = Pattern.compile("(?<![\\w$])(?:assert(?!DoesNotThrow\\b|Throws\\b|ThrowsExactly\\b|Timeout\\w*\\b)\\w*|fail)\\s*\\(");

    /** A method or constructor declaration header up to its body's opening brace, on masked source. */
    private static final Pattern METHOD_HEADER = Pattern.compile("(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*\\([^()]*\\)\\s*(?:throws\\s+[\\w$.,\\s]+)?\\{");

    private static final Set<String> NON_METHOD_KEYWORDS = Set.of("if", "for", "while", "switch", "catch", "synchronized", "try", "do", "else", "return", "new");

    private static final Pattern BASE64_TEXT = Pattern.compile("[A-Za-z0-9+/=\\s]*");

    /**
     * One student-owned class whose {@code main} the graded tests call.
     *
     * @param solutionPath repository-relative path of the class in the solution
     * @param simpleName   the class name, which is also the file name
     * @param binaryName   the fully qualified name {@code java} launches
     */
    record Target(String solutionPath, String simpleName, String binaryName) {
    }

    private OutputOnlyMainProbe() {
    }

    /**
     * Runs the probe for every eligible target.
     *
     * @param testFiles             the graded tests repository
     * @param templateFiles         the student starter, which decides what is student-owned
     * @param solutionFiles         the reference solution
     * @param testPlanJson          the grading plan, which decides which tests carry weight; without a usable plan every test counts as graded
     * @param excludedSolutionPaths classes whose probe is already tracked as a pending mutant and is rechecked there
     * @param captureTranscript     runs the reference {@code main} of a target and returns its exact output, or empty when that run is not deterministic and bounded
     * @param runner                executes the graded suite against one variant
     * @return one executed outcome per synthesized variant
     */
    static List<SemanticMutantOutcome> evaluate(Map<String, String> testFiles, Map<String, String> templateFiles, Map<String, String> solutionFiles, @Nullable String testPlanJson,
            Set<String> excludedSolutionPaths, Function<Target, Optional<String>> captureTranscript, SemanticMutantExecution.ProbeRunner runner) {
        List<SemanticMutant> variants = new ArrayList<>();
        for (Target target : targets(testFiles, templateFiles, solutionFiles, gradedTestMethods(testPlanJson))) {
            if (excludedSolutionPaths.contains(target.solutionPath())) {
                continue;
            }
            String source = solutionFiles.get(target.solutionPath());
            captureTranscript.apply(target).flatMap(transcript -> transcriptOnlyVariant(source, transcript))
                    .ifPresent(variant -> variants.add(SemanticMutant.printedTranscript(target.solutionPath(), source, variant, target.simpleName())));
        }
        return variants.isEmpty() ? List.of() : SemanticMutantExecution.recheck(solutionFiles, variants, runner);
    }

    /**
     * Student-owned classes (absent from the template or changed in the solution) in the student source tree that declare exactly one {@code public static void main(String[])}
     * whose printed output a graded test compares. A test that only calls {@code main}, for example to check that it completes, cannot be passed by printing alone and is not a
     * reason to probe. A solution that reads standard input anywhere is skipped, because an empty input would not reproduce the transcript the tests expect.
     *
     * @param gradedTestMethods method names of the tests that carry weight, or {@code null} to treat every test as graded
     */
    static List<Target> targets(Map<String, String> testFiles, Map<String, String> templateFiles, Map<String, String> solutionFiles, @Nullable Set<String> gradedTestMethods) {
        if (solutionFiles.values().stream().anyMatch(source -> source != null && READS_STANDARD_INPUT.matcher(JavaSourceText.maskCommentsAndLiterals(source)).find())) {
            return List.of();
        }
        List<Target> targets = new ArrayList<>();
        for (Map.Entry<String, String> entry : solutionFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String path = entry.getKey();
            String source = entry.getValue();
            if (!path.startsWith(STUDENT_SOURCE_ROOT) || !JavaSourceInspector.isJavaSource(path) || source == null || source.equals(templateFiles.get(path))) {
                continue;
            }
            String masked = JavaSourceText.maskCommentsAndLiterals(source);
            String simpleName = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            if (!IDENTIFIER.matcher(simpleName).matches() || mainDeclarations(masked).size() != 1) {
                continue;
            }
            if (!gradesPrintedOutput(testFiles, simpleName, gradedTestMethods)) {
                continue;
            }
            Matcher packageMatcher = PACKAGE.matcher(masked);
            String binaryName = packageMatcher.find() ? packageMatcher.group(1).replaceAll("\\s+", "") + "." + simpleName : simpleName;
            targets.add(new Target(path, simpleName, binaryName));
            if (targets.size() == MAX_TARGETS) {
                break;
            }
        }
        return List.copyOf(targets);
    }

    /**
     * Whether a probe still applies to the current tests: a graded test still compares what the probed {@code main} prints. A repair that grades the behavior through objects
     * and keeps a pure transcript check ungraded, or removes it, resolves the probe without another build.
     *
     * @param probe        a printed-transcript probe
     * @param testFiles    the current tests repository
     * @param testPlanJson the current grading plan
     * @return whether the probe must still be executed against the graded suite
     */
    static boolean stillApplies(SemanticMutant probe, Map<String, String> testFiles, @Nullable String testPlanJson) {
        String simpleName = probe.ruleId().substring(0, probe.ruleId().length() - ".main".length());
        return gradesPrintedOutput(testFiles, simpleName, gradedTestMethods(testPlanJson));
    }

    /** Method names of the tests the plan weights above zero, or {@code null} when there is no usable plan and every test must count as graded. */
    @Nullable
    static Set<String> gradedTestMethods(@Nullable String testPlanJson) {
        if (testPlanJson == null || testPlanJson.isBlank()) {
            return null;
        }
        try {
            return GeneratedTestPlan.parse(testPlanJson).assessmentEntries().stream().filter(entry -> entry.seamWeightTier() > 0).map(entry -> methodName(entry.name()))
                    .collect(Collectors.toUnmodifiableSet());
        }
        catch (IllegalArgumentException invalidPlan) {
            return null;
        }
    }

    /** {@code Suite.method(args)[1]} and {@code method} both name the method {@code method}. */
    private static String methodName(String testName) {
        String withoutArguments = testName.strip().replaceFirst("[(\\[].*$", "");
        return withoutArguments.substring(withoutArguments.lastIndexOf('.') + 1);
    }

    /**
     * Whether a graded test compares what {@code simpleName.main} prints: its test class captures standard output, and the test calls {@code main} directly or through a helper
     * of the same class and makes a comparing assertion.
     */
    static boolean gradesPrintedOutput(Map<String, String> testFiles, String simpleName, @Nullable Set<String> gradedTestMethods) {
        Pattern mainCall = Pattern.compile("(?<![\\w$])" + Pattern.quote(simpleName) + "\\s*\\.\\s*main\\s*\\(");
        for (Map.Entry<String, String> file : testFiles.entrySet()) {
            if (!JavaSourceInspector.isJavaSource(file.getKey()) || file.getValue() == null) {
                continue;
            }
            String masked = JavaSourceText.maskCommentsAndLiterals(file.getValue());
            if (!OUTPUT_CAPTURE.matcher(masked).find() || !mainCall.matcher(masked).find()) {
                continue;
            }
            Map<String, String> bodies = methodBodies(masked);
            Set<String> callers = bodies.entrySet().stream().filter(method -> mainCall.matcher(method.getValue()).find()).map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(java.util.HashSet::new));
            Set<String> directCallers = Set.copyOf(callers);
            bodies.forEach((name, body) -> {
                if (directCallers.stream().anyMatch(helper -> Pattern.compile("(?<![\\w$.])" + Pattern.quote(helper) + "\\s*\\(").matcher(body).find())) {
                    callers.add(name);
                }
            });
            boolean compared = callers.stream().filter(name -> gradedTestMethods == null || gradedTestMethods.contains(name))
                    .anyMatch(name -> COMPARING_ASSERTION.matcher(bodies.get(name)).find());
            if (compared) {
                return true;
            }
        }
        return false;
    }

    /** Method bodies by name on masked source; overloads share a name and their bodies are joined. */
    private static Map<String, String> methodBodies(String masked) {
        Map<String, String> bodies = new java.util.LinkedHashMap<>();
        Matcher header = METHOD_HEADER.matcher(masked);
        while (header.find()) {
            String name = header.group(1);
            String before = masked.substring(0, header.start()).stripTrailing();
            if (NON_METHOD_KEYWORDS.contains(name) || before.endsWith("new") || before.endsWith(".")) {
                continue;
            }
            int open = header.end() - 1;
            int close = matchingBrace(masked, open);
            if (close < 0) {
                break;
            }
            bodies.merge(name, masked.substring(open, close + 1), (first, second) -> first + "\n" + second);
        }
        return bodies;
    }

    /**
     * A POSIX shell command that compiles the solution's student source tree into a private directory and runs one {@code main} there without arguments, with empty standard
     * input and bounded time, printing its standard output base64-encoded. The tree is compiled without the exercise's dependencies, which only a build could resolve, so a
     * tree that needs them fails to compile and the probe is skipped. Exit codes: 90 no student sources, 91 they do not compile alone (compiler output on stderr), 92
     * {@code main} failed or timed out, 93 it printed more than {@link #MAX_TRANSCRIPT_BYTES}.
     */
    static String captureCommand(Target target) {
        if (!target.binaryName().chars().allMatch(character -> Character.isJavaIdentifierPart(character) || character == '.')) {
            throw new IllegalArgumentException("Not a Java binary name: " + target.binaryName());
        }
        String solution = GenerationWorkspace.WORKSPACE + "/" + GenerationWorkspace.directoryFor(RepositoryRole.SOLUTION);
        return """
                OUT=$(mktemp -d /tmp/hyperion-main.XXXXXX) || exit 90
                trap 'rm -rf "$OUT"' EXIT
                cd '@@SOLUTION@@' || exit 90
                [ -d '@@SOURCE_ROOT@@' ] || exit 90
                find '@@SOURCE_ROOT@@' -type f -name '*.java' > "$OUT/sources" || exit 90
                [ -s "$OUT/sources" ] || exit 90
                javac -nowarn -proc:none -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources" > "$OUT/javac.log" 2>&1 || { head -c 2000 "$OUT/javac.log" >&2; exit 91; }
                cd "$OUT" || exit 90
                { timeout @@SECONDS@@ java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" '@@MAIN@@' < /dev/null 2> /dev/null; echo $? > "$OUT/status"; } \\
                    | head -c @@LIMIT_PLUS_ONE@@ > "$OUT/stdout"
                [ "$(cat "$OUT/status" 2> /dev/null)" = 0 ] || exit 92
                [ "$(wc -c < "$OUT/stdout")" -le @@LIMIT@@ ] || exit 93
                base64 < "$OUT/stdout"
                """.replace("@@SOLUTION@@", solution).replace("@@SOURCE_ROOT@@", STUDENT_SOURCE_ROOT.substring(0, STUDENT_SOURCE_ROOT.length() - 1))
                .replace("@@SECONDS@@", Integer.toString(MAIN_RUN_SECONDS)).replace("@@MAIN@@", target.binaryName())
                .replace("@@LIMIT_PLUS_ONE@@", Integer.toString(MAX_TRANSCRIPT_BYTES + 1)).replace("@@LIMIT@@", Integer.toString(MAX_TRANSCRIPT_BYTES));
    }

    /** Why a capture yielded no transcript, for the operator log. */
    static String skipReason(SandboxExecResultDTO result) {
        return switch (result.exitCode()) {
            case 0 -> "its output was truncated or not valid UTF-8 text";
            case 90 -> "the solution has no Java sources under " + STUDENT_SOURCE_ROOT;
            case 91 -> "the student source tree does not compile on its own, for example because it needs the exercise's dependencies, which the probe does not resolve: "
                    + Objects.requireNonNullElse(result.stderr(), "").strip();
            case 92 -> "main failed, read input, or did not finish within " + MAIN_RUN_SECONDS + " seconds";
            case 93 -> "main printed more than " + MAX_TRANSCRIPT_BYTES + " bytes";
            default -> "the capture command exited with " + result.exitCode();
        };
    }

    /** The exact transcript of a successful capture, or empty when the run failed or its output is truncated, not base64, or not bounded UTF-8 text. */
    static Optional<String> transcript(SandboxExecResultDTO result) {
        // The sandbox marks truncated output with a bracketed prefix, which is not base64; the MIME decoder would silently skip it and decode the remainder into garbage.
        if (!result.isSuccess() || result.stdout() == null || !BASE64_TEXT.matcher(result.stdout()).matches()) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(result.stdout().strip());
            if (bytes.length > MAX_TRANSCRIPT_BYTES) {
                return Optional.empty();
            }
            return Optional.of(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        }
        catch (IllegalArgumentException | CharacterCodingException exception) {
            return Optional.empty();
        }
    }

    /** The class source with only the body of its single {@code main} replaced by statements printing {@code transcript}; empty when that body cannot be located safely. */
    static Optional<String> transcriptOnlyVariant(String source, String transcript) {
        String masked = JavaSourceText.maskCommentsAndLiterals(source);
        List<MatchResult> declarations = mainDeclarations(masked);
        if (declarations.size() != 1) {
            return Optional.empty();
        }
        int open = masked.indexOf('{', declarations.getFirst().end());
        int terminator = masked.indexOf(';', declarations.getFirst().end());
        if (open < 0 || (terminator >= 0 && terminator < open)) {
            return Optional.empty();
        }
        int close = matchingBrace(masked, open);
        if (close < 0) {
            return Optional.empty();
        }
        StringBuilder body = new StringBuilder("{");
        for (int start = 0; start < transcript.length();) {
            int end = Math.min(transcript.length(), start + LITERAL_CHUNK_CHARS);
            if (end < transcript.length() && Character.isHighSurrogate(transcript.charAt(end - 1))) {
                end--;
            }
            body.append(" System.out.print(\"").append(javaStringContent(transcript.substring(start, end))).append("\");");
            start = end;
        }
        body.append(" System.out.flush(); }");
        return Optional.of(source.substring(0, open) + body + source.substring(close + 1));
    }

    private static List<MatchResult> mainDeclarations(String masked) {
        return MAIN_DECLARATION.matcher(masked).results().filter(declaration -> {
            List<String> modifiers = List.of(declaration.group(1).strip().split("\\s+"));
            return modifiers.contains("public") && modifiers.contains("static");
        }).toList();
    }

    private static int matchingBrace(String masked, int open) {
        int depth = 0;
        for (int index = open; index < masked.length(); index++) {
            char current = masked.charAt(index);
            if (current == '{') {
                depth++;
            }
            else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Escapes text for a Java string literal. Control characters use three-digit octal escapes rather than Unicode escapes, which the compiler translates before lexing and
     * would turn into a literal line break.
     */
    private static String javaStringContent(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20 || current == 0x7f) {
                        escaped.append(String.format("\\%03o", (int) current));
                    }
                    else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
