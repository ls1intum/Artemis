package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The FeedbackType enumeration.
 * <p>
 * The order in which they are declared is important, as we use the enums default implementation of
 * <a href="https://docs.oracle.com/javase/10/docs/api/java/lang/Enum.html#compareTo(E)">compareTo()</a>
 * <p>
 * Note: The order is used as part of an {@link javax.persistence.EnumType#ORDINAL} mapping in {@link de.tum.in.www1.artemis.domain.Feedback}.
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
