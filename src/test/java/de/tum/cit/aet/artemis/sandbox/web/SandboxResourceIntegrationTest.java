package de.tum.cit.aet.artemis.sandbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.sandbox.AbstractSandboxIntegrationTest;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionsDTO;

/**
 * Tests the REST endpoints of the sandbox against real LocalVC repositories: who may read and change the configuration, what it reports for the tests
 * repository, what each change does to it, and which requests are rejected.
 */
class SandboxResourceIntegrationTest extends AbstractSandboxIntegrationTest {

    private static final String TEST_PREFIX = "sandboxresourceint";

    private static final String POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                regardingThreadCreations: [ ]
                regardingPackageImports: [ ]
                regardingTimeouts: [ ]
            """;

    private static final String ACTIVE_ANNOTATION = "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")";

    private static final String INACTIVE_ANNOTATION = "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\", activated = false)";

    private static final String TEST_CLASS = """
            package de.example;

            import org.junit.jupiter.api.Test;

            @de.tum.cit.ase.ares.api.jupiter.Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class SortingTest {

                @Test
                void sorts() {
                }
            }
            """;

    private static final String SORTING_TEST_PATH = "test/de/example/SortingTest.java";

    private LocalVCTestRepository testsRepository;

    private String basePath;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void initRepositories() throws Exception {
        userUtilService.addEditorToCourse(TEST_PREFIX + "editor1", course);
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        basePath = "/api/sandbox/programming-exercises/" + programmingExercise.getId();

        RepositoryExportTestUtil.writeFilesAndPush(
                RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, templateRepositorySlug)),
                Map.of("src/de/example/Client.java", "package de.example;\n\nclass Client {\n}\n"), "Set up the template repository");
        RepositoryExportTestUtil.writeFilesAndPush(
                RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, solutionRepositorySlug)),
                Map.of("src/de/example/Client.java", "package de.example;\n\nclass Client {\n}\n"), "Set up the solution repository");
        testsRepository = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug));
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository,
                Map.of("build.gradle", "ext {\n    aresVersion = '2.2.1'\n}\n", "SecurityPolicy.yaml", POLICY, SORTING_TEST_PATH, TEST_CLASS), "Set up the tests repository");
        dockerClientTestService.mockInspectImage(dockerClient);
    }

    @AfterEach
    void cleanUpRepositories() throws Exception {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    private SandboxConfigurationDTO getConfig() throws Exception {
        return request.get(basePath + "/configuration", HttpStatus.OK, SandboxConfigurationDTO.class);
    }

    private void pushTestFiles(Map<String, String> files) throws Exception {
        testsRepository.workingCopy().pull().call();
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository, files, "Change the tests by hand");
    }

    /**
     * Sends a change of the configuration: {@code framework-version} is a PUT with the version, every other change a POST, with the version if one is given.
     */
    private SandboxConfigurationDTO change(String change, String frameworkVersion, HttpStatus expectedStatus) throws Exception {
        String path = basePath + "/configuration/" + change;
        SandboxFrameworkVersionDTO body = frameworkVersion == null ? null : new SandboxFrameworkVersionDTO(frameworkVersion);
        if ("framework-version".equals(change)) {
            return request.putWithResponseBody(path, body, SandboxConfigurationDTO.class, expectedStatus);
        }
        return request.postWithResponseBody(path, body, SandboxConfigurationDTO.class, expectedStatus);
    }

    /**
     * @return the entries of {@code theFollowingClassesAreTestClasses} of a policy, as YAML reads them
     */
    @SuppressWarnings("unchecked")
    private static List<String> testClassesOf(String policy) {
        Map<String, Object> root = new Yaml().load(policy);
        Map<String, Object> supervisedCode = (Map<String, Object>) root.get("regardingTheSupervisedCode");
        return (List<String>) supervisedCode.get("theFollowingClassesAreTestClasses");
    }

    /**
     * Pulls the pushed state of the tests repository into the working copy.
     */
    private Path pulledTestsRepository() throws Exception {
        testsRepository.workingCopy().pull().call();
        return testsRepository.workingCopyPath();
    }

    private String pushedHead() throws Exception {
        return testsRepository.bareRepository().getRepository().resolve("HEAD").name();
    }

    private String parentOf(String commit) throws Exception {
        try (RevWalk revWalk = new RevWalk(testsRepository.bareRepository().getRepository())) {
            return revWalk.parseCommit(testsRepository.bareRepository().getRepository().resolve(commit)).getParent(0).name();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsCanReadTheStateAndTheVersions() throws Exception {
        SandboxConfigurationDTO config = getConfig();

        assertThat(config.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(config.frameworkVersion()).isEqualTo("2.2.1");
        assertThat(request.get(basePath + "/framework-versions", HttpStatus.OK, SandboxFrameworkVersionsDTO.class).selectableVersions()).contains("2.2.1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void tutorsCannotReadTheSandbox() throws Exception {
        request.get(basePath + "/configuration", HttpStatus.FORBIDDEN, SandboxConfigurationDTO.class);
        request.get(basePath + "/framework-versions", HttpStatus.FORBIDDEN, SandboxFrameworkVersionsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentsCannotReadTheConfigurationOrTheVersions() throws Exception {
        request.get(basePath + "/configuration", HttpStatus.FORBIDDEN, SandboxConfigurationDTO.class);
        request.get(basePath + "/framework-versions", HttpStatus.FORBIDDEN, SandboxFrameworkVersionsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "USER")
    void instructorsOfAnotherCourseCannotReadTheSandbox() throws Exception {
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor2", courseUtilService.addEmptyCourse());

        request.get(basePath + "/configuration", HttpStatus.FORBIDDEN, SandboxConfigurationDTO.class);
        request.get(basePath + "/framework-versions", HttpStatus.FORBIDDEN, SandboxFrameworkVersionsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aTestClassThatSwitchesItsPolicyOffIsReportedWhileAnotherKeepsTheSandboxActive() throws Exception {
        pushTestFiles(Map.of("test/de/example/ClassTest.java", TEST_CLASS.replace("SortingTest", "ClassTest").replace(ACTIVE_ANNOTATION, INACTIVE_ANNOTATION)));

        SandboxConfigurationDTO mixed = getConfig();

        assertThat(mixed.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(mixed.disabledTestClasses()).containsExactly("de.example.ClassTest");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aTestClassMissingFromThePolicyIsReported() throws Exception {
        pushTestFiles(Map.of("test/de/example/NewTest.java", TEST_CLASS.replace("SortingTest", "NewTest")));

        assertThat(getConfig().policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.missingTestClasses()).containsExactly("de.example.NewTest"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aMissingPolicyFileIsMisconfigured() throws Exception {
        testsRepository.workingCopy().pull().call();
        Files.delete(testsRepository.workingCopyPath().resolve("SecurityPolicy.yaml"));
        testsRepository.workingCopy().rm().addFilepattern("SecurityPolicy.yaml").call();
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository, Map.of(), "Delete the policy by hand");

        SandboxConfigurationDTO misconfigured = getConfig();

        assertThat(misconfigured.status()).isEqualTo(SandboxStatus.MISCONFIGURED);
        assertThat(misconfigured.policyFiles()).singleElement().satisfies(policyFile -> {
            assertThat(policyFile.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.MISSING);
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void anUnsupportedRepositoryIsReportedWithItsReasons() throws Exception {
        pushTestFiles(Map.of(SORTING_TEST_PATH, TEST_CLASS.replace("@de.tum.cit.ase.ares.api.jupiter.Public\n", "")));

        SandboxConfigurationDTO config = getConfig();

        assertThat(config.status()).isEqualTo(SandboxStatus.UNSUPPORTED);
        assertThat(config.unsupportedReasons()).extracting(SandboxConfigurationDTO.SandboxUnsupportedReasonDTO::type).contains(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aNonJavaExerciseIsInactive() throws Exception {
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExerciseRepository.save(programmingExercise);

        assertThat(getConfig().status()).isEqualTo(SandboxStatus.INACTIVE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsCannotChangeTheSandbox() throws Exception {
        String headBefore = pushedHead();

        change("deactivate", null, HttpStatus.FORBIDDEN);
        change("activate", "2.2.1", HttpStatus.FORBIDDEN);
        change("framework-version", "2.2.1", HttpStatus.FORBIDDEN);
        change("add-missing-test-classes", null, HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void tutorsCannotChangeTheSandbox() throws Exception {
        String headBefore = pushedHead();

        change("deactivate", null, HttpStatus.FORBIDDEN);
        change("activate", "2.2.1", HttpStatus.FORBIDDEN);
        change("framework-version", "2.2.1", HttpStatus.FORBIDDEN);
        change("add-missing-test-classes", null, HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "USER")
    void instructorsOfAnotherCourseCannotChangeTheSandbox() throws Exception {
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor2", courseUtilService.addEmptyCourse());
        String headBefore = pushedHead();

        change("deactivate", null, HttpStatus.FORBIDDEN);
        change("activate", "2.2.1", HttpStatus.FORBIDDEN);
        change("framework-version", "2.2.1", HttpStatus.FORBIDDEN);
        change("add-missing-test-classes", null, HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deactivatingAndActivatingChangesTheTestsRepositoryAndRunsThePushProcessing() throws Exception {
        String headBefore = pushedHead();

        SandboxConfigurationDTO deactivated = change("deactivate", null, HttpStatus.OK);

        assertThat(deactivated.status()).isEqualTo(SandboxStatus.INACTIVE);
        String deactivationCommit = pushedHead();
        // Exactly one commit on top of the previous state.
        assertThat(parentOf(deactivationCommit)).isEqualTo(headBefore);
        Path repository = pulledTestsRepository();
        assertThat(repository.resolve("SecurityPolicy.yaml")).doesNotExist();
        assertThat(Files.readString(repository.resolve(SORTING_TEST_PATH))).contains(INACTIVE_ANNOTATION);
        assertThat(getConfig().status()).isEqualTo(SandboxStatus.INACTIVE);
        // The change is processed like any push to the tests repository: the solution participation gets a submission for it.
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), deactivationCommit)).isTrue();

        SandboxConfigurationDTO activated = change("activate", "2.2.1", HttpStatus.OK);

        assertThat(activated.status()).isEqualTo(SandboxStatus.CONFIGURED);
        repository = pulledTestsRepository();
        assertThat(Files.readString(repository.resolve("SecurityPolicy.yaml"))).isEqualTo(POLICY);
        assertThat(Files.readString(repository.resolve(SORTING_TEST_PATH))).isEqualTo(TEST_CLASS);
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), pushedHead())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aMixedStateIsConfiguredAndActivatingKeepsTheExceptionAndAddsNewTestClasses() throws Exception {
        pushTestFiles(Map.of("test/de/example/ClassTest.java", TEST_CLASS.replace("SortingTest", "ClassTest").replace(ACTIVE_ANNOTATION, INACTIVE_ANNOTATION)));

        SandboxConfigurationDTO mixed = getConfig();
        assertThat(mixed.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(mixed.disabledTestClasses()).containsExactly("de.example.ClassTest");

        change("deactivate", null, HttpStatus.OK);
        pushTestFiles(Map.of("test/de/example/NewTest.java", TEST_CLASS.replace("SortingTest", "NewTest").replace(ACTIVE_ANNOTATION, INACTIVE_ANNOTATION)));
        SandboxConfigurationDTO activated = change("activate", "2.2.1", HttpStatus.OK);

        assertThat(activated.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(activated.disabledTestClasses()).containsExactly("de.example.ClassTest");
        Path repository = pulledTestsRepository();
        assertThat(Files.readString(repository.resolve("test/de/example/NewTest.java"))).contains(ACTIVE_ANNOTATION);
        assertThat(testClassesOf(Files.readString(repository.resolve("SecurityPolicy.yaml")))).contains("de.example.SortingTest", "de.example.NewTest");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void instructorsCanAddTheDiscoveredTestClassesToThePolicy() throws Exception {
        pushTestFiles(Map.of("test/de/example/NewTest.java", TEST_CLASS.replace("SortingTest", "NewTest")));
        assertThat(getConfig().policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.missingTestClasses()).containsExactly("de.example.NewTest"));

        SandboxConfigurationDTO config = change("add-missing-test-classes", null, HttpStatus.OK);

        // The pushed policy still reads as a policy, and its list holds both classes.
        assertThat(config.policyFiles()).singleElement().satisfies(policyFile -> {
            assertThat(policyFile.problem()).isNull();
            assertThat(policyFile.missingTestClasses()).isNullOrEmpty();
        });
        assertThat(testClassesOf(Files.readString(pulledTestsRepository().resolve("SecurityPolicy.yaml")))).containsExactly("de.example.SortingTest", "de.example.NewTest");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aMissingPolicyFileIsMisconfiguredAndActivatingRepairsIt() throws Exception {
        testsRepository.workingCopy().pull().call();
        Files.delete(testsRepository.workingCopyPath().resolve("SecurityPolicy.yaml"));
        testsRepository.workingCopy().rm().addFilepattern("SecurityPolicy.yaml").call();
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository, Map.of(), "Delete the policy by hand");

        SandboxConfigurationDTO misconfigured = getConfig();
        assertThat(misconfigured.status()).isEqualTo(SandboxStatus.MISCONFIGURED);
        assertThat(misconfigured.policyFiles()).singleElement().satisfies(policyFile -> {
            assertThat(policyFile.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.MISSING);
        });

        assertThat(change("activate", "2.2.1", HttpStatus.OK).status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(Files.readString(pulledTestsRepository().resolve("SecurityPolicy.yaml"))).isEqualTo(POLICY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void anUnsupportedRepositoryIsReportedButNeverChanged() throws Exception {
        pushTestFiles(Map.of(SORTING_TEST_PATH, TEST_CLASS.replace("@de.tum.cit.ase.ares.api.jupiter.Public\n", "")));
        String headBefore = pushedHead();

        SandboxConfigurationDTO config = getConfig();
        assertThat(config.status()).isEqualTo(SandboxStatus.UNSUPPORTED);
        assertThat(config.unsupportedReasons()).extracting(SandboxConfigurationDTO.SandboxUnsupportedReasonDTO::type).contains(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES);

        change("deactivate", null, HttpStatus.BAD_REQUEST);
        change("activate", "2.2.1", HttpStatus.BAD_REQUEST);
        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void invalidVersionsAndInactiveVersionChangesAreRejected() throws Exception {
        String headBefore = pushedHead();

        change("activate", "2.1.0", HttpStatus.BAD_REQUEST);
        change("framework-version", "9.9.9", HttpStatus.BAD_REQUEST);
        // The same version is no change, so nothing is committed.
        assertThat(change("framework-version", "2.2.1", HttpStatus.OK).status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(pushedHead()).isEqualTo(headBefore);

        change("deactivate", null, HttpStatus.OK);
        request.putAndExpectError(basePath + "/configuration/framework-version", new SandboxFrameworkVersionDTO("2.2.1"), HttpStatus.BAD_REQUEST, "sandboxNotActive");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void nonJavaExercisesAreRejected() throws Exception {
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExerciseRepository.save(programmingExercise);
        String headBefore = pushedHead();

        change("deactivate", null, HttpStatus.BAD_REQUEST);
        change("activate", "2.2.1", HttpStatus.BAD_REQUEST);
        change("framework-version", "2.2.1", HttpStatus.BAD_REQUEST);
        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aRepositoryWithSymbolicLinksIsNeverChanged() throws Exception {
        testsRepository.workingCopy().pull().call();
        Path link = testsRepository.workingCopyPath().resolve("linked.yaml");
        Files.createSymbolicLink(link, testsRepository.workingCopyPath().resolve("SecurityPolicy.yaml"));
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository, Map.of(), "Add a symbolic link");
        String headBefore = pushedHead();

        request.postAndExpectError(basePath + "/configuration/deactivate", null, HttpStatus.BAD_REQUEST, "sandboxSymbolicLinks");
        assertThat(pushedHead()).isEqualTo(headBefore);
        assertThat(Files.readString(testsRepository.workingCopyPath().resolve("SecurityPolicy.yaml"), StandardCharsets.UTF_8)).isEqualTo(POLICY);
    }
}
