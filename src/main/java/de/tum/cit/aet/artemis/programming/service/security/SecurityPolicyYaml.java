package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Reads the list of test classes of an Ares policy file and adds test classes to it.
 * <p>
 * Policy files are written by instructors, so they are read defensively: with SnakeYAML's {@link SafeConstructor}, which only builds plain maps, lists and
 * scalars, a size limit, a small alias limit and without duplicate keys. Adding test classes rewrites only the value of
 * {@code theFollowingClassesAreTestClasses}; every other line, including comments and permissions, stays as written. If that value cannot be located
 * unambiguously, the file is left alone.
 */
final class SecurityPolicyYaml {

    /** The largest policy file that is read. Policies are short; anything larger is rejected before it is parsed. */
    static final int MAX_POLICY_SIZE = 256 * 1024;

    private static final String SUPERVISED_CODE_KEY = "regardingTheSupervisedCode";

    private static final String TEST_CLASSES_KEY = "theFollowingClassesAreTestClasses";

    private static final Pattern SUPERVISED_CODE_LINE = Pattern.compile("^" + SUPERVISED_CODE_KEY + ":\\s*$", Pattern.MULTILINE);

    private static final Pattern TEST_CLASSES_LINE = Pattern.compile("^([ \\t]+)" + TEST_CLASSES_KEY + ":(.*)$", Pattern.MULTILINE);

    private static final Pattern BLOCK_ITEM = Pattern.compile("^([ \\t]*)-[ \\t]+\\S.*$");

    /** A line without content: blank or only a comment. Such lines may appear between the items of a block list. */
    private static final Pattern BLANK_OR_COMMENT_LINE = Pattern.compile("^[ \\t]*(#.*)?$");

    /** A comment at the end of a line, which in YAML starts with a {@code #} after whitespace. */
    private static final Pattern TRAILING_COMMENT = Pattern.compile("[ \\t]+#.*$");

    private static final Pattern EMPTY_FLOW_LIST = Pattern.compile("^\\s*\\[\\s*]\\s*$");

    /**
     * The result of reading a policy file.
     *
     * @param valid       whether the file is a YAML mapping that SnakeYAML reads within the limits
     * @param testClasses the entries of {@code theFollowingClassesAreTestClasses}; empty if the list cannot be located or contains anything but strings
     */
    record ReadPolicy(boolean valid, Optional<List<String>> testClasses) {
    }

    private SecurityPolicyYaml() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * @param yaml the content of a policy file
     * @return what could be read from it
     */
    static ReadPolicy read(String yaml) {
        Object document;
        try {
            document = newYaml().load(yaml);
        }
        catch (YAMLException | ClassCastException e) {
            return new ReadPolicy(false, Optional.empty());
        }
        if (!(document instanceof Map<?, ?> root)) {
            return new ReadPolicy(false, Optional.empty());
        }
        return new ReadPolicy(true, testClasses(root));
    }

    private static Optional<List<String>> testClasses(Map<?, ?> root) {
        if (!(root.get(SUPERVISED_CODE_KEY) instanceof Map<?, ?> supervisedCode) || !(supervisedCode.get(TEST_CLASSES_KEY) instanceof List<?> entries)) {
            return Optional.empty();
        }
        List<String> testClasses = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof String testClass)) {
                return Optional.empty();
            }
            testClasses.add(testClass);
        }
        return Optional.of(testClasses);
    }

    /**
     * Adds test classes to the end of {@code theFollowingClassesAreTestClasses}, in the given order, and keeps the rest of the file byte for byte.
     *
     * @param yaml         the content of a valid policy file
     * @param classesToAdd the fully qualified names to add; names that are already listed are skipped
     * @return the rewritten content, or empty if the list cannot be located unambiguously or the result would not read back as the same policy with the
     *         added classes; the content unchanged if there is nothing to add
     */
    static Optional<String> addTestClasses(String yaml, List<String> classesToAdd) {
        ReadPolicy before = read(yaml);
        if (!before.valid() || before.testClasses().isEmpty()) {
            return Optional.empty();
        }
        List<String> missing = classesToAdd.stream().filter(testClass -> !before.testClasses().get().contains(testClass)).distinct().toList();
        if (missing.isEmpty()) {
            return Optional.of(yaml);
        }
        Optional<String> rewritten = rewrite(yaml, missing);
        if (rewritten.isEmpty()) {
            return Optional.empty();
        }
        List<String> expected = new ArrayList<>(before.testClasses().get());
        expected.addAll(missing);
        return readsBackAs(yaml, rewritten.get(), expected) ? rewritten : Optional.empty();
    }

    private static Optional<String> rewrite(String yaml, List<String> missing) {
        Matcher supervisedCode = SUPERVISED_CODE_LINE.matcher(yaml);
        if (!supervisedCode.find()) {
            return Optional.empty();
        }
        int supervisedCodeStart = supervisedCode.start();
        if (supervisedCode.find()) {
            return Optional.empty();
        }
        Matcher key = TEST_CLASSES_LINE.matcher(yaml);
        if (!key.find()) {
            return Optional.empty();
        }
        int keyStart = key.start();
        String indent = key.group(1);
        String value = key.group(2);
        int keyEnd = key.end();
        if (key.find() || keyStart < supervisedCodeStart) {
            return Optional.empty();
        }
        // A comment behind the key or the inline list stays where it is.
        Matcher comment = TRAILING_COMMENT.matcher(value);
        String trailingComment = comment.find() ? value.substring(comment.start()) : "";
        String listValue = value.substring(0, value.length() - trailingComment.length());
        String newline = yaml.contains("\r\n") ? "\r\n" : "\n";
        if (!listValue.isBlank()) {
            return rewriteFlowList(yaml, keyStart, keyEnd, indent, listValue, trailingComment, missing, newline);
        }
        return appendToBlockList(yaml, keyEnd, indent, missing, newline);
    }

    /**
     * An inline list such as {@code [ ]} or {@code ["a", "b"]}: an empty one becomes a block list, a filled one gets the classes before its closing bracket.
     */
    private static Optional<String> rewriteFlowList(String yaml, int keyStart, int keyEnd, String indent, String value, String trailingComment, List<String> missing,
            String newline) {
        String trimmed = value.strip();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]") || trimmed.indexOf(']') != trimmed.length() - 1) {
            return Optional.empty();
        }
        StringBuilder replacement = new StringBuilder(indent).append(TEST_CLASSES_KEY).append(':');
        if (EMPTY_FLOW_LIST.matcher(trimmed).matches()) {
            replacement.append(trailingComment);
            missing.forEach(testClass -> replacement.append(newline).append(indent).append("  - ").append(quote(testClass)));
        }
        else {
            StringBuilder list = new StringBuilder(trimmed.substring(0, trimmed.length() - 1).stripTrailing());
            missing.forEach(testClass -> list.append(", ").append(quote(testClass)));
            replacement.append(' ').append(list).append(']').append(trailingComment);
        }
        return Optional.of(yaml.substring(0, keyStart) + replacement + yaml.substring(keyEnd));
    }

    /**
     * A block list on the following lines: the classes are appended after its last item, with the indentation of its items.
     */
    private static Optional<String> appendToBlockList(String yaml, int keyEnd, String keyIndent, List<String> missing, String newline) {
        int lineStart = yaml.indexOf('\n', keyEnd);
        if (lineStart < 0) {
            return Optional.empty();
        }
        lineStart++;
        int insertAt = -1;
        String itemIndent = null;
        while (lineStart < yaml.length()) {
            int lineEnd = yaml.indexOf('\n', lineStart);
            String line = yaml.substring(lineStart, lineEnd < 0 ? yaml.length() : lineEnd).replace("\r", "");
            if (BLANK_OR_COMMENT_LINE.matcher(line).matches()) {
                // Blank and comment lines may separate the items; the classes are still appended after the last item.
                lineStart = lineEnd < 0 ? yaml.length() : lineEnd + 1;
                continue;
            }
            Matcher item = BLOCK_ITEM.matcher(line);
            if (!item.matches() || item.group(1).length() < keyIndent.length() || (itemIndent != null && !item.group(1).equals(itemIndent))) {
                break;
            }
            itemIndent = item.group(1);
            insertAt = lineEnd < 0 ? yaml.length() : lineEnd;
            lineStart = lineEnd < 0 ? yaml.length() : lineEnd + 1;
        }
        if (insertAt < 0) {
            return Optional.empty();
        }
        StringBuilder addition = new StringBuilder();
        String prefix = itemIndent;
        missing.forEach(testClass -> addition.append(newline).append(prefix).append("- ").append(quote(testClass)));
        String before = yaml.substring(0, insertAt);
        if (before.endsWith("\r")) {
            before = before.substring(0, before.length() - 1);
            return Optional.of(before + addition + "\r" + yaml.substring(insertAt));
        }
        return Optional.of(before + addition + yaml.substring(insertAt));
    }

    private static String quote(String testClass) {
        return "\"" + testClass + "\"";
    }

    /**
     * @return whether the rewritten content reads as the original policy with only the list of test classes replaced by the expected one
     */
    private static boolean readsBackAs(String original, String rewritten, List<String> expectedTestClasses) {
        try {
            Map<String, Object> before = copyWithTestClasses(newYaml().load(original), expectedTestClasses);
            Map<String, Object> after = copyWithTestClasses(newYaml().load(rewritten), expectedTestClasses);
            ReadPolicy reread = read(rewritten);
            return before != null && before.equals(after) && reread.testClasses().filter(expectedTestClasses::equals).isPresent();
        }
        catch (YAMLException | ClassCastException e) {
            return false;
        }
    }

    /**
     * @return a copy of the document whose list of test classes is replaced, so two documents can be compared apart from that list; null if it is no
     *         policy mapping
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyWithTestClasses(Object document, List<String> testClasses) {
        if (!(document instanceof Map<?, ?> root) || !(root.get(SUPERVISED_CODE_KEY) instanceof Map<?, ?> supervisedCode)) {
            return null;
        }
        Map<String, Object> rootCopy = new LinkedHashMap<>((Map<String, Object>) root);
        Map<String, Object> supervisedCodeCopy = new LinkedHashMap<>((Map<String, Object>) supervisedCode);
        supervisedCodeCopy.put(TEST_CLASSES_KEY, testClasses);
        rootCopy.put(SUPERVISED_CODE_KEY, supervisedCodeCopy);
        return rootCopy;
    }

    private static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(MAX_POLICY_SIZE);
        options.setMaxAliasesForCollections(10);
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setNestingDepthLimit(20);
        return new Yaml(new SafeConstructor(options));
    }
}
