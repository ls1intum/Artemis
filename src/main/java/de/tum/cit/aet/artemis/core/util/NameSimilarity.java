package de.tum.cit.aet.artemis.core.util;

import java.util.Objects;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public class NameSimilarity {

    /**
     * Analyzes the similarity between two given strings by calculating a Levenshtein simple ratio.
     *
     * @param string1 the first of the two strings that should be compared
     * @param string2 the second of the two strings that should be compared
     * @return the Levenshtein simple ratio between the two input strings
     */
    public static double levenshteinSimilarity(String string1, String string2) {
        if (Objects.equals(string1, string2)) {
            return 1;
        }
        if (string1 == null || string2 == null) {
            return 0;
        }

        return FuzzySearch.ratio(string1, string2) / 100.0;
    }
}
