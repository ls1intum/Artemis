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
 * <b>Why validation demands positive evidence.</b> "The build did not report this as failing" is not evidence that it passed — it is equally satisfied by a witness that never
 * ran. A witness is therefore validated only when the build reports it among the tests it RAN. Reading silence as success would manufacture evidence out of a build that never
 * exercised the witness, which is the exact mistake this whole mechanism exists to stop.
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
     * Decides which witnesses the build actually validated: a witness must appear among the tests the build REPORTED RUNNING, and must not appear among its failures.
     * <p>
     * Requiring positive evidence of execution is the whole point. "Absent from the failure list" is satisfied by a witness that never ran at all — one the runner did not
     * discover, one whose annotation the model omitted, one disabled or aborted by an assumption, or a whole probe class that failed to compile while the ordinary graded tests
     * still ran and made the build look healthy. Each of those would otherwise be recorded as a passing witness on no evidence whatsoever.
     *
     * @param executedTestNames the names the build reported running (from the same parsed report production grading uses)
     * @param failedTestNames   the names the build reported as failing
     * @param witnesses         the candidates that were written into the probe
     * @return the witnesses that demonstrably ran and passed against the reference solution
     */
    public static List<ContractWitness> validated(List<String> executedTestNames, List<String> failedTestNames, List<ContractWitness> witnesses) {
        Set<String> executed = bareNames(executedTestNames);
        Set<String> failed = bareNames(failedTestNames);
        List<ContractWitness> validated = new ArrayList<>();
        for (ContractWitness witness : witnesses) {
            if (executed.contains(witness.testName()) && !failed.contains(witness.testName())) {
                validated.add(witness);
            }
        }
        return List.copyOf(validated);
    }

    /**
     * Reduces reported test names to bare method names. Report forms differ per framework ({@code testFoo}, {@code testFoo()}, {@code ClassName.testFoo}), so matching on the
     * bare name keeps attribution stable across them.
     */
    private static Set<String> bareNames(@Nullable List<String> reportedNames) {
        Set<String> bare = new LinkedHashSet<>();
        if (reportedNames == null) {
            return bare;
        }
        for (String reported : reportedNames) {
            if (reported == null) {
                continue;
            }
            String name = reported;
            int parenthesis = name.indexOf('(');
            if (parenthesis >= 0) {
                name = name.substring(0, parenthesis);
            }
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
            bare.add(name.strip());
        }
        return bare;
    }

    /**
     * The workspace-relative path the probe is written to: alongside the graded test it borrowed its package from, so the build discovers it by the same convention.
     *
     * @param existingTestPath  the workspace-relative path of that graded test
     * @param existingFilePaths every path already present in the tests repository
     * @return the probe's workspace-relative path, or {@code null} when the path has no directory or that name is already taken
     */
    public static @Nullable String probePath(String existingTestPath, Set<String> existingFilePaths) {
        int lastSlash = existingTestPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return null;
        }
        String path = existingTestPath.substring(0, lastSlash + 1) + PROBE_CLASS_NAME + ".java";
        // The name is distinctive, not reserved. Writing over a generated test of the same name would destroy graded work, and removing the probe afterwards would delete it.
        return existingFilePaths.contains(path) ? null : path;
    }
}
