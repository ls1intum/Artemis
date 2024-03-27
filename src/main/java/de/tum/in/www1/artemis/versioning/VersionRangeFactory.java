package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.versioning.VersionRangeService.versionRangeToIntegerList;

import java.lang.annotation.Annotation;
import java.util.List;

import de.tum.in.www1.artemis.exception.ApiVersionRangeNotValidException;

/**
 * Holds logic for creating new {@link VersionRange} annotations.
 */
public class VersionRangeFactory {

    /**
     * Factory method for {@link VersionRange} instances.
     *
     * @param value the version numbers to pass
     * @return the created {@link VersionRange} instance
     */
    public static VersionRange getInstanceOfVersionRange(int... value) {
        return new VersionRange() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return VersionRange.class;
            }

            @Override
            public int[] value() {
                return value;
            }
        };
    }

    /**
     * Combines two combinable ranges into one range.
     *
     * @param range1 The first range
     * @param range2 The second range
     * @return The combined range
     * @throws ApiVersionRangeNotValidException if the ranges are not valid or the two ranges can't be combined
     */
    public static VersionRange combine(VersionRange range1, VersionRange range2) {
        List<Integer> versions1 = versionRangeToIntegerList(range1);
        List<Integer> versions2 = versionRangeToIntegerList(range2);
        if (versions1.size() == 2 && versions2.size() == 2) {
            // Both are finite. If they don't overlap, we can't combine them, otherwise we return the combined range
            if ((versions1.get(1) < versions2.get(0) && versions1.get(1) + 1 < versions2.get(0))
                    || (versions2.get(1) < versions1.get(0) && versions2.get(1) + 1 < versions1.get(0))) {
                // Not concatenated ranges => not valid combination
                throw new ApiVersionRangeNotValidException();
            }
            else {
                int newStart = Math.min(versions1.get(0), versions2.get(0));
                int newEnd = Math.max(versions1.get(1), versions2.get(1));
                return getInstanceOfVersionRange(newStart, newEnd);
            }
        }
        else if ((versions1.size() == 2 && versions2.size() == 1) || (versions1.size() == 1 && versions2.size() == 2)) {
            // One is finite and one is infinite.
            int limit = versions1.size() == 1 ? versions1.get(0) : versions2.get(0);
            List<Integer> range = versions1.size() == 2 ? versions1 : versions2;

            if (range.get(1) + 1 < limit) {
                // there is a range and then there is a start limit afterwards => not a valid combination
                throw new ApiVersionRangeNotValidException();
            }
            else if (limit <= range.get(0)) {
                // start limit dominates range (includes)
                return getInstanceOfVersionRange(limit);
            }
            else {
                // start limit is within the range => create a start limit from the beginning of range
                return getInstanceOfVersionRange(range.get(0));
            }
        }
        else if (versions1.size() == 1 && versions2.size() == 1) {
            // Both are infinite. Redefine start limit
            if (versions1.get(0) < versions2.get(0)) {
                return getInstanceOfVersionRange(versions1.get(0));
            }
            else {
                return getInstanceOfVersionRange(versions2.get(0));
            }
        }

        // There should be no other case as we except both lists to be of size 1 or 2
        throw new ApiVersionRangeNotValidException();
    }
}
