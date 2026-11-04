package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.aiworker.dto.SandboxExecResultDTO;
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

    static final int MAX_TRANSCRIPT_BYTES = 64 * 1024;

    private static final int MAX_TARGETS = 2;

    private static final int MAIN_RUN_SECONDS = 20;

    private static final int LITERAL_CHUNK_CHARS = 512;

    private static final Pattern MAIN_DECLARATION = Pattern.compile(
            "(?<![\\w$.])((?:(?:public|static|final|synchronized|strictfp)\\s+)+)void\\s+main\\s*\\(\\s*(?:final\\s+)?String\\s*(?:\\[\\s*]\\s*[\\w$]+|\\.\\.\\.\\s*[\\w$]+|[\\w$]+\\s*\\[\\s*])\\s*\\)");

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)\\s*;", Pattern.MULTILINE);

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    private static final Pattern READS_STANDARD_INPUT = Pattern.compile("\\bSystem\\s*\\.\\s*in\\b");

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
     * @param excludedSolutionPaths classes whose probe is already tracked as a pending mutant and is rechecked there
     * @param captureTranscript     runs the reference {@code main} of a target and returns its exact output, or empty when that run is not deterministic and bounded
     * @param runner                executes the graded suite against one variant
     * @return one executed outcome per synthesized variant
     */
    static List<SemanticMutantOutcome> evaluate(Map<String, String> testFiles, Map<String, String> templateFiles, Map<String, String> solutionFiles,
            Set<String> excludedSolutionPaths, Function<Target, Optional<String>> captureTranscript, SemanticMutantExecution.ProbeRunner runner) {
        List<SemanticMutant> variants = new ArrayList<>();
        for (Target target : targets(testFiles, templateFiles, solutionFiles)) {
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
     * Student-owned classes (absent from the template or changed in the solution) that declare exactly one {@code public static void main(String[])} which a graded test calls
     * directly. A solution that reads standard input anywhere is skipped, because an empty input would not reproduce the transcript the tests expect.
     */
    static List<Target> targets(Map<String, String> testFiles, Map<String, String> templateFiles, Map<String, String> solutionFiles) {
        if (solutionFiles.values().stream().anyMatch(source -> source != null && READS_STANDARD_INPUT.matcher(JavaSourceText.maskCommentsAndLiterals(source)).find())) {
            return List.of();
        }
        List<String> testSources = testFiles.entrySet().stream().filter(entry -> JavaSourceInspector.isJavaSource(entry.getKey()) && entry.getValue() != null)
                .map(entry -> JavaSourceText.maskCommentsAndLiterals(entry.getValue())).toList();
        List<Target> targets = new ArrayList<>();
        for (Map.Entry<String, String> entry : solutionFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String path = entry.getKey();
            String source = entry.getValue();
            if (!JavaSourceInspector.isJavaSource(path) || source == null || source.equals(templateFiles.get(path))) {
                continue;
            }
            String masked = JavaSourceText.maskCommentsAndLiterals(source);
            String simpleName = path.substring(path.lastIndexOf('/') + 1, path.length() - ".java".length());
            if (!IDENTIFIER.matcher(simpleName).matches() || mainDeclarations(masked).size() != 1) {
                continue;
            }
            Pattern call = Pattern.compile("(?<![\\w$])" + Pattern.quote(simpleName) + "\\s*\\.\\s*main\\s*\\(");
            if (testSources.stream().noneMatch(test -> call.matcher(test).find())) {
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
     * A POSIX shell command that compiles the solution into a private directory and runs one {@code main} there without arguments, with empty standard input and bounded time,
     * printing its standard output base64-encoded. It exits non-zero when compiling or running fails, times out, or prints more than {@link #MAX_TRANSCRIPT_BYTES}.
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
                find . -type f -name '*.java' ! -path './build/*' ! -path './target/*' ! -path './.*' > "$OUT/sources" || exit 90
                [ -s "$OUT/sources" ] || exit 90
                javac -nowarn -proc:none -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources" > /dev/null 2>&1 || exit 91
                cd "$OUT" || exit 90
                { timeout @@SECONDS@@ java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT/classes" '@@MAIN@@' < /dev/null 2> /dev/null; echo $? > "$OUT/status"; } \\
                    | head -c @@LIMIT_PLUS_ONE@@ > "$OUT/stdout"
                [ "$(cat "$OUT/status" 2> /dev/null)" = 0 ] || exit 92
                [ "$(wc -c < "$OUT/stdout")" -le @@LIMIT@@ ] || exit 93
                base64 < "$OUT/stdout"
                """.replace("@@SOLUTION@@", solution).replace("@@SECONDS@@", Integer.toString(MAIN_RUN_SECONDS)).replace("@@MAIN@@", target.binaryName())
                .replace("@@LIMIT_PLUS_ONE@@", Integer.toString(MAX_TRANSCRIPT_BYTES + 1)).replace("@@LIMIT@@", Integer.toString(MAX_TRANSCRIPT_BYTES));
    }

    /** The exact transcript of a successful capture, or empty when the run failed or its output is not bounded UTF-8 text. */
    static Optional<String> transcript(SandboxExecResultDTO result) {
        if (!result.isSuccess() || result.stdout() == null) {
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
