package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCPushProcessingService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyChangeDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyCommitResultDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyDocumentDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyEditorDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyImportDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityPolicyPreviewDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTemplateFilesDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Tests {@link SecurityPolicyEditorService} against real git repositories, set up like in {@link SecurityFrameworkServiceTest}: a bare tests repository and
 * a bare template repository stand in for LocalVC, and only the lookups of {@link GitService} and {@link BareGitRepositoryService} are replaced. The
 * assertions read the pushed state, which is exactly what the next build runs with.
 */
class SecurityPolicyEditorServiceTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    private static final String POLICY = """
            # Ares 2 security policy for the tests of this repository.
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
                - "de.example.StressTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                regardingThreadCreations:
                  - createTheFollowingNumberOfThreads: 1
                    ofThisClass: "java.util.concurrent.ThreadLocalRandom"
                regardingPackageImports: [ ]
                regardingTimeouts: [ ]
            """;

    private static final String SORTING_TEST = """
            package de.example;

            import org.junit.jupiter.api.Test;

            @de.tum.cit.ase.ares.api.jupiter.Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class SortingTest {

                @Test
                void testBubbleSort() {
                }

                @Test
                void testMergeSort() {
                }
            }
            """;

    private static final String STRESS_TEST = """
            package de.example;

            import org.junit.jupiter.api.Test;

            @de.tum.cit.ase.ares.api.jupiter.Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class StressTest {

                @Test
                void testManyElements() {
                }
            }
            """;

    private static final String SORTING_TEST_PATH = "test/de/example/SortingTest.java";

    private static final String STRESS_TEST_PATH = "test/de/example/StressTest.java";

    @TempDir
    private Path root;

    private Path testsRemote;

    private Path templateRemote;

    private final AtomicInteger directoryCounter = new AtomicInteger();

    /** Runs once after the next checkout, e.g. another instructor's change that is pushed while this change is being prepared. */
    private Runnable afterNextCheckout;

    private LocalVCPushProcessingService localVCPushProcessingService;

    private BareGitRepositoryService bareGitRepositoryService;

    private ResourceLoaderService resourceLoaderService;

    private SecurityPolicyEditorService editorService;

    private SecurityFrameworkService securityFrameworkService;

    private final LocalVCRepositoryUri testsUri = mock(LocalVCRepositoryUri.class);

    private final LocalVCRepositoryUri templateUri = mock(LocalVCRepositoryUri.class);

    private final User user = new User();

    @BeforeEach
    void setUp() throws Exception {
        testsRemote = root.resolve("tests.git");
        templateRemote = root.resolve("template.git");
        createRemote(testsRemote, Map.of("build.gradle", "ext {\n    aresVersion = '2.2.1'\n}\n", SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, POLICY, SORTING_TEST_PATH,
                SORTING_TEST, STRESS_TEST_PATH, STRESS_TEST));
        createRemote(templateRemote, Map.of("src/de/example/Client.java", "package de.example;\n\nclass Client {\n}\n", "data/input.txt", "3 1 2\n", "scripts/check.py",
                "print('ok')\n", "build.gradle", "plugins {}\n"));

        GitService gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepositoryOnBranch(any(LocalVCRepositoryUri.class), any(Path.class), anyString())).thenAnswer(call -> {
            Path localPath = call.getArgument(1, Path.class);
            Git.cloneRepository().setURI(testsRemote.toUri().toString()).setDirectory(localPath.toFile()).setBranch(call.getArgument(2, String.class)).call().close();
            Runnable hook = afterNextCheckout;
            afterNextCheckout = null;
            if (hook != null) {
                hook.run();
            }
            return new Repository(new FileRepositoryBuilder().setGitDir(localPath.resolve(".git").toFile()).setWorkTree(localPath.toFile()).setup(), localPath,
                    mock(LocalVCRepositoryUri.class));
        });
        doAnswer(call -> {
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                Status status = git.status().call();
                git.add().setUpdate(true).addFilepattern(".").call();
                git.add().addFilepattern(".").call();
                return !status.isClean();
            }
        }).when(gitService).stageAllChanges(any(Repository.class));
        when(gitService.getLocalHeadHash(any(Repository.class))).thenAnswer(call -> call.getArgument(0, Repository.class).resolve("HEAD").name());
        when(gitService.commitStagedChanges(any(Repository.class), anyString(), any())).thenAnswer(call -> {
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                return GitService.commit(git).setMessage(call.getArgument(1, String.class)).setAuthor("Instructor", "instructor@example.org").call().getName();
            }
        });
        doAnswer(call -> {
            String remoteRef = "refs/heads/" + call.getArgument(2, String.class);
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                for (PushResult result : git.push().setRefSpecs(new RefSpec(call.getArgument(1, String.class) + ":" + remoteRef))
                        .setRefLeaseSpecs(new RefLeaseSpec(remoteRef, call.getArgument(3, String.class))).call()) {
                    for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                        if (update.getStatus() != RemoteRefUpdate.Status.OK && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                            throw new TransportException("Push rejected: " + update.getStatus());
                        }
                    }
                }
            }
            return null;
        }).when(gitService).pushCommitWithLease(any(Repository.class), anyString(), anyString(), anyString());

        bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(eq(testsUri), anyString(), anyBoolean())).thenAnswer(_ -> bareRepository(testsRemote));
        when(bareGitRepositoryService.getBareRepository(eq(templateUri), anyString(), anyBoolean())).thenAnswer(_ -> bareRepository(templateRemote));

        TempFileUtilService tempFileUtilService = mock(TempFileUtilService.class);
        when(tempFileUtilService.createTempDirectory(anyString())).thenAnswer(call -> newDirectory(call.getArgument(0, String.class)));
        resourceLoaderService = mock(ResourceLoaderService.class);
        when(resourceLoaderService.getResource(any(Path.class))).thenAnswer(call -> new FileSystemResource(MAIN_RESOURCES.resolve(call.getArgument(0, Path.class))));
        localVCPushProcessingService = mock(LocalVCPushProcessingService.class);

        ProgrammingExerciseBuildConfigRepository buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(anyLong())).thenReturn(new ProgrammingExerciseBuildConfig());
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(5L);
        SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        when(solutionParticipationRepository.findByProgrammingExerciseId(anyLong())).thenReturn(Optional.of(solutionParticipation));
        securityFrameworkService = new SecurityFrameworkService(gitService, bareGitRepositoryService, tempFileUtilService, buildConfigRepository, resourceLoaderService,
                Optional.of(localVCPushProcessingService), solutionParticipationRepository, mock(ProgrammingSubmissionTestRepository.class), new LocalDataProviderService(), "main",
                new String[] { "2.2.1" });
        editorService = new SecurityPolicyEditorService(securityFrameworkService, bareGitRepositoryService, resourceLoaderService, JsonMapper.builder().build());
    }

    private static Repository bareRepository(Path remote) throws IOException {
        return new Repository(new FileRepositoryBuilder().setGitDir(remote.toFile()).setBare().setup(), remote, mock(LocalVCRepositoryUri.class));
    }

    /**
     * Creates a new, uniquely named directory below the test's temporary folder (the architecture tests forbid {@code Files.createTempDirectory}).
     */
    private Path newDirectory(String prefix) throws IOException {
        return Files.createDirectories(root.resolve(prefix + directoryCounter.incrementAndGet()));
    }

    private void createRemote(Path remote, Map<String, String> files) throws Exception {
        Path seed = newDirectory("seed-");
        for (Map.Entry<String, String> file : files.entrySet()) {
            FileUtils.writeStringToFile(seed.resolve(file.getKey()).toFile(), file.getValue(), StandardCharsets.UTF_8);
        }
        try (Git git = Git.init().setDirectory(seed.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Set up the repository").setAuthor("Artemis", "artemis@example.org").call();
        }
        Git.cloneRepository().setURI(seed.toUri().toString()).setDirectory(remote.toFile()).setBare(true).call().close();
    }

    /** Pushes a change to the tests repository as someone else, e.g. through the online editor. */
    private void pushToTests(String path, String content) {
        try {
            Path checkout = newDirectory("other-");
            try (Git git = Git.cloneRepository().setURI(testsRemote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
                FileUtils.writeStringToFile(checkout.resolve(path).toFile(), content, StandardCharsets.UTF_8);
                git.add().addFilepattern(".").call();
                GitService.commit(git).setMessage("Change " + path).setAuthor("Other Instructor", "other@example.org").call();
                git.push().call();
            }
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ProgrammingExercise exercise() {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getProgrammingLanguage()).thenReturn(ProgrammingLanguage.JAVA);
        when(exercise.getPackageName()).thenReturn("de.example");
        when(exercise.getVcsTestRepositoryUri()).thenReturn(testsUri);
        when(exercise.getVcsTemplateRepositoryUri()).thenReturn(templateUri);
        return exercise;
    }

    private Optional<String> readPushed(String path) throws IOException {
        try (var repository = new FileRepositoryBuilder().setGitDir(testsRemote.toFile()).setBare().build(); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve("refs/heads/main");
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, revWalk.parseCommit(head).getTree())) {
                return treeWalk == null ? Optional.empty() : Optional.of(new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private String pushedHead() throws IOException {
        try (var repository = new FileRepositoryBuilder().setGitDir(testsRemote.toFile()).setBare().build()) {
            return repository.resolve("refs/heads/main").name();
        }
    }

    private SecurityPolicyDocumentDTO loadedPolicy(SecurityPolicyEditorDTO editor) {
        return editor.policies().getFirst().document();
    }

    private static SecurityPolicyDocumentDTO withFiles(SecurityPolicyDocumentDTO document, List<SecurityPolicyDocumentDTO.FilePermissionDTO> files) {
        return new SecurityPolicyDocumentDTO(document.policyPath(), document.preset(), document.languageConfiguration(), document.supervisedPackage(), document.mainClass(),
                document.testClasses(), files, document.network(), document.commands(), document.threads(), document.packages(), document.timeouts(), document.testBehavior());
    }

    private static SecurityPolicyDocumentDTO withTestClasses(SecurityPolicyDocumentDTO document, String policyPath, List<String> testClasses) {
        return new SecurityPolicyDocumentDTO(policyPath, document.preset(), document.languageConfiguration(), document.supervisedPackage(), document.mainClass(), testClasses,
                document.files(), document.network(), document.commands(), document.threads(), document.packages(), document.timeouts(), document.testBehavior());
    }

    private static SecurityPolicyDocumentDTO withBehavior(SecurityPolicyDocumentDTO document, SecurityPolicyDocumentDTO.TestBehaviorDTO behavior) {
        return new SecurityPolicyDocumentDTO(document.policyPath(), document.preset(), document.languageConfiguration(), document.supervisedPackage(), document.mainClass(),
                document.testClasses(), document.files(), document.network(), document.commands(), document.threads(), document.packages(), document.timeouts(), behavior);
    }

    private static SecurityPolicyChangeDTO.PolicyChangeDTO edited(SecurityPolicyDocumentDTO document, List<String> assignedTests) {
        return new SecurityPolicyChangeDTO.PolicyChangeDTO(document.policyPath(), document, assignedTests);
    }

    private static SecurityPolicyChangeDTO.PolicyChangeDTO added(SecurityPolicyDocumentDTO document, List<String> assignedTests) {
        return new SecurityPolicyChangeDTO.PolicyChangeDTO(null, document, assignedTests);
    }

    private static SecurityPolicyChangeDTO change(SecurityPolicyEditorDTO editor, SecurityPolicyChangeDTO.PolicyChangeDTO... policies) {
        return new SecurityPolicyChangeDTO(editor.baseCommitHash(), null, null, List.of(policies), List.of());
    }

    private static final List<String> BOTH_CLASSES = List.of("de.example.SortingTest", "de.example.StressTest");

    private static final SecurityPolicyDocumentDTO.FilePermissionDTO LOGS = new SecurityPolicyDocumentDTO.FilePermissionDTO("logs/", false, true, true, false, false);

    @Test
    void loadsThePoliciesWithTheCommitTheyWereReadFrom() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());

        assertThat(editor.baseCommitHash()).isEqualTo(pushedHead());
        assertThat(editor.lastCommit()).isNotNull();
        assertThat(editor.lastCommit().hash()).isEqualTo(pushedHead());
        assertThat(editor.lastCommit().authorName()).isEqualTo("Artemis");
        assertThat(editor.frameworkVersion()).isEqualTo("2.2.1");
        assertThat(editor.presets()).isEmpty();
        assertThat(editor.policies()).singleElement().satisfies(policy -> {
            assertThat(policy.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policy.content()).isEqualTo(POLICY);
            assertThat(policy.readOnlyReason()).isNull();
            assertThat(policy.assignedTests()).containsExactly("de.example.SortingTest", "de.example.StressTest");
            assertThat(policy.document().testClasses()).containsExactly("de.example.SortingTest", "de.example.StressTest");
            assertThat(policy.document().threads()).containsExactly(new SecurityPolicyDocumentDTO.ThreadPermissionDTO("java.util.concurrent.ThreadLocalRandom", 1));
        });
    }

    @Test
    void showsAPolicyReadOnlyIfTheEditorCannotKeepItsContent() throws Exception {
        pushToTests(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME,
                POLICY.replace("  theFollowingResourceAccessesArePermitted:", "  aFieldAresDoesNotKnow: 1\n  theFollowingResourceAccessesArePermitted:"));

        SecurityPolicyEditorDTO.PolicyDTO policy = editorService.getEditor(exercise()).policies().getFirst();

        assertThat(policy.document()).isNull();
        assertThat(policy.readOnlyReason()).isEqualTo(SecurityPolicyDocuments.ReadOnlyReason.UNSUPPORTED_CONTENT.name());
        assertThat(policy.content()).contains("aFieldAresDoesNotKnow");
    }

    @Test
    void previewsAChangeWithoutCommittingIt() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));

        SecurityPolicyPreviewDTO preview = editorService.preview(exercise(), change(editor, edited(changed, BOTH_CLASSES)));

        assertThat(preview.verdict()).isEqualTo("COMPATIBLE");
        assertThat(preview.frameworkVersion()).isEqualTo("2.2.1");
        assertThat(preview.problems()).isEmpty();
        assertThat(preview.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("SecurityPolicy.yaml");
            assertThat(file.previousPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(file.before()).isEqualTo(POLICY);
            assertThat(file.after()).startsWith("# Ares 2 security policy for the tests of this repository.\n").contains("onThisPathAndAllPathsBelow: \"logs/\"");
        });
        assertThat(pushedHead()).isEqualTo(editor.baseCommitHash());
    }

    @Test
    void previewReportsWhatThePinnedVersionDoesNotAccept() {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO withTimeout = withBehavior(loadedPolicy(editor),
                new SecurityPolicyDocumentDTO.TestBehaviorDTO(new SecurityPolicyDocumentDTO.StrictTimeoutDTO(2, "SECONDS", null, null), null, null, null));
        SecurityPolicyDocumentDTO withTestCase = withTestClasses(loadedPolicy(editor), "SecurityPolicy.yaml", List.of("de.example.SortingTest#testBubbleSort"));

        SecurityPolicyPreviewDTO timeoutPreview = editorService.preview(exercise(), change(editor, edited(withTimeout, BOTH_CLASSES)));
        SecurityPolicyPreviewDTO testCasePreview = editorService.preview(exercise(), change(editor, edited(withTestCase, BOTH_CLASSES)));

        assertThat(timeoutPreview.verdict()).isEqualTo("INCOMPATIBLE");
        assertThat(timeoutPreview.problems()).singleElement().asString().startsWith("SecurityPolicy.yaml: ").contains("regardingStrictTimeouts");
        assertThat(testCasePreview.verdict()).isEqualTo("INCOMPATIBLE");
        assertThat(testCasePreview.problems()).singleElement().asString().contains("single test cases (de.example.SortingTest#testBubbleSort)");
    }

    @Test
    void commitsAChangedPolicyInOneCommitAndStartsTheBuilds() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        String expected = editorService.preview(exercise(), change(editor, edited(changed, BOTH_CLASSES))).files().getFirst().after();

        SecurityPolicyCommitResultDTO result = editorService.commit(exercise(),
                new SecurityPolicyChangeDTO(editor.baseCommitHash(), "Allow writing logs", null, List.of(edited(changed, BOTH_CLASSES)), List.of()), user);

        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains(expected);
        assertThat(result.config().status()).isEqualTo("CONFIGURED");
        assertThat(result.editor().baseCommitHash()).isEqualTo(pushedHead()).isNotEqualTo(editor.baseCommitHash());
        assertThat(result.editor().lastCommit().authorName()).isEqualTo("Instructor");
        assertThat(loadedPolicy(result.editor()).files()).containsExactly(LOGS);
        assertThat(readPushed(SORTING_TEST_PATH)).contains(SORTING_TEST);
        try (var repository = new FileRepositoryBuilder().setGitDir(testsRemote.toFile()).setBare().build(); RevWalk revWalk = new RevWalk(repository)) {
            assertThat(revWalk.parseCommit(repository.resolve("refs/heads/main")).getFullMessage()).startsWith("Allow writing logs\n\n")
                    .contains("Security-Framework-Action: policy");
        }
        verify(localVCPushProcessingService).processNewPush(eq(pushedHead()), any(), any(), any(), any());
    }

    @Test
    void aNewPolicyIsUsedByTheTestClassesAssignedToIt() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO existing = withTestClasses(loadedPolicy(editor), "SecurityPolicy.yaml", List.of("de.example.SortingTest"));
        SecurityPolicyDocumentDTO stress = withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of("de.example.StressTest"));

        SecurityPolicyCommitResultDTO result = editorService.commit(exercise(),
                change(editor, edited(existing, List.of("de.example.SortingTest")), added(stress, List.of("de.example.StressTest"))), user);

        assertThat(readPushed("StressTests.yaml")).hasValueSatisfying(content -> assertThat(content).contains("- \"de.example.StressTest\"").doesNotContain("SortingTest"));
        assertThat(readPushed(STRESS_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy(value = \"StressTests.yaml\")"));
        assertThat(readPushed(SORTING_TEST_PATH)).contains(SORTING_TEST);
        assertThat(result.editor().policies()).extracting(SecurityPolicyEditorDTO.PolicyDTO::policyPath).containsExactly("SecurityPolicy.yaml", "StressTests.yaml");
        assertThat(result.editor().policies().getLast().assignedTests()).containsExactly("de.example.StressTest");
        assertThat(result.config().status()).isEqualTo("CONFIGURED");
    }

    @Test
    void deactivatingDeletesEveryPolicyFileAndActivatingRestoresThem() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO existing = withTestClasses(loadedPolicy(editor), "SecurityPolicy.yaml", List.of("de.example.SortingTest"));
        SecurityPolicyDocumentDTO stress = withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of("de.example.StressTest"));
        editorService.commit(exercise(), change(editor, edited(existing, List.of("de.example.SortingTest")), added(stress, List.of("de.example.StressTest"))), user);
        String stressPolicy = readPushed("StressTests.yaml").orElseThrow();

        securityFrameworkService.deactivate(exercise(), user);

        assertThat(readPushed("SecurityPolicy.yaml")).isEmpty();
        assertThat(readPushed("StressTests.yaml")).isEmpty();

        securityFrameworkService.activate(exercise(), "2.2.1", user);

        assertThat(readPushed("StressTests.yaml")).contains(stressPolicy);
        assertThat(editorService.getEditor(exercise()).policies()).extracting(SecurityPolicyEditorDTO.PolicyDTO::policyPath).containsExactly("SecurityPolicy.yaml",
                "StressTests.yaml");
    }

    @Test
    void refusesANewPolicyNoTestClassUses() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO unused = withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of());

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, added(unused, List.of())), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyUnused"));
        assertThat(pushedHead()).isEqualTo(editor.baseCommitHash());
        assertThat(readPushed("StressTests.yaml")).isEmpty();
    }

    @Test
    void refusesTheCommitIfThePolicyChangedSinceItWasLoaded() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        pushToTests(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, POLICY.replace("ThreadLocalRandom", "ForkJoinPool"));
        String concurrentHead = pushedHead();

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(changed, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyChanged"));
        assertThat(pushedHead()).isEqualTo(concurrentHead);
        verify(localVCPushProcessingService, never()).processNewPush(any(), any(), any(), any(), any());
    }

    @Test
    void refusesTheCommitIfATestMethodGotAnOverrideSinceThePoliciesWereLoaded() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()"));
        String concurrentHead = pushedHead();

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(changed, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyChanged"));
        assertThat(pushedHead()).isEqualTo(concurrentHead);
        assertThat(readPushed(SORTING_TEST_PATH))
                .hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()"));
    }

    @Test
    void refusesTheCommitIfATestWasAddedToAClassAssignedCaseByCaseSinceThePoliciesWereLoaded() throws Exception {
        String withOverride = SORTING_TEST.replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()");
        pushToTests(SORTING_TEST_PATH, withOverride);
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactly("de.example.SortingTest#testMergeSort", "de.example.StressTest");
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        pushToTests(SORTING_TEST_PATH, withOverride.replace("    @Test\n    void testMergeSort() {\n    }",
                "    @Test\n    void testMergeSort() {\n    }\n\n    @Test\n    void testQuickSort() {\n    }"));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(changed, List.of("de.example.SortingTest#testMergeSort", "de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyChanged"));
    }

    @Test
    void acceptsTheCommitIfOnlyATestOfAClassAssignedAsAWholeChanged() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testMergeSort() {\n    }",
                "    @Test\n    void testMergeSort() {\n    }\n\n    @Test\n    void testQuickSort() {\n    }"));

        editorService.commit(exercise(), change(editor, edited(changed, BOTH_CLASSES)), user);

        assertThat(loadedPolicy(editorService.getEditor(exercise())).files()).containsExactly(LOGS);
    }

    @Test
    void refusesSplittingAClassAssignedAsAWholeIfATestWasAddedToItSinceThePoliciesWereLoaded() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testMergeSort() {\n    }",
                "    @Test\n    void testMergeSort() {\n    }\n\n    @Test\n    void testQuickSort() {\n    }"));
        String concurrentHead = pushedHead();

        // Releasing testBubbleSort would release testQuickSort as well, which the editor never showed.
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest#testMergeSort", "de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyChanged"));
        assertThat(pushedHead()).isEqualTo(concurrentHead);
    }

    @Test
    void acceptsSplittingAClassAssignedAsAWholeIfOnlyTheBodyOfOneOfItsTestsChanged() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        pushToTests(SORTING_TEST_PATH,
                SORTING_TEST.replace("    @Test\n    void testMergeSort() {\n    }", "    @Test\n    void testMergeSort() {\n        int[] values = { 3, 1, 2 };\n    }"));

        editorService.commit(exercise(), change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest#testMergeSort", "de.example.StressTest"))), user);

        assertThat(editorService.getEditor(exercise()).policies().getFirst().assignedTests()).containsExactly("de.example.SortingTest#testMergeSort", "de.example.StressTest");
    }

    @Test
    void refusesTheCommitIfSomeonePushesWhileItIsPrepared() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        afterNextCheckout = () -> pushToTests(STRESS_TEST_PATH,
                STRESS_TEST.replace("Policy(value = \"SecurityPolicy.yaml\")", "Policy(value = \"SecurityPolicy.yaml\", activated = false)"));

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(changed, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityFrameworkPushFailed"));
        assertThat(readPushed(STRESS_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("Policy(value = \"SecurityPolicy.yaml\", activated = false)"));
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains(POLICY);
    }

    @Test
    void anUnrelatedCommitSinceLoadingDoesNotConflict() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO changed = withFiles(loadedPolicy(editor), List.of(LOGS));
        pushToTests("README.md", "Edited in the online editor");

        editorService.commit(exercise(), change(editor, edited(changed, BOTH_CLASSES)), user);

        assertThat(readPushed("README.md")).contains("Edited in the online editor");
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).hasValueSatisfying(content -> assertThat(content).contains("\"logs/\""));
    }

    @Test
    void refusesAPolicyThePinnedVersionDoesNotAccept() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO withMirroring = withBehavior(loadedPolicy(editor),
                new SecurityPolicyDocumentDTO.TestBehaviorDTO(null, new SecurityPolicyDocumentDTO.OutputMirroringDTO(true, 1000L), null, null));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(withMirroring, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyVersionIncompatible"));
        assertThat(pushedHead()).isEqualTo(editor.baseCommitHash());
    }

    @Test
    void refusesATestThatIsAssignedToTwoPolicies() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO stress = withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of("de.example.StressTest"));
        SecurityPolicyDocumentDTO sorting = withTestClasses(loadedPolicy(editor), "SortingTests.yaml", List.of("de.example.SortingTest"));

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(), change(editor, added(stress, List.of("de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyTestAssignedTwice"));
        // A whole class and one of its test cases cannot use two policies.
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, edited(loadedPolicy(editor), BOTH_CLASSES), added(sorting, List.of("de.example.SortingTest#testBubbleSort"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyTestAssignedTwice"));
    }

    @Test
    void refusesToRewriteAPolicyTheEditorShowsReadOnly() throws Exception {
        // Only the rules of Ares 2.2.1 are known, so a policy with a field the editor does not know stays configured for a newer version.
        pushToTests("build.gradle", "ext {\n    aresVersion = '2.3.0'\n}\n");
        pushToTests(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME,
                POLICY.replace("  theFollowingResourceAccessesArePermitted:", "  aFieldAresDoesNotKnow: 1\n  theFollowingResourceAccessesArePermitted:"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO document = withFiles(SecurityPolicyDocuments.parse("SecurityPolicy.yaml", POLICY, null).document(), List.of(LOGS));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(document, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyReadOnly"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "../SecurityPolicy.yaml", "Policy.txt", "/etc/SecurityPolicy.yaml", "Stress\"Tests.yaml" })
    void refusesAnInvalidPolicyPath(String policyPath) {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO document = withTestClasses(loadedPolicy(editor), policyPath, List.of("de.example.StressTest"));

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(), change(editor, added(document, List.of("de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyInvalidPath"));
    }

    @Test
    void refusesAChangeWithoutTheCommitItIsBasedOn() {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(
                        () -> editorService.commit(exercise(), new SecurityPolicyChangeDTO(null, null, null, List.of(edited(loadedPolicy(editor), BOTH_CLASSES)), List.of()), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyNoBaseCommit"));
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyNoChange"));
    }

    @Test
    void refusesInvalidValues() {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO document = withFiles(loadedPolicy(editor), List.of(new SecurityPolicyDocumentDTO.FilePermissionDTO("logs/", false, false, false, false, false)));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.preview(exercise(), change(editor, edited(document, BOTH_CLASSES))))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyInvalid"));
    }

    @Test
    void refusesToCommitAResourceListedTwice() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        String headBefore = pushedHead();
        SecurityPolicyDocumentDTO twice = withFiles(loadedPolicy(editor), List.of(LOGS, LOGS));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(twice, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyInvalid"));
        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    void readsAnImportedPolicyWithoutWritingIt() throws Exception {
        String headBefore = pushedHead();

        SecurityPolicyEditorDTO.PolicyDTO imported = editorService.importPolicy(exercise(),
                new SecurityPolicyImportDTO("SecurityPolicy.yaml", POLICY.replace("regardingTimeouts: [ ]", "regardingTimeouts:\n      - timeout: 1000")));
        SecurityPolicyEditorDTO.PolicyDTO broken = editorService.importPolicy(exercise(), new SecurityPolicyImportDTO("SecurityPolicy.yaml", "a: [\n"));
        SecurityPolicyEditorDTO.PolicyDTO twice = editorService.importPolicy(exercise(), new SecurityPolicyImportDTO("SecurityPolicy.yaml",
                POLICY.replace("regardingPackageImports: [ ]", "regardingPackageImports:\n      - importTheFollowingPackage: \"a\"\n      - importTheFollowingPackage: \"a\"")));

        assertThat(imported.document()).isNotNull();
        assertThat(imported.document().timeouts()).containsExactly(1000L);
        assertThat(broken.document()).isNull();
        assertThat(broken.readOnlyReason()).isEqualTo("YAML_ERROR");
        assertThat(twice.document()).isNull();
        assertThat(twice.readOnlyReason()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(pushedHead()).isEqualTo(headBefore);
    }

    @Test
    void assignsSingleTestCasesWithAnnotationsOnTheirMethods() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO stress = withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of("de.example.SortingTest"));

        SecurityPolicyCommitResultDTO result = editorService.commit(exercise(),
                change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest#testBubbleSort", "de.example.StressTest")),
                        added(stress, List.of("de.example.SortingTest#testMergeSort"))),
                user);

        assertThat(readPushed(SORTING_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy\nclass SortingTest {")
                .contains("    @de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\n    @Test\n    void testBubbleSort()")
                .contains("    @de.tum.cit.ase.ares.api.Policy(value = \"StressTests.yaml\")\n    @Test\n    void testMergeSort()"));
        assertThat(readPushed(STRESS_TEST_PATH)).contains(STRESS_TEST);
        assertThat(result.config().status()).isEqualTo("CONFIGURED");
        assertThat(result.editor().policies()).extracting(SecurityPolicyEditorDTO.PolicyDTO::assignedTests)
                .containsExactly(List.of("de.example.SortingTest#testBubbleSort", "de.example.StressTest"), List.of("de.example.SortingTest#testMergeSort"));

        // Assigning both test cases to one policy again makes the class annotation point to it, so test cases added later use it too.
        SecurityPolicyEditorDTO reloaded = result.editor();
        SecurityPolicyDocumentDTO keptStress = reloaded.policies().getLast().document();
        editorService.commit(exercise(), new SecurityPolicyChangeDTO(reloaded.baseCommitHash(), null, null,
                List.of(edited(reloaded.policies().getFirst().document(), BOTH_CLASSES)), List.of(keptStress.policyPath())), user);

        assertThat(readPushed(SORTING_TEST_PATH)).contains(SORTING_TEST);
        assertThat(readPushed("StressTests.yaml")).isEmpty();
    }

    @Test
    void aRetryAfterAFailedProcessingResumesItInsteadOfConflictingWithItself() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyChangeDTO change = change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest")),
                added(withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of("de.example.StressTest")), List.of("de.example.StressTest")));
        doThrow(new VersionControlException("Build trigger failed")).when(localVCPushProcessingService).processNewPush(anyString(), any(), any(), any(), any());

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change, user));
        String pushed = pushedHead();

        doNothing().when(localVCPushProcessingService).processNewPush(anyString(), any(), any(), any(), any());
        editorService.commit(exercise(), change, user);

        assertThat(pushedHead()).isEqualTo(pushed);
        verify(localVCPushProcessingService, times(2)).processNewPush(eq(pushed), any(), any(), any(), any());

        // Another change from the same base commit is still a conflict.
        SecurityPolicyChangeDTO other = change(editor, edited(loadedPolicy(editor), BOTH_CLASSES));
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> editorService.commit(exercise(), other, user));
    }

    @Test
    void aRetryResumesTheProcessingAlsoAfterAnUnrelatedCommitBeforeTheChange() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        pushToTests("README.md", "An unrelated change\n");
        SecurityPolicyChangeDTO change = change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest")),
                added(withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of("de.example.StressTest")), List.of("de.example.StressTest")));
        doThrow(new VersionControlException("Build trigger failed")).when(localVCPushProcessingService).processNewPush(anyString(), any(), any(), any(), any());

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change, user));
        String pushed = pushedHead();
        doNothing().when(localVCPushProcessingService).processNewPush(anyString(), any(), any(), any(), any());
        editorService.commit(exercise(), change, user);

        assertThat(pushedHead()).isEqualTo(pushed);
        verify(localVCPushProcessingService, times(2)).processNewPush(eq(pushed), any(), any(), any(), any());
    }

    @Test
    void refusesToAssignOneOfSeveralTestMethodsWithTheSameName() {
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testMergeSort() {\n    }",
                "    @Test\n    void testMergeSort() {\n    }\n\n    @org.junit.jupiter.params.ParameterizedTest\n    void testMergeSort(int size) {\n    }"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());

        SecurityTestStructureDTO.TestClassDTO structure = editorService.getTestStructure(exercise()).testClasses().getFirst();
        assertThat(structure.overloadedCases()).containsExactly("testMergeSort");
        assertThat(structure.fixedCases()).isEmpty();
        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest#testMergeSort", "de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyTestCaseOverloaded"));
    }

    @Test
    void refusesARenameThatWouldDropThePolicyOfOverloadedTestMethodsUnnoticed() {
        pushToTests(SORTING_TEST_PATH,
                SORTING_TEST.replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()").replace(
                        "    @Test\n    void testMergeSort() {\n    }",
                        "    @Test\n    void testMergeSort() {\n    }\n\n    @org.junit.jupiter.params.ParameterizedTest\n    void testMergeSort(int size) {\n    }"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO renamed = withTestClasses(loadedPolicy(editor), "SortingPolicy.yaml", BOTH_CLASSES);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, new SecurityPolicyChangeDTO.PolicyChangeDTO("SecurityPolicy.yaml", renamed, List.of("de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyOverloadsLosePolicy"));
    }

    @Test
    void refusesARenameWhenOnlyOneOverloadedTestMethodHasAnAnnotationOfItsOwn() {
        pushToTests(SORTING_TEST_PATH,
                SORTING_TEST.replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()").replace(
                        "    @Test\n    void testMergeSort() {\n    }",
                        "    @de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\n    @Test\n    void testMergeSort() {\n    }\n\n"
                                + "    @org.junit.jupiter.params.ParameterizedTest\n    void testMergeSort(int size) {\n    }"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO renamed = withTestClasses(loadedPolicy(editor), "SortingPolicy.yaml", BOTH_CLASSES);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, new SecurityPolicyChangeDTO.PolicyChangeDTO("SecurityPolicy.yaml", renamed, List.of("de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyOverloadsLosePolicy"));
    }

    @Test
    void renamesAPolicyAndPointsItsTestsToTheNewFile() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO renamed = withTestClasses(loadedPolicy(editor), "SortingPolicy.yaml", BOTH_CLASSES);

        SecurityPolicyCommitResultDTO result = editorService.commit(exercise(),
                change(editor, new SecurityPolicyChangeDTO.PolicyChangeDTO("SecurityPolicy.yaml", renamed, BOTH_CLASSES)), user);

        assertThat(readPushed("SecurityPolicy.yaml")).isEmpty();
        assertThat(readPushed("SortingPolicy.yaml")).hasValueSatisfying(content -> assertThat(content).startsWith("# Ares 2 security policy"));
        assertThat(readPushed(SORTING_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("Policy(value = \"SortingPolicy.yaml\")"));
        assertThat(result.editor().policies()).extracting(SecurityPolicyEditorDTO.PolicyDTO::policyPath).containsExactly("SortingPolicy.yaml");
    }

    @Test
    void renamingAPolicyKeepsTheOtherArgumentsOfAMethodAnnotation() throws Exception {
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testBubbleSort()",
                "    @de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\", withinPath = \"test\")\n    @Test\n    void testBubbleSort()"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
        SecurityPolicyDocumentDTO renamed = withTestClasses(loadedPolicy(editor), "SortingPolicy.yaml", BOTH_CLASSES);

        editorService.commit(exercise(), change(editor, new SecurityPolicyChangeDTO.PolicyChangeDTO("SecurityPolicy.yaml", renamed, BOTH_CLASSES)), user);

        assertThat(readPushed(SORTING_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("Policy(value = \"SortingPolicy.yaml\")\nclass SortingTest")
                .contains("@de.tum.cit.ase.ares.api.Policy(value = \"SortingPolicy.yaml\", withinPath = \"test\")\n    @Test\n    void testBubbleSort()"));
        assertThat(editorService.getEditor(exercise()).policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
    }

    @Test
    void renamingAPolicyKeepsAMethodAnnotationThatLeavesOutAnArgumentOfTheClass() throws Exception {
        pushToTests(SORTING_TEST_PATH,
                SORTING_TEST
                        .replace("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\nclass SortingTest",
                                "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\", withinPath = \"test\")\nclass SortingTest")
                        .replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy(\"SecurityPolicy.yaml\")\n    @Test\n    void testBubbleSort()"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
        SecurityPolicyDocumentDTO renamed = withTestClasses(loadedPolicy(editor), "SortingPolicy.yaml", BOTH_CLASSES);

        editorService.commit(exercise(), change(editor, new SecurityPolicyChangeDTO.PolicyChangeDTO("SecurityPolicy.yaml", renamed, BOTH_CLASSES)), user);

        assertThat(readPushed(SORTING_TEST_PATH))
                .hasValueSatisfying(content -> assertThat(content).contains("Policy(value = \"SortingPolicy.yaml\", withinPath = \"test\")\nclass SortingTest")
                        .contains("    @de.tum.cit.ase.ares.api.Policy(\"SortingPolicy.yaml\")\n    @Test\n    void testBubbleSort()"));
    }

    @Test
    void refusesToAssignTheOwnTestCasesOfAClassThatInheritsTestsOneByOne() {
        pushToTests("test/de/example/ChildTest.java", """
                package de.example;

                import org.junit.jupiter.api.Test;

                @de.tum.cit.ase.ares.api.jupiter.Public
                @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
                class ChildTest extends SortingTest {

                    @Test
                    void testOwnB() {
                    }

                    @Test
                    void testOwnC() {
                    }
                }
                """);
        List<String> withChild = List.of("de.example.ChildTest", "de.example.SortingTest", "de.example.StressTest");
        SecurityPolicyEditorDTO initial = editorService.getEditor(exercise());
        editorService.commit(exercise(), change(initial, edited(withTestClasses(loadedPolicy(initial), "SecurityPolicy.yaml", withChild), withChild)), user);
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactlyElementsOf(withChild);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, edited(loadedPolicy(editor), List.of("de.example.ChildTest#testOwnC", "de.example.SortingTest", "de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyInheritedTestsLosePolicy"));
    }

    @Test
    void refusesToReleaseTheLastShownTestCaseOfAClassThatInheritsTestsAndIsAssignedCaseByCase() {
        pushToTests("test/de/example/ChildTest.java", """
                package de.example;

                import org.junit.jupiter.api.Test;

                @de.tum.cit.ase.ares.api.jupiter.Public
                @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
                class ChildTest extends SortingTest {

                    @de.tum.cit.ase.ares.api.Policy
                    @Test
                    void testOwnB() {
                    }

                    @Test
                    void testOwnC() {
                    }
                }
                """);
        List<String> withChild = List.of("de.example.ChildTest#testOwnC", "de.example.SortingTest", "de.example.StressTest");
        SecurityPolicyEditorDTO initial = editorService.getEditor(exercise());
        editorService.commit(exercise(),
                change(initial,
                        edited(withTestClasses(loadedPolicy(initial), "SecurityPolicy.yaml", List.of("de.example.ChildTest", "de.example.SortingTest", "de.example.StressTest")),
                                withChild)),
                user);
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactlyElementsOf(withChild);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(loadedPolicy(editor), BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyInheritedTestsLosePolicy"));
    }

    @Test
    void renamingAPolicyKeepsTheOverridesOfTestCasesWithoutAPolicyFile() throws Exception {
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()")
                .replace("    @Test\n    void testMergeSort()", "    @de.tum.cit.ase.ares.api.Policy(activated = false)\n    @Test\n    void testMergeSort()"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityTestStructureDTO structure = editorService.getTestStructure(exercise());

        // Neither test case uses the policy file, so the class is not assigned as a whole and only the switched-off test case cannot be assigned.
        assertThat(editor.policies().getFirst().assignedTests()).containsExactly("de.example.StressTest");
        assertThat(structure.testClasses().getFirst().fixedCases()).containsExactly("testMergeSort");

        SecurityPolicyDocumentDTO renamed = withTestClasses(loadedPolicy(editor), "SortingPolicy.yaml", BOTH_CLASSES);
        editorService.commit(exercise(), change(editor, new SecurityPolicyChangeDTO.PolicyChangeDTO("SecurityPolicy.yaml", renamed, List.of("de.example.StressTest"))), user);

        assertThat(readPushed(SORTING_TEST_PATH))
                .hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()")
                        .contains("@de.tum.cit.ase.ares.api.Policy(activated = false)\n    @Test\n    void testMergeSort()"));
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> {
            SecurityPolicyEditorDTO reloaded = editorService.getEditor(exercise());
            editorService.commit(exercise(),
                    change(reloaded, edited(reloaded.policies().getFirst().document(), List.of("de.example.StressTest", "de.example.SortingTest#testMergeSort"))), user);
        }).satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyTestCaseSwitchedOff"));
    }

    @Test
    void assigningAWholeClassRemovesTheOverridesWithoutAPolicyFileOfItsTestCases() throws Exception {
        pushToTests(SORTING_TEST_PATH, SORTING_TEST.replace("    @Test\n    void testBubbleSort()", "    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void testBubbleSort()"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactly("de.example.SortingTest#testMergeSort", "de.example.StressTest");

        editorService.commit(exercise(), change(editor, edited(loadedPolicy(editor), BOTH_CLASSES)), user);

        assertThat(readPushed(SORTING_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).doesNotContain("@de.tum.cit.ase.ares.api.Policy\n    @Test"));
        assertThat(editorService.getEditor(exercise()).policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
    }

    @Test
    void assignsAClassWhoseAnnotationNamesNoPolicyFile() throws Exception {
        pushToTests(STRESS_TEST_PATH, STRESS_TEST.replace("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")", "@de.tum.cit.ase.ares.api.Policy(value = \"\")"));
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        assertThat(editor.policies().getFirst().assignedTests()).containsExactly("de.example.SortingTest");

        editorService.commit(exercise(), change(editor, edited(loadedPolicy(editor), BOTH_CLASSES)), user);

        assertThat(readPushed(STRESS_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")"));
        assertThat(editorService.getEditor(exercise()).policies().getFirst().assignedTests()).containsExactlyElementsOf(BOTH_CLASSES);
    }

    @Test
    void deletingAPolicyLetsItsTestsUseTheDefaultPolicy() throws Exception {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());

        SecurityPolicyCommitResultDTO result = editorService.commit(exercise(),
                new SecurityPolicyChangeDTO(editor.baseCommitHash(), null, null, List.of(), List.of("SecurityPolicy.yaml")), user);

        assertThat(readPushed("SecurityPolicy.yaml")).isEmpty();
        assertThat(readPushed(STRESS_TEST_PATH)).hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy\nclass StressTest"));
        assertThat(result.config().status()).isEqualTo("CONFIGURED");
        assertThat(result.editor().policies()).isEmpty();
        // Without a policy to copy them from, a new policy starts with the supervised code of a generated one.
        assertThat(result.editor().newPolicyTemplate()).isNotNull().satisfies(template -> {
            assertThat(template.languageConfiguration()).isEqualTo("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ");
            assertThat(template.supervisedPackage()).isEqualTo("de.example");
        });
    }

    @Test
    void pinsAnotherVersionWithTheCommit() throws Exception {
        pushToTests("build.gradle", "ext {\n    aresVersion = '2.1.0'\n}\n");
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyChangeDTO change = new SecurityPolicyChangeDTO(editor.baseCommitHash(), null, "2.2.1", List.of(), List.of());

        SecurityPolicyPreviewDTO preview = editorService.preview(exercise(), change);
        editorService.commit(exercise(), change, user);

        assertThat(preview.frameworkVersion()).isEqualTo("2.2.1");
        assertThat(preview.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("build.gradle");
            assertThat(file.after()).contains("aresVersion = '2.2.1'");
        });
        assertThat(readPushed("build.gradle")).hasValueSatisfying(content -> assertThat(content).contains("aresVersion = '2.2.1'"));
    }

    @Test
    void refusesDependencyScanningWithoutTheAresAgent() {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO document = loadedPolicy(editor);
        SecurityPolicyDocumentDTO scanning = new SecurityPolicyDocumentDTO(document.policyPath(), document.preset(), "JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION",
                document.supervisedPackage(), document.mainClass(), document.testClasses(), document.files(), document.network(), document.commands(), document.threads(),
                document.packages(), document.timeouts(), document.testBehavior());

        assertThat(editor.scanDependenciesAvailable()).isFalse();
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(scanning, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyAgentMissing"));
    }

    @Test
    void refusesDependencyScanningWithoutTheAresAgentAlsoIfAPolicyCommentNamesIt() {
        pushToTests(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, POLICY + "# JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION\n");
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO document = loadedPolicy(editor);
        SecurityPolicyDocumentDTO scanning = new SecurityPolicyDocumentDTO(document.policyPath(), document.preset(), "JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION",
                document.supervisedPackage(), document.mainClass(), document.testClasses(), document.files(), document.network(), document.commands(), document.threads(),
                document.packages(), document.timeouts(), document.testBehavior());

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> editorService.commit(exercise(), change(editor, edited(scanning, BOTH_CLASSES)), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyAgentMissing"));
    }

    @Test
    void refusesATestWhoseClassThePolicyDoesNotDeclare() {
        SecurityPolicyEditorDTO editor = editorService.getEditor(exercise());
        SecurityPolicyDocumentDTO stress = withTestClasses(loadedPolicy(editor), "StressTests.yaml", List.of());

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> editorService.commit(exercise(),
                        change(editor, edited(loadedPolicy(editor), List.of("de.example.SortingTest")), added(stress, List.of("de.example.StressTest"))), user))
                .satisfies(exception -> assertThat(exception.getErrorKey()).isEqualTo("securityPolicyTestClassNotDeclared"));
    }

    @Test
    void listsTheTestClassesWithTheirTestCases() {
        SecurityTestStructureDTO structure = editorService.getTestStructure(exercise());

        assertThat(structure.testClasses()).containsExactly(
                new SecurityTestStructureDTO.TestClassDTO("de.example.SortingTest", List.of("testBubbleSort", "testMergeSort"), true, false, List.of(), List.of()),
                new SecurityTestStructureDTO.TestClassDTO("de.example.StressTest", List.of("testManyElements"), true, false, List.of(), List.of()));
    }

    @Test
    void listsTheFilesOfTheTemplateRepository() {
        SecurityTemplateFilesDTO files = editorService.getTemplateFiles(exercise());

        assertThat(files.paths()).containsExactly("build.gradle", "data/input.txt", "scripts/check.py", "src/de/example/Client.java");
        assertThat(files.truncated()).isFalse();
    }

    @Test
    void readsThePresetsOfTheCatalog() {
        List<SecurityPolicyEditorDTO.PresetDTO> presets = SecurityPolicyEditorService.parsePresets("""
                catalogVersion: 1
                presets:
                  - name: MOCKITO
                    label: Mockito
                    theFollowingResourceAccessesArePermitted:
                      regardingFileSystemInteractions:
                        - onThisPathAndAllPathsBelow: "tmp/mockito/"
                          readAllFiles: true
                          overwriteAllFiles: false
                          createAllFiles: true
                          executeAllFiles: false
                          deleteAllFiles: false
                      regardingNetworkConnections: [ ]
                      regardingCommandExecutions: [ ]
                      regardingThreadCreations: [ ]
                      regardingPackageImports:
                        - importTheFollowingPackage: "org.mockito"
                      regardingTimeouts: [ ]
                """);

        assertThat(presets).singleElement().satisfies(preset -> {
            assertThat(preset.name()).isEqualTo("MOCKITO");
            assertThat(preset.label()).isEqualTo("Mockito");
            assertThat(preset.files()).containsExactly(new SecurityPolicyDocumentDTO.FilePermissionDTO("tmp/mockito/", true, false, true, false, false));
            assertThat(preset.packages()).containsExactly(new SecurityPolicyDocumentDTO.PackagePermissionDTO("org.mockito"));
        });
        assertThatIllegalArgumentException().isThrownBy(() -> SecurityPolicyEditorService.parsePresets("presets:\n  - name: MOCKITO\n"));
        assertThatIllegalArgumentException().isThrownBy(() -> SecurityPolicyEditorService.parsePresets("catalogVersion: 1\n"));
    }
}
