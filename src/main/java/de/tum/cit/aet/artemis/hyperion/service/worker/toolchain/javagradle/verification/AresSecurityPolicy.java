package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps Ares 2 permissions frozen while registering only instructor-owned test source classes. */
final class AresSecurityPolicy {

    static final String PATH = "SecurityPolicy.yaml";

    private static final Pattern TEST_CLASSES = Pattern.compile("(?m)^  theFollowingClassesAreTestClasses:\\n((?:    - \\\"[A-Za-z_$][\\w.$]*\\\"\\n)*)");

    private static final Pattern SOURCE = Pattern.compile("^(?:test|behavior/test|structural/test)/([A-Za-z_$][\\w$/]*)\\.java$");

    private AresSecurityPolicy() {
    }

    static boolean onlyTestClassesChanged(String seed, String produced) {
        if (seed == null || produced == null) {
            return false;
        }
        Matcher seedClasses = TEST_CLASSES.matcher(seed.replace("\r\n", "\n"));
        Matcher producedClasses = TEST_CLASSES.matcher(produced.replace("\r\n", "\n"));
        if (!seedClasses.find() || !producedClasses.find()) {
            return false;
        }
        String seedWithoutClasses = seedClasses.replaceFirst("  theFollowingClassesAreTestClasses:\n");
        String producedWithoutClasses = producedClasses.replaceFirst("  theFollowingClassesAreTestClasses:\n");
        return !seedClasses.find() && !producedClasses.find() && seedWithoutClasses.equals(producedWithoutClasses);
    }

    static List<String> reasons(Map<String, String> seed, Map<String, String> produced) {
        String policy = produced.get(PATH);
        if (policy == null) {
            return List.of("Ares 2 tests must keep the explicit SecurityPolicy.yaml; the default policy must not derive exemptions from student code.");
        }
        if (!seed.isEmpty() && !onlyTestClassesChanged(seed.get(PATH), policy)) {
            return List.of("Keep the seeded Ares 2 policy unchanged except for theFollowingClassesAreTestClasses; resource permissions and supervised package are immutable.");
        }
        Matcher classes = TEST_CLASSES.matcher(policy.replace("\r\n", "\n"));
        if (!classes.find()) {
            return List.of("Declare Ares 2 test classes as a double-quoted list under theFollowingClassesAreTestClasses in SecurityPolicy.yaml.");
        }
        String declared = classes.group(1);
        TreeSet<String> expected = testClasses(produced);
        TreeSet<String> actual = new TreeSet<>();
        for (String line : declared.lines().toList()) {
            String name = line.substring(7, line.length() - 1);
            if (!actual.add(name)) {
                return List.of("The Ares 2 test class list must not contain duplicate entries.");
            }
        }
        if (!actual.equals(expected)) {
            return List.of("SecurityPolicy.yaml must list exactly the instructor test source classes (not student classes): " + String.join(", ", expected) + ".");
        }
        return List.of();
    }

    /** Registers instructor source paths only; no assignment sources enter this map. All other policy bytes remain untouched. */
    static String registerTestClasses(Map<String, String> tests) {
        String policy = tests.get(PATH);
        if (policy == null) {
            return null;
        }
        Matcher classes = TEST_CLASSES.matcher(policy.replace("\r\n", "\n"));
        if (!classes.find()) {
            throw new IllegalStateException("The seeded Ares 2 test-class list cannot be inspected safely");
        }
        String list = testClasses(tests).stream().map(name -> "    - \"" + name + "\"\n").collect(java.util.stream.Collectors.joining());
        return classes.replaceFirst(Matcher.quoteReplacement("  theFollowingClassesAreTestClasses:\n" + list));
    }

    private static TreeSet<String> testClasses(Map<String, String> produced) {
        TreeSet<String> expected = new TreeSet<>();
        for (String path : produced.keySet()) {
            Matcher source = SOURCE.matcher(path);
            if (source.matches()) {
                expected.add(source.group(1).replace('/', '.'));
            }
        }
        return expected;
    }

}
