package de.tum.cit.aet.artemis.sandbox.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.sandbox.AbstractSandboxIntegrationTest;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionsDTO;

/**
 * Tests the REST endpoints of the sandbox against real LocalVC repositories: who may read the configuration and what it reports for the tests
 * repository.
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
}
