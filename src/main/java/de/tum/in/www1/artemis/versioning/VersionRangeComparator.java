package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeComparisonType.*;
import static de.tum.in.www1.artemis.versioning.VersionRangeService.versionRangeToIntegerList;

import java.util.List;

import de.tum.in.www1.artemis.exception.ApiVersionRangeNotValidException;

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
        var versions1 = versionRangeToIntegerList(a);
        var versions2 = versionRangeToIntegerList(b);
        if (versions1.size() == 1 && versions2.size() == 1) {
            return compareTwoLimits(versions1, versions2);
        }
        else if (versions1.size() == 2 && versions2.size() == 2) {
            return compareTwoRanges(versions1, versions2);
        }
        else if (versions1.size() == 1 && versions2.size() == 2) {
            return compareLimitAndRange(versions1.getFirst(), versions2);
        }
        else if (versions1.size() == 2 && versions2.size() == 1) {
            return compareLimitAndRange(versions2.getFirst(), versions1).invert();
        }
        throw new ApiVersionRangeNotValidException();
    }

    private static VersionRangeComparisonType compareTwoLimits(List<Integer> limit1, List<Integer> limit2) {
        var result = Integer.compare(limit1.getFirst(), limit2.getFirst());
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

    private static VersionRangeComparisonType compareLimitAndRange(int limitVersion, List<Integer> range) {
        var result = Integer.compare(limitVersion, range.get(0));
        if (result < 1) {
            // Range starts with or after the limit starts
            return A_INCLUDES_B;
        }
        else if (range.get(1) + 1 < limitVersion) {
            // Range starts end ends before the limit
            return FIRST_B_NO_INTERSECT;
        }
        else {
            // Range starts before the limit does, but intersects
            return B_THEN_A;
        }
    }

    private static VersionRangeComparisonType compareTwoRanges(List<Integer> range1, List<Integer> range2) {
        int startResult = Integer.compare(range1.get(0), range2.get(0));
        int endResult = Integer.compare(range1.get(1), range2.get(1));

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
            if (range1.get(1) + 1 < range2.getFirst()) {
                return FIRST_A_NO_INTERSECT;
            }
            else {
                return A_THEN_B;
            }
        }
        else {
            if (endResult < 1) {
                return B_INCLUDES_A;
            }
            if (range2.get(1) + 1 < range1.getFirst()) {
                return FIRST_B_NO_INTERSECT;
            }
            else {
                return B_THEN_A;
            }
        }
    }
}
