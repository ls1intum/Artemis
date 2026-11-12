package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ExerciseIntegrityGate.isHarnessFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentSystemPrompt;

/** Rejects graded tests that read student source files, following path values within their Java scopes. */
final class SourceReadingTestGate {

    private SourceReadingTestGate() {
    }

    /** File and resource reads a test could aim at a source file; matched on comment- and literal-masked code. */
    private static final Pattern FILE_READ = Pattern
            .compile("\\bFiles\\s*\\.\\s*(?:readString|readAllLines|readAllBytes|lines|newBufferedReader|newInputStream|newByteChannel)\\s*\\("
                    + "|\\bnew\\s+(?:java\\s*\\.\\s*io\\s*\\.\\s*)?(?:FileReader|FileInputStream|RandomAccessFile)\\s*\\("
                    + "|\\bnew\\s+(?:java\\s*\\.\\s*util\\s*\\.\\s*)?Scanner\\s*\\(\\s*(?:new\\s+(?:java\\s*\\.\\s*io\\s*\\.\\s*)?File\\b|(?:java\\s*\\.\\s*nio\\s*\\.\\s*file\\s*\\.\\s*)?(?:Path\\s*\\.\\s*of|Paths\\s*\\.\\s*get)\\b"
                    // Scanner variable arguments are checked for an actual File/Path value before their literals are traced.
                    + "|[A-Za-z_$][\\w$]*\\s*\\))|\\bgetResource(?:AsStream)?\\s*\\(|\\bFileUtils\\s*\\.\\s*read\\w*\\s*\\(");

    /** A double-quoted literal on one line; the possessive repetition keeps a long literal from overflowing the stack. */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*+)\"");

    /**
     * A string naming the student source tree or a source or class file: a {@code .java} or {@code .class} name, a {@code src} or {@code assignment} path segment, or the CI
     * placeholder.
     */
    private static final Pattern SOURCE_PATH_LITERAL = Pattern.compile("(?i)\\.(?:java|class)$|studentWorkingDirectory|(?:^|[/\\\\])(?:src|assignment)(?:[/\\\\]|$)");

    /** Whitespace inside a literal: a path or path segment of a source tree has none, while an assertion message such as "Implement Parser.java" is prose. */
    private static final Pattern INNER_WHITESPACE = Pattern.compile("\\s");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    /** An assignment or initialization of a named variable or field, on masked code; its value runs to the next semicolon. */
    private static final Pattern ASSIGNMENT = Pattern.compile("(?<![\\w$.])([A-Za-z_$][\\w$]*)\\s*=(?!=)");

    /**
     * Rejects graded tests that read a source or class file of the exercise. The grading sandbox forbids the file access, the build layout does not place the sources where a
     * test could find them, and source shape is not graded behavior. A file counts when it reads a file or resource and a string literal naming a source path appears in the
     * arguments of a path expression or in the value of a variable those arguments use, which catches a path built in one statement and read in another without flagging tests
     * that read their own data files or compare a file name. A literal with whitespace is a message, not a path.
     */
    static List<String> reasons(Map<String, String> producedTestsFiles) {
        if (producedTestsFiles == null || producedTestsFiles.isEmpty()) {
            return List.of();
        }
        List<String> offending = new ArrayList<>();
        for (Map.Entry<String, String> file : producedTestsFiles.entrySet()) {
            if (!JavaSourceInspector.isJavaSource(file.getKey()) || isHarnessFile(file.getKey()) || file.getValue() == null
                    || !FILE_READ.matcher(JavaSourceInspector.maskCommentsAndLiterals(file.getValue())).find()) {
                continue;
            }
            List<PathExpression> pathRanges = pathExpressionRanges(JavaSourceInspector.maskCommentsAndLiterals(file.getValue()));
            List<String> sourcePaths = STRING_LITERAL.matcher(JavaSourceInspector.stripJavaComments(file.getValue())).results()
                    .filter(literal -> pathRanges.stream().anyMatch(range -> range.start() <= literal.start() && literal.end() <= range.end())).map(literal -> literal.group(1))
                    .map(String::strip).filter(literal -> !INNER_WHITESPACE.matcher(literal).find() && SOURCE_PATH_LITERAL.matcher(literal).find()).distinct().toList();
            if (!sourcePaths.isEmpty()) {
                offending.add(file.getKey() + " (reads " + sourcePaths + ")");
            }
        }
        if (offending.isEmpty()) {
            return List.of();
        }
        return List.of("these graded test files read source files: " + offending + ". Tests must not read source files: the grading sandbox forbids it and source shape is "
                + "not graded behavior. Make the graded code take the objects or inputs it works on so tests can vary them. " + AgentSystemPrompt.TESTABLE_STUDENT_CODE_RULE);
    }

    /**
     * The source ranges that can contribute to a path a test reads: the arguments of actual file/resource reads, and the value of every variable or field named in such a range
     * and in scope there, followed until no further variable is named. Local values follow the latest preceding assignment in the closest enclosing scope and any later
     * reassignment in a branch before the read; assignments after the read and overwritten values cannot supply its path. A parameter or header variable shadows a field of
     * the same name. Fields remain conservative because declaration order does not determine their value at a method call.
     *
     * @param masked comment- and literal-masked source, so parentheses inside literals are not counted
     * @return the ranges, each from its start to its exclusive end
     */
    private static List<PathExpression> pathExpressionRanges(String masked) {
        List<PathExpression> ranges = new ArrayList<>();
        Matcher call = FILE_READ.matcher(masked);
        while (call.find()) {
            int open = masked.indexOf('(', call.start());
            PathExpression root = new PathExpression(open, closingParenthesis(masked, open), call.start());
            if (!SCANNER_CONSTRUCTION.matcher(call.group()).find() || scannerReadsFile(masked, root)) {
                ranges.add(root);
            }
        }
        return followAssignments(masked, ranges);
    }

    private static final Pattern SCANNER_CONSTRUCTION = Pattern.compile("^new\\s++(?:java\\s*+\\.\\s*+util\\s*+\\.\\s*+)?Scanner\\b");

    private static final Pattern FILE_VALUE = Pattern.compile("^\\s*+(?:new\\s++(?:java\\s*+\\.\\s*+io\\s*+\\.\\s*+)?File\\s*+\\("
            + "|(?:java\\s*+\\.\\s*+nio\\s*+\\.\\s*+file\\s*+\\.\\s*+)?(?:Path\\s*+\\.\\s*+of|Paths\\s*+\\.\\s*+get)\\s*+\\()");

    private static final Pattern FILE_ADAPTER = Pattern.compile("^\\s*+\\.\\s*+(?:toFile|toPath|normalize|toAbsolutePath|getAbsoluteFile|getCanonicalFile)\\s*+\\(\\s*+\\)");

    /** Scanner(String) scans text. Follow only direct aliases and known File/Path factories, not arbitrary expressions using them. */
    private static boolean scannerReadsFile(String masked, PathExpression root) {
        Map<Integer, String> assignments = ASSIGNMENT.matcher(masked).results().collect(Collectors.toMap(MatchResult::end, match -> match.group(1)));
        List<PathExpression> values = new ArrayList<>(List.of(root));
        Set<PathExpression> seen = new HashSet<>(values);
        for (int index = 0; index < values.size(); index++) {
            PathExpression range = values.get(index);
            String value = masked.substring(range.start(), range.end()).strip();
            if (value.startsWith("(") && value.endsWith(")")) {
                value = value.substring(1, value.length() - 1).strip();
            }
            if (isFileValue(value)) {
                return true;
            }
            if (IDENTIFIER.matcher(value).matches()) {
                String name = value;
                followAssignments(masked, new ArrayList<>(List.of(range))).stream().skip(1).filter(assignment -> name.equals(assignments.get(assignment.start()))).filter(seen::add)
                        .forEach(values::add);
            }
        }
        return false;
    }

    private static boolean isFileValue(String value) {
        if (!FILE_VALUE.matcher(value).find()) {
            return false;
        }
        String tail = value.substring(closingParenthesis(value, value.indexOf('(')));
        Matcher adapter = FILE_ADAPTER.matcher(tail);
        int offset = 0;
        while (adapter.region(offset, tail.length()).lookingAt()) {
            offset = adapter.end();
        }
        return tail.substring(offset).isBlank();
    }

    private static List<PathExpression> followAssignments(String masked, List<PathExpression> ranges) {
        List<SourceRange> blocks = braceBlocks(masked);
        Set<Integer> followedAssignments = new HashSet<>();
        for (int index = 0; index < ranges.size(); index++) {
            PathExpression range = ranges.get(index);
            Set<String> named = IDENTIFIER.matcher(masked.substring(range.start(), range.end())).results().map(MatchResult::group).collect(Collectors.toSet());
            if (named.isEmpty()) {
                continue;
            }
            for (String name : named) {
                // A parameter or header variable of an enclosing block shadows an outer field of that name; only assignments inside that block can supply its value. A
                // qualified use such as Test.fileName still reads the field.
                boolean qualifiedUse = QUALIFIED_MEMBER.matcher(masked.substring(range.start(), range.end())).results().anyMatch(use -> use.group(1).equals(name));
                Optional<SourceRange> shadowingBlock = qualifiedUse ? Optional.empty() : shadowingBlock(masked, blocks, range.valueAt(), name);
                List<MatchResult> candidates = ASSIGNMENT.matcher(masked).results().filter(assignment -> name.equals(assignment.group(1)))
                        .filter(assignment -> shadowingBlock.isEmpty() || shadowingBlock.get().start() < assignment.start() && assignment.start() < shadowingBlock.get().end())
                        .toList();
                List<MatchResult> assignments = candidates.stream().filter(assignment -> visibleAt(blocks, assignment.start(), range.valueAt()))
                        .filter(assignment -> blockDepth(blocks, assignment.start()) < 2 || assignment.start() < range.valueAt()).toList();
                int closestScope = assignments.stream().mapToInt(assignment -> blockDepth(blocks, assignment.start())).max().orElse(-1);
                int latest = assignments.stream().filter(assignment -> blockDepth(blocks, assignment.start()) == closestScope).mapToInt(MatchResult::start).max().orElse(-1);
                // A reassignment inside a branch between the latest assignment of the read's scope and the read can reach the read as well.
                List<MatchResult> branchAssignments = closestScope < 2 ? List.of()
                        : candidates.stream()
                                .filter(assignment -> latest < assignment.start() && assignment.start() < range.valueAt() && blockDepth(blocks, assignment.start()) > closestScope
                                        && !visibleAt(blocks, assignment.start(), range.valueAt()) && !declaresVariable(masked, assignment.start()))
                                .toList();
                for (MatchResult assignment : java.util.stream.Stream.concat(assignments.stream(), branchAssignments.stream()).toList()) {
                    boolean branch = branchAssignments.contains(assignment);
                    if (!branch && (blockDepth(blocks, assignment.start()) != closestScope || closestScope >= 2 && assignment.start() != latest)
                            || !followedAssignments.add(assignment.start())) {
                        continue;
                    }
                    int end = masked.indexOf(';', assignment.end());
                    ranges.add(new PathExpression(assignment.end(), end < 0 ? masked.length() : end, assignment.start()));
                }
            }
        }
        return ranges;
    }

    private record PathExpression(int start, int end, int valueAt) {
    }

    /** A member access by name, such as {@code fileName} in {@code Test.fileName}. */
    private static final Pattern QUALIFIED_MEMBER = Pattern.compile("\\.\\s*([A-Za-z_$][\\w$]*)");

    /** A type, optionally generic or an array, followed by the declared name and the end of a parameter or header variable. */
    private static final Pattern HEADER_DECLARATION = Pattern
            .compile("[\\w$](?:\\s*+<[^()<>]*+(?:<[^()<>]*+>[^()<>]*+)*+>)?(?:\\s*+\\[\\s*+\\])*+\\s++([A-Za-z_$][\\w$]*+)\\s*+[,):]");

    /** The innermost block around {@code use} whose header, such as a method's parameter list, an enhanced for or a catch clause, declares {@code name}. */
    private static Optional<SourceRange> shadowingBlock(String masked, List<SourceRange> blocks, int use, String name) {
        return blocks.stream().filter(block -> block.start() < use && use < block.end()).sorted(java.util.Comparator.comparingInt(SourceRange::start).reversed())
                .filter(block -> HEADER_DECLARATION.matcher(blockHeaderParameters(masked, block.start())).results().anyMatch(declared -> declared.group(1).equals(name)))
                .findFirst();
    }

    /** The text of the last parenthesized group in the header before the brace at {@code open}, or empty when the header has none. */
    private static String blockHeaderParameters(String masked, int open) {
        int headerStart = Math.max(masked.lastIndexOf(';', open - 1), Math.max(masked.lastIndexOf('{', open - 1), masked.lastIndexOf('}', open - 1))) + 1;
        int close = masked.lastIndexOf(')', open - 1);
        if (close < headerStart) {
            return "";
        }
        int depth = 0;
        for (int index = close; index >= headerStart; index--) {
            char current = masked.charAt(index);
            depth += current == ')' ? 1 : current == '(' ? -1 : 0;
            if (depth == 0) {
                return masked.substring(index + 1, close + 1);
            }
        }
        return "";
    }

    /** Whether the assignment at {@code position} declares a new variable, because a type precedes its name, rather than reassigning one declared earlier. */
    private static boolean declaresVariable(String masked, int position) {
        String before = masked.substring(0, position).stripTrailing();
        return !before.isEmpty() && (Character.isJavaIdentifierPart(before.charAt(before.length() - 1)) || before.endsWith(">") || before.endsWith("]"));
    }

    private static int blockDepth(List<SourceRange> blocks, int position) {
        return (int) blocks.stream().filter(block -> block.start() < position && position < block.end()).count();
    }

    /** Every brace-delimited block of the masked source, from its opening to its closing brace. */
    private static List<SourceRange> braceBlocks(String masked) {
        List<SourceRange> blocks = new ArrayList<>();
        java.util.Deque<Integer> open = new java.util.ArrayDeque<>();
        for (int index = 0; index < masked.length(); index++) {
            char current = masked.charAt(index);
            if (current == '{') {
                open.push(index);
            }
            else if (current == '}' && !open.isEmpty()) {
                blocks.add(new SourceRange(open.pop(), index));
            }
        }
        open.forEach(start -> blocks.add(new SourceRange(start, masked.length())));
        return blocks;
    }

    private static boolean visibleAt(List<SourceRange> blocks, int declaration, int use) {
        return blocks.stream().filter(block -> block.start() < declaration && declaration < block.end()).allMatch(block -> block.start() < use && use < block.end());
    }

    private record SourceRange(int start, int end) {
    }

    /** The index after the parenthesis closing the one at {@code open}, or the end of the source when it is never closed. */
    private static int closingParenthesis(String masked, int open) {
        int depth = 0;
        for (int index = open; index < masked.length(); index++) {
            char current = masked.charAt(index);
            if (current == '(') {
                depth++;
            }
            else if (current == ')' && --depth == 0) {
                return index + 1;
            }
        }
        return masked.length();
    }

}
