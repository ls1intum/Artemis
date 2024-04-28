package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.A_CUT_B;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.A_INCLUDES_B;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.A_THEN_B;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.B_CUT_A;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.B_INCLUDES_A;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.B_THEN_A;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.EQUALS;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.FIRST_A_NO_INTERSECT;
import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.FIRST_B_NO_INTERSECT;

/**
 * Holds comparison logic for {@link VersionRange} annotations.
 */
public class VersionRangeComparator {

    /**
     * @param a Item containing two versions sorted ASC
     * @param b Item to be compared to containing two versions sorted ASC
     * @return A {@link VersionRangeComparisonType}
     */
    public static VersionRangeComparisonType compare(VersionRange a, VersionRange b) {
        if (a.end() == VersionRange.UNDEFINED && b.end() == VersionRange.UNDEFINED) {
            return compareTwoLimits(a.start(), b.start());
        }
        else if (a.end() == VersionRange.UNDEFINED) {
            return compareLimitAndRange(a.start(), b);
        }
        else if (b.end() == VersionRange.UNDEFINED) {
            return compareLimitAndRange(b.start(), a).invert();
        }
        else {
            return compareTwoRanges(a, b);
        }
    }

    private static VersionRangeComparisonType compareTwoLimits(int a, int b) {
        var result = Integer.compare(a, b);
        // This could be simplified but for better readability (Constants) it is not
        if (result < 0) {
            // First limit starts after second one. Since both are infinite second one includes first one
            return A_INCLUDES_B;
        }
        else if (result > 0) {
            // First limit starts before second one. Since both are infinite first one includes second one
            return B_INCLUDES_A;
        }
        else {
            // Both start the same point
            return EQUALS;
        }
    }

    private static VersionRangeComparisonType compareLimitAndRange(int limitVersion, VersionRange range) {
        var result = Integer.compare(limitVersion, range.start());
        if (result < 1) {
            // Range starts with or after the limit starts
            return A_INCLUDES_B;
        }
        else if (range.end() + 1 < limitVersion) {
            // Range starts end ends before the limit
            return FIRST_B_NO_INTERSECT;
        }
        else if (range.end() < limitVersion) {
            // Range touches the limit
            return B_THEN_A;
        }
        else {
            // Range starts before the limit does, but intersects
            return B_CUT_A;
        }
    }

    private static VersionRangeComparisonType compareTwoRanges(VersionRange rangeA, VersionRange rangeB) {
        int startResult = Integer.compare(rangeA.start(), rangeB.start());
        int endResult = Integer.compare(rangeA.end(), rangeB.end());

        if (startResult == 0 && endResult == 0) {
            return EQUALS;
        }
        else if (startResult == 0) {
            if (endResult < 0) {
                return B_INCLUDES_A;
            }
            else {
                return A_INCLUDES_B;
            }
        }
        else if (endResult == 0) {
            if (startResult < 0) {
                return A_INCLUDES_B;
            }
            else {
                return B_INCLUDES_A;
            }
        }
        else if (startResult < 0) {
            if (endResult > -1) {
                return A_INCLUDES_B;
            }
            if (rangeA.end() + 1 < rangeB.start()) {
                return FIRST_A_NO_INTERSECT;
            }
            else if (rangeA.end() < rangeB.start()) {
                return A_THEN_B;
            }
            else {
                return A_CUT_B;
            }
        }
        else {
            if (endResult < 1) {
                return B_INCLUDES_A;
            }
            if (rangeB.end() + 1 < rangeA.start()) {
                return FIRST_B_NO_INTERSECT;
            }
            else if (rangeB.end() < rangeA.start()) {
                return B_THEN_A;
            }
            else {
                return B_CUT_A;
            }
        }
    }
}
