package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Identifies non-behavioural build/compile/configure gate test cases, such as the C/C++ FACT harness's {@code GBS-Tester-1.36.CompileSort} or a generic {@code Configure}. They
 * assert only that the tree compiles or configures, which the same-signature placeholder template satisfies by design, so they legitimately pass on both assignments.
 * <p>
 * Shared by two call sites that must agree: the differential oracle exempts them from its "every gradable test must fail on the template" gate, and persistence zero-weights them
 * on the generated exercise. Without the second half, a student submitting the untouched (compiling) template would score above 0%.
 */
public final class BuildGateTestNames {

    private static final Set<String> EXACT_NAMES = Set.of("testconfigure", "configure", "compile", "testcompile", "build", "testbuild", "cmake");

    /** Prefixes of a per-target build gate ({@code CompileSort}, {@code ConfigureDebug}, {@code BuildTests}). */
    private static final List<String> PREFIXES = List.of("compile", "configure", "build");

    private BuildGateTestNames() {
    }

    /**
     * Whether a test name is a build gate (an exact word, or a {@code GateWord<UpperCaseTarget>} form). Both the whole name and its last dot-segment are checked, because a
     * harness may prefix the gate with its framework suite ({@code GBS-Tester-1.36.TestConfigure}).
     *
     * @param name the test name as the runner reports it; a trailing {@code ()} is tolerated
     * @return whether it is a build/compile/configure gate
     */
    public static boolean isBuildGate(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.endsWith("()") ? name.substring(0, name.length() - 2) : name;
        if (matchesToken(normalized)) {
            return true;
        }
        int lastDot = normalized.lastIndexOf('.');
        return lastDot >= 0 && lastDot < normalized.length() - 1 && matchesToken(normalized.substring(lastDot + 1));
    }

    private static boolean matchesToken(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (EXACT_NAMES.contains(lower)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            // Build gates are PascalCase in both halves (CompileSort, ConfigureDebug), so requiring an uppercase first character AND an uppercase target keeps camelCase
            // (buildGraphFromEdges) and snake_case (compiles_an_empty_program) behaviour tests from ever being mistaken for one.
            if (lower.startsWith(prefix) && token.length() > prefix.length() && Character.isUpperCase(token.charAt(0)) && Character.isUpperCase(token.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }
}
