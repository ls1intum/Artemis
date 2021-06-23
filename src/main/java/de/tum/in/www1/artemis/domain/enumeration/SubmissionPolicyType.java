package de.tum.in.www1.artemis.domain.enumeration;

/**
 * Type of Submission Policy that is active for one particular programming exercise
 */
public enum SubmissionPolicyType {

    /**
     * Indicates that the programming exercise does not have a submission policy.
     * If this type is set, no SubmissionPolicy object will be loaded from the
     * database.
     */
    NONE,

    /**
     * Indicates that the programming exercise has the "Lock Student Repository
     * after x Submissions" - Policy
     */
    LOCK_REPOSITORY,

    /**
     * Indicates that the programming exercise has the "x% Penalty after y
     * Submissions" - Policy
     */
    SUBMISSION_PENALTY
}
