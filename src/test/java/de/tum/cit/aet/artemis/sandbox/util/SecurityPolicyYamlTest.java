package de.tum.cit.aet.artemis.sandbox.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Test
    void appendsToABlockListAndKeepsEverythingElse() {
        String updated = SecurityPolicyYaml.addTestClasses(BLOCK_LIST_POLICY, List.of("de.example.SortingTest", "de.example.NewTest"), false).orElseThrow();

        assertThat(updated).isEqualTo(BLOCK_LIST_POLICY.replace("    - de.example.ClassTest\n", "    - de.example.ClassTest\n    - \"de.example.NewTest\"\n"));
    }

    @Test
    void turnsAnEmptyInlineListIntoABlockList() {
        String policy = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ]\n  theFollowingResourceAccessesArePermitted: { }\n";

        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest"), false))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - \"de.example.NewTest\"\n  theFollowingResourceAccessesArePermitted: { }\n");
    }

    @Test
    void extendsAFilledInlineList() {
        String policy = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [\"de.example.SortingTest\"]\n";

        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest"), false))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [\"de.example.SortingTest\", \"de.example.NewTest\"]\n");
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " -> ", value = { "[\"de.example.SortingTest\",] -> [\"de.example.SortingTest\", \"de.example.NewTest\", \"de.example.OtherTest\"]",
            "'[ \"de.example.SortingTest\" , ]' -> '[ \"de.example.SortingTest\" , \"de.example.NewTest\", \"de.example.OtherTest\"]'" })
    void reusesTheCommaAfterTheLastEntryOfAnInlineList(String list, String extended) {
        String policy = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: " + list + "\n";

        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest", "de.example.OtherTest"), false))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: " + extended + "\n");
    }

    @Test
    void readsTheListWithTheRulesOfThePinnedAresVersion() {
        String policy = """
                thisPolicyFileCompliesToThePolicyVersion: 1
                regardingTheSupervisedCode:
                  theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
                  theSupervisedCodeUsesTheFollowingPackage: "de.example"
                  theFollowingClassesAreTestClasses: [yes]
                  theFollowingResourceAccessesArePermitted:
                    regardingFileSystemInteractions: [ ]
                    regardingNetworkConnections: [ ]
                    regardingCommandExecutions: [ ]
                    regardingThreadCreations: [ ]
                    regardingPackageImports: [ ]
                    regardingTimeouts: [ ]
                """;

        // Ares 2.2.1 reads yes as the class name yes, as the state does when it reports the missing class; YAML 1.1 reads a Boolean.
        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest"), true)).contains(policy.replace("[yes]", "[yes, \"de.example.NewTest\"]"));
        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest"), false)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "value: !!int 0xZZ\n", "value: !!binary \"%%%\"\n" })
    void readsAMalformedScalarAsAnUnreadablePolicy(String yaml) {
        assertThat(SecurityPolicyYaml.read(yaml).valid()).isFalse();
    }

    @Test
    void leavesTheFileAloneIfNothingIsMissing() {
        assertThat(SecurityPolicyYaml.addTestClasses(BLOCK_LIST_POLICY, List.of("de.example.ClassTest"), false)).contains(BLOCK_LIST_POLICY);
    }

    @Test
    void keepsCommentsAndBlankLinesInAndAroundTheList() {
        String commented = BLOCK_LIST_POLICY.replace("  theFollowingClassesAreTestClasses:\n", "  theFollowingClassesAreTestClasses: # the tests\n")
                .replace("    - de.example.ClassTest\n", "    # structural tests\n\n    - de.example.ClassTest # keep\n");

        assertThat(SecurityPolicyYaml.addTestClasses(commented, List.of("de.example.NewTest"), false))
                .contains(commented.replace("    - de.example.ClassTest # keep\n", "    - de.example.ClassTest # keep\n    - \"de.example.NewTest\"\n"));
        assertThat(SecurityPolicyYaml.addTestClasses("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ] # none yet\n", List.of("de.example.NewTest"), false))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: # none yet\n    - \"de.example.NewTest\"\n");
    }

    @Test
    void acceptsACommentBehindTheSectionHeader() {
        String commented = BLOCK_LIST_POLICY.replace("regardingTheSupervisedCode:\n", "regardingTheSupervisedCode: # sandbox settings\n");

        assertThat(SecurityPolicyYaml.addTestClasses(commented, List.of("de.example.NewTest"), false))
                .contains(commented.replace("    - de.example.ClassTest\n", "    - de.example.ClassTest\n    - \"de.example.NewTest\"\n"));
    }

    @Test
    void refusesListsItCannotLocateUnambiguously() {
        String withoutList = "regardingTheSupervisedCode:\n  theMainClassInsideThisPackageIs: \"Client\"\n";
        String twoSupervisedCodes = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ]\n---\nregardingTheSupervisedCode:\n  x: 1\n";

        assertThat(SecurityPolicyYaml.addTestClasses(withoutList, List.of("de.example.NewTest"), false)).isEmpty();
        assertThat(SecurityPolicyYaml.read(withoutList).testClasses()).isEmpty();
        assertThat(SecurityPolicyYaml.addTestClasses(twoSupervisedCodes, List.of("de.example.NewTest"), false)).isEmpty();
    }

    @Test
    void readsNoTestClassesFromAListWithOtherEntries() {
        SecurityPolicyYaml.ReadPolicy policy = SecurityPolicyYaml.read("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"de.example.SortingTest\", 1 ]\n");

        assertThat(policy.valid()).isTrue();
        assertThat(policy.testClasses()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // The section header is no line of its own.
            "regardingTheSupervisedCode: { theFollowingClassesAreTestClasses: [ ] }\n",
            // A second line looks like the section header, because a quoted value continues on it.
            "notes: \"first\nregardingTheSupervisedCode:\n  last\"\nregardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ]\n",
            // The key is quoted, so no line starts with it.
            "regardingTheSupervisedCode:\n  \"theFollowingClassesAreTestClasses\": [ ]\n",
            // A second line looks like the key, because a block scalar contains it.
            "regardingTheSupervisedCode:\n  notes: |\n    theFollowingClassesAreTestClasses:\n  theFollowingClassesAreTestClasses: [ ]\n",
            // The only line that looks like the key comes before the section header.
            "notes: |\n  theFollowingClassesAreTestClasses:\nregardingTheSupervisedCode:\n  \"theFollowingClassesAreTestClasses\": [ ]\n",
            // A tagged inline list, a quoted bracket and a quoted comment marker: the list cannot be extended safely.
            "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: !!seq [ ]\n", "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"a]b\" ]\n",
            "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"a #b\" ]\n", "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"a\t#b\" ]\n",
            // Lines end in a carriage return only, so the block list cannot be located line by line.
            "regardingTheSupervisedCode:\r  theFollowingClassesAreTestClasses:\r    - \"de.example.SortingTest\"\r",
            // The item starts on the line after its dash.
            "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    -\n      \"de.example.SortingTest\"\n",
            // The last item continues on the next line, so an appended item would change it.
            "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - de.example.SortingTest\n      continued\n",
            "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - de.example.SortingTest\n      - continued\n" })
    void leavesAValidPolicyAloneIfTheListCannotBeExtendedSafely(String policy) {
        assertThat(SecurityPolicyYaml.read(policy).testClasses()).isPresent();
        assertThat(SecurityPolicyYaml.addTestClasses(policy, List.of("de.example.NewTest"), false)).isEmpty();
    }

    @Test
    void keepsTheLineEndingsAndTheEndOfTheFile() {
        String crlf = "regardingTheSupervisedCode:\r\n  theFollowingClassesAreTestClasses:\r\n    - \"de.example.SortingTest\"\r\n";
        String withoutFinalLineBreak = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - \"de.example.SortingTest\"";

        assertThat(SecurityPolicyYaml.addTestClasses(crlf, List.of("de.example.NewTest"), false))
                .contains("regardingTheSupervisedCode:\r\n  theFollowingClassesAreTestClasses:\r\n    - \"de.example.SortingTest\"\r\n    - \"de.example.NewTest\"\r\n");
        assertThat(SecurityPolicyYaml.addTestClasses(withoutFinalLineBreak, List.of("de.example.NewTest"), false))
                .contains(withoutFinalLineBreak + "\n    - \"de.example.NewTest\"");
    }

    @Test
    void readsTheCommentBehindAnInlineListOnlyFromWhitespaceBeforeTheHash() {
        String trailingWhitespace = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ ]   \n";
        String hashInAName = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"a#b\" ]  # tests\n";

        assertThat(SecurityPolicyYaml.addTestClasses(trailingWhitespace, List.of("de.example.NewTest"), false))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - \"de.example.NewTest\"\n");
        assertThat(SecurityPolicyYaml.addTestClasses(hashInAName, List.of("de.example.NewTest"), false))
                .contains("regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: [ \"a#b\", \"de.example.NewTest\"]  # tests\n");
    }

    @Test
    void rejectsDuplicateKeysAndDocumentsThatAreNoMapping() {
        assertThat(SecurityPolicyYaml.read("regardingTheSupervisedCode: {}\nregardingTheSupervisedCode: {}\n").valid()).isFalse();
        assertThat(SecurityPolicyYaml.read("- just\n- a list\n").valid()).isFalse();
        assertThat(SecurityPolicyYaml.read("key: !!java.io.File /etc/passwd\n").valid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "thisPolicyFileCompliesToThePolicyVersion: 1\n", "regardingTheSupervisedCode: []\n",
            "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses: de.example.SortingTest\n" })
    void readsNoTestClassesWithoutAListOfThem(String yaml) {
        SecurityPolicyYaml.ReadPolicy policy = SecurityPolicyYaml.read(yaml);

        assertThat(policy.valid()).isTrue();
        assertThat(policy.testClasses()).isEmpty();
    }
}
