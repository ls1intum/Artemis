package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.CommandPermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.FilePermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.HiddenTestsDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.NetworkPermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.OutputMirroringDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.PackagePermissionDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.PrivilegedExceptionsDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.StrictTimeoutDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.TestBehaviorDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO.ThreadPermissionDTO;

/**
 * Tests that {@link SecurityPolicyDocuments} reads policy files into documents only if rewriting them keeps everything Ares reads, and renders documents into
 * policy files that read back as the same document.
 */
class SecurityPolicyDocumentsTest {

    /** The policy of the Java Gradle template, with its comments. */
    private static final String TEMPLATE_POLICY = """
            # Ares 2 security policy for the tests of this repository, referenced by @Policy in the test classes.
            # Docs: https://ls1intum.github.io/Ares2/instructor/policy-reference

            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theMainClassInsideThisPackageIs: "Client"
              theFollowingClassesAreTestClasses:
                - "de.example.AttributeTest"
                - "de.example.SortingExampleBehaviorTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                # The sorting exercise uses ThreadLocalRandom to create random dates
                regardingThreadCreations:
                  - createTheFollowingNumberOfThreads: 1
                    ofThisClass: "java.util.concurrent.ThreadLocalRandom"
                regardingPackageImports:
                  - importTheFollowingPackage: "org.apache.commons.lang3"
                regardingTimeouts: [ ]
            """;

    private static SecurityPolicyDocumentDTO fullDocument() {
        return new SecurityPolicyDocumentDTO("SecurityPolicy.yaml", null, "JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ", "de.example", "Client",
                List.of("de.example.SortingTest", "de.example.NewBehaviorTest"),
                List.of(new FilePermissionDTO("data/input.txt", true, false, false, false, false), new FilePermissionDTO("logs/", false, true, true, false, false)),
                List.of(new NetworkPermissionDTO("localhost", 8080, true, true, true)), List.of(new CommandPermissionDTO("python3", List.of("scripts/check.py"))),
                List.of(new ThreadPermissionDTO("java.lang.Thread", 2)), List.of(new PackagePermissionDTO("org.apache.commons.lang3")), List.of(3000L), null);
    }

    @Test
    void readsTheTemplatePolicy() {
        SecurityPolicyDocuments.Parsed parsed = SecurityPolicyDocuments.parse("SecurityPolicy.yaml", TEMPLATE_POLICY, null);

        assertThat(parsed.reason()).isNull();
        SecurityPolicyDocumentDTO document = parsed.document();
        assertThat(document).isNotNull();
        assertThat(document.policyPath()).isEqualTo("SecurityPolicy.yaml");
        assertThat(document.languageConfiguration()).isEqualTo("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ");
        assertThat(document.supervisedPackage()).isEqualTo("de.example");
        assertThat(document.mainClass()).isEqualTo("Client");
        assertThat(document.testClasses()).containsExactly("de.example.AttributeTest", "de.example.SortingExampleBehaviorTest");
        assertThat(document.threads()).containsExactly(new ThreadPermissionDTO("java.util.concurrent.ThreadLocalRandom", 1));
        assertThat(document.packages()).containsExactly(new PackagePermissionDTO("org.apache.commons.lang3"));
        assertThat(document.files()).isEmpty();
        assertThat(document.testBehavior()).isNull();
    }

    @Test
    void renderingKeepsTheLeadingCommentsAndReadsBackAsTheSameDocument() {
        SecurityPolicyDocumentDTO document = SecurityPolicyDocuments.parse("SecurityPolicy.yaml", TEMPLATE_POLICY, null).document();

        String rendered = SecurityPolicyDocuments.render(document, TEMPLATE_POLICY);

        assertThat(rendered).startsWith("""
                # Ares 2 security policy for the tests of this repository, referenced by @Policy in the test classes.
                # Docs: https://ls1intum.github.io/Ares2/instructor/policy-reference

                thisPolicyFileCompliesToThePolicyVersion: 1
                """);
        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", rendered, null).document()).isEqualTo(document);
        // The rules of Ares 2.2.1 accept the rendered file.
        assertThat(SecurityPolicySchema.read(rendered).valid()).isTrue();
    }

    @Test
    void rendersEveryResourceAccessInTheFormatOfAres() {
        String rendered = SecurityPolicyDocuments.render(fullDocument(), null);

        assertThat(rendered).isEqualTo("""
                thisPolicyFileCompliesToThePolicyVersion: 1
                regardingTheSupervisedCode:
                  theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
                  theSupervisedCodeUsesTheFollowingPackage: "de.example"
                  theMainClassInsideThisPackageIs: "Client"
                  theFollowingClassesAreTestClasses:
                    - "de.example.SortingTest"
                    - "de.example.NewBehaviorTest"
                  theFollowingResourceAccessesArePermitted:
                    regardingFileSystemInteractions:
                      - onThisPathAndAllPathsBelow: "data/input.txt"
                        readAllFiles: true
                        overwriteAllFiles: false
                        createAllFiles: false
                        executeAllFiles: false
                        deleteAllFiles: false
                      - onThisPathAndAllPathsBelow: "logs/"
                        readAllFiles: false
                        overwriteAllFiles: true
                        createAllFiles: true
                        executeAllFiles: false
                        deleteAllFiles: false
                    regardingNetworkConnections:
                      - onTheHost: "localhost"
                        onThePort: 8080
                        openConnections: true
                        sendData: true
                        receiveData: true
                    regardingCommandExecutions:
                      - executeTheCommand: "python3"
                        withTheseArguments: ["scripts/check.py"]
                    regardingThreadCreations:
                      - createTheFollowingNumberOfThreads: 2
                        ofThisClass: "java.lang.Thread"
                    regardingPackageImports:
                      - importTheFollowingPackage: "org.apache.commons.lang3"
                    regardingTimeouts:
                      - timeout: 3000
                """);
        assertThat(SecurityPolicySchema.read(rendered).valid()).isTrue();
        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", rendered, null).document()).isEqualTo(fullDocument());
    }

    @Test
    void rendersTheTestBehaviorAndThePresetAndReadsThemBack() {
        SecurityPolicyDocumentDTO base = fullDocument();
        TestBehaviorDTO behavior = new TestBehaviorDTO(new StrictTimeoutDTO(2, "SECONDS", 500L, "MILLISECONDS"), new OutputMirroringDTO(true, 100_000_000L),
                new HiddenTestsDTO("2026-12-24 23:59 Europe/Berlin", "2026-12-25 23:59 Europe/Berlin"), new PrivilegedExceptionsDTO(true, "Test failed."));
        SecurityPolicyDocumentDTO document = new SecurityPolicyDocumentDTO(base.policyPath(), "MOCKITO", base.languageConfiguration(), base.supervisedPackage(), base.mainClass(),
                base.testClasses(), base.files(), base.network(), base.commands(), base.threads(), base.packages(), base.timeouts(), behavior);

        String rendered = SecurityPolicyDocuments.render(document, null);

        assertThat(rendered).contains("basedOnTheFollowingPreset: MOCKITO\n").contains("""
                  theFollowingTestBehaviorIsConfigured:
                    regardingStrictTimeouts:
                      theTimeoutIs: 2
                      theTimeUnitIs: SECONDS
                      theTerminationGraceIs: 500
                      theTerminationGraceUnitIs: MILLISECONDS
                    regardingOutputMirroring:
                      theOutputIsMirrored: true
                      theMaximumCharacterCountIs: 100000000
                    regardingHiddenTests:
                      theDeadlineIs: "2026-12-25 23:59 Europe/Berlin"
                      hiddenTestsAlwaysRunBefore: "2026-12-24 23:59 Europe/Berlin"
                      unlistedTestsAreHidden: true
                    regardingPrivilegedExceptions:
                      onlyPrivilegedExceptionsAreReported: true
                      theFailureMessageIs: "Test failed."
                """);
        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", rendered, null).document()).isEqualTo(document);
    }

    @Test
    void quotesStringsSoTheyReadBackExactly() {
        assertThat(SecurityPolicyDocuments.quote("a\"b\\c\nd\te\u0007")).isEqualTo("\"a\\\"b\\\\c\\nd\\te\\u0007\"");
        SecurityPolicyDocumentDTO base = fullDocument();
        SecurityPolicyDocumentDTO document = new SecurityPolicyDocumentDTO(base.policyPath(), null, base.languageConfiguration(), base.supervisedPackage(), null,
                base.testClasses(), List.of(new FilePermissionDTO("data/\"quoted\" #not a comment: yes", true, false, false, false, false)), List.of(), List.of(), List.of(),
                List.of(), List.of(), null);

        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", SecurityPolicyDocuments.render(document, null), null).document()).isEqualTo(document);
    }

    @Test
    void reportsTheLineOfAYamlError() {
        SecurityPolicyDocuments.Parsed parsed = SecurityPolicyDocuments.parse("SecurityPolicy.yaml", "thisPolicyFileCompliesToThePolicyVersion: 1\nregardingTheSupervisedCode: [\n",
                null);

        assertThat(parsed.document()).isNull();
        assertThat(parsed.reason()).isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.YAML_ERROR);
        assertThat(parsed.errorLine()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = { "theFollowingClassesAreTestClasses: [ ]\n  anUnknownField: true", "theFollowingClassesAreTestClasses:\n    - 42",
            "theFollowingClassesAreTestClasses: [ ]\n  theFollowingTestBehaviorIsConfigured:\n    regardingHiddenTests:\n"
                    + "      theDeadlineIs: \"2200-01-01 00:00 UTC\"\n      unlistedTestsAreHidden: false" })
    void keepsAPolicyReadOnlyIfRewritingItWouldChangeIt(String supervisedCodeTail) {
        String policy = """
                thisPolicyFileCompliesToThePolicyVersion: 1
                regardingTheSupervisedCode:
                  theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
                  theFollowingResourceAccessesArePermitted:
                    regardingFileSystemInteractions: [ ]
                    regardingNetworkConnections: [ ]
                    regardingCommandExecutions: [ ]
                    regardingThreadCreations: [ ]
                    regardingPackageImports: [ ]
                    regardingTimeouts: [ ]
                """ + "  " + supervisedCodeTail + "\n";

        SecurityPolicyDocuments.Parsed parsed = SecurityPolicyDocuments.parse("SecurityPolicy.yaml", policy, null);

        assertThat(parsed.document()).isNull();
        assertThat(parsed.reason()).isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.UNSUPPORTED_CONTENT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "files", "packages" })
    void keepsAPolicyReadOnlyIfItListsAResourceTwice(String kind) {
        String rendered = SecurityPolicyDocuments.render(fullDocument(), null);
        String twice = kind.equals("files") ? rendered.replace("onThisPathAndAllPathsBelow: \"logs/\"", "onThisPathAndAllPathsBelow: \"data/input.txt\"")
                : rendered.replace("      - importTheFollowingPackage: \"org.apache.commons.lang3\"\n",
                        "      - importTheFollowingPackage: \"org.apache.commons.lang3\"\n      - importTheFollowingPackage: \"org.apache.commons.lang3\"\n");
        assertThat(twice).isNotEqualTo(rendered);

        SecurityPolicyDocuments.Parsed parsed = SecurityPolicyDocuments.parse("SecurityPolicy.yaml", twice, null);

        assertThat(parsed.document()).isNull();
        assertThat(parsed.reason()).isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.DUPLICATE_RESOURCE);
        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", rendered, null).document()).isNotNull();
    }

    @Test
    void reportsWhyAFileCannotBeRead() {
        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", null, SecurityFrameworkState.PolicyFileProblem.MISSING).reason())
                .isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.MISSING);
        assertThat(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", null, SecurityFrameworkState.PolicyFileProblem.TOO_LARGE).reason())
                .isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.TOO_LARGE);
        assertThat(SecurityPolicyDocuments.parse("../SecurityPolicy.yaml", null, SecurityFrameworkState.PolicyFileProblem.INVALID_PATH).reason())
                .isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.INVALID_PATH);
    }

    @Test
    void acceptsAValidDocument() {
        assertThat(SecurityPolicyDocuments.validate(fullDocument())).isEmpty();
    }

    @Test
    void rejectsValuesNoAresVersionAccepts() {
        SecurityPolicyDocumentDTO document = new SecurityPolicyDocumentDTO("SecurityPolicy.yaml", "not a preset", " ", null, null, List.of("de.example.A", "de.example.A"),
                List.of(new FilePermissionDTO("logs/", false, false, false, false, false)), List.of(new NetworkPermissionDTO("local host", 70000, false, false, false)),
                List.of(new CommandPermissionDTO("", List.of())), List.of(new ThreadPermissionDTO("java.lang.Thread", -1)),
                List.of(new PackagePermissionDTO("org mockito"), new PackagePermissionDTO("org mockito")), List.of(0L),
                new TestBehaviorDTO(new StrictTimeoutDTO(0, "FORTNIGHTS", null, null), new OutputMirroringDTO(true, 0L), new HiddenTestsDTO(null, " "),
                        new PrivilegedExceptionsDTO(true, "two\nlines")));

        List<String> problems = SecurityPolicyDocuments.validate(document);

        assertThat(problems).hasSize(17).anySatisfy(problem -> assertThat(problem).contains("is listed twice"))
                .anySatisfy(problem -> assertThat(problem).contains("listed more than once")).anySatisfy(problem -> assertThat(problem).contains("logs/ grants no permission"))
                .anySatisfy(problem -> assertThat(problem).contains("70000")).anySatisfy(problem -> assertThat(problem).contains("must not be negative"))
                .anySatisfy(problem -> assertThat(problem).contains("strict timeout")).anySatisfy(problem -> assertThat(problem).contains("single line"));
    }

    @Test
    void acceptsAThreadCountOfZeroLikeTheSchemaOfAres() {
        SecurityPolicyDocumentDTO base = fullDocument();
        SecurityPolicyDocumentDTO document = new SecurityPolicyDocumentDTO(base.policyPath(), null, base.languageConfiguration(), base.supervisedPackage(), base.mainClass(),
                base.testClasses(), base.files(), base.network(), base.commands(), List.of(new ThreadPermissionDTO("java.lang.Thread", 0)), base.packages(), base.timeouts(), null);

        assertThat(SecurityPolicyDocuments.validate(document)).isEmpty();
    }

    @Test
    void reportsEmptyEntriesInsteadOfFailing() {
        SecurityPolicyDocumentDTO base = fullDocument();
        SecurityPolicyDocumentDTO emptyFile = new SecurityPolicyDocumentDTO(base.policyPath(), null, base.languageConfiguration(), base.supervisedPackage(), base.mainClass(),
                base.testClasses(), Arrays.asList(base.files().getFirst(), null), base.network(), base.commands(), base.threads(), base.packages(), base.timeouts(), null);
        SecurityPolicyDocumentDTO emptyArgument = new SecurityPolicyDocumentDTO(base.policyPath(), null, base.languageConfiguration(), base.supervisedPackage(), base.mainClass(),
                base.testClasses(), base.files(), base.network(), List.of(new CommandPermissionDTO("python3", Arrays.asList("check.py", null))), base.threads(), base.packages(),
                base.timeouts(), null);
        SecurityPolicyDocumentDTO missingUnit = new SecurityPolicyDocumentDTO(base.policyPath(), null, base.languageConfiguration(), base.supervisedPackage(), base.mainClass(),
                base.testClasses(), base.files(), base.network(), base.commands(), base.threads(), base.packages(), base.timeouts(),
                new TestBehaviorDTO(new StrictTimeoutDTO(5, null, null, null), null, null, null));

        assertThat(SecurityPolicyDocuments.validate(emptyFile)).containsExactly("A permission entry is empty.");
        assertThat(SecurityPolicyDocuments.validate(emptyArgument)).containsExactly("A command of the command permissions is not valid.");
        assertThat(SecurityPolicyDocuments.validate(missingUnit)).containsExactly("The strict timeout must be positive and use a valid time unit.");
    }

    @Test
    void reportsAMissingHostInsteadOfFailing() {
        SecurityPolicyDocumentDTO document = new SecurityPolicyDocumentDTO("SecurityPolicy.yaml", null, "JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ", null, null, List.of(), List.of(),
                List.of(new NetworkPermissionDTO(null, 80, true, false, false), new NetworkPermissionDTO(null, 80, true, false, false)), List.of(), List.of(), List.of(), List.of(),
                null);

        assertThat(SecurityPolicyDocuments.validate(document)).anySatisfy(problem -> assertThat(problem).contains("host"))
                .anySatisfy(problem -> assertThat(problem).contains("listed more than once"));
    }

    @Test
    void readsTheResourceAccessesOfAPreset() {
        Object section = SecurityPolicySchema.readTree("""
                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                regardingThreadCreations: [ ]
                regardingPackageImports:
                  - importTheFollowingPackage: "org.mockito"
                regardingTimeouts: [ ]
                """).orElseThrow();

        assertThat(SecurityPolicyDocuments.resourceAccesses(section))
                .hasValueSatisfying(document -> assertThat(document.packages()).containsExactly(new PackagePermissionDTO("org.mockito")));
        assertThat(SecurityPolicyDocuments.resourceAccesses(null)).isEmpty();
    }
}
