package de.tum.cit.aet.artemis.sandbox.domain;

import org.jspecify.annotations.Nullable;

/**
 * The content of a policy file as far as it could be read.
 *
 * @param problem why the file cannot be used; absent if it was read
 * @param content the content; absent if there is a problem
 */
public record PolicyFileContent(@Nullable PolicyFileProblem problem, @Nullable String content) {

    /**
     * @param content the content of a file that was read
     * @return the content without a problem
     */
    public static PolicyFileContent of(String content) {
        return new PolicyFileContent(null, content);
    }

    /**
     * @param problem why the file cannot be used
     * @return the problem without content
     */
    public static PolicyFileContent failed(PolicyFileProblem problem) {
        return new PolicyFileContent(problem, null);
    }
}
