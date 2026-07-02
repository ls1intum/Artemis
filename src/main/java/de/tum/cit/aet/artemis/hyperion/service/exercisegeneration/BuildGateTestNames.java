package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Identifies non-behavioural build/compile/configure gate test cases — the C/C++ FACT harness reports e.g. {@code GBS-Tester-1.36.CompileSort} / {@code TestConfigure}, the C
 * {@code Compile}/{@code TestCompile}, a generic {@code Configure}/{@code Build}. They assert only "does it compile/configure", which the same-signature placeholder template
 * satisfies BY DESIGN, so they legitimately pass on both the solution and the template.
 * <p>
 * This single definition is the source of truth for two call sites that must agree (parity by construction): the differential oracle EXEMPTS these from its "every gradable test
 * must
 * fail on the template" gate, and the persistence step ZERO-WEIGHTS them on the generated exercise so production grading also gives no points for them — otherwise a student
 * submitting the untouched (compiling) template would score above 0%.
 */
public final class BuildGateTestNames {

    private static final Set<String> EXACT_NAMES = Set.of("testconfigure", "configure", "compile", "testcompile", "build", "testbuild", "cmake");

    /** Prefixes of a per-target build gate ({@code CompileSort}, {@code ConfigureDebug}, {@code BuildTests}). Case-insensitive. */
    private static final List<String> PREFIXES = List.of("compile", "configure", "build");

    private BuildGateTestNames() {
    }

    /**
     * Whether a test name is a build/compile/configure gate (exact word, or a {@code GateWord<UpperCaseTarget>} form), checking both the whole name and its last dot-segment (the
     * real C++ harness prefixes the gate with the framework suite, e.g. {@code GBS-Tester-1.36.TestConfigure}).
     *
     * @param name the test name (as the test runner reports it; a leading/trailing {@code ()} is tolerated)
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

    /** Whether a token is exactly a build-gate word, or a {@code GateWord<UpperCaseTarget>} form (e.g. {@code CompileSort}). */
    private static boolean matchesToken(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        if (EXACT_NAMES.contains(lower)) {
            return true;
        }
        for (String prefix : PREFIXES) {
            // Require an uppercase target after the gate word so a behaviour test like "compiles_an_empty_program" is never a false positive.
            if (lower.startsWith(prefix) && token.length() > prefix.length() && Character.isUpperCase(token.charAt(prefix.length()))) {
                return true;
            }
        }
        return false;
    }
}
