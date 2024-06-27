package de.tum.in.www1.artemis.versioning;

import java.lang.annotation.Annotation;

import de.tum.in.www1.artemis.exception.ApiVersionRangeNotValidException;

/**
 * Holds logic for creating new {@link VersionRange} annotations.
 */
public class VersionRangeFactory {

    public static VersionRange getInstanceOfVersionRange(int start) {
        return getInstanceOfVersionRange(start, VersionRange.UNDEFINED);
    }

    /**
     * Factory method for {@link VersionRange} instances.
     *
     * @param start the starting version of the range
     * @param end   the ending version of the range, see {@link VersionRange#UNDEFINED} for an undefined end
     * @return the created {@link VersionRange} instance
     */
    public static VersionRange getInstanceOfVersionRange(int start, int end) {
        return new VersionRange() {

            @Override
            public Class<? extends Annotation> annotationType() {
                return VersionRange.class;
            }

            @Override
            public int value() {
                return start;
            }

            @Override
            public int start() {
                return start;
            }

            @Override
            public int end() {
                return end;
            }
        };
    }

    /**
     * Combines two potentially combinable ranges into one range.
     *
     * @param rangeA The first range
     * @param rangeB The second range
     * @return The combined range
     * @throws ApiVersionRangeNotValidException if the ranges can't be combined
     */
    public static VersionRange combine(VersionRange rangeA, VersionRange rangeB) {
        if (rangeA.end() == VersionRange.UNDEFINED && rangeB.end() == VersionRange.UNDEFINED) {
            // Both are infinite. Redefine start limit
            if (rangeA.start() < rangeB.start()) {
                return getInstanceOfVersionRange(rangeA.start());
            }
            else {
                return getInstanceOfVersionRange(rangeB.start());
            }
        }
        else if (rangeA.end() == VersionRange.UNDEFINED || rangeB.end() == VersionRange.UNDEFINED) {
            // One is finite and one is infinite.
            int limit;
            VersionRange range;
            if (rangeA.end() == VersionRange.UNDEFINED) {
                limit = rangeA.start();
                range = rangeB;
            }
            else {
                limit = rangeB.start();
                range = rangeA;
            }

            if (range.end() + 1 < limit) {
                // there is a range and then there is a start limit afterward => not a valid combination
                throw new ApiVersionRangeNotValidException();
            }
            else if (limit <= range.start()) {
                // start limit dominates range (includes)
                return getInstanceOfVersionRange(limit);
            }
            else {
                // start limit is within the range => create a start limit from the beginning of range
                return getInstanceOfVersionRange(range.start());
            }
        }
        else {
            // Both are finite. If they don't overlap, we can't combine them, otherwise we return the combined range
            if ((rangeA.end() < rangeB.start() && rangeA.end() + 1 < rangeB.start()) || (rangeB.end() < rangeA.start() && rangeB.end() + 1 < rangeA.start())) {
                // Not concatenated ranges => not valid combination
                throw new ApiVersionRangeNotValidException();
            }
            else {
                int newStart = Math.min(rangeA.start(), rangeB.start());
                int newEnd = Math.max(rangeA.end(), rangeB.end());
                return getInstanceOfVersionRange(newStart, newEnd);
            }
        }
    }
}
