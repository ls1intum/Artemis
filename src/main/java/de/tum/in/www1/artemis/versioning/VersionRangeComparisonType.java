package de.tum.in.www1.artemis.versioning;

/**
 * Holds the different types of version range comparisons.
 */
public enum VersionRangeComparisonType {

    FIRST_A_NO_INTERSECT, A_THEN_B, A_INCLUDES_B, EQUALS, B_INCLUDES_A, B_THEN_A, FIRST_B_NO_INTERSECT;

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
