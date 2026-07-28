package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResultDTO;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.FakeInteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.StageCheckService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the agent tools, focused on the security-relevant path allowlist and the all-or-nothing edit semantics. A fake sandbox records the commands it is asked to run
 * so the tests can assert that unsafe paths never reach the shell.
 */
class SandboxAgentToolsTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    @Test
    void workspaceRelativePath_rejectsTraversalQuotesAndShellMetacharacters() {
        assertThat(SandboxAgentTools.workspaceRelativePath("solution/src/Calculator.java")).isEqualTo("solution/src/Calculator.java");
        assertThat(SandboxAgentTools.workspaceRelativePath("/workspace/solution/A.java")).isEqualTo("solution/A.java");
        assertThat(SandboxAgentTools.workspaceRelativePath("../etc/passwd")).isNull();
        assertThat(SandboxAgentTools.workspaceRelativePath("foo'.java")).isNull();
        assertThat(SandboxAgentTools.workspaceRelativePath("a; rm -rf /")).isNull();
        assertThat(SandboxAgentTools.workspaceRelativePath("$(whoami).java")).isNull();
        assertThat(SandboxAgentTools.workspaceRelativePath("solution/*.java")).isNull();
        assertThat(SandboxAgentTools.workspaceRelativePath("/absolute")).isNull();
        assertThat(SandboxAgentTools.workspaceRelativePath("")).isNull();
    }

    @Test
    void readFile_withUnsafePath_returnsErrorWithoutTouchingTheSandbox() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.readFile("../secret");
        assertThat(result).startsWith("ERROR: invalid path");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void readFile_blocksSupportedSecretMaterialWithoutReturningTheMatch() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/src/fixture.txt", "before " + GITHUB_SENTINEL + " after");

        String result = new SandboxAgentTools(sandbox, "s").readFile("solution/src/fixture.txt");

        assertThat(result).contains("GITHUB_TOKEN").contains("solution/src/fixture.txt").doesNotContain(GITHUB_SENTINEL).doesNotContain("before").doesNotContain("after");
    }

    @Test
    void readFile_blocksCanonicalCredentialPathEvenWithOrdinaryContent() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/.env.production", "ordinary");

        String result = new SandboxAgentTools(sandbox, "s").readFile("solution/.env.production");

        assertThat(result).contains("CREDENTIAL_FILE").contains("solution/.env.production").doesNotContain("ordinary");
    }

    @Test
    void search_returnsMatchingLinesWithoutDirtyingTheWorkspace() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(new SandboxExecResultDTO(0,
                "/workspace/problem-statement.md:2:Elevator list is empty\n/workspace/problem-statement.md:3:last Elevator list is empty\n", "", false));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        boolean dirtyBefore = tools.checkpointState().dirtySinceLastPassingCheck();

        assertThat(tools.search("problem-statement.md", "Elevator list is empty"))
                .isEqualTo("problem-statement.md:2:Elevator list is empty\nproblem-statement.md:3:last Elevator list is empty");
        assertThat(tools.checkpointState().dirtySinceLastPassingCheck()).isEqualTo(dirtyBefore);
    }

    @Test
    void search_acceptsADirectoryPath() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(new SandboxExecResultDTO(0,
                "/workspace/reference/.env.production:1:@WhitelistPath(\"secret\")\n/workspace/reference/tests/Example.java:4:@WhitelistPath(\"target\")\n", "", false));

        assertThat(new SandboxAgentTools(sandbox, "s").search("reference/tests", "@WhitelistPath")).isEqualTo("reference/tests/Example.java:4:@WhitelistPath(\"target\")");
    }

    @Test
    void search_rejectsUnsafePathsAndMultilineQueries() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.search("../secret", "token")).startsWith("ERROR: invalid path");
        assertThat(tools.search("problem-statement.md", "one\ntwo")).isEqualTo("ERROR: query must be non-empty text from a single line.");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void editFile_rejectsAmbiguousMatch() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/A.java", "x x x");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.editFile("solution/A.java", "x", "y");
        assertThat(result).contains("occurs 3 times").contains("more surrounding context");
    }

    @Test
    void editFile_rejectsMissingMatch() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/A.java", "hello");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.editFile("solution/A.java", "world", "y");
        assertThat(result).contains("not found");
    }

    @Test
    void editFile_appliesUniqueReplacement() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/A.java", "return 0; // TODO");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.editFile("solution/A.java", "return 0;", "return a + b;");
        assertThat(result).isEqualTo("Replaced 1 occurrence in solution/A.java.");
    }

    @Test
    void editFile_toleratesTrailingWhitespaceAndSmartQuoteDrift() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        // The file has a trailing space after "b;" and a smart quote in the comment; the model re-types both in plain ASCII without the trailing space.
        sandbox.files().put("/workspace/solution/A.java", "int a;\nint b; \n// it\u2019s fine\nint c;");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.editFile("solution/A.java", "int b;\n// it's fine", "int b2;\n// still fine");

        assertThat(result).isEqualTo("Replaced 1 occurrence in solution/A.java.");
        // Untouched lines keep their original bytes; only the matched lines are rewritten.
        String written = new String(java.util.Base64.getDecoder().decode(sandbox.lastWrittenBase64()), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(written).isEqualTo("int a;\nint b2;\n// still fine\nint c;");
    }

    @Test
    void editFile_tolerantMatchStillRejectsAmbiguityWithACount() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/A.java", "x \nx \ny");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.editFile("solution/A.java", "x\n", "z\n");

        assertThat(result).contains("occurs 2 times").contains("whitespace-only differences");
    }

    /**
     * The tools' inline caps and the agent loop's per-tool-result cap are two constants in two classes that only work as a pair: if a tool returns more than the loop keeps, the
     * loop elides on top of the tool's own elision and the tool's truncation marker misdescribes what the model can still see.
     */
    @Test
    void inlineOutputCaps_stayBelowTheAgentLoopPerToolResultCap() {
        assertThat(SandboxAgentTools.READ_INLINE_MAX_CHARS).isLessThan(AgentLoopRunner.MAX_TOOL_RESPONSE_CHARS);
        // Bytes, not characters: UTF-8 never encodes a character in fewer than one byte, so staying under the cap in bytes keeps it under in characters too.
        assertThat(SandboxAgentTools.BASH_TAIL_BYTES).isLessThan(AgentLoopRunner.MAX_TOOL_RESPONSE_CHARS);
    }

    @Test
    void readFile_longFile_isPagedWithAnActionableContinuationFooter() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        String line = "x".repeat(99);
        sandbox.files().put("/workspace/solution/Big.java", (line + "\n").repeat(300));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.readFile("solution/Big.java");

        assertThat(result.length()).isLessThanOrEqualTo(SandboxAgentTools.READ_INLINE_MAX_CHARS + 200);
        assertThat(result).contains("[Showing lines 1-100 of 300. Call read_file with offset=101 to continue.]");
    }

    @Test
    void readFile_offsetAndLimit_returnTheRequestedSliceWithAFooterWhenMoreRemains() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/A.java", "l1\nl2\nl3\nl4\nl5");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.readFile("solution/A.java", 2, 2)).isEqualTo("l2\nl3\n\n[Showing lines 2-3 of 5. Call read_file with offset=4 to continue.]");
        assertThat(tools.readFile("solution/A.java", 4, null)).isEqualTo("l4\nl5");
        assertThat(tools.readFile("solution/A.java", null, null)).isEqualTo("l1\nl2\nl3\nl4\nl5");
    }

    @Test
    void readFile_offsetBeyondEndOfFile_returnsAnActionableError() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/A.java", "l1\nl2");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.readFile("solution/A.java", 7, null)).isEqualTo("ERROR: offset 7 is beyond the end of \'solution/A.java\' (2 lines total).");
    }

    @Test
    void readFile_giantSingleLine_pointsAtABashSliceRecipeInsteadOfReturningNothing() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        sandbox.files().put("/workspace/solution/one-liner.json", "y".repeat(SandboxAgentTools.READ_INLINE_MAX_CHARS + 5));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.readFile("solution/one-liner.json")).contains("Line 1").contains("sed -n \'1p\' solution/one-liner.json");
    }

    @Test
    void writeFile_rejectsTestsRepositoryHarnessFiles() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.writeFile("tests/pom.xml", "<project/>");

        assertThat(result).startsWith("ERROR: do not modify tests/pom.xml");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void writeFile_rejectsWorkspaceBuildInfrastructureOutsideTheExerciseRepositories() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.writeFile("buildSrc/build.gradle", "plugins { id 'java' }");

        assertThat(result).contains("Workspace build infrastructure is managed by Artemis");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void writeFile_rejectsSeededBuildInfrastructureInsideExerciseRepositories() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.writeFile("solution/build.gradle", "plugins { id 'java' }")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(tools.writeFile("template/buildSrc/src/main/java/FakePlugin.java", "class FakePlugin {}")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(tools.writeFile("solution/.mvn/maven.config", "-o")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(tools.writeFile("solution/.m2/plugin.jar", "generated")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(tools.writeFile("tests/target/test-classes/Test.class", "generated")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void writeFile_keepsStagedWritesMonotonicWhileLegacyRepairsRemainUnrestricted() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools legacy = new SandboxAgentTools(sandbox, "s");
        assertThat(legacy.writeFile("SPEC.md", "## Rules")).startsWith("Wrote ");
        assertThat(legacy.writeFile("test-plan.json", "{\"tests\":[]}")).startsWith("Wrote ");
        assertThat(legacy.writeFile("problem-statement.md", "# Exercise")).startsWith("Wrote ");

        for (GenerationStage stage : GenerationStage.values()) {
            SandboxAgentTools staged = new SandboxAgentTools(new FakeInteractiveSandbox(), "s");
            staged.enterStage(stage);
            if (stage != GenerationStage.SPEC) {
                assertThat(staged.writeFile("SPEC.md", "## Rules")).as("stage %s", stage).contains("cannot write");
            }
            else {
                assertThat(staged.writeFile("SPEC.md", "## Rules")).as("stage %s", stage).startsWith("Wrote ");
            }
            if (stage == GenerationStage.TESTS) {
                assertThat(staged.writeFile("test-plan.json", "{\"tests\":[]}")).as("stage %s", stage).startsWith("Wrote ");
            }
            else {
                assertThat(staged.writeFile("test-plan.json", "{\"tests\":[]}")).as("stage %s", stage).contains("cannot write");
            }
            if (stage == GenerationStage.STATEMENT) {
                assertThat(staged.writeFile("problem-statement.md", "# Exercise")).startsWith("Wrote ");
            }
            else {
                assertThat(staged.writeFile("problem-statement.md", "# Exercise")).contains("cannot write");
            }
        }

        // The executable artifacts are authored together in the TESTS stage, so all three roots are writable there and nowhere else.
        SandboxAgentTools testsStage = new SandboxAgentTools(new FakeInteractiveSandbox(), "s");
        testsStage.enterStage(GenerationStage.TESTS);
        assertThat(testsStage.writeFile("solution/src/Answer.java", "class Answer {}")).startsWith("Wrote ");
        assertThat(testsStage.writeFile("template/src/Answer.java", "class Answer {}")).startsWith("Wrote ");
        assertThat(testsStage.writeFile("tests/test/AnswerTest.java", "class AnswerTest {}")).startsWith("Wrote ");

        SandboxAgentTools statementStage = new SandboxAgentTools(new FakeInteractiveSandbox(), "s");
        statementStage.enterStage(GenerationStage.STATEMENT);
        assertThat(statementStage.writeFile("solution/src/Answer.java", "class Answer {}")).contains("cannot write");
        assertThat(statementStage.writeFile("tests/test/AnswerTest.java", "class AnswerTest {}")).contains("cannot write");
    }

    @Test
    void repairScope_limitsStructuredWritesToTheFindingSurfaceAndCanBeExited() {
        SandboxAgentTools tools = new SandboxAgentTools(new FakeInteractiveSandbox(), "s");

        tools.enterRepairScope(Set.of("tests", "test-plan.json"));

        assertThat(tools.writeFile("tests/test/AnswerTest.java", "class AnswerTest {}")).startsWith("Wrote ");
        assertThat(tools.writeFile("test-plan.json", "{\"tests\":[]}")).startsWith("Wrote ");
        assertThat(tools.writeFile("solution/src/Answer.java", "class Answer {}")).contains("current repair").contains("cannot write");
        assertThat(tools.writeFile("template/src/Answer.java", "class Answer {}")).contains("current repair").contains("cannot write");

        tools.exitRepairScope();
        assertThat(tools.writeFile("solution/src/Answer.java", "class Answer {}")).startsWith("Wrote ");
    }

    @Test
    void writeFile_rejectsATemplateDeclarationThatViolatesTheApprovedStudentOwnership() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        StageCheckService stageChecks = mock(StageCheckService.class);
        when(stageChecks.validateArtifactWrite("s", "template/src/FuelStrategy.java", "public interface FuelStrategy {}"))
                .thenReturn(Optional.of("The approved specification assigns FuelStrategy to the student."));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", null, null, Map.of(), false, stageChecks);
        tools.enterStage(GenerationStage.TESTS);

        String result = tools.writeFile("template/src/FuelStrategy.java", "public interface FuelStrategy {}");

        assertThat(result).contains("approved specification assigns FuelStrategy to the student");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void writeFile_rejectsACommentOnlyArtifactNamedAfterAStudentCreatedType() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        StageCheckService stageChecks = mock(StageCheckService.class);
        when(stageChecks.validateArtifactWrite("s", "template/src/FuelStrategy.java", "// TODO: create this type"))
                .thenReturn(Optional.of("The approved specification assigns FuelStrategy to the student."));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", null, null, Map.of(), false, stageChecks);
        tools.enterStage(GenerationStage.TESTS);

        assertThat(tools.writeFile("template/src/FuelStrategy.java", "// TODO: create this type")).contains("assigns FuelStrategy to the student");
        assertThat(sandbox.execCount()).isZero();
    }

    @Test
    void bash_surfacesAndRepairsAnOutOfBandApprovedSpecMutation() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        StageCheckService stageChecks = mock(StageCheckService.class);
        when(stageChecks.restoreApprovedSpecAfterCommand(sandbox, "s"))
                .thenReturn(Optional.of("ERROR: the shell command changed read-only SPEC.md. Artemis restored the approved specification."));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", null, null, Map.of(), false, stageChecks);

        String result = tools.bash("printf tampered > SPEC.md");

        assertThat(result).contains("changed read-only SPEC.md").contains("restored the approved specification");
    }

    @Test
    void bash_immediatelySurfacesAnOutOfBandStudentCreatedTemplateArtifact() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        StageCheckService stageChecks = mock(StageCheckService.class);
        when(stageChecks.approvedOwnershipViolationAfterCommand(sandbox, "s"))
                .thenReturn(Optional.of("ERROR: the shell command introduced template artifacts for student-created types."));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", null, null, Map.of(), false, stageChecks);

        String result = tools.bash("cp solution/src/FuelStrategy.java template/src/FuelStrategy.java");

        assertThat(result).contains("introduced template artifacts for student-created types");
    }

    @Test
    void deleteFile_removesOnlyGeneratedWorkspaceFiles() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.deleteFile("solution/src/main/java/Wrong.java")).isEqualTo("Deleted solution/src/main/java/Wrong.java");
        assertThat(tools.deleteFile("../secret")).startsWith("ERROR: invalid path");
        assertThat(tools.deleteFile("tests/pom.xml")).startsWith("ERROR: do not modify tests/pom.xml");
        assertThat(sandbox.execCount()).isOne();
    }

    private static SandboxExecResultDTO bashStdout(int exitCode, String stdout) {
        return new SandboxExecResultDTO(exitCode, stdout, "", false);
    }

    @Test
    void bash_buildsSpillWrapper_subshellUlimitRedirectTailAndSoftTimeout() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=5 lines=1\nhello"));
        new SandboxAgentTools(sandbox, "s").bash("echo hello");
        // The command travels base64-encoded into its own script file (quoting can never corrupt the wrapper), runs in a subshell under a file-size ulimit with stdin from
        // /dev/null and combined output to the log, is stopped by coreutils `timeout` before the session-destroying exec timeout when the image has it, and a bounded tail
        // comes back.
        String encoded = java.util.Base64.getEncoder().encodeToString("echo hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(sandbox.lastScript()).contains("printf '%s' '" + encoded + "' | base64 -d > \"$CMD\"")
                .contains("if [ \"$snapshot_ok\" -eq 1 ] && command -v timeout >/dev/null 2>&1; then")
                .contains("( ulimit -f 65536 2>/dev/null; cd /workspace && timeout 270 sh \"$CMD\" )").contains("( ulimit -f 65536 2>/dev/null; cd /workspace && sh \"$CMD\" )")
                .contains("</dev/null > \"$LOG\" 2>&1").contains("rc=$?").contains("/tmp/hyperion/bash-0.log").contains("/tmp/hyperion/bash-0.sh").contains("__HYP_META__")
                .contains("tail -c 10000");
    }

    @Test
    void bash_inAStageSnapshotsAndRestoresArtifactsOutsideThatStagesWriteBoundary() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=2 lines=1\nok"));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        tools.enterStage(GenerationStage.STATEMENT);

        tools.bash("printf changed > test-plan.json");

        assertThat(sandbox.lastScript()).contains("for item in SPEC.md solution template tests test-plan.json").contains("diff -qr \"$SNAP/data/$item\" \"/workspace/$item\"")
                .contains("Artemis restored those protected artifacts").contains("rc=2");
    }

    @Test
    void bash_inARepairScopeSnapshotsAndRestoresArtifactsOutsideThatRepairSurface() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=2 lines=1\nok"));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        tools.enterRepairScope(Set.of("tests", "test-plan.json"));

        tools.bash("printf changed > solution/src/Answer.java");

        assertThat(sandbox.lastScript()).contains("for item in SPEC.md solution template problem-statement.md").doesNotContain("for item in tests")
                .contains("Artemis restored those protected artifacts").contains("rc=2");
    }

    @Test
    void bash_softTimeoutExitCode_namesTheLikelyCauseAndKeepsThePartialOutput() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=124 bytes=8 lines=1\npartial…"));
        String out = new SandboxAgentTools(sandbox, "s").bash("mvn -q verify");
        assertThat(out).startsWith("exit=124\n").contains("partial…").contains("stopped at the 270-second limit").contains("output above is partial");
    }

    @Test
    void bash_withNoCommand_returnsHelpfulErrorWithoutTouchingTheSandbox() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "unused"));
        String out = new SandboxAgentTools(sandbox, "s").bash(null);
        assertThat(out).contains("No command provided").contains("\"command\"");
        assertThat(sandbox.lastScript()).isNull();
    }

    @Test
    void bash_mangledJsonArrayCommand_isRejectedLoudlyWithoutTouchingTheSandbox() {
        // The mangled array form must be rejected with a non-zero exit and never reach the sandbox.
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("[bash, -lc, ls -R]");
        assertThat(out).startsWith("exit=2\n").contains("must be a single shell string").contains("JSON array");
        assertThat(sandbox.lastScript()).isNull();
    }

    @Test
    void bash_posixTestCommand_isNotMistakenForAMangledArray() {
        // A POSIX test ("[ -f x ]", space after the bracket) must run normally; only the no-space array render is rejected.
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        String out = new SandboxAgentTools(sandbox, "s").bash("[ -f solution/pom.xml ] && echo yes");
        assertThat(out).startsWith("exit=0");
        assertThat(sandbox.lastScript())
                .contains(java.util.Base64.getEncoder().encodeToString("[ -f solution/pom.xml ] && echo yes".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void bash_authoritativeExitCodeComesFromMetaNotWrapper() {
        // The container exec exit code reflects the wrapper (0); the real command exit code and output must come from the meta line, never a blank success-looking observation.
        String out = new SandboxAgentTools(FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=127 bytes=21 lines=1\nsh: nope: not found")), "s").bash("nope");
        assertThat(out).isEqualTo("exit=127\nsh: nope: not found");
    }

    @Test
    void bash_smallOutput_hasNoTruncationMarker() {
        String out = new SandboxAgentTools(FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=11 lines=1\nhello world")), "s").bash("echo hello world");
        assertThat(out).isEqualTo("exit=0\nhello world").doesNotContain("Full output");
    }

    @Test
    void bash_blocksSupportedSecretMaterialWithoutReturningTheMatch() {
        String wrapperOutput = "__HYP_META__ rc=0 bytes=43 lines=1\n" + GITHUB_SENTINEL;

        String result = new SandboxAgentTools(FakeInteractiveSandbox.returning(bashStdout(0, wrapperOutput)), "s").bash("printenv");

        assertThat(result).contains("GITHUB_TOKEN").contains("tool/bash").doesNotContain(GITHUB_SENTINEL);
    }

    @Test
    void bash_largeOutput_appendsSpillMarkerWithReadInstructions() {
        String body = "x".repeat(10_000);
        String out = new SandboxAgentTools(FakeInteractiveSandbox.returning(bashStdout(1, "__HYP_META__ rc=1 bytes=50000 lines=900\n" + body)), "s").bash("sh verify.sh solution");
        assertThat(out).startsWith("exit=1\n").contains("Showing the last 10000 of 50000 bytes (900 lines total)")
                .contains("Full output saved in the sandbox at /tmp/hyperion/bash-0.log").contains("tail -n 200 /tmp/hyperion/bash-0.log");
    }

    @Test
    void bash_timeoutFailsBecauseTheSandboxWasTerminated() {
        SandboxAgentTools tools = new SandboxAgentTools(FakeInteractiveSandbox.returning(new SandboxExecResultDTO(-1, "", "", true)), "s");

        assertThatThrownBy(() -> tools.bash("sleep 999")).isInstanceOf(LocalCIException.class).hasMessageContaining("sandbox session was terminated");
    }

    @Test
    void bash_spillSequenceIncrementsPerCall() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        tools.bash("a");
        assertThat(sandbox.lastScript()).contains("/tmp/hyperion/bash-0.log");
        tools.bash("b");
        assertThat(sandbox.lastScript()).contains("/tmp/hyperion/bash-1.log");
    }

    @Test
    void bash_metaAbsent_fallsBackToRawOutput() {
        String out = new SandboxAgentTools(FakeInteractiveSandbox.returning(bashStdout(3, "unexpected wrapper failure")), "s").bash("x");
        assertThat(out).isEqualTo("exit=3\nunexpected wrapper failure");
    }

    @Test
    void bash_applyPatchCommand_shortCircuitsLoudlyWithoutTouchingTheSandbox() {
        // A bare `apply_patch` must short-circuit with a loud, non-zero result and never reach the sandbox.
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("apply_patch");
        assertThat(out).isEqualTo("exit=2\napply_patch is NOT available. Use write_file (new file / full rewrite) or edit_file (exact unique snippet) instead.");
        assertThat(sandbox.lastScript()).isNull();
    }

    @Test
    void bash_harnessMutationCommand_shortCircuitsLoudlyWithoutTouchingTheSandbox() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "should not run"));

        String out = new SandboxAgentTools(sandbox, "s").bash("cat > tests/pom.xml <<'EOF'\n<project/>\nEOF");

        assertThat(out).startsWith("exit=2\n").contains("Do not modify tests-repository build/harness files");
        assertThat(sandbox.lastScript()).isNull();
    }

    @Test
    void bash_commandMentioningApplyPatch_isNotMistakenForAnInvocation() {
        // A command that merely mentions apply_patch (e.g. grepping for it) must run normally; only the first shell word matters.
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        String out = new SandboxAgentTools(sandbox, "s").bash("grep -r apply_patch tests");
        assertThat(out).startsWith("exit=0");
        assertThat(sandbox.lastScript()).contains(java.util.Base64.getEncoder().encodeToString("grep -r apply_patch tests".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void isApplyPatchInvocation_matchesOnlyTheFirstProgramWord() {
        assertThat(SandboxAgentTools.isApplyPatchInvocation("apply_patch")).isTrue();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("  apply_patch <<'EOF'")).isTrue();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("./apply_patch foo")).isTrue();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("/usr/bin/apply_patch")).isTrue();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("apply_patch<<'EOF'")).isTrue();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("grep apply_patch x")).isFalse();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("echo apply_patch")).isFalse();
        assertThat(SandboxAgentTools.isApplyPatchInvocation("sh verify.sh solution")).isFalse();
    }

    @Test
    void isRenderedArgvArray_matchesTheArgvArrayFormButNotAPosixTest() {
        // Rendered argv array (bracket, no space, comma inside): matches.
        assertThat(SandboxAgentTools.isRenderedArgvArray("[bash, -lc, ls -R]")).isTrue();
        assertThat(SandboxAgentTools.isRenderedArgvArray("  [sh, -c, sh verify.sh solution]  ")).isTrue();
        assertThat(SandboxAgentTools.isRenderedArgvArray("[ls -R, grep foo]")).isTrue();
        // POSIX test (space after the bracket): does not match.
        assertThat(SandboxAgentTools.isRenderedArgvArray("[ -f solution/pom.xml ]")).isFalse();
        assertThat(SandboxAgentTools.isRenderedArgvArray("[ -d tests ] && echo y")).isFalse();
        // Single-element render (no comma) and ordinary commands: do not match.
        assertThat(SandboxAgentTools.isRenderedArgvArray("[ls]")).isFalse();
        assertThat(SandboxAgentTools.isRenderedArgvArray("ls -R solution template tests")).isFalse();
        assertThat(SandboxAgentTools.isRenderedArgvArray("grep -n 'a,b' tests/Foo.java")).isFalse();
    }

    @Test
    void mutatesManagedBuildInfrastructure_flagsOnlyLikelyWrites() {
        assertThat(SandboxAgentTools.mutatesManagedBuildInfrastructure("cat > tests/pom.xml")).isTrue();
        assertThat(SandboxAgentTools.mutatesManagedBuildInfrastructure("cat > solution/build.gradle")).isTrue();
        assertThat(SandboxAgentTools.mutatesManagedBuildInfrastructure("sed -i 's/a/b/' tests/package.json")).isTrue();
        assertThat(SandboxAgentTools.mutatesManagedBuildInfrastructure("cat tests/pom.xml")).isFalse();
        assertThat(SandboxAgentTools.mutatesManagedBuildInfrastructure("cat > tests/test/de/test/CalculatorTest.java")).isFalse();
    }

    @Test
    void verify_blocksSupportedSecretMaterialWithoutReturningTheMatch() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        AgentVerifyReport report = new AgentVerifyReport(0, false, List.of(), 0, false, false, List.of(), List.of(), List.of(), List.of(), false,
                List.of("failure " + GITHUB_SENTINEL));
        when(verifier.selfCheck(eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), eq(false), anySet())).thenReturn(report);

        String result = new SandboxAgentTools(sandbox, "s", verifier, exercise).verify();

        assertThat(result).contains("GITHUB_TOKEN").contains("tool/verify").doesNotContain(GITHUB_SENTINEL).doesNotContain("failure");
    }

    @Test
    void verify_whenVerifierUnavailable_returnsAnActionableFallback() {
        // No verifier (two-arg ctor): the tool must point at the bash fallback rather than NPE.
        String out = new SandboxAgentTools(new FakeInteractiveSandbox(), "s").verify();
        assertThat(out).startsWith("ERROR: the verify tool is unavailable").contains("sh verify.sh solution");
    }

    // Stage-aware verify()/submit(): a staged session delegates to the wired StageCheckService for the CURRENT stage, while an unstaged session runs the full differential
    // through the verifier directly.

    @Test
    void unstagedRepairVerifyRefreshesAndThreadsTheStructuralOracle() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        Set<String> names = Set.of("testClass[StudentStrategy]");
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("testFoo", "testClass[StudentStrategy]"), List.of(), List.of(), true,
                List.of());
        when(verifier.selfCheck(eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), eq(false), eq(names))).thenReturn(report);
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", verifier, exercise);
        tools.configureStructuralOracleRefresh(() -> names);

        assertThat(tools.verify()).isEqualTo(report.toObservation());
    }

    private static SandboxAgentTools stagedTools(InteractiveSandbox sandbox, ProgrammingExercise exercise, StageCheckService stageCheckService) {
        return new SandboxAgentTools(sandbox, "s", null, exercise, Map.of(), false, stageCheckService);
    }

    @Test
    void verify_inStagedSession_delegatesToTheStageCheckServiceForTheCurrentStageAndNeverTouchesTheVerifier() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        StageCheckService stageCheckService = mock(StageCheckService.class);

        for (GenerationStage stage : GenerationStage.values()) {
            when(stageCheckService.check(eq(stage), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet()))
                    .thenReturn(StageCheckResult.passed("stage " + stage + " note"));
            SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", verifier, exercise, Map.of(), false, stageCheckService);
            tools.enterStage(stage);

            String out = tools.verify();

            assertThat(out).as("stage %s", stage).contains("MECHANICAL PRECHECK: PASS").contains("stage " + stage + " note");
        }
        verifyNoInteractions(verifier);
    }

    @Test
    void verify_inStagedSession_reportsAFailingStageCheckAsAMechanicalPrecheckFailure() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.SPEC), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet()))
                .thenReturn(StageCheckResult.failed("SPEC.md is missing required section(s)"));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.SPEC);

        String out = tools.verify();

        assertThat(out).contains("MECHANICAL PRECHECK: FAIL").contains("SPEC.md is missing required section(s)");
    }

    @Test
    void verify_inTestsStage_returnsTheDifferentialObservationVerbatimWithoutDoublingTheVerdictLine() {
        // The TESTS stage's observation already carries its own MECHANICAL PRECHECK line (AgentVerifyReport#toObservation()); verify() must not prepend a second one.
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("t_a"), List.of(), List.of(), true, List.of());
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet()))
                .thenReturn(new StageCheckResult(true, report.toObservation(), report));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        String out = tools.verify();

        assertThat(out).isEqualTo(report.toObservation());
    }

    @Test
    void verify_inStatementStage_resolvesBindingsAgainstTheTestsStagesExactTestNames() {
        // The TESTS stage's report must be threaded into the STATEMENT stage's binding check, even though verify() never re-runs a build for STATEMENT.
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        AgentVerifyReport testsReport = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("t_a"), List.of(), List.of(), true, List.of());
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), isNull(), anySet()))
                .thenReturn(new StageCheckResult(true, testsReport.toObservation(), testsReport));
        when(stageCheckService.check(eq(GenerationStage.STATEMENT), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), eq(testsReport), anySet()))
                .thenReturn(StageCheckResult.passed(""));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);

        tools.enterStage(GenerationStage.TESTS);
        tools.verify();
        tools.enterStage(GenerationStage.STATEMENT);
        String out = tools.verify();

        assertThat(out).contains("MECHANICAL PRECHECK: PASS");
    }

    @Test
    void verify_inStagedSession_whenTheStageCheckServiceIsUnwired_returnsAnActionableFallbackInsteadOfTheLegacyDifferential() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", verifier, exercise); // 4-arg ctor: stageCheckService is null
        tools.enterStage(GenerationStage.TESTS);

        String out = tools.verify();

        assertThat(out).contains("ERROR").contains("stage-check service is unavailable");
        verifyNoInteractions(verifier);
    }

    // submit(): staged sessions run this stage's own check first and veto the loop-ending effect on a failure; unstaged sessions are never gated.

    @Test
    void submit_inStagedSession_rejectsAFailingStageCheckAndSetsTheVeto() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet()))
                .thenReturn(StageCheckResult.failed("the reference solution does not compile"));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        String out = tools.submit("done");

        assertThat(out).startsWith("SUBMIT REJECTED").contains("the reference solution does not compile");
        assertThat(tools.consumeSubmitVeto()).isTrue();
        assertThat(tools.consumeSubmitVeto()).as("consuming the veto clears it").isFalse();
    }

    @Test
    void submit_inStagedSession_afterAFixThatPasses_succeedsWithNoVeto() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet()))
                .thenReturn(StageCheckResult.failed("the reference solution does not compile"), StageCheckResult.passed(""));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        String rejected = tools.submit("done");
        String accepted = tools.submit("done");

        assertThat(rejected).startsWith("SUBMIT REJECTED");
        assertThat(accepted).isEqualTo("Submitted for verification: done");
        assertThat(tools.consumeSubmitVeto()).as("the passing resubmit must not leave a stale veto").isFalse();
    }

    @Test
    void submit_inUnstagedLegacySession_isNeverGated() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s"); // currentStage stays null

        assertThat(tools.submit(null)).isEqualTo("Submitted for verification.");
        assertThat(tools.consumeSubmitVeto()).isFalse();
    }

    // Dirty-flag lifecycle: write/delete/bash mark the stage dirty; a passing check clears it; enterStage always resets to dirty.

    @Test
    void reuseCachedPassingCheck_isEmptyUntilAPassingCheckHasRunForThatStage() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isEmpty();
    }

    @Test
    void reuseCachedPassingCheck_afterAPassingVerify_isPresentUntilAWriteFileMarksItDirtyAgain() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet())).thenReturn(StageCheckResult.passed("clean"));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        tools.verify();
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).contains(new StageCheckResult(true, "clean", null));

        tools.writeFile("solution/A.java", "x");
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).as("a write after the passing check must invalidate the cache").isEmpty();
    }

    @Test
    void reuseCachedPassingCheck_afterAPassingVerify_isInvalidatedByDeleteFile() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet())).thenReturn(StageCheckResult.passed("clean"));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        tools.verify();
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isPresent();

        tools.deleteFile("solution/Old.java");
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isEmpty();
    }

    @Test
    void reuseCachedPassingCheck_afterAPassingVerify_isInvalidatedByBashEvenThoughItSkipsTheWriteGuardrails() {
        FakeInteractiveSandbox sandbox = FakeInteractiveSandbox.returning(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), anyString(), eq(exercise), eq(Map.of()), any(), anySet()))
                .thenReturn(StageCheckResult.passed("clean"));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);

        tools.verify();
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isPresent();

        tools.bash("ls");
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isEmpty();
    }

    @Test
    void reuseCachedPassingCheck_isClearedByEnterStageEvenForTheSameStage() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.TESTS), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet())).thenReturn(StageCheckResult.passed("clean"));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.TESTS);
        tools.verify();
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isPresent();

        tools.enterStage(GenerationStage.TESTS); // a fresh re-entry into the same stage must not read a stale pass

        assertThat(tools.reuseCachedPassingCheck(GenerationStage.TESTS)).isEmpty();
    }

    @Test
    void reuseCachedPassingCheck_isClearedByExitStagedGeneration() {
        FakeInteractiveSandbox sandbox = new FakeInteractiveSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        StageCheckService stageCheckService = mock(StageCheckService.class);
        when(stageCheckService.check(eq(GenerationStage.STATEMENT), eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), any(), anySet())).thenReturn(StageCheckResult.passed(""));
        SandboxAgentTools tools = stagedTools(sandbox, exercise, stageCheckService);
        tools.enterStage(GenerationStage.STATEMENT);
        tools.verify();
        assertThat(tools.reuseCachedPassingCheck(GenerationStage.STATEMENT)).isPresent();

        tools.exitStagedGeneration();

        assertThat(tools.reuseCachedPassingCheck(GenerationStage.STATEMENT)).isEmpty();
        // A tool call after exiting staged mode must not be gated or dispatched through the still-wired stage-check service.
        assertThat(tools.submit(null)).isEqualTo("Submitted for verification.");
    }

    @Test
    void agentVerifyReport_observation_truncatesLongNameLists() {
        // A long name list is truncated with a remaining-count so it cannot flood the agent's context.
        List<String> names = java.util.stream.IntStream.range(0, 60).mapToObj(i -> "t" + i).toList();
        AgentVerifyReport report = new AgentVerifyReport(60, true, List.of(), 60, true, true, List.of(), names, List.of(), List.of(), true, List.of());
        assertThat(report.toObservation()).contains("(+20 more)");
    }
}
