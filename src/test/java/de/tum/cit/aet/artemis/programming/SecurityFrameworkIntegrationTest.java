package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Tests the REST endpoints of the Security Framework against real LocalVC repositories: who may read and change it, what each change does to the tests
 * repository, and which requests are rejected.
 */
class SecurityFrameworkIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "securityframeworkint";

    private static final String POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
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
        basePath = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/security-framework";

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

    private SecurityFrameworkConfigDTO getConfig() throws Exception {
        return request.get(basePath, HttpStatus.OK, SecurityFrameworkConfigDTO.class);
    }

    private SecurityFrameworkConfigDTO put(String action, String frameworkVersion, HttpStatus expectedStatus) throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (frameworkVersion != null) {
            params.add("frameworkVersion", frameworkVersion);
        }
        return request.putWithResponseBodyAndParams(basePath + "/" + action, null, SecurityFrameworkConfigDTO.class, expectedStatus, params);
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

    private void pushTestFiles(Map<String, String> files) throws Exception {
        testsRepository.workingCopy().pull().call();
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository, files, "Change the tests by hand");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsCanReadTheStateAndTheVersions() throws Exception {
        SecurityFrameworkConfigDTO config = getConfig();

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(config.frameworkVersion()).isEqualTo("2.2.1");
        assertThat(request.get("/api/programming/security-framework/versions", HttpStatus.OK, SecurityFrameworkVersionsDTO.class).selectableVersions()).contains("2.2.1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsCannotChangeTheSandbox() throws Exception {
        String headBefore = pushedHead();

        put("deactivate", null, HttpStatus.FORBIDDEN);
        put("activate", "2.2.1", HttpStatus.FORBIDDEN);
        put("framework-version", "2.2.1", HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void tutorsCannotReadTheState() throws Exception {
        request.get(basePath, HttpStatus.FORBIDDEN, SecurityFrameworkConfigDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentsCannotReadTheStateButTheVersions() throws Exception {
        request.get(basePath, HttpStatus.FORBIDDEN, SecurityFrameworkConfigDTO.class);
        // The versions are server configuration without a course to check a role in, so every signed-in user may read them.
        request.get("/api/programming/security-framework/versions", HttpStatus.OK, SecurityFrameworkVersionsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "USER")
    void instructorsOfAnotherCourseCannotReadOrChangeTheSandbox() throws Exception {
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor2", courseUtilService.addEmptyCourse());
        String headBefore = pushedHead();

        request.get(basePath, HttpStatus.FORBIDDEN, SecurityFrameworkConfigDTO.class);
        put("deactivate", null, HttpStatus.FORBIDDEN);
        put("activate", "2.2.1", HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void deactivatingAndActivatingChangesTheTestsRepositoryAndRunsThePushProcessing() throws Exception {
        String headBefore = pushedHead();

        SecurityFrameworkConfigDTO deactivated = put("deactivate", null, HttpStatus.OK);

        assertThat(deactivated.status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        String deactivationCommit = pushedHead();
        // Exactly one commit on top of the previous state.
        assertThat(parentOf(deactivationCommit)).isEqualTo(headBefore);
        Path repository = pulledTestsRepository();
        assertThat(repository.resolve("SecurityPolicy.yaml")).doesNotExist();
        assertThat(Files.readString(repository.resolve(SORTING_TEST_PATH))).contains(INACTIVE_ANNOTATION);
        assertThat(getConfig().status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        // The change is processed like any push to the tests repository: the solution participation gets a submission for it.
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), deactivationCommit)).isTrue();

        SecurityFrameworkConfigDTO activated = put("activate", "2.2.1", HttpStatus.OK);

        assertThat(activated.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        repository = pulledTestsRepository();
        assertThat(Files.readString(repository.resolve("SecurityPolicy.yaml"))).isEqualTo(POLICY);
        assertThat(Files.readString(repository.resolve(SORTING_TEST_PATH))).isEqualTo(TEST_CLASS);
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), pushedHead())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aMixedStateIsActiveAndActivatingKeepsTheExceptionAndAddsNewTestClasses() throws Exception {
        pushTestFiles(Map.of("test/de/example/ClassTest.java", TEST_CLASS.replace("SortingTest", "ClassTest").replace(ACTIVE_ANNOTATION, INACTIVE_ANNOTATION)));

        SecurityFrameworkConfigDTO mixed = getConfig();
        assertThat(mixed.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(mixed.disabledTestClasses()).containsExactly("de.example.ClassTest");

        put("deactivate", null, HttpStatus.OK);
        pushTestFiles(Map.of("test/de/example/NewTest.java", TEST_CLASS.replace("SortingTest", "NewTest").replace(ACTIVE_ANNOTATION, INACTIVE_ANNOTATION)));
        SecurityFrameworkConfigDTO activated = put("activate", "2.2.1", HttpStatus.OK);

        assertThat(activated.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(activated.disabledTestClasses()).containsExactly("de.example.ClassTest");
        Path repository = pulledTestsRepository();
        assertThat(Files.readString(repository.resolve("test/de/example/NewTest.java"))).contains(ACTIVE_ANNOTATION);
        assertThat(Files.readString(repository.resolve("SecurityPolicy.yaml"))).contains("- \"de.example.NewTest\"").contains("- \"de.example.SortingTest\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aMissingPolicyFileIsMisconfiguredAndActivatingRepairsIt() throws Exception {
        testsRepository.workingCopy().pull().call();
        Files.delete(testsRepository.workingCopyPath().resolve("SecurityPolicy.yaml"));
        testsRepository.workingCopy().rm().addFilepattern("SecurityPolicy.yaml").call();
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository, Map.of(), "Delete the policy by hand");

        SecurityFrameworkConfigDTO misconfigured = getConfig();
        assertThat(misconfigured.status()).isEqualTo(SecurityActivationStatus.MISCONFIGURED.name());
        assertThat(misconfigured.policyFiles()).singleElement().satisfies(policyFile -> {
            assertThat(policyFile.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policyFile.problem()).isEqualTo("MISSING");
        });

        assertThat(put("activate", "2.2.1", HttpStatus.OK).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(Files.readString(pulledTestsRepository().resolve("SecurityPolicy.yaml"))).isEqualTo(POLICY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void anUnsupportedRepositoryIsReportedButNeverChanged() throws Exception {
        pushTestFiles(Map.of(SORTING_TEST_PATH, TEST_CLASS.replace("@de.tum.cit.ase.ares.api.jupiter.Public\n", "")));
        String headBefore = pushedHead();

        SecurityFrameworkConfigDTO config = getConfig();
        assertThat(config.status()).isEqualTo(SecurityActivationStatus.UNSUPPORTED.name());
        assertThat(config.unsupportedReasons()).extracting(SecurityFrameworkConfigDTO.UnsupportedReasonDTO::type).contains("TEST_CLASS_WITHOUT_ARES");

        put("deactivate", null, HttpStatus.BAD_REQUEST);
        put("activate", "2.2.1", HttpStatus.BAD_REQUEST);
        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void invalidVersionsAndInactiveVersionChangesAreRejected() throws Exception {
        String headBefore = pushedHead();

        put("activate", "2.1.0", HttpStatus.BAD_REQUEST);
        put("framework-version", "9.9.9", HttpStatus.BAD_REQUEST);
        // The same version is no change, so nothing is committed.
        assertThat(put("framework-version", "2.2.1", HttpStatus.OK).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(pushedHead()).isEqualTo(headBefore);

        put("deactivate", null, HttpStatus.OK);
        put("framework-version", "2.2.1", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void nonJavaExercisesAreRejected() throws Exception {
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExerciseRepository.save(programmingExercise);
        String headBefore = pushedHead();

        put("deactivate", null, HttpStatus.BAD_REQUEST);
        put("activate", "2.2.1", HttpStatus.BAD_REQUEST);
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

        put("deactivate", null, HttpStatus.BAD_REQUEST);
        assertThat(pushedHead()).isEqualTo(headBefore);
        assertThat(Files.readString(testsRepository.workingCopyPath().resolve("SecurityPolicy.yaml"), StandardCharsets.UTF_8)).isEqualTo(POLICY);
    }
}
