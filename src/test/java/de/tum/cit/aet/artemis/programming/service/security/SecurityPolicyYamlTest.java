package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests how {@link SecurityPolicyYaml} reads the test classes of a policy and adds test classes without touching the rest of the file.
 */
class SecurityPolicyYamlTest {

    private static final String BLOCK_LIST_POLICY = """
            # Instructor notes stay as they are
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theMainClassInsideThisPackageIs: "Client"
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
                - de.example.ClassTest
              theFollowingResourceAccessesArePermitted:
                regardingThreadCreations:
                  - createTheFollowingNumberOfThreads: 1
                    ofThisClass: "java.util.concurrent.ThreadLocalRandom"
            """;

    @Test
    void readsTheTestClasses() {
        SecurityPolicyYaml.ReadPolicy policy = SecurityPolicyYaml.read(BLOCK_LIST_POLICY);

        assertThat(policy.valid()).isTrue();
        assertThat(policy.testClasses()).contains(List.of("de.example.SortingTest", "de.example.ClassTest"));
    }

    @Test
    void appendsToABlockListAndKeepsEverythingElse() {
        String updated = SecurityPolicyYaml.addTestClasses(BLOCK_LIST_POLICY, List.of("de.example.SortingTest", "de.example.NewTest")).orElseThrow();

        assertThat(updated).isEqualTo(BLOCK_LIST_POLICY.replace("    - de.example.ClassTest\n", "    - de.example.ClassTest\n    - \"de.example.NewTest\"\n"));
    }

    @Test
    void turnsAnEmptyInlineListIntoABlockList() {
        String policy = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ]\n  theFollowingResourceAccessesArePermitted: { }\n";

        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest")))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - \"de.example.NewTest\"\n  theFollowingResourceAccessesArePermitted: { }\n");
    }

    @Test
    void extendsAFilledInlineList() {
        String policy = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [\"de.example.SortingTest\"]\n";

        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest")))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [\"de.example.SortingTest\", \"de.example.NewTest\"]\n");
    }

    @Test
    void leavesTheFileAloneIfNothingIsMissing() {
        assertThat(SecurityPolicyYaml.addTestClasses(BLOCK_LIST_POLICY, List.of("de.example.ClassTest"))).contains(BLOCK_LIST_POLICY);
    }

    @Test
    void keepsCommentsAndBlankLinesInAndAroundTheList() {
        String commented = BLOCK_LIST_POLICY.replace("  theFollowingClassesAreTestClasses:\n", "  theFollowingClassesAreTestClasses: # the tests\n")
                .replace("    - de.example.ClassTest\n", "    # structural tests\n\n    - de.example.ClassTest # keep\n");

        assertThat(SecurityPolicyYaml.addTestClasses(commented, List.of("de.example.NewTest")))
                .contains(commented.replace("    - de.example.ClassTest # keep\n", "    - de.example.ClassTest # keep\n    - \"de.example.NewTest\"\n"));
        assertThat(SecurityPolicyYaml.addTestClasses("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ] # none yet\n", List.of("de.example.NewTest")))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: # none yet\n    - \"de.example.NewTest\"\n");
    }

    @Test
    void refusesListsItCannotLocateUnambiguously() {
        String withoutList = "regardingTheSupervisedCode:\n  theMainClassInsideThisPackageIs: \"Client\"\n";
        String twoSupervisedCodes = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ]\n---\nregardingTheSupervisedCode:\n  x: 1\n";

        assertThat(SecurityPolicyYaml.addTestClasses(withoutList, List.of("de.example.NewTest"))).isEmpty();
        assertThat(SecurityPolicyYaml.read(withoutList).testClasses()).isEmpty();
        assertThat(SecurityPolicyYaml.addTestClasses(twoSupervisedCodes, List.of("de.example.NewTest"))).isEmpty();
    }

    @Test
    void rejectsDuplicateKeysAndDocumentsThatAreNoMapping() {
        assertThat(SecurityPolicyYaml.read("regardingTheSupervisedCode: {}\nregardingTheSupervisedCode: {}\n").valid()).isFalse();
        assertThat(SecurityPolicyYaml.read("- just\n- a list\n").valid()).isFalse();
        assertThat(SecurityPolicyYaml.read("key: !!java.io.File /etc/passwd\n").valid()).isFalse();
    }
}
