package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the agent tools, focused on the security-relevant path allowlist and the all-or-nothing edit semantics. A fake sandbox records the commands it is asked to run
 * so the tests can assert that unsafe paths never reach the shell.
 */
class SandboxAgentToolsTest {

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
    void bash_largeOutput_appendsSpillMarkerWithReadInstructions() {
        String body = "x".repeat(10_000);
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(1, "__HYP_META__ rc=1 bytes=50000 lines=900\n" + body)), "s").bash("sh verify.sh solution");
        assertThat(out).startsWith("exit=1\n").contains("Showing the last 10000 of 50000 bytes (900 lines total)")
                .contains("Full output saved in the sandbox at /tmp/hyperion/bash-0.log").contains("tail -n 200 /tmp/hyperion/bash-0.log");
    }

    @Test
    void bash_timeout_pointsAtPartialSpillFile() {
        String out = new SandboxAgentTools(new ScriptedSandbox(new SandboxExecResult(-1, "", "", true)), "s").bash("sleep 999");
        assertThat(out).contains("exit=timeout").contains("Partial output was written in the sandbox to /tmp/hyperion/bash-0.log");
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
    void verify_whenWiredToTheVerifier_returnsItsStructuredObservation() {
        // The verify tool delegates to the AuthoritativeVerificationService and returns its observation verbatim.
        RecordingSandbox sandbox = new RecordingSandbox();
        ProgrammingExercise exercise = new ProgrammingExercise();
        AuthoritativeVerificationService verifier = mock(AuthoritativeVerificationService.class);
        AgentVerifyReport report = new AgentVerifyReport(2, true, List.of(), 2, true, true, List.of(), List.of("t_a", "t_b"), List.of(), List.of(), true, List.of());
        when(verifier.selfCheck(eq(sandbox), eq("s"), eq(exercise))).thenReturn(report);

        String out = new SandboxAgentTools(sandbox, "s", verifier, exercise).verify();
        assertThat(out).isEqualTo(report.toObservation()).contains("Solution: 2/2 tests pass.").contains("VERDICT: would be ACCEPTED");
    }

    @Test
    void verify_whenVerifierUnavailable_returnsAnActionableFallback() {
        // The test-only two-arg constructor leaves the verifier absent; the tool must say so and point at the bash fallback rather than NPE.
        String out = new SandboxAgentTools(new RecordingSandbox(), "s").verify();
        assertThat(out).startsWith("ERROR: the verify tool is unavailable").contains("sh verify.sh solution");
    }

    @Test
    void agentVerifyReport_observation_truncatesLongNameLists() {
        // A huge suite must not flood the agent's context: the observation truncates a long name list with a remaining-count.
        List<String> names = java.util.stream.IntStream.range(0, 60).mapToObj(i -> "t" + i).toList();
        AgentVerifyReport report = new AgentVerifyReport(60, true, List.of(), 60, true, true, List.of(), names, List.of(), List.of(), true, List.of());
        assertThat(report.toObservation()).contains("(+20 more)");
    }
}
