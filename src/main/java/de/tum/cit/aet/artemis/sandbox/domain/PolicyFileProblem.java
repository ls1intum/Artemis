package de.tum.cit.aet.artemis.sandbox.domain;

/**
 * Why a policy file cannot be used or checked.
 */
public enum PolicyFileProblem {

    /** The path leaves the repository, is absolute, points into {@code .git} or to a symbolic link or directory. */
    INVALID_PATH,
    /** The file does not exist, so Ares fails the tests with {@code security.policy.reader.path.not.exists}. */
    MISSING,
    /** The file is larger than the Security Framework reads. */
    TOO_LARGE,
    /** The file is not a valid policy. */
    UNPARSEABLE,
    /** The file is valid, but its list of test classes cannot be located, so it is neither checked nor updated. */
    TEST_CLASS_LIST_NOT_FOUND;

    /**
     * @return whether Ares fails the tests or the state cannot be determined because of this problem
     */
    public boolean breaksTheSandbox() {
        return this != TEST_CLASS_LIST_NOT_FOUND;
    }
}
