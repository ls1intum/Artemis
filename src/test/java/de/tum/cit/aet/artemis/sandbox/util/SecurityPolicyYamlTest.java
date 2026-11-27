package de.tum.cit.aet.artemis.sandbox.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = { "value: !!int 0xZZ\n", "value: !!binary \"%%%\"\n" })
    void readsAMalformedScalarAsAnUnreadablePolicy(String yaml) {
        assertThat(SecurityPolicyYaml.read(yaml).valid()).isFalse();
    }

    @Test
    void readsNoTestClassesFromAListWithOtherEntries() {
        SecurityPolicyYaml.ReadPolicy policy = SecurityPolicyYaml.read("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"de.example.SortingTest\", 1 ]\n");

        assertThat(policy.valid()).isTrue();
        assertThat(policy.testClasses()).isEmpty();
    }

    @Test
    void rejectsDuplicateKeysAndDocumentsThatAreNoMapping() {
        assertThat(SecurityPolicyYaml.read("regardingTheSupervisedCode: {}\nregardingTheSupervisedCode: {}\n").valid()).isFalse();
        assertThat(SecurityPolicyYaml.read("- just\n- a list\n").valid()).isFalse();
        assertThat(SecurityPolicyYaml.read("key: !!java.io.File /etc/passwd\n").valid()).isFalse();
    }
}
