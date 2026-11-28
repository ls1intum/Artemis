package de.tum.cit.aet.artemis.sandbox.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public final class SecurityPolicyYaml {

    /** The largest policy file that is read. Policies are short; anything larger is rejected before it is parsed. */
    public static final int MAX_POLICY_SIZE = 256 * 1024;

    private static final String SUPERVISED_CODE_KEY = "regardingTheSupervisedCode";

    private static final String TEST_CLASSES_KEY = "theFollowingClassesAreTestClasses";

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
        catch (YAMLException | ClassCastException _) {
            return new ReadPolicy(false, Optional.empty());
        }
        if (!(document instanceof Map<?, ?> root)) {
            return new ReadPolicy(false, Optional.empty());
        }
        return new ReadPolicy(true, testClasses(root));
    }

    static Optional<List<String>> testClasses(Map<?, ?> root) {
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

    static Yaml newYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(MAX_POLICY_SIZE);
        options.setMaxAliasesForCollections(10);
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setNestingDepthLimit(20);
        // A malformed scalar, e.g. !!int 0xZZ or a broken !!binary, then fails as a YAMLException like any other unreadable file.
        options.setWrappedToRootException(true);
        return new Yaml(new SafeConstructor(options));
    }
}
