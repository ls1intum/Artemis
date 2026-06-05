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
        // The command runs in a subshell (so its `exit` cannot abort the wrapper), under a file-size ulimit, with stdin from /dev/null and combined output redirected to the log;
        // only the bounded tail is streamed back.
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
        // Spring AI coerces {"command":["bash","-lc","ls -R"]} to the literal "[bash, -lc, ls -R]". That is not a runnable command; the tool must reject it with a NON-ZERO exit
        // and
        // a single corrective instruction, and never reach the sandbox (otherwise the agent blindly retries the array form).
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("[bash, -lc, ls -R]");
        assertThat(out).startsWith("exit=2\n").contains("must be a single shell string").contains("JSON array");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_posixTestCommand_isNotMistakenForAMangledArray() {
        // A genuine POSIX test has a SPACE after the bracket ("[ -f x ]"), so it must run normally and only the no-space array render is rejected.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=0 bytes=1 lines=1\nx"));
        String out = new SandboxAgentTools(sandbox, "s").bash("[ -f solution/pom.xml ] && echo yes");
        assertThat(out).startsWith("exit=0");
        assertThat(sandbox.lastScript).contains("[ -f solution/pom.xml ] && echo yes");
    }

    @Test
    void bash_failingCommand_surfacesTheRealNonZeroExitAndStderr() {
        // OBSERVATION INTEGRITY: a genuinely failing command must report its REAL non-zero exit and its output, never a blank success-looking observation (the old array bug looked
        // like success). The authoritative exit code comes from the wrapper's meta line, not the container exec's own code.
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=127 bytes=21 lines=1\nsh: nope: not found")), "s").bash("nope");
        assertThat(out).isEqualTo("exit=127\nsh: nope: not found");
        assertThat(out).doesNotContain("exit=0");
    }

    @Test
    void bash_authoritativeExitCodeComesFromMetaNotWrapper() {
        // The container exec exit code reflects the wrapper (0); the real command exit code (7) must be taken from the meta line.
        String out = new SandboxAgentTools(new ScriptedSandbox(bashStdout(0, "__HYP_META__ rc=7 bytes=2 lines=1\nno")), "s").bash("false");
        assertThat(out).isEqualTo("exit=7\nno");
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
        // A bare `apply_patch` invocation: the tool does not exist, so a real shell would leave the workspace UNCHANGED while the agent believes the edit landed. The tool must
        // short-circuit with a loud, non-zero result and never reach the sandbox.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("apply_patch");
        assertThat(out).isEqualTo("exit=2\napply_patch is NOT available. Use write_file (new file / full rewrite) or edit_file (exact unique snippet) instead.");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_applyPatchHeredoc_shortCircuits() {
        // The most common form the model emits: an apply_patch heredoc. The first word is still apply_patch, so it is caught and the sandbox is never touched.
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("apply_patch <<'PATCH'\n*** Begin Patch\n*** End Patch\nPATCH");
        assertThat(out).startsWith("exit=2\napply_patch is NOT available");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_applyPatchWithRedirect_shortCircuits() {
        ScriptedSandbox sandbox = new ScriptedSandbox(bashStdout(0, "should not run"));
        String out = new SandboxAgentTools(sandbox, "s").bash("apply_patch < my.patch");
        assertThat(out).startsWith("exit=2\napply_patch is NOT available");
        assertThat(sandbox.lastScript).isNull();
    }

    @Test
    void bash_commandMentioningApplyPatch_isNotMistakenForAnInvocation() {
        // A genuine command that merely MENTIONS apply_patch (e.g. grepping for it) must run normally — only the FIRST shell word being apply_patch is short-circuited.
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
        // The List.toString() render of a JSON argv array: opening bracket, no space, a comma inside, closing bracket.
        assertThat(SandboxAgentTools.isMangledArrayCommand("[bash, -lc, ls -R]")).isTrue();
        assertThat(SandboxAgentTools.isMangledArrayCommand("  [sh, -c, sh verify.sh solution]  ")).isTrue();
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ls -R, grep foo]")).isTrue();
        // A genuine POSIX test has a space right after the bracket, so it is NOT a mangled array.
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ -f solution/pom.xml ]")).isFalse();
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ -d tests ] && echo y")).isFalse();
        // A single-element render has no comma, so it does not match (and is harmless as a shell glob).
        assertThat(SandboxAgentTools.isMangledArrayCommand("[ls]")).isFalse();
        // Ordinary commands never match.
        assertThat(SandboxAgentTools.isMangledArrayCommand("ls -R solution template tests")).isFalse();
        assertThat(SandboxAgentTools.isMangledArrayCommand("grep -n 'a,b' tests/Foo.java")).isFalse();
    }

    @Test
    void verify_whenWiredToTheVerifier_returnsItsStructuredObservation() {
        // The verify tool delegates to the SAME AuthoritativeVerificationService the post-loop acceptance gate uses, and returns its agent-readable observation verbatim.
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
