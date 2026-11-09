package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that {@link SecurityPolicySchema} accepts and rejects the same policy files as Ares 2.2.1, including the YAML notations in which the parser of Ares
 * differs from the Jackson version of the server.
 */
class SecurityPolicySchemaTest {

    private static final String VALID_POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theMainClassInsideThisPackageIs: "Client"
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions:
                  - onThisPathAndAllPathsBelow: "${PROJECT_ROOT}/data"
                    readAllFiles: true
                    overwriteAllFiles: false
                    createAllFiles: false
                    executeAllFiles: false
                    deleteAllFiles: false
                regardingNetworkConnections:
                  - onTheHost: "example.org"
                    onThePort: 443
                    openConnections: true
                    sendData: false
                    receiveData: false
                regardingCommandExecutions:
                  - "ls"
                  - executeTheCommand: "git"
                    withTheseArguments: [ "status" ]
                regardingThreadCreations:
                  - createTheFollowingNumberOfThreads: 1
                    ofThisClass: "java.util.concurrent.ThreadLocalRandom"
                regardingPackageImports:
                  - importTheFollowingPackage: "org.apache.commons.lang3"
                regardingTimeouts:
                  - timeout: 1000
            """;

    private static final String MAIN_CLASS = "theMainClassInsideThisPackageIs: \"Client\"";

    private static final String PACKAGE = "theSupervisedCodeUsesTheFollowingPackage: \"de.example\"";

    private static final String VERSION = "thisPolicyFileCompliesToThePolicyVersion: 1";

    private static final String READ_FLAG = "readAllFiles: true";

    private static final String PORT = "onThePort: 443";

    private static final String THREAD_COUNT = "createTheFollowingNumberOfThreads: 1";

    private static final String TIMEOUT = "timeout: 1000";

    private static final String TIMEOUTS_LIST = "    regardingTimeouts:\n      - timeout: 1000\n";

    private static String replaced(String target, String replacement) {
        assertThat(VALID_POLICY).contains(target);
        return VALID_POLICY.replace(target, replacement);
    }

    private static Arguments replacing(String target, String replacement) {
        return Arguments.of(target + " -> " + replacement, replaced(target, replacement));
    }

    @Test
    void readsTheTestClassesOfAValidPolicy() {
        SecurityPolicyYaml.ReadPolicy policy = SecurityPolicySchema.read(VALID_POLICY);

        assertThat(policy.valid()).isTrue();
        assertThat(policy.testClasses()).contains(List.of("de.example.SortingTest"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesAresAccepts")
    void acceptsWhatAresAccepts(String description, String policy) {
        assertThat(SecurityPolicySchema.read(policy).valid()).isTrue();
    }

    static Stream<Arguments> policiesAresAccepts() {
        return Stream.of(replacing(MAIN_CLASS, "theMainClassInsideThisPackageIs: null"), replacing(MAIN_CLASS, "theMainClassInsideThisPackageIs: ~"),
                replacing(MAIN_CLASS, "theMainClassInsideThisPackageIs:"), replacing("  " + MAIN_CLASS + "\n", ""),
                replacing(MAIN_CLASS, MAIN_CLASS + "\n  theFollowingTestBehaviorIsConfigured: { }"), replacing(PACKAGE, "theSupervisedCodeUsesTheFollowingPackage: yes"),
                replacing(READ_FLAG, "readAllFiles: True"), replacing(READ_FLAG, "readAllFiles: TRUE"), replacing(READ_FLAG, "readAllFiles: !!bool true"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: +1"), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 0x1"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 0b1"), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 01"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: !!int 1"), replacing(PORT, "onThePort: 0"), replacing(PORT, "onThePort: 65535"),
                replacing(PORT, "onThePort: 0xFFFF"), replacing(TIMEOUT, "timeout: 1_000"), replacing(TIMEOUT, "timeout: 9223372036854775807"),
                replacing(THREAD_COUNT, "createTheFollowingNumberOfThreads: 0"), replacing(THREAD_COUNT, "createTheFollowingNumberOfThreads: 2147483647"),
                replacing("\"org.apache.commons.lang3\"", "\"*\""), replacing("\"java.util.concurrent.ThreadLocalRandom\"", "\"Lambda-Expression\""),
                replacing("\"java.util.concurrent.ThreadLocalRandom\"", "\"<implicit-thread-op:Thread.sleep>\""), replacing("\"example.org\"", "\"2001:db8::1\""),
                replacing("\"example.org\"", "\"*\""), replacing("\"${PROJECT_ROOT}/data\"", "\"*\""), replacing("\"status\"", "\"--all\", \"-v\""),
                replacing(TIMEOUTS_LIST, "    regardingTimeouts: [ ]\n"), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: !<tag:yaml.org,2002:int,foo> 1"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: !!int " + "0".repeat(1000) + "1"),
                replacing(THREAD_COUNT, "createTheFollowingNumberOfThreads: !!int -_" + "0".repeat(999)), Arguments.of("explicit document start", "---\n" + VALID_POLICY),
                Arguments.of("explicit document end", VALID_POLICY + "...\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesAresRejects")
    void rejectsWhatAresRejects(String description, String policy) {
        assertThat(SecurityPolicySchema.read(policy).valid()).isFalse();
    }

    static Stream<Arguments> policiesAresRejects() {
        return Stream.of(replacing(VERSION + "\n", ""), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 2"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 1.0"), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 1e0"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: \"1\""), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: !!str 1"),
                replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: 4294967297"), replacing(VERSION, VERSION + "\nsomethingElse: 1"),
                replacing(VERSION, VERSION + "\n" + VERSION), replacing("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ", "JAVA_USING_ANT_ARCHUNIT_AND_ASPECTJ"),
                replacing(PACKAGE, "theSupervisedCodeUsesTheFollowingPackage: \"de.class\""), replacing(PACKAGE, "theSupervisedCodeUsesTheFollowingPackage: \"\""),
                replacing(PACKAGE, "theSupervisedCodeUsesTheFollowingPackage: 2020-01-01"), replacing(MAIN_CLASS, "theMainClassInsideThisPackageIs: \"a.B\""),
                replacing(MAIN_CLASS, "theMainClassInsideThisPackageIs: \"record\""), replacing(MAIN_CLASS, MAIN_CLASS + "\n  theFollowingTestBehaviorIsConfigured: null"),
                replacing(MAIN_CLASS, MAIN_CLASS + "\n  theFollowingTestBehaviorIsConfigured: { a: 1 }"), replacing(MAIN_CLASS, MAIN_CLASS + "\n  somethingElse: 1"),
                replacing("\"de.example.SortingTest\"", "\"de.example.class\""), replacing("\"de.example.SortingTest\"", "null"),
                replacing("\"${PROJECT_ROOT}/data\"", "\"a/../b\""), replacing("\"${PROJECT_ROOT}/data\"", "\"a/*\""), replacing("\"${PROJECT_ROOT}/data\"", "\"${UNKNOWN}/data\""),
                replacing(READ_FLAG, "readAllFiles: yes"), replacing(READ_FLAG, "readAllFiles: on"), replacing(READ_FLAG, "readAllFiles: \"true\""),
                replacing(READ_FLAG, "readAllFiles: !!bool yes"), replacing(READ_FLAG, "readAllFiles: null"), replacing("        " + READ_FLAG + "\n", ""),
                replacing("\"example.org\"", "\"256.1.1.1\""), replacing("\"example.org\"", "\"exa_mple\""), replacing(PORT, "onThePort: 65536"), replacing(PORT, "onThePort: -1"),
                replacing(PORT, "onThePort: 80.0"), replacing(PORT, "onThePort: 1:30"), replacing(PORT, "onThePort: \"80\""), replacing(PORT, "onThePort: !!float 80"),
                replacing("- \"ls\"", "- \"  \""), replacing("- \"ls\"", "- 1"), replacing("[ \"status\" ]", "[ \" \" ]"), replacing("[ \"status\" ]", "[ 1 ]"),
                replacing("[ \"status\" ]", "null"), replacing("\"git\"\n        withTheseArguments: [ \"status\" ]", "\"git\""),
                replacing(THREAD_COUNT, "createTheFollowingNumberOfThreads: -1"), replacing(THREAD_COUNT, "createTheFollowingNumberOfThreads: 2147483648"),
                replacing("\"java.util.concurrent.ThreadLocalRandom\"", "\"<implicit-thread-op:other>\""), replacing("\"org.apache.commons.lang3\"", "\"org.apache.*\""),
                replacing(TIMEOUT, "timeout: 0"), replacing(TIMEOUT, "timeout: 9223372036854775808"), replacing(TIMEOUT, "timeout: 1.5"), replacing(TIMEOUT, "timeout: 2020-01-01"),
                replacing(TIMEOUT, "timeout: .inf"), replacing(TIMEOUT, "timeout: !!binary MTAwMA=="), replacing(TIMEOUTS_LIST, ""),
                replacing(TIMEOUTS_LIST, "    regardingTimeouts: null\n"), replacing(TIMEOUTS_LIST, TIMEOUTS_LIST + "    somethingElse: [ ]\n"),
                replacing(TIMEOUTS_LIST, "    <<: { regardingTimeouts: [ ] }\n"), replacing(TIMEOUT, "timeout: &limit 1000\n      - timeout: *limit"),
                replacing(READ_FLAG, "readAllFiles: &flag true\n        extra: *flag"), replacing(MAIN_CLASS, "theMainClassInsideThisPackageIs: !!null \"\""),
                replacing("- \"ls\"", "- !<tag:yaml.org,2002:int,foo> 1"), replacing(VERSION, "thisPolicyFileCompliesToThePolicyVersion: !!int " + "0".repeat(1001) + "1"),
                replacing("\"${PROJECT_ROOT}/data\"", "\"" + "a".repeat(100_000) + "\""), replacing(THREAD_COUNT, "createTheFollowingNumberOfThreads: !!int -_" + "0".repeat(1000)),
                Arguments.of("two documents", VALID_POLICY + "---\n" + VALID_POLICY), Arguments.of("an empty second document", VALID_POLICY + "---\n"),
                Arguments.of("empty file", ""), Arguments.of("only a comment", "# nothing\n"), Arguments.of("a list", "- a\n"), Arguments.of("a scalar", "a\n"),
                Arguments.of("null", "null\n"), Arguments.of("broken YAML", "a: [unclosed"), Arguments.of("a key that is no scalar", "? [ a ]\n: 1\n"),
                Arguments.of("an alias as key", "&a x: 1\n*a : 2\n"), Arguments.of("a very deep document", "[".repeat(100_000) + "]".repeat(100_000)),
                Arguments.of("too large", VALID_POLICY + "#".repeat(SecurityPolicyYaml.MAX_POLICY_SIZE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policiesWithoutAPackage")
    void additionallyRequiresAPackage(String description, String policy) {
        assertThat(SecurityPolicySchema.read(policy).valid()).isFalse();
    }

    static Stream<Arguments> policiesWithoutAPackage() {
        return Stream.of(replacing("  " + PACKAGE + "\n", ""), replacing(PACKAGE, "theSupervisedCodeUsesTheFollowingPackage: null"),
                replacing(PACKAGE, "theSupervisedCodeUsesTheFollowingPackage:"));
    }
}
