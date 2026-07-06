package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * The shared LLM-facing {@code @Tool}/{@code @ToolParam} description strings used by {@link SandboxAgentTools} and its streaming decorator {@link FileSnapshotEmittingAgentTools},
 * which expose an identical tool surface to the model (the decorator only adds snapshot emission). Referencing these compile-time constants from both classes' annotations keeps
 * the
 * two tool contracts from diverging.
 */
final class AgentToolDescriptions {

    private AgentToolDescriptions() {
    }

    static final String READ_FILE = "Read a UTF-8 text file in the workspace and return its full contents. The path is workspace-relative (e.g. 'solution/src/Calculator.java'). Prefer this over 'cat'. For a large file, or to find one thing, use bash with grep/sed instead of reading the whole file.";

    static final String READ_FILE_PATH = "workspace-relative path to read, e.g. 'tests/test/sorting/SortTest.java'";

    static final String WRITE_FILE = "Write the full content of a workspace file, creating it (and any parent directories) or overwriting it if it exists. Use only for new files or complete rewrites; for small changes to an existing file use edit_file. The path is workspace-relative.";

    static final String WRITE_FILE_PATH = "workspace-relative path to write, e.g. 'solution/palindrome.py'";

    static final String WRITE_FILE_CONTENT = "the complete new content of the file";

    static final String EDIT_FILE = "Replace an exact, unique snippet in an existing workspace file. 'oldText' must match the file byte-for-byte including whitespace and newlines, and must occur exactly once — keep it as small as possible while still unique, do not pad with unchanged lines. Prefer this over write_file for small, targeted changes.";

    static final String EDIT_FILE_PATH = "workspace-relative path to edit";

    static final String EDIT_FILE_OLD_TEXT = "the exact existing text to replace, byte-for-byte; must be unique in the file";

    static final String EDIT_FILE_NEW_TEXT = "the replacement text";

    static final String BASH = "Run a shell command in the workspace, e.g. {\"command\":\"ls -R\"}. Send the command as a single string (NOT a JSON array). Returns its exit code plus combined stdout/stderr. Use it to run 'sh verify.sh solution' / 'sh verify.sh template', inspect the project, and debug. Long output is truncated to the LAST 10000 characters (build failures and the verify.sh HYPERION_COLLECTED line are at the end); the COMPLETE output is saved in the sandbox to /tmp/hyperion/bash-<n>.log, so read earlier parts with sed/grep/head/tail on that file. After a verify.sh run the test reports are collected under /opt/hyperion/reports/<solution|template>/ — grep them for exact test names and pass/fail. Prefer grep/sed here over re-reading whole files.";

    static final String BASH_COMMAND = "the shell command to run, as ONE string (not a JSON array), e.g. 'sh verify.sh solution', 'ls -R', or 'grep -n sort tests/test/sorting/SortTest.java'";

    static final String VERIFY = "Run the authoritative self-check: builds the solution and the template, parses the test reports with the SAME production parser the final grader uses, and returns which tests pass/fail on each, the EXACT test names to bind your [task]s to (copy them verbatim — never guess), any template tests that wrongly pass, and a VERDICT. This is your primary self-check — call it after changes and iterate until the VERDICT says ACCEPTED before you submit. Each call re-runs both builds (no cache); it takes the same time as one 'sh verify.sh solution' plus one 'sh verify.sh template'.";

    static final String SUBMIT = "Submit the finished exercise for authoritative verification and end the session. Only call this after the 'verify' tool's VERDICT says ACCEPTED. Stop immediately after calling it.";

    static final String SUBMIT_SUMMARY = "one-line summary of what you created or changed";

    static final String EXAMINER_SUBMIT = "Submit your independently-authored test suite and end the session. Call this once your tests COMPILE against the template (you have run 'sh verify.sh template' via bash and it builds without a compile error) and cover every stated postcondition/edge case of the problem statement. Do NOT try to make tests pass — you do not have the reference solution. Stop immediately after calling it.";
}
