package de.tum.in.www1.artemis.versioning;

/**
 * Holds the different types of version range comparisons.
 */
public enum VersionRangeComparisonType {

    // e.g. [1,2] and [4,5]
    FIRST_A_NO_INTERSECT,
    // e.g. [1,4] and [2,5]
    A_THEN_B,
    // e.g. [1,4] and [1,3]
    A_INCLUDES_B,
    // e.g. [1,2] and [1,2]
    EQUALS,
    // e.g. [1,3] and [1,4]
    B_INCLUDES_A,
    // e.g. [2,5] and [1,4]
    B_THEN_A,
    // e.g. [4,5] and [1,2]
    FIRST_B_NO_INTERSECT;

    /**
     * @return The inverted comparison type
     */
    public VersionRangeComparisonType invert() {
        return switch (this) {
            case FIRST_B_NO_INTERSECT -> FIRST_A_NO_INTERSECT;
            case B_THEN_A -> A_THEN_B;
            case B_INCLUDES_A -> A_INCLUDES_B;
            case EQUALS -> EQUALS;
            case A_INCLUDES_B -> B_INCLUDES_A;
            case A_THEN_B -> B_THEN_A;
            case FIRST_A_NO_INTERSECT -> FIRST_B_NO_INTERSECT;
        };
    }
}
