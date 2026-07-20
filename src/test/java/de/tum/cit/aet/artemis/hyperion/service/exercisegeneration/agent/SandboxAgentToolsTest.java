package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.SandboxExecResult;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.AgentVerifyReport;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification.DifferentialVerificationService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the agent tools, focused on the security-relevant path allowlist and the all-or-nothing edit semantics. A fake sandbox records the commands it is asked to run
 * so the tests can assert that unsafe paths never reach the shell.
 */
class SandboxAgentToolsTest {

    private static final String GITHUB_SENTINEL = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    /** Records commands and serves canned file content keyed by absolute container path. */
    private static final class RecordingSandbox implements InteractiveSandbox {

        private final Map<String, String> files = new HashMap<>();

        private int execCount;

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            execCount++;
            if (command.length >= 2 && "cat".equals(command[0])) {
                String content = files.get(command[1]);
                return content == null ? new SandboxExecResult(1, "", "no such file", false) : new SandboxExecResult(0, content, "", false);
            }
            return new SandboxExecResult(0, "", "", false);
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

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
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.readFile("../secret");
        assertThat(result).startsWith("ERROR: invalid path");
        assertThat(sandbox.execCount).isZero();
    }

    @Test
    void readFile_blocksSupportedSecretMaterialWithoutReturningTheMatch() {
        RecordingSandbox sandbox = new RecordingSandbox();
        sandbox.files.put("/workspace/solution/src/fixture.txt", "before " + GITHUB_SENTINEL + " after");

        String result = new SandboxAgentTools(sandbox, "s").readFile("solution/src/fixture.txt");

        assertThat(result).contains("GITHUB_TOKEN").contains("solution/src/fixture.txt").doesNotContain(GITHUB_SENTINEL).doesNotContain("before").doesNotContain("after");
    }

    @Test
    void readFile_blocksCanonicalCredentialPathEvenWithOrdinaryContent() {
        RecordingSandbox sandbox = new RecordingSandbox();
        sandbox.files.put("/workspace/solution/.env.production", "ordinary");

        String result = new SandboxAgentTools(sandbox, "s").readFile("solution/.env.production");

        assertThat(result).contains("CREDENTIAL_FILE").contains("solution/.env.production").doesNotContain("ordinary");
    }

    @Test
    void editFile_rejectsAmbiguousMatch() {
        RecordingSandbox sandbox = new RecordingSandbox();
        sandbox.files.put("/workspace/solution/A.java", "x x x");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.editFile("solution/A.java", "x", "y");
        assertThat(result).contains("more than once");
    }

    @Test
    void editFile_rejectsMissingMatch() {
        RecordingSandbox sandbox = new RecordingSandbox();
        sandbox.files.put("/workspace/solution/A.java", "hello");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.editFile("solution/A.java", "world", "y");
        assertThat(result).contains("not found");
    }

    @Test
    void editFile_appliesUniqueReplacement() {
        RecordingSandbox sandbox = new RecordingSandbox();
        sandbox.files.put("/workspace/solution/A.java", "return 0; // TODO");
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        String result = tools.editFile("solution/A.java", "return 0;", "return a + b;");
        assertThat(result).startsWith("Wrote ");
    }

    @Test
    void writeFile_rejectsTestsRepositoryHarnessFiles() {
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.writeFile("tests/pom.xml", "<project/>");

        assertThat(result).startsWith("ERROR: do not modify tests/pom.xml");
        assertThat(sandbox.execCount).isZero();
    }

    @Test
    void writeFile_rejectsWorkspaceBuildInfrastructureOutsideTheExerciseRepositories() {
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String result = tools.writeFile("buildSrc/build.gradle", "plugins { id 'java' }");

        assertThat(result).contains("Workspace build infrastructure is managed by Artemis");
        assertThat(sandbox.execCount).isZero();
    }

    @Test
    void writeFile_rejectsSeededBuildInfrastructureInsideExerciseRepositories() {
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.writeFile("solution/build.gradle", "plugins { id 'java' }")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(tools.writeFile("template/buildSrc/src/main/java/FakePlugin.java", "class FakePlugin {}")).contains("build infrastructure is seeded and managed by Artemis");
        assertThat(sandbox.execCount).isZero();
    }

    @Test
    void writeFile_acceptsDesignMdAtTheWorkspaceRootInEveryStageAndInLegacyMode() {
        // DESIGN.md is the one workspace-root file allowed: working memory for the staged workflow, never persisted by extraction, updatable from any stage (or legacy/unstaged
        // sessions, where currentStage stays null).
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools legacy = new SandboxAgentTools(sandbox, "s");
        assertThat(legacy.writeFile("DESIGN.md", "## Classes")).startsWith("Wrote ");

        for (GenerationStage stage : GenerationStage.values()) {
            SandboxAgentTools staged = new SandboxAgentTools(new RecordingSandbox(), "s");
            staged.enterStage(stage);
            assertThat(staged.writeFile("DESIGN.md", "## Classes")).as("stage %s", stage).startsWith("Wrote ");
        }
    }

    @Test
    void writeFile_designMdContentRoundTripsThroughReadFile() {
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        String written = tools.writeFile("DESIGN.md", "## Classes\n| Foo | role |\n");
        assertThat(written).isEqualTo("Wrote " + "## Classes\n| Foo | role |\n".length() + " characters to DESIGN.md");

        // The recording sandbox only serves 'cat' against content it was told to hold; simulate the write landing so the round trip is observable through readFile.
        sandbox.files.put("/workspace/DESIGN.md", "## Classes\n| Foo | role |\n");
        assertThat(tools.readFile("DESIGN.md")).isEqualTo("## Classes\n| Foo | role |\n");
    }

    @Test
    void deleteFile_removesOnlyGeneratedWorkspaceFiles() {
        RecordingSandbox sandbox = new RecordingSandbox();
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");

        assertThat(tools.deleteFile("solution/src/main/java/Wrong.java")).isEqualTo("Deleted solution/src/main/java/Wrong.java");
        assertThat(tools.deleteFile("../secret")).startsWith("ERROR: invalid path");
        assertThat(tools.deleteFile("tests/pom.xml")).startsWith("ERROR: do not modify tests/pom.xml");
        assertThat(sandbox.execCount).isOne();
    }

    /** Records the exec script and returns a scripted result, to test the bash spill wrapper and output composition without Docker. */
    private static final class ScriptedSandbox implements InteractiveSandbox {

        private final SandboxExecResult result;

        private String lastScript;

        ScriptedSandbox(SandboxExecResult result) {
            this.result = result;
        }

        @Override
        public String createSession(SandboxSessionSpec spec) {
            return "s";
        }

        @Override
        public SandboxExecResult exec(String sessionId, Duration timeout, String... command) {
            lastScript = command[command.length - 1];
            return result;
        }

        @Override
        public void copyIn(String sessionId, String destinationPath, InputStream tarArchive) {
        }

        @Override
        public TarArchiveInputStream copyOut(String sessionId, String path) {
            return null;
        }

        @Override
        public void destroySession(String sessionId) {
        }
    }

    private static SandboxExecResult bashStdout(int exitCode, String stdout) {
        return new SandboxExecResult(exitCode, stdout, "", false);
    }

    @Test
    void bash_buildsSpillWrapper_subshellUlimitRedirectTail() {
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=0 bytes=5 lines=1\nhello"));
        new SandboxAgentTools(sandbox, "s").bash("echo hello");
        // Subshell, file-size ulimit, stdin from /dev/null, combined output to the log, bounded tail back.
        assertThat(sandbox.lastScript).contains("( ulimit -f 65536 2>/dev/null; cd /workspace && echo hello )").contains("</dev/null > \"$LOG\" 2>&1").contains("rc=$?")
                .contains("/tmp/hyperion/bash-0.log").contains("__HYP_META__").contains("tail -c 10000");
    }

    @Test
    void bash_withNoCommand_returnsHelpfulErrorWithoutTouchingTheSandbox() {
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "unused"));
        String out = new SandboxAgentTools(sandbox, "s").bash(null);
        assertThat(out).contains("No command provided").contains("\"command\"");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_mangledJsonArrayCommand_isRejectedLoudlyWithoutTouchingTheSandbox() {
        // The mangled array form must be rejected with a non-zero exit and never reach the sandbox.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("[bash, -lc, ls -R]");
        assertThat(out).startsWith("exit=2\n").contains("must be a single shell string").contains("JSON array");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_posixTestCommand_isNotMistakenForAMangledArray() {
        // A POSIX test ("[ -f x ]", space after the bracket) must run normally; only the no-space array render is rejected.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        String out = new SandboxAgentTools(sandbox, "s").bash("[ -f solution/pom.xml ] && echo yes");
        assertThat(out).startsWith("exit=0");
        assertThat(sandbox.lastScript).contains("[ -f solution/pom.xml ] && echo yes");
    }

    @Test
    void bash_authoritativeExitCodeComesFromMetaNotWrapper() {
        // The container exec exit code reflects the wrapper (0); the real command exit code and output must come from the meta line, never a blank success-looking observation.
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=127 bytes=21 lines=1\nsh: nope: not found")), "s").bash("nope");
        assertThat(out).isEqualTo("exit=127\nsh: nope: not found");
    }

    @Test
    void bash_smallOutput_hasNoTruncationMarker() {
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=0 bytes=11 lines=1\nhello world")), "s").bash("echo hello world");
        assertThat(out).isEqualTo("exit=0\nhello world").doesNotContain("Full output");
    }

    @Test
    void bash_blocksSupportedSecretMaterialWithoutReturningTheMatch() {
        String wrapperOutput = "__HYP_META__ rc=0 bytes=43 lines=1\n" + GITHUB_SENTINEL;

        String result = new SandboxAgentTools(new ScriptedSandbox(bashStdout(0, wrapperOutput)), "s").bash("printenv");

        assertThat(result).contains("GITHUB_TOKEN").contains("tool/bash").doesNotContain(GITHUB_SENTINEL);
    }

    @Test
    void bash_largeOutput_appendsSpillMarkerWithReadInstructions() {
        String body = "x".repeat(10_000);
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(1, "__HYP_META__ rc=1 bytes=50000 lines=900\n" + body)), "s").bash("sh verify.sh solution");
        assertThat(out).startsWith("exit=1\n").contains("Showing the last 10000 of 50000 bytes (900 lines total)")
                .contains("Full output saved in the sandbox at /tmp/hyperion/bash-0.log").contains("tail -n 200 /tmp/hyperion/bash-0.log");
    }

    @Test
    void bash_timeoutFailsBecauseTheSandboxWasTerminated() {
        SandboxAgentTools tools = new SandboxAgentTools(new ScriptedSandbox(new SandboxExecResult(-1, "", "", true)), "s");

        assertThatThrownBy(() -> tools.bash("sleep 999")).isInstanceOf(LocalCIException.class).hasMessageContaining("sandbox session was terminated");
    }

    @Test
    void bash_spillSequenceIncrementsPerCall() {
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s");
        tools.bash("a");
        assertThat(sandbox.lastScript).contains("/tmp/hyperion/bash-0.log");
        tools.bash("b");
        assertThat(sandbox.lastScript).contains("/tmp/hyperion/bash-1.log");
    }

    @Test
    void bash_metaAbsent_fallsBackToRawOutput() {
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(3, "unexpected wrapper failure")), "s").bash("x");
        assertThat(out).isEqualTo("exit=3\nunexpected wrapper failure");
    }

    @Test
    void bash_applyPatchCommand_shortCircuitsLoudlyWithoutTouchingTheSandbox() {
        // A bare `apply_patch` must short-circuit with a loud, non-zero result and never reach the sandbox.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("apply_patch");
        assertThat(out).isEqualTo("exit=2\napply_patch is NOT available. Use write_file (new file / full rewrite) or edit_file (exact unique snippet) instead.");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_harnessMutationCommand_shortCircuitsLoudlyWithoutTouchingTheSandbox() {
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));

        String out = new SandboxAgentTools(sandbox, "s").bash("cat > tests/pom.xml <<'EOF'\n<project/>\nEOF");

        assertThat(out).startsWith("exit=2\n").contains("Do not modify tests-repository build/harness files");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_commandMentioningApplyPatch_isNotMistakenForAnInvocation() {
        // A command that merely mentions apply_patch (e.g. grepping for it) must run normally; only the first shell word matters.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        String out = new SandboxAgentTools(sandbox, "s").bash("grep -r apply_patch tests");
        assertThat(out).startsWith("exit=0");
        assertThat(sandbox.lastScript).contains("grep -r apply_patch tests");
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
    void isMangledArrayCommand_matchesOnlyTheRenderedArgvArray() {
        // Rendered argv array (bracket, no space, comma inside): matches.
        assertThat(SandboxAgentTools.isMangledArrayCommand("[bash, -lc, ls -R]")).isTrue();
        assertThat(SandboxAgentTools.isMangledArrayCommand("  [sh, -c, sh verify.sh solution]  ")).isTrue();
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ls -R, grep foo]")).isTrue();
        // POSIX test (space after the bracket): does not match.
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ -f solution/pom.xml ]")).isFalse();
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ -d tests ] && echo y")).isFalse();
        // Single-element render (no comma) and ordinary commands: do not match.
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ls]")).isFalse();
        assertThat(SandboxAgentTools.isMangledArrayCommand("ls -R solution template tests")).isFalse();
        assertThat(SandboxAgentTools.isMangledArrayCommand("grep -n 'a,b' tests/Foo.java")).isFalse();
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
    void verify_whenWiredToTheVerifier_returnsItsStructuredObservation() {
        // The verify tool delegates to the DifferentialVerificationService and returns its observation verbatim.
        RecordingSandbox sandbox = new RecordingSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("t_a", "t_b"), List.of(), List.of(), true, List.of());
        when(verifier.selfCheck(eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), eq(false))).thenReturn(report);

        String out = new SandboxAgentTools(sandbox, "s", verifier, exercise).verify();
        assertThat(out).isEqualTo(report.toObservation());
    }

    @Test
    void verify_blocksSupportedSecretMaterialWithoutReturningTheMatch() {
        RecordingSandbox sandbox = new RecordingSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        AgentVerifyReport report = new AgentVerifyReport(0, false, List.of(), 0, false, false, List.of(), List.of(), List.of(), List.of(), false,
                List.of("failure " + GITHUB_SENTINEL));
        when(verifier.selfCheck(eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), eq(false))).thenReturn(report);

        String result = new SandboxAgentTools(sandbox, "s", verifier, exercise).verify();

        assertThat(result).contains("GITHUB_TOKEN").contains("tool/verify").doesNotContain(GITHUB_SENTINEL).doesNotContain("failure");
    }

    @Test
    void verify_whenVerifierUnavailable_returnsAnActionableFallback() {
        // No verifier (two-arg ctor): the tool must point at the bash fallback rather than NPE.
        String out = new SandboxAgentTools(new RecordingSandbox(), "s").verify();
        assertThat(out).startsWith("ERROR: the verify tool is unavailable").contains("sh verify.sh solution");
    }

    // Stage-aware verify(): DESIGN, TEMPLATE, and STATEMENT short-circuit to an advisory instead of running builds; SOLUTION and TESTS (and an unstaged/legacy null stage) still
    // delegate to the wired verifier.

    private static SandboxAgentTools toolsWiredToAVerifier(RecordingSandbox sandbox, ProgrammingExercise exercise, DifferentialVerificationService verifier,
            AgentVerifyReport report) {
        when(verifier.selfCheck(eq(sandbox), eq("s"), eq(exercise), eq(Map.of()), eq(false))).thenReturn(report);
        return new SandboxAgentTools(sandbox, "s", verifier, exercise);
    }

    @Test
    void verify_inDesignOrTemplateStage_returnsAdvisoryInsteadOfRunningBuilds() {
        // No stubbing on the verifier mock at all (an unstubbed selfCheck() call would return null and blow up toObservation()), so the test fails loudly if the advisory branch
        // ever falls through to the real differential.
        RecordingSandbox sandbox = new RecordingSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);

        for (GenerationStage stage : List.of(GenerationStage.DESIGN, GenerationStage.TEMPLATE)) {
            SandboxAgentTools tools = new SandboxAgentTools(sandbox, "s", verifier, exercise);
            tools.enterStage(stage);

            String out = tools.verify();

            assertThat(out).as("stage %s", stage).isEqualTo(AgentSystemPromptService.STAGE_VERIFY_ADVISORY).contains("SOLUTION, TESTS, and STATEMENT stages");
        }
        verifyNoInteractions(verifier);
    }

    @Test
    void verify_inSolutionOrTestsStage_stillDelegatesToTheVerifier() {
        RecordingSandbox sandbox = new RecordingSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("t_a"), List.of(), List.of(), true, List.of());

        for (GenerationStage stage : List.of(GenerationStage.SOLUTION, GenerationStage.TESTS, GenerationStage.STATEMENT)) {
            DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
            SandboxAgentTools tools = toolsWiredToAVerifier(sandbox, exercise, verifier, report);
            tools.enterStage(stage);

            assertThat(tools.verify()).as("stage %s", stage).isEqualTo(report.toObservation());
        }
    }

    @Test
    void verify_withNoStageEntered_keepsTheLegacyUnstagedBehaviorAndDelegates() {
        // enterStage() never called (currentStage stays null): the legacy single-loop path, where verify is always available.
        RecordingSandbox sandbox = new RecordingSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        DifferentialVerificationService verifier = mock(DifferentialVerificationService.class);
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("t_a"), List.of(), List.of(), true, List.of());
        SandboxAgentTools tools = toolsWiredToAVerifier(sandbox, exercise, verifier, report);

        assertThat(tools.verify()).isEqualTo(report.toObservation());
    }

    @Test
    void agentVerifyReport_observation_truncatesLongNameLists() {
        // A long name list is truncated with a remaining-count so it cannot flood the agent's context.
        List<String> names = java.util.stream.IntStream.range(0, 60).mapToObj(i -> "t" + i).toList();
        AgentVerifyReport report = new AgentVerifyReport(60, true, List.of(), 60, true, true, List.of(), names, List.of(), List.of(), true, List.of());
        assertThat(report.toObservation()).contains("(+20 more)");
    }
}
