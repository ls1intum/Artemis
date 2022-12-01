package de.tum.in.www1.artemis.versioning;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides functionality to work with {link VersionRange} annotations.
 */
public class VersionRangeService {

    /**
     * Converts the values of the range in a list of integers
     * @param range The range to convert
     * @return The list of integers representing versions
     */
    public static List<Integer> versionRangeToIntegerList(VersionRange range) {
        List<Integer> list = new ArrayList<>();
        int[] versions = range.value();
        for (int version : versions) {
            list.add(version);
        }
        return list;
    }

}
