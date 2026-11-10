package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the compatibility manifest of decision B10 of Ares2 issue #386: the bundled manifest, how a policy is checked against a release, and which manifests
 * are refused.
 */
class AresCompatibilityManifestTest {

    private static final Path MANIFEST = Path.of("src", "main", "resources", "security-framework", "ares-compatibility.yml");

    private static final List<Path> TEMPLATE_POLICIES = List.of(
            Path.of("src", "main", "resources", "templates", "java", "test", "gradle", "projectTemplate", "SecurityPolicy.yaml"),
            Path.of("src", "main", "resources", "templates", "java", "test", "maven", "projectTemplate", "SecurityPolicy.yaml"));

    /** Uses every field Ares 2.2.1 accepts, including every key of every resource permission. */
    private static final String FULL_POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theMainClassInsideThisPackageIs: "Client"
              theFollowingClassesAreTestClasses: [ "de.example.SortingTest" ]
              theFollowingTestBehaviorIsConfigured: { }
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions:
                  - onThisPathAndAllPathsBelow: "data.txt"
                    readAllFiles: true
                    overwriteAllFiles: false
                    createAllFiles: false
                    executeAllFiles: false
                    deleteAllFiles: false
                regardingNetworkConnections:
                  - onTheHost: "localhost"
                    onThePort: 8080
                    openConnections: true
                    sendData: true
                    receiveData: true
                regardingCommandExecutions:
                  - executeTheCommand: "echo"
                    withTheseArguments: [ "hello" ]
                regardingThreadCreations:
                  - createTheFollowingNumberOfThreads: 1
                    ofThisClass: "java.lang.Thread"
                regardingPackageImports:
                  - importTheFollowingPackage: "java.util"
                regardingTimeouts:
                  - timeout: 10
            """;

    private static AresCompatibilityManifest manifest;

    @BeforeAll
    static void readTheBundledManifest() throws IOException {
        manifest = AresCompatibilityManifest.parse(Files.readString(MANIFEST, StandardCharsets.UTF_8));
    }

    @Test
    void theBundledManifestListsTheVersionTheSchemaWasVerifiedAgainst() {
        assertThat(manifest.verifiedVersions()).contains(SecurityPolicySchema.VERIFIED_ARES_VERSION);
        assertThat(manifest.release(SecurityPolicySchema.VERIFIED_ARES_VERSION)).hasValueSatisfying(release -> {
            assertThat(release.minimumFormatVersion()).isEqualTo(1);
            assertThat(release.maximumFormatVersion()).isEqualTo(1);
        });
    }

    @Test
    void theVerifiedVersionAcceptsEveryFieldItsSchemaAccepts() {
        assertThat(SecurityPolicySchema.read(FULL_POLICY).valid()).isTrue();
        assertThat(manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, FULL_POLICY)).isEqualTo(AresCompatibilityManifest.Check.COMPATIBLE);
    }

    @Test
    void theVerifiedVersionAcceptsThePoliciesOfTheTemplates() throws IOException {
        for (Path templatePolicy : TEMPLATE_POLICIES) {
            String policy = Files.readString(templatePolicy, StandardCharsets.UTF_8);
            assertThat(manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, policy).verdict()).as(templatePolicy.toString())
                    .isEqualTo(AresCompatibilityManifest.Verdict.COMPATIBLE);
        }
    }

    @Test
    void anUnknownFieldIsReportedOnceByItsOutermostPath() {
        String policy = FULL_POLICY.replace("theFollowingTestBehaviorIsConfigured: { }", """
                theFollowingTestBehaviorIsConfigured:
                    regardingStrictTimeouts:
                      theTimeoutIs: 2
                      theTimeUnitIs: SECONDS
                """.indent(2).stripTrailing().stripLeading());

        AresCompatibilityManifest.Check check = manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, policy);

        assertThat(check.verdict()).isEqualTo(AresCompatibilityManifest.Verdict.INCOMPATIBLE);
        assertThat(check.unsupportedFields()).containsExactly("regardingTheSupervisedCode.theFollowingTestBehaviorIsConfigured.regardingStrictTimeouts");
        assertThat(check.unsupportedFormatVersion()).isNull();
        // Ares 2.2.1 itself refuses the same policy.
        assertThat(SecurityPolicySchema.read(policy).valid()).isFalse();
    }

    @Test
    void unknownFieldsAtTheRootAndInListEntriesAreReportedWithTheirPath() {
        String policy = "basedOnTheFollowingPreset: SMOKE_TEST\n" + FULL_POLICY.replace("readAllFiles: true", "readAllFiles: true\n        followSymbolicLinks: true")
                .replace("onThePort: 8080", "onThePort: 8080\n        followSymbolicLinks: true");

        AresCompatibilityManifest.Check check = manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, policy);

        assertThat(check.verdict()).isEqualTo(AresCompatibilityManifest.Verdict.INCOMPATIBLE);
        assertThat(check.unsupportedFields()).containsExactly("basedOnTheFollowingPreset",
                "regardingTheSupervisedCode.theFollowingResourceAccessesArePermitted.regardingFileSystemInteractions[].followSymbolicLinks",
                "regardingTheSupervisedCode.theFollowingResourceAccessesArePermitted.regardingNetworkConnections[].followSymbolicLinks");
    }

    @Test
    void theSameUnknownFieldInSeveralListEntriesIsReportedOnce() {
        String policy = FULL_POLICY.replace("""
                    regardingPackageImports:
                      - importTheFollowingPackage: "java.util"
                """, """
                    regardingPackageImports:
                      - importTheFollowingPackage: "java.util"
                        transitively: true
                      - importTheFollowingPackage: "java.io"
                        transitively: true
                """);

        assertThat(manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, policy).unsupportedFields())
                .containsExactly("regardingTheSupervisedCode.theFollowingResourceAccessesArePermitted.regardingPackageImports[].transitively");
    }

    @Test
    void aFormatVersionOutsideTheRangeOfTheReleaseIsIncompatible() {
        String policy = FULL_POLICY.replace("thisPolicyFileCompliesToThePolicyVersion: 1", "thisPolicyFileCompliesToThePolicyVersion: 2");

        AresCompatibilityManifest.Check check = manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, policy);

        assertThat(check.verdict()).isEqualTo(AresCompatibilityManifest.Verdict.INCOMPATIBLE);
        assertThat(check.unsupportedFields()).isEmpty();
        assertThat(check.unsupportedFormatVersion()).isEqualTo(BigInteger.TWO);
    }

    @Test
    void aReleaseTheManifestDoesNotListIsNotVerified() {
        assertThat(manifest.check("9.9.9", FULL_POLICY)).isEqualTo(AresCompatibilityManifest.Check.UNVERIFIED);
        assertThat(AresCompatibilityManifest.empty().check(SecurityPolicySchema.VERIFIED_ARES_VERSION, FULL_POLICY)).isEqualTo(AresCompatibilityManifest.Check.UNVERIFIED);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "- just a list", "a: [unclosed", "--- a: 1\n--- b: 2\n" })
    void aFileThatCannotBeReadAsAPolicyIsNotVerified(String policy) {
        assertThat(manifest.check(SecurityPolicySchema.VERIFIED_ARES_VERSION, policy)).isEqualTo(AresCompatibilityManifest.Check.UNVERIFIED);
    }

    @Test
    void aManifestWithTwoEntriesForOneReleaseIsRefused() {
        String release = """
                  - aresVersion: "2.2.1"
                    policyFormatVersions: { minimum: 1, maximum: 1 }
                    fields: [ thisPolicyFileCompliesToThePolicyVersion ]
                """;
        assertThatIllegalArgumentException().isThrownBy(() -> AresCompatibilityManifest.parse("manifestVersion: 1\nreleases:\n" + release + release))
                .withMessageContaining("twice");
    }

    @ParameterizedTest
    @ValueSource(strings = { "releases: []", "manifestVersion: 2\nreleases: []", "manifestVersion: 1",
            "manifestVersion: 1\nreleases:\n  - policyFormatVersions: { minimum: 1, maximum: 1 }\n    fields: [ a ]",
            "manifestVersion: 1\nreleases:\n  - aresVersion: \"2.2.1\"\n    policyFormatVersions: { minimum: 2, maximum: 1 }\n    fields: [ a ]",
            "manifestVersion: 1\nreleases:\n  - aresVersion: \"2.2.1\"\n    policyFormatVersions: { minimum: 1, maximum: 1 }\n    fields: [ ]",
            "manifestVersion: 1\nreleases:\n  - aresVersion: \"2.2.1\"\n    policyFormatVersions: { minimum: 1, maximum: 1 }\n    fields: [ \".a\" ]", "a: [unclosed" })
    void anInvalidManifestIsRefused(String content) {
        assertThatIllegalArgumentException().isThrownBy(() -> AresCompatibilityManifest.parse(content));
    }
}
