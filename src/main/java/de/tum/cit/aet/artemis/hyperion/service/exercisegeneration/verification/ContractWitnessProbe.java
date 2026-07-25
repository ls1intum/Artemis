package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;

/**
 * Turns candidate {@link ContractWitness}es into validated ones by running them against the reference solution.
 * <p>
 * The pieces here are pure so they are unit-testable without Docker: building the probe source, and deciding what a build outcome is allowed to prove. The sandbox half (write the
 * probe, build the solution, remove the probe) belongs to the caller that already owns a session.
 * <p>
 * <b>Why the classification is deliberately timid.</b> "The suite reported no failure" is only meaningful when the suite actually ran. A compile failure, a timeout, a crashed
 * runner or an infrastructure error all produce zero results, and reading any of them as "the witness passed" would manufacture evidence out of a broken build — the exact
 * mistake this whole mechanism exists to stop. So every outcome except "the tests ran and reported results" discards all witnesses and claims nothing.
 */
public final class ContractWitnessProbe {

    /** The probe class name. Distinctive on purpose: it must be recognisable as machine-authored and never collide with a generated suite. */
    public static final String PROBE_CLASS_NAME = "HyperionContractWitnessProbeTest";

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+[^;]+;", Pattern.MULTILINE);

    private static final Pattern IMPORT_DECLARATION = Pattern.compile("^\\s*import\\s+[^;]+;", Pattern.MULTILINE);

    private ContractWitnessProbe() {
    }

    /**
     * Builds one compilable probe class carrying every witness method.
     * <p>
     * The package and imports are lifted from a test the authoring agent actually produced rather than assembled from a fixed list, so the probe automatically matches whichever
     * assertion library, JUnit version and harness annotations that exercise uses. Class-level harness annotations are deliberately NOT copied: they are the graded suite's
     * contract with the production grader, and the probe is a throwaway that must never look like a graded test.
     *
     * @param existingTestSource one graded test source from the same repository, used only as the source of the package and import declarations
     * @param witnesses          the candidate witnesses, each contributing one method
     * @return the complete probe source, or empty when there is nothing to build
     */
    public static String buildProbeSource(String existingTestSource, List<ContractWitness> witnesses) {
        if (witnesses.isEmpty()) {
            return "";
        }
        StringBuilder source = new StringBuilder();
        Matcher packageMatcher = PACKAGE_DECLARATION.matcher(existingTestSource);
        if (packageMatcher.find()) {
            source.append(packageMatcher.group().strip()).append("\n\n");
        }
        Set<String> imports = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT_DECLARATION.matcher(existingTestSource);
        while (importMatcher.find()) {
            imports.add(importMatcher.group().strip());
        }
        imports.forEach(declaration -> source.append(declaration).append("\n"));
        if (!imports.isEmpty()) {
            source.append("\n");
        }
        source.append("class ").append(PROBE_CLASS_NAME).append(" {\n");
        for (ContractWitness witness : witnesses) {
            source.append("\n").append(witness.code().strip().indent(4));
        }
        return source.append("}\n").toString();
    }

    /**
     * Decides which witnesses the build actually validated.
     *
     * @param testsRan        whether the build ran the test suite and reported results at all; when false nothing is validated, whatever else is reported
     * @param failedTestNames the names the build reported as failing
     * @param witnesses       the candidates that were written into the probe
     * @return the witnesses that ran and passed against the reference solution
     */
    public static List<ContractWitness> validated(boolean testsRan, List<String> failedTestNames, List<ContractWitness> witnesses) {
        if (!testsRan) {
            return List.of();
        }
        Set<String> failed = new LinkedHashSet<>(failedTestNames == null ? List.of() : failedTestNames);
        List<ContractWitness> validated = new ArrayList<>();
        for (ContractWitness witness : witnesses) {
            if (!failedByName(failed, witness.testName())) {
                validated.add(witness);
            }
        }
        return List.copyOf(validated);
    }

    /**
     * Whether a reported failure belongs to this witness. Report forms differ per framework ({@code testFoo}, {@code testFoo()}, {@code ClassName.testFoo}), so the match is on
     * the bare method name rather than on an exact string, and a failure that cannot be attributed leaves the witness unvalidated rather than silently accepted.
     */
    private static boolean failedByName(Set<String> failedTestNames, String witnessName) {
        for (String failed : failedTestNames) {
            String bare = failed;
            int parenthesis = bare.indexOf('(');
            if (parenthesis >= 0) {
                bare = bare.substring(0, parenthesis);
            }
            int lastDot = bare.lastIndexOf('.');
            if (lastDot >= 0) {
                bare = bare.substring(lastDot + 1);
            }
            if (bare.strip().equals(witnessName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The workspace-relative path the probe is written to: alongside the graded test it borrowed its package from, so the build discovers it by the same convention.
     *
     * @param existingTestPath the workspace-relative path of that graded test
     * @return the probe's workspace-relative path, or empty when the path has no directory to sit in
     */
    public static @Nullable String probePath(String existingTestPath) {
        int lastSlash = existingTestPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return null;
        }
        return existingTestPath.substring(0, lastSlash + 1) + PROBE_CLASS_NAME + ".java";
    }
}
