package de.tum.cit.aet.artemis.assessment.domain;

/**
 * The FeedbackType enumeration.
 * <p>
 * The order in which they are declared is important, as we use the enums default implementation of
 * <a href="https://docs.oracle.com/javase/10/docs/api/java/lang/Enum.html#compareTo(E)">compareTo()</a>
 * <p>
 * Note: The order is used as part of an {@link jakarta.persistence.EnumType#ORDINAL} mapping in {@link Feedback}.
 * Do NOT change the order of existing values.
 */
public enum FeedbackType {
    /**
     * ordinal = 0
     */
    MANUAL,
    /**
     * ordinal = 1
     */
    MANUAL_UNREFERENCED,
    /**
     * ordinal = 2
     */
    AUTOMATIC_ADAPTED,
    /**
     * ordinal = 3
     */
    AUTOMATIC,
}
