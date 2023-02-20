package de.tum.in.www1.artemis.domain.enumeration;

/**
 * The FeedbackType enumeration.
 * The order in which they are declared is important, as we use the enums default implementation of
 * <a href="https://docs.oracle.com/javase/10/docs/api/java/lang/Enum.html#compareTo(E)">compareTo()</a>
 */
public enum FeedbackType {
    MANUAL, MANUAL_UNREFERENCED, AUTOMATIC_ADAPTED, AUTOMATIC
}
