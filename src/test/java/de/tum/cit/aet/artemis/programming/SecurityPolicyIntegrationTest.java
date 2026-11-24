package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

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

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyChangeDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyCommitResultDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyEditorDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyImportDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyPreviewDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTemplateFilesDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;

/**
 * Tests the REST endpoints of the policy editor of the Security Framework against real LocalVC repositories: who may read, preview and commit policies, and
 * what a commit writes to the tests repository.
 */
class SecurityPolicyIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "securitypolicyint";

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

    private static final String TEST_CLASS = """
            package de.example;

            import org.junit.jupiter.api.Test;

            @de.tum.cit.ase.ares.api.jupiter.Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class SortingTest {

                @Test
                void testBubbleSort() {
                }
            }
            """;

    private static final SecurityPolicyDocumentDTO.FilePermissionDTO LOGS = new SecurityPolicyDocumentDTO.FilePermissionDTO("logs/", false, true, true, false, false);

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
                Map.of("src/de/example/Client.java", "package de.example;\n\nclass Client {\n}\n", "data/input.txt", "3 1 2\n"), "Set up the template repository");
        RepositoryExportTestUtil.writeFilesAndPush(
                RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, solutionRepositorySlug)),
                Map.of("src/de/example/Client.java", "package de.example;\n\nclass Client {\n}\n"), "Set up the solution repository");
        testsRepository = RepositoryExportTestUtil.trackRepository(localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug));
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository,
                Map.of("build.gradle", "ext {\n    aresVersion = '2.2.1'\n}\n", "SecurityPolicy.yaml", POLICY, "test/de/example/SortingTest.java", TEST_CLASS),
                "Set up the tests repository");
        dockerClientTestService.mockInspectImage(dockerClient);
    }

    @AfterEach
    void cleanUpRepositories() throws Exception {
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    private SecurityPolicyEditorDTO getEditor(HttpStatus expectedStatus) throws Exception {
        return request.get(basePath + "/policies", expectedStatus, SecurityPolicyEditorDTO.class);
    }

    private String pushedHead() throws Exception {
        return testsRepository.bareRepository().getRepository().resolve("HEAD").name();
    }

    private static SecurityPolicyChangeDTO addLogsPermission(SecurityPolicyEditorDTO editor) {
        SecurityPolicyDocumentDTO document = editor.policies().getFirst().document();
        SecurityPolicyDocumentDTO changed = new SecurityPolicyDocumentDTO(document.policyPath(), document.preset(), document.languageConfiguration(), document.supervisedPackage(),
                document.mainClass(), document.testClasses(), List.of(LOGS), document.network(), document.commands(), document.threads(), document.packages(), document.timeouts(),
                document.testBehavior());
        return new SecurityPolicyChangeDTO(editor.baseCommitHash(), "Allow writing logs", null,
                List.of(new SecurityPolicyChangeDTO.PolicyChangeDTO(document.policyPath(), changed, editor.policies().getFirst().assignedTests())), List.of());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void editorsCanReadAndPreviewButNotCommit() throws Exception {
        SecurityPolicyEditorDTO editor = getEditor(HttpStatus.OK);
        String headBefore = pushedHead();

        assertThat(editor.baseCommitHash()).isEqualTo(headBefore);
        assertThat(editor.policies()).singleElement().satisfies(policy -> {
            assertThat(policy.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policy.document().testClasses()).containsExactly("de.example.SortingTest");
            assertThat(policy.assignedTests()).containsExactly("de.example.SortingTest");
        });
        SecurityPolicyPreviewDTO preview = request.postWithResponseBody(basePath + "/policies/preview", addLogsPermission(editor), SecurityPolicyPreviewDTO.class, HttpStatus.OK);
        assertThat(preview.verdict()).isEqualTo("COMPATIBLE");
        assertThat(preview.files().getFirst().after()).contains("onThisPathAndAllPathsBelow: \"logs/\"");
        assertThat(request.get(basePath + "/test-structure", HttpStatus.OK, SecurityTestStructureDTO.class).testClasses()).singleElement().satisfies(testClass -> {
            assertThat(testClass.qualifiedName()).isEqualTo("de.example.SortingTest");
            assertThat(testClass.testCases()).containsExactly("testBubbleSort");
            assertThat(testClass.testClass()).isTrue();
            assertThat(testClass.switchedOff()).isFalse();
            // An empty list is left out of the JSON, so it arrives as null.
            assertThat(testClass.fixedCases()).isNullOrEmpty();
            assertThat(testClass.overloadedCases()).isNullOrEmpty();
        });
        assertThat(request.get(basePath + "/template-files", HttpStatus.OK, SecurityTemplateFilesDTO.class).paths()).contains("data/input.txt", "src/de/example/Client.java");

        assertThat(request.postWithResponseBody(basePath + "/policies/import", new SecurityPolicyImportDTO("SecurityPolicy.yaml", POLICY), SecurityPolicyEditorDTO.PolicyDTO.class,
                HttpStatus.OK).document()).isEqualTo(editor.policies().getFirst().document());

        request.putWithResponseBody(basePath + "/policies", addLogsPermission(editor), SecurityPolicyCommitResultDTO.class, HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void tutorsCannotReadOrChangeThePolicies() throws Exception {
        String headBefore = pushedHead();
        SecurityPolicyChangeDTO change = new SecurityPolicyChangeDTO(headBefore, null, null, List.of(), List.of());

        getEditor(HttpStatus.FORBIDDEN);
        request.postWithResponseBody(basePath + "/policies/preview", change, SecurityPolicyPreviewDTO.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody(basePath + "/policies", change, SecurityPolicyCommitResultDTO.class, HttpStatus.FORBIDDEN);
        request.get(basePath + "/test-structure", HttpStatus.FORBIDDEN, SecurityTestStructureDTO.class);
        request.get(basePath + "/template-files", HttpStatus.FORBIDDEN, SecurityTemplateFilesDTO.class);
        request.postWithResponseBody(basePath + "/policies/import", new SecurityPolicyImportDTO("SecurityPolicy.yaml", POLICY), SecurityPolicyEditorDTO.PolicyDTO.class,
                HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "USER")
    void instructorsOfAnotherCourseCannotReadOrChangeThePolicies() throws Exception {
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor2", courseUtilService.addEmptyCourse());
        String headBefore = pushedHead();

        getEditor(HttpStatus.FORBIDDEN);
        request.putWithResponseBody(basePath + "/policies", new SecurityPolicyChangeDTO(headBefore, null, null, List.of(), List.of()), SecurityPolicyCommitResultDTO.class,
                HttpStatus.FORBIDDEN);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void instructorsCommitAChangedPolicyAndThePushIsProcessed() throws Exception {
        SecurityPolicyEditorDTO editor = getEditor(HttpStatus.OK);
        String expected = request.postWithResponseBody(basePath + "/policies/preview", addLogsPermission(editor), SecurityPolicyPreviewDTO.class, HttpStatus.OK).files().getFirst()
                .after();

        SecurityPolicyCommitResultDTO result = request.putWithResponseBody(basePath + "/policies", addLogsPermission(editor), SecurityPolicyCommitResultDTO.class, HttpStatus.OK);

        String commit = pushedHead();
        try (RevWalk revWalk = new RevWalk(testsRepository.bareRepository().getRepository())) {
            assertThat(revWalk.parseCommit(testsRepository.bareRepository().getRepository().resolve(commit)).getParent(0).name()).isEqualTo(editor.baseCommitHash());
        }
        testsRepository.workingCopy().pull().call();
        Path repository = testsRepository.workingCopyPath();
        assertThat(Files.readString(repository.resolve("SecurityPolicy.yaml"))).isEqualTo(expected);
        assertThat(result.config().status()).isEqualTo(SecurityActivationStatus.CONFIGURED.name());
        assertThat(result.editor().baseCommitHash()).isEqualTo(commit);
        assertThat(result.editor().policies().getFirst().document().files()).containsExactly(LOGS);
        assertThat(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(solutionParticipation.getId(), commit)).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void aCommitBasedOnAnOutdatedPolicyIsRefusedWithAConflict() throws Exception {
        SecurityPolicyEditorDTO editor = getEditor(HttpStatus.OK);
        testsRepository.workingCopy().pull().call();
        RepositoryExportTestUtil.writeFilesAndPush(testsRepository,
                Map.of("SecurityPolicy.yaml", POLICY.replace("regardingTimeouts: [ ]", "regardingTimeouts:\n      - timeout: 1000")), "Change the policy by hand");
        String headBefore = pushedHead();

        request.putWithResponseBody(basePath + "/policies", addLogsPermission(editor), SecurityPolicyCommitResultDTO.class, HttpStatus.CONFLICT);

        assertThat(pushedHead()).isEqualTo(headBefore);
    }
}
