package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * Shared LLM-facing tool descriptions used by {@link SandboxAgentTools} and {@link FileChangeEmittingAgentTools} so their tool contracts remain identical.
 */
final class AgentToolDescriptions {

    private AgentToolDescriptions() {
    }

    static final String READ_FILE = "Read a UTF-8 text file in the workspace. The path is workspace-relative (e.g. 'solution/src/Calculator.java'). Prefer this over 'cat'. Output is limited to about 10000 characters; a longer file returns its first part plus a footer naming the exact offset to continue from — repeat with that offset until you have what you need. Use offset (1-indexed line) and limit (line count) to read a specific slice. To find one thing in a large file, use bash with grep instead.";

    static final String READ_FILE_PATH = "workspace-relative path to read, e.g. 'tests/test/sorting/SortTest.java'";

    static final String READ_FILE_OFFSET = "1-indexed line number to start reading from (omit to start at the top)";

    static final String READ_FILE_LIMIT = "maximum number of lines to return (omit to read to the output limit)";

    static final String SEARCH = "Search a UTF-8 workspace file or directory recursively for an exact text fragment. Returns paths, line numbers, and matching lines without changing the workspace. Use this instead of bash/grep.";

    static final String SEARCH_PATH = "workspace-relative file or directory path to search";

    static final String SEARCH_QUERY = "exact non-empty text fragment to find on a line";

    static final String WRITE_FILE = "Write the full content of a workspace file, creating it (and any parent directories) or overwriting it if it exists. Use only for new files or complete rewrites; for small changes to an existing file use edit_file. The path is workspace-relative.";

    static final String WRITE_FILE_PATH = "workspace-relative path to write, e.g. 'solution/palindrome.py'";

    static final String WRITE_FILE_CONTENT = "the complete new content of the file";

    static final String EDIT_FILE = "Replace an exact, unique snippet in an existing workspace file. 'oldText' must match the file byte-for-byte including whitespace and newlines, and must occur exactly once — keep it as small as possible while still unique, do not pad with unchanged lines. Prefer this over write_file for small, targeted changes.";

    static final String EDIT_FILE_PATH = "workspace-relative path to edit";

    static final String EDIT_FILE_OLD_TEXT = "the exact existing text to replace, byte-for-byte; must be unique in the file";

    static final String EDIT_FILE_NEW_TEXT = "the replacement text";

    static final String DELETE_FILE = "Delete one generated workspace file. Deleting an absent file is a reported no-op. Rejected writes are atomic, so never delete a path "
            + "after write_file says the workspace is unchanged. Use this when a file was created at the wrong path or is no longer part of the exercise. The path is "
            + "workspace-relative; directories and Artemis-managed build infrastructure cannot be deleted.";

    static final String DELETE_FILE_PATH = "workspace-relative path to delete";

    static final String BASH = "Run a shell command in the workspace, e.g. {\"command\":\"ls -R\"}. Send the command as a single string (NOT a JSON array). Returns its exit code plus combined stdout/stderr. Use it for workspace inspection and targeted diagnostics when the structured tools do not expose enough detail. Do not treat raw Maven, Gradle, or verify.sh commands as an acceptance verdict: use the dedicated verify tool, which runs the grader-equivalent check and parses its reports. Long output is truncated to the LAST 10000 characters; the COMPLETE output is saved in the sandbox to /tmp/hyperion/bash-<n>.log, so inspect earlier parts with sed/grep/head/tail on that file. Prefer search or targeted grep/sed over re-reading whole files.";

    static final String BASH_COMMAND = "the shell command to run, as ONE string (not a JSON array), e.g. 'ls -R' or 'grep -n sort tests/test/sorting/SortTest.java'";

    static final String VERIFY = "Run the mechanical precheck. In an unstaged/legacy session this always builds the solution and template, parses reports with the SAME production parser, and returns exact test names, pass/fail results, bounded failure evidence, binding problems, and mechanical gate results. In staged generation, verify checks the CURRENT stage's artifact at the right depth instead: SPEC scans SPEC.md's required sections and evidence (no build), TESTS runs the full solution/template differential exactly like the unstaged path, and STATEMENT resolves [task] bindings against the TESTS stage's exact test names (no build). Use the failure evidence to spot assertion, exception, or shared setup failures. A passing precheck does not prove semantic relevance; authoritative post-loop verification determines save eligibility, while quality review may request repairs or flag instructor review. Call it after changes and iterate until the mechanical precheck passes before submitting. Each call re-runs its check (no cache).";

    static final String SUBMIT = "Submit the finished exercise (or, in staged generation, this stage's artifact) for verification and end this loop. In staged generation, submit itself re-runs THIS STAGE's mechanical check first; if it fails, the submission is rejected with the same report `verify` would show and the session continues instead of ending — fix the reported issues and call submit again. Only call this once you expect the check to pass. Stop immediately after a successful submit.";

    static final String SUBMIT_SUMMARY = "one-line summary of what you created or changed";
}
