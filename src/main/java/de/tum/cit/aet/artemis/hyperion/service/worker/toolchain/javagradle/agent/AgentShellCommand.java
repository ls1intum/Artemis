package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent;

/** Recognizes unsupported command encodings before they reach the sandbox shell. */
final class AgentShellCommand {

    private AgentShellCommand() {
    }

    /**
     * Matches the first shell word rather than a substring, so a command that merely mentions the name ({@code grep apply_patch}) still runs. A path prefix and a trailing
     * heredoc are tolerated.
     */
    static boolean isApplyPatchInvocation(String command) {
        String trimmed = command.strip();
        int firstWordEnd = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '<' || c == '\r') {
                firstWordEnd = i;
                break;
            }
        }
        String firstWord = trimmed.substring(0, firstWordEnd);
        int lastSlash = firstWord.lastIndexOf('/');
        String program = lastSlash >= 0 ? firstWord.substring(lastSlash + 1) : firstWord;
        return "apply_patch".equals(program);
    }

    /**
     * Whether the command is the {@code List.toString()} render of a JSON argv array (e.g. {@code [bash, -lc, ls -R]}), which is what arrives when the model sends
     * {@code {"command":[...]}}
     * instead of a shell string. A POSIX {@code [ -f x ]} test has a space after the bracket and a single-element {@code [foo]} has no comma, so neither is caught.
     */
    static boolean isRenderedArgvArray(String command) {
        String stripped = command.strip();
        int comma = stripped.indexOf(',', 2);
        return stripped.length() >= 4 && stripped.charAt(0) == '[' && " \t\n\u000B\f\r".indexOf(stripped.charAt(1)) < 0 && stripped.endsWith("]") && comma >= 0
                && comma < stripped.length() - 1;
    }

}
