package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseRepositoryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

/**
 * Tests the persistence orchestration: the canonical tests build is triggered with the tests commit only after the solution was committed (so the build sees the final solution),
 * the problem statement is rewritten only when the agent actually changed it, and a mid-sequence commit failure is surfaced (not swallowed into a false success) so the exercise is
 * never left half-written without anyone noticing.
 */
class GenerationPersistenceServiceTest {

    private GitService gitService;

    private RepositoryService repositoryService;

    private ProgrammingExerciseParticipationService participationService;

    private ContinuousIntegrationTriggerService continuousIntegrationTriggerService;

    private ProgrammingSubmissionService programmingSubmissionService;

    private ProgrammingExerciseCreationUpdateService creationUpdateService;

    private ExerciseVersionService exerciseVersionService;

    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private GenerationPersistenceService service;

    private ProgrammingExercise exercise;

    private LocalVCRepositoryUri templateUri;

    private LocalVCRepositoryUri solutionUri;

    private LocalVCRepositoryUri testsUri;

    private final User user = new User();

    @BeforeEach
    void setUp() {
        gitService = mock(GitService.class);
        repositoryService = mock(RepositoryService.class);
        participationService = mock(ProgrammingExerciseParticipationService.class);
        continuousIntegrationTriggerService = mock(ContinuousIntegrationTriggerService.class);
        programmingSubmissionService = mock(ProgrammingSubmissionService.class);
        creationUpdateService = mock(ProgrammingExerciseCreationUpdateService.class);
        exerciseVersionService = mock(ExerciseVersionService.class);
        testCaseRepository = mock(ProgrammingExerciseTestCaseTestRepository.class);
        programmingExerciseRepositoryService = mock(ProgrammingExerciseRepositoryService.class);
        // The build-gate adjustment waits for the freshly triggered tests-build to re-sync the COMPLETE set: it captures a pre-build baseline and waits for the count to move off
        // it
        // and settle. Return empty first (baseline) then a single non-build-gate case, so the wait settles at once and zero-weights nothing in these generic tests.
        ProgrammingExerciseTestCase behaviourCase = mock(ProgrammingExerciseTestCase.class);
        when(behaviourCase.getTestName()).thenReturn("behaviourTest");
        when(testCaseRepository.findByExerciseId(anyLong())).thenReturn(Set.of(), Set.of(behaviourCase));
        service = new GenerationPersistenceService("main", gitService, repositoryService, participationService, continuousIntegrationTriggerService, programmingSubmissionService,
                creationUpdateService, exerciseVersionService, testCaseRepository, programmingExerciseRepositoryService);
        service.setTestCaseSyncTimingForTests(Duration.ofSeconds(2), Duration.ofMillis(5));

        exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        // Distinct URIs per repository so the tests-build trigger can be asserted to fire with the tests commit hash specifically.
        templateUri = mock(LocalVCRepositoryUri.class);
        solutionUri = mock(LocalVCRepositoryUri.class);
        testsUri = mock(LocalVCRepositoryUri.class);
        when(exercise.getRepositoryURI(RepositoryType.TEMPLATE)).thenReturn(templateUri);
        when(exercise.getRepositoryURI(RepositoryType.SOLUTION)).thenReturn(solutionUri);
        when(exercise.getRepositoryURI(RepositoryType.TESTS)).thenReturn(testsUri);
    }

    private GenerationOutcome outcomeWith(Map<String, String> template, Map<String, String> solution, Map<String, String> tests, String problemStatement) {
        GenerationOutcome outcome = mock(GenerationOutcome.class);
        when(outcome.producedFiles(RepositoryType.TEMPLATE)).thenReturn(template);
        when(outcome.producedFiles(RepositoryType.SOLUTION)).thenReturn(solution);
        when(outcome.producedFiles(RepositoryType.TESTS)).thenReturn(tests);
        when(outcome.producedProblemStatement()).thenReturn(problemStatement);
        return outcome;
    }

    private Repository repository;

    private void stubSuccessfulCheckoutAndCommits() throws Exception {
        repository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        // By default the scaffolded tree holds only the files the agent produces (no orphans), so the orphan sweep is a no-op unless a test stubs extra tracked files.
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        when(gitService.getLastCommitHash(templateUri)).thenReturn("hash-template");
        when(gitService.getLastCommitHash(solutionUri)).thenReturn("hash-solution");
        when(gitService.getLastCommitHash(testsUri)).thenReturn("hash-tests");
    }

    @Test
    void persist_happyPath_commitsInProductionOrderTriggersTestsBuildAndCreatesVersion() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        ProgrammingExerciseParticipation solutionParticipation = mock(ProgrammingExerciseParticipation.class);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(solutionParticipation);

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");
        when(exercise.getProblemStatement()).thenReturn("old statement");

        service.persist(exercise, user, outcome);

        // The observable ordering contract: the tests build is triggered with the TESTS commit only AFTER the solution repository was committed, so the build sees the final
        // solution.
        // We assert it via the commit effect (the per-repository getLastCommitHash) rather than the raw file-write order, which is an implementation detail.
        InOrder order = Mockito.inOrder(gitService, programmingSubmissionService, continuousIntegrationTriggerService);
        order.verify(gitService).getLastCommitHash(solutionUri);
        order.verify(gitService).getLastCommitHash(testsUri);
        order.verify(programmingSubmissionService).createSolutionParticipationSubmissionWithTypeTest(1L, "hash-tests");
        order.verify(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "hash-tests", RepositoryType.TESTS);

        // The problem statement changed, so it is rewritten.
        verify(creationUpdateService).updateProblemStatement(exercise, "new statement", null);

        // The canonical tests build is triggered with the tests commit specifically.
        verify(programmingSubmissionService).createSolutionParticipationSubmissionWithTypeTest(1L, "hash-tests");
        verify(continuousIntegrationTriggerService).triggerBuild(solutionParticipation, "hash-tests", RepositoryType.TESTS);

        // A successful persist records a new exercise version.
        verify(exerciseVersionService).createExerciseVersion(exercise, user);
    }

    @Test
    void persist_normalizesCheckoutPlaceholders_inEveryCommittedRepository_soNoHarnessShipsRawPlaceholders() throws Exception {
        // The agent's sandbox harness can carry (or re-introduce) raw ${...} checkout placeholders \u2014 most visibly the Haskell run.sh, whose
        // ${studentParentWorkingDirectoryName}/
        // ${solutionWorkingDirectory} expand to empty strings under real CI (`find / -type l`, `rm -rf`), failing the build so no test case syncs. The persist must re-run the same
        // production placeholder substitution exercise creation applies, on EACH committed repository's working copy, so the committed harness is byte-identical to an
        // instructor-created one. We assert the normalization happens for all three repositories (template, solution, tests).
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        service.persist(exercise, user, outcome);

        verify(programmingExerciseRepositoryService, times(3)).replacePlaceholders(exercise, repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void persist_zeroWeightsBuildGateTestCases_setsTheRealWeightToZeroAndPersistsOnlyThoseCases() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        // REAL entities (not mocks) so the assertion proves the persisted WEIGHT actually became 0 \u2014 not merely that setWeight was invoked. The tests-build synced a C/C++
        // FACT
        // report: a build gate that passes on the compiling template (CompileSort) plus a real behaviour test, both at the default weight 1.0.
        ProgrammingExerciseTestCase buildGate = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.CompileSort").weight(1.0);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("sort-test.push_then_pop").weight(1.0);
        // Empty baseline, then the full synced set: the wait must move off the baseline and settle before zero-weighting, mirroring the real stale-partial-then-complete sync.
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(), Set.of(buildGate, behaviour));

        service.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), ""));

        // The build gate now grades for zero points (so the untouched compiling template scores 0%); the behaviour test is left graded.
        assertThat(buildGate.getWeight()).as("build gate zero-weighted").isEqualTo(0.0);
        assertThat(behaviour.getWeight()).as("behaviour test left graded").isEqualTo(1.0);
        // Exactly the build gate is written back \u2014 the unchanged behaviour case is not needlessly re-saved.
        ArgumentCaptor<Iterable<ProgrammingExerciseTestCase>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(testCaseRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).containsExactly(buildGate);
    }

    @Test
    void persist_waitsForTheCompleteSyncBeforeZeroWeighting_soNoGateFromTheFullSetIsMissed() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        ProgrammingExerciseTestCase configure = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.TestConfigure").weight(1.0);
        ProgrammingExerciseTestCase compileSort = new ProgrammingExerciseTestCase().testName("GBS-Tester-1.36.CompileSort").weight(1.0);
        ProgrammingExerciseTestCase behaviour = new ProgrammingExerciseTestCase().testName("sort-test.push_then_pop").weight(1.0);
        // The real failure mode (the live 9.1%-template bug): an exercise-setup build leaves a STALE PARTIAL set ({configure}); the freshly triggered tests-build then syncs the
        // COMPLETE set a few polls later. Acting on the partial set would MISS CompileSort. Baseline {} -> partial {configure} -> complete {configure, compileSort, behaviour}.
        when(testCaseRepository.findByExerciseId(1L)).thenReturn(Set.of(), Set.of(configure), Set.of(configure, compileSort, behaviour));

        service.persist(exercise, user, outcomeWith(Map.of("Template.cpp", "t"), Map.of("Solution.cpp", "s"), Map.of("Test.cpp", "x"), ""));

        // BOTH gates from the COMPLETE set are zero-weighted \u2014 not just the one visible in the partial sync. A regression to "act on the first non-empty set" leaves
        // compileSort at
        // 1.0.
        assertThat(configure.getWeight()).as("configure gate (present in the partial set) zero-weighted").isEqualTo(0.0);
        assertThat(compileSort.getWeight()).as("compile gate (only in the complete set) zero-weighted").isEqualTo(0.0);
        assertThat(behaviour.getWeight()).as("behaviour test left graded").isEqualTo(1.0);
    }

    @Test
    void persist_removesOrphanedCanonicalTestSources_butKeepsHarness_soPersistedTreeMirrorsTheSandbox() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        // The scaffolded TESTS repo still holds the canonical sample's orphaned test sources (a behaviour test for a DIFFERENT exercise and the previous structure oracle) PLUS the
        // immutable build harness. The agent produced only its own test and the regenerated oracle. The persist must delete the orphaned SOURCES (so they cannot fail real grading)
        // while never touching the harness (graded verbatim).
        Map<String, FileType> trackedTestFiles = Map.of("test/de/test/StringsTest.java", FileType.FILE, "test/de/test/SortingExampleBehaviorTest.java", FileType.FILE,
                "test/de/test/test.json", FileType.FILE, "test/de/test/OldClassTest.java", FileType.FILE, "pom.xml", FileType.FILE, "test", FileType.FOLDER);
        // The tests repo is the last committed; return the orphan-bearing tracked set only for it (template/solution have no orphans here).
        when(repositoryService.getFiles(any())).thenReturn(Map.of(), Map.of(), trackedTestFiles);

        // The sandbox-final tests tree the oracle validated: the agent's test plus the regenerated oracle (test.json kept, same path) \u2014 and the harness pom.xml.
        Map<String, String> producedTests = Map.of("test/de/test/StringsTest.java", "...", "test/de/test/test.json", "[]", "pom.xml", "<project/>");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), producedTests, "");

        service.persist(exercise, user, outcome);

        // The orphaned canonical sources (a different exercise's behaviour test and a stale structural test class) are deleted so they cannot run in real grading.
        verify(repositoryService).deleteFile(repository, "test/de/test/SortingExampleBehaviorTest.java");
        verify(repositoryService).deleteFile(repository, "test/de/test/OldClassTest.java");
        // The immutable harness (pom.xml) is NEVER deleted as an orphan even though the sweep saw it \u2014 it is in producedFiles and harness-protected besides.
        verify(repositoryService, never()).deleteFile(repository, "pom.xml");
        // A file the agent DID produce at the same path (test.json regenerated) is overwritten by the write path, not removed by the orphan sweep.
        verify(repositoryService, never()).deleteFile(repository, "test/de/test/test.json");
    }

    @Test
    void persist_preservesScaffoldedBinary_neverDeletedAsOrphanNorRewritten(@org.junit.jupiter.api.io.TempDir java.nio.file.Path workingTree) throws Exception {
        // Gap 2: a Java PLAIN_GRADLE template/solution ships a binary gradle/wrapper/gradle-wrapper.jar the agent never edits. It cannot survive the UTF-8 String round-trip, so
        // the
        // read-back EXCLUDES it from producedFiles \u2014 which makes it look like an orphan to the sweep. The persist must (a) NOT delete it (it is the byte-exact scaffolded
        // original
        // already in the working tree) and (b) NOT rewrite it (it is absent from producedFiles), so it commits intact. A text source the agent produced is still written normally.
        java.nio.file.Path wrapperDir = workingTree.resolve("gradle/wrapper");
        byte[] wrapperBytes = { 0x50, 0x4B, 0x03, 0x04, 0, 1, 2, (byte) 0xFF, (byte) 0x89 };
        org.apache.commons.io.FileUtils.writeByteArrayToFile(wrapperDir.resolve("gradle-wrapper.jar").toFile(), wrapperBytes);

        stubSuccessfulCheckoutAndCommits();
        when(repository.getLocalPath()).thenReturn(workingTree);
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        // The scaffolded TEMPLATE tree tracks the agent's source PLUS the binary wrapper jar (the latter excluded from producedFiles by the binary-safe read-back). Only the
        // TEMPLATE
        // sweep sees these tracked files; solution/tests have none here.
        Map<String, FileType> trackedTemplateFiles = Map.of("src/de/test/BankAccount.java", FileType.FILE, "gradle/wrapper/gradle-wrapper.jar", FileType.FILE);
        when(repositoryService.getFiles(any())).thenReturn(trackedTemplateFiles, Map.of(), Map.of());

        Map<String, String> producedTemplate = Map.of("src/de/test/BankAccount.java", "class BankAccount {}");
        GenerationOutcome outcome = outcomeWith(producedTemplate, Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        service.persist(exercise, user, outcome);

        // The binary wrapper jar is NEVER deleted as an orphan (it is preserved byte-exact from the scaffold) and NEVER rewritten (it is not in producedFiles, so no createFile for
        // it). The on-disk file remains the exact scaffolded bytes.
        verify(repositoryService, never()).deleteFile(repository, "gradle/wrapper/gradle-wrapper.jar");
        verify(repositoryService, never()).createFile(eq(repository), eq("gradle/wrapper/gradle-wrapper.jar"), any());
        assertThat(java.nio.file.Files.readAllBytes(wrapperDir.resolve("gradle-wrapper.jar"))).as("scaffolded wrapper jar is byte-identical").containsExactly(wrapperBytes);
        // The agent's text source is still written.
        verify(repositoryService).createFile(eq(repository), eq("src/de/test/BankAccount.java"), any());
    }

    @Test
    void persist_doesNotDeleteHarnessFile_evenWhenAbsentFromProducedFiles() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        // Defensive: a flaky read-back dropped the harness pom.xml from producedFiles. The orphan sweep must NOT delete it \u2014 a build/harness/manifest file is graded verbatim
        // and
        // immutable by contract, so a missing-from-extraction harness can corrupt the build but must never be silently wiped from the repository.
        Map<String, FileType> trackedTestFiles = Map.of("test/de/test/StringsTest.java", FileType.FILE, "pom.xml", FileType.FILE);
        when(repositoryService.getFiles(any())).thenReturn(Map.of(), Map.of(), trackedTestFiles);

        Map<String, String> producedTests = Map.of("test/de/test/StringsTest.java", "...");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), producedTests, "");

        service.persist(exercise, user, outcome);

        verify(repositoryService, never()).deleteFile(repository, "pom.xml");
    }

    @Test
    void persist_problemStatementUnchanged_doesNotRewriteIt() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));

        // The agent re-emitted the identical statement: rewriting it would create a spurious version/diff, so it must be skipped.
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "same statement");
        when(exercise.getProblemStatement()).thenReturn("same statement");

        service.persist(exercise, user, outcome);

        verify(creationUpdateService, never()).updateProblemStatement(any(), any(), any());
    }

    @Test
    void persist_propagatesMidSequenceCommitFailure_andDoesNotCreateVersion() throws Exception {
        Repository repository = mock(Repository.class);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), eq(true), eq("main"), eq(false))).thenReturn(repository);
        when(gitService.getFileByName(any(), any())).thenReturn(Optional.empty());
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
        // The template commit succeeds, but the solution commit then fails \u2014 the exact half-written scenario that must be surfaced rather than reported as success.
        Mockito.doNothing().doThrow(new org.eclipse.jgit.api.errors.NoHeadException("boom")).when(repositoryService).commitChanges(any(), any());

        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persist(exercise, user, outcome)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Failed to commit");
        // A failed persist must not record a version that would imply the exercise was saved consistently.
        verify(exerciseVersionService, never()).createExerciseVersion(any(), any());
    }

    // ================================================================================================================================================================
    // W3 \u2014 recovery must never regress a working exercise. persistRecoveryDraft routes a from-scratch target to the in-place default-branch persist (nothing to lose) and an
    // adapt
    // target to an ISOLATED branch, leaving the live default branch byte-identical and triggering NO grading build of the broken draft.
    // ================================================================================================================================================================

    private final String jobId = "job-42";

    /** All three repositories are empty (no tracked FILE) \u2014 a from-scratch target. */
    private void stubEmptyRepositories() {
        when(repositoryService.getFiles(any())).thenReturn(Map.of());
    }

    /** The given repository type already tracks a real source file \u2014 an adapt of an already-working exercise. */
    private void stubAdaptTarget() {
        // The first getFiles() call in persistRecoveryDraft is the adapt probe (template); returning a tracked FILE there is enough to classify the whole run as adapt.
        when(repositoryService.getFiles(any())).thenReturn(Map.of("src/de/test/BankAccount.java", FileType.FILE));
    }

    @Test
    void persistRecoveryDraft_fromScratchTarget_committedInPlaceToDefaultBranch_andTriggersBuildAndVersion() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        stubEmptyRepositories();
        when(participationService.retrieveSolutionParticipation(exercise)).thenReturn(mock(ProgrammingExerciseParticipation.class));
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        GenerationPersistenceService.RecoveryPersistResult result = service.persistRecoveryDraft(exercise, user, outcome, jobId);

        // From-scratch: there is nothing to lose, so the draft is committed to the live default branch in place exactly like an accepted run.
        assertThat(result.liveExerciseUntouched()).isFalse();
        assertThat(result.draftBranch()).isNull();
        verify(repositoryService, times(3)).commitChanges(any(), eq(user));
        // The canonical tests build IS triggered and a version IS recorded \u2014 this is a brand-new exercise being authored, not a working one being protected.
        verify(continuousIntegrationTriggerService).triggerBuild(any(), eq("hash-tests"), eq(RepositoryType.TESTS));
        verify(exerciseVersionService).createExerciseVersion(exercise, user);
        // It must NOT divert to an isolated branch.
        verify(gitService, never()).commitToIsolatedBranchAndPush(any(), any(), any(), any());
    }

    @Test
    void persistRecoveryDraft_adaptTarget_divertsToIsolatedBranch_leavesLiveExerciseUntouched_noGradingBuildNoVersion() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        stubAdaptTarget();
        when(gitService.commitToIsolatedBranchAndPush(any(), eq("hyperion-draft/" + jobId), any(), eq(user))).thenReturn("draft-hash");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "new statement");
        when(exercise.getProblemStatement()).thenReturn("old statement");

        GenerationPersistenceService.RecoveryPersistResult result = service.persistRecoveryDraft(exercise, user, outcome, jobId);

        // The live default branch is left byte-identical: the draft is on the isolated branch only.
        assertThat(result.liveExerciseUntouched()).isTrue();
        assertThat(result.draftBranch()).isEqualTo("hyperion-draft/" + jobId);
        // Every repository's draft is pushed to the SAME isolated branch; the live default branch is never committed to (no RepositoryService.commitChanges).
        verify(gitService, times(3)).commitToIsolatedBranchAndPush(any(), eq("hyperion-draft/" + jobId), any(), eq(user));
        verify(repositoryService, never()).commitChanges(any(), any());
        // CRITICAL: the broken draft must NOT be graded against the live exercise, and the live exercise must NOT be recorded as a new version (it did not change).
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyString(), any());
        verify(exerciseVersionService, never()).createExerciseVersion(any(), any());
        // The live exercise's problem statement must NOT be overwritten by the broken draft's statement.
        verify(creationUpdateService, never()).updateProblemStatement(any(), any(), any());
        // The working copy is reset back to origin/HEAD so the diverted commit does not linger on the checkout.
        verify(gitService, times(3)).resetToOriginHead(repository);
    }

    @Test
    void persistRecoveryDraft_adaptDetectionThrows_failsClosedToAdapt_neverOverwritesLiveBranch() throws Exception {
        // The adapt PROBE (the first getFiles call) blows up. The safe default is to TREAT IT AS ADAPT and never overwrite a possibly-working exercise; the subsequent orphan-sweep
        // getFiles calls succeed (empty), so the diversion to the isolated branch proceeds normally.
        stubSuccessfulCheckoutAndCommits();
        when(repositoryService.getFiles(any())).thenThrow(new RuntimeException("repo inspection exploded")).thenReturn(Map.of());
        when(gitService.commitToIsolatedBranchAndPush(any(), any(), any(), any())).thenReturn("draft-hash");
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        GenerationPersistenceService.RecoveryPersistResult result = service.persistRecoveryDraft(exercise, user, outcome, jobId);

        // The inspection error failed closed to adapt: the draft was diverted to the isolated branch and the live default branch was never touched.
        assertThat(result.liveExerciseUntouched()).as("an inspection error must fail closed to 'do not overwrite'").isTrue();
        verify(gitService, atLeastOnce()).commitToIsolatedBranchAndPush(any(), any(), any(), any());
        verify(repositoryService, never()).commitChanges(any(), any());
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyString(), any());
        verify(exerciseVersionService, never()).createExerciseVersion(any(), any());
    }

    @Test
    void persistRecoveryDraft_adaptIsolatedPushFailsMidMultiRepo_propagates_andNeverTouchesDefaultBranch() throws Exception {
        stubSuccessfulCheckoutAndCommits();
        stubAdaptTarget();
        // Template diverts fine, but the next isolated push (solution) blows up \u2014 the multi-repo half-write scenario. Because we never touch the default branch in adapt mode,
        // this
        // can only ever half-write the ISOLATED branch (a throwaway draft), never half-regress the live exercise.
        when(gitService.commitToIsolatedBranchAndPush(any(), eq("hyperion-draft/" + jobId), any(), eq(user))).thenReturn("draft-template")
                .thenThrow(new org.eclipse.jgit.api.errors.NoHeadException("isolated push boom"));
        GenerationOutcome outcome = outcomeWith(Map.of("Template.java", "t"), Map.of("Solution.java", "s"), Map.of("Test.java", "x"), "");

        assertThatThrownBy(() -> service.persistRecoveryDraft(exercise, user, outcome, jobId)).isInstanceOf(IllegalStateException.class).hasMessageContaining("recovery draft");
        // The live default branch was NEVER committed to, NEVER graded, NEVER versioned \u2014 the working exercise is intact despite the isolated-branch failure.
        verify(repositoryService, never()).commitChanges(any(), any());
        verify(continuousIntegrationTriggerService, never()).triggerBuild(any(), anyString(), any());
        verify(exerciseVersionService, never()).createExerciseVersion(any(), any());
    }

    @Test
    void normalizeTypography_replacesTypographicDashesAndSpacesWithAscii() {
        // gpt-oss reliably leaks a non-breaking hyphen (U+2011) in compound modifiers ("non-negative") plus the occasional en/em dash and non-breaking space, which it keeps
        // emitting
        // even when the prompt forbids them. The deterministic persist-time pass must leave the student-facing statement pure ASCII regardless of model compliance, untouched
        // elsewhere.
        String produced = "Reject a non\u2011negative amount \u2013 see the deposit\u2014withdraw flow.";
        String normalized = GenerationPersistenceService.normalizeTypography(produced);
        assertThat(normalized).isEqualTo("Reject a non-negative amount - see the deposit-withdraw flow.");
        assertThat(normalized.chars().allMatch(c -> c < 0x80)).as("the normalised statement is pure ASCII").isTrue();
        // A statement already free of typographic punctuation is returned unchanged; null is tolerated.
        String clean = "# Stack\nImplement push and pop.";
        assertThat(GenerationPersistenceService.normalizeTypography(clean)).isEqualTo(clean);
        assertThat(GenerationPersistenceService.normalizeTypography(null)).isNull();
    }
}
